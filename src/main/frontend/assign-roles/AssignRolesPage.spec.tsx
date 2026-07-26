import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { StrategyClient } from "../common/api/strategy.ts";
import { checkSidName } from "../common/api/validation.ts";
import type {
  AssignRolesBootstrap,
  AssignRoleTypeBootstrap,
} from "../common/types/bootstrap.ts";
import { AssignRolesPage } from "./AssignRolesPage.tsx";

vi.mock("../common/api/validation.ts", () => ({
  checkSidName: vi.fn().mockResolvedValue(""),
}));

const typeBootstrap = (
  overrides: Partial<AssignRoleTypeBootstrap> = {},
): AssignRoleTypeBootstrap => ({
  visible: true,
  canEdit: true,
  roles: [{ name: "admin", permissionIds: [] }],
  entries: [],
  ...overrides,
});

const bootstrap = (
  overrides: Partial<AssignRolesBootstrap> = {},
): AssignRolesBootstrap => ({
  globalRoles: typeBootstrap({
    roles: [
      { name: "admin", permissionIds: [] },
      { name: "reader", permissionIds: [] },
    ],
    entries: [{ name: "alice", type: "USER", roles: ["admin"] }],
  }),
  projectRoles: typeBootstrap({
    roles: [{ name: "dev", pattern: "dev-.*", permissionIds: [] }],
    entries: [{ name: "devs", type: "GROUP", roles: ["dev"] }],
  }),
  slaveRoles: typeBootstrap({ roles: [], entries: [] }),
  ...overrides,
});

const createClient = (): StrategyClient => ({
  addTemplate: vi.fn(),
  removeTemplates: vi.fn(),
  addRole: vi.fn(),
  removeRoles: vi.fn(),
  getMatchingJobs: vi.fn(),
  getMatchingAgents: vi.fn(),
  assignSidRole: vi.fn().mockResolvedValue(undefined),
  unassignSidRole: vi.fn().mockResolvedValue(undefined),
  deleteSidEntry: vi.fn().mockResolvedValue(undefined),
  getRoleAssignments: vi.fn().mockResolvedValue([]),
  getSidsInfo: vi.fn().mockResolvedValue([]),
});

const renderPage = (
  b: AssignRolesBootstrap = bootstrap(),
  client: StrategyClient = createClient(),
) => {
  render(
    <AssignRolesPage
      bootstrap={b}
      client={client}
      checkSidNameUrl="/checkSidName"
    />,
  );
  return client;
};

const cardOf = (sid: string) => {
  const name = screen.getByText(sid, { selector: "[data-sid]" });
  const card = name.closest(".rsp-card");
  if (!(card instanceof HTMLElement)) throw new Error(`No card for ${sid}`);
  return card;
};

const expandCard = async (
  user: ReturnType<typeof userEvent.setup>,
  sid: string,
) => {
  const card = cardOf(sid);
  await user.click(within(card).getByRole("button", { expanded: false }));
  return card;
};

describe("AssignRolesPage", () => {
  let addButton: HTMLButtonElement;

  beforeEach(() => {
    // The add buttons are rendered by Jelly in the app-bar, outside React.
    addButton = document.createElement("button");
    addButton.id = "rsp-add-sid-btn";
    document.body.append(addButton);
    vi.stubGlobal("dialog", {
      confirm: vi.fn().mockResolvedValue(true),
    });
  });

  afterEach(() => {
    addButton.remove();
    vi.unstubAllGlobals();
    window.location.hash = "";
  });

  it("renders entries per tab and switches between them", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getAllByRole("tab")).toHaveLength(3);
    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.queryByText("devs")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    expect(screen.getByText("devs")).toBeInTheDocument();
    expect(screen.queryByText("alice")).not.toBeInTheDocument();
  });

  it("always shows the built-in anonymous and authenticated entries", () => {
    renderPage();
    expect(screen.getByText("anonymous")).toBeInTheDocument();
    expect(screen.getByText("authenticated")).toBeInTheDocument();
    expect(screen.getAllByText("Built-in")).toHaveLength(2);
    // Built-in entries have no remove action.
    expect(
      within(cardOf("anonymous")).queryByLabelText(/Remove/),
    ).not.toBeInTheDocument();
  });

  it("shows the assigned roles read-only in the expanded card", async () => {
    const user = userEvent.setup();
    renderPage();

    const card = await expandCard(user, "alice");

    expect(
      within(card).getByText("admin", { selector: ".rsp-assign__chip" }),
    ).toBeInTheDocument();
    expect(within(card).queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("assigns and unassigns roles through the edit dialog", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(within(cardOf("alice")).getByLabelText("Edit roles"));
    expect(await screen.findByText("Edit roles: alice")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /admin/ })).toBeChecked();

    await user.click(screen.getByRole("checkbox", { name: /reader/ }));
    await user.click(screen.getByRole("checkbox", { name: /admin/ }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(client.assignSidRole).toHaveBeenCalledWith(
        "globalRoles",
        "reader",
        "alice",
        "USER",
      ),
    );
    expect(client.unassignSidRole).toHaveBeenCalledWith(
      "globalRoles",
      "admin",
      "alice",
      "USER",
    );
    expect(within(cardOf("alice")).getByText("reader")).toBeInTheDocument();
  });

  it("dispatches group edits to the group endpoint", async () => {
    const user = userEvent.setup();
    const client = renderPage(
      bootstrap({
        projectRoles: typeBootstrap({
          roles: [
            { name: "dev", pattern: "dev-.*", permissionIds: [] },
            { name: "qa", pattern: "qa-.*", permissionIds: [] },
          ],
          entries: [{ name: "devs", type: "GROUP", roles: ["dev", "qa"] }],
        }),
      }),
    );

    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    await user.click(within(cardOf("devs")).getByLabelText("Edit roles"));
    await user.click(screen.getByRole("checkbox", { name: /dev/ }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(client.unassignSidRole).toHaveBeenCalledWith(
        "projectRoles",
        "dev",
        "devs",
        "GROUP",
      ),
    );
  });

  it("shows the error in the dialog and resyncs when saving fails", async () => {
    const user = userEvent.setup();
    const client = createClient();
    vi.mocked(client.assignSidRole).mockRejectedValue(new Error("boom"));
    vi.mocked(client.getRoleAssignments).mockResolvedValue([
      { name: "alice", type: "USER", roles: ["admin"] },
    ]);
    renderPage(bootstrap(), client);

    await user.click(within(cardOf("alice")).getByLabelText("Edit roles"));
    await user.click(screen.getByRole("checkbox", { name: /reader/ }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("Failed to save the assignment."),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(client.getRoleAssignments).toHaveBeenCalledWith("globalRoles"),
    );
    // The dialog stays open for a retry.
    expect(screen.getByText("Edit roles: alice")).toBeInTheDocument();
  });

  it("removes an entry after confirmation", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(within(cardOf("alice")).getByLabelText("Remove user"));

    await waitFor(() =>
      expect(client.deleteSidEntry).toHaveBeenCalledWith(
        "globalRoles",
        "alice",
        "USER",
      ),
    );
    await waitFor(() =>
      expect(screen.queryByText("alice")).not.toBeInTheDocument(),
    );
  });

  it("keeps the entry when the removal is cancelled", async () => {
    vi.stubGlobal("dialog", {
      confirm: vi.fn().mockRejectedValue(new Error("cancelled")),
    });
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(within(cardOf("alice")).getByLabelText("Remove user"));

    expect(client.deleteSidEntry).not.toHaveBeenCalled();
    expect(screen.getByText("alice")).toBeInTheDocument();
  });

  it("requires at least one role to add an entry", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(addButton);
    await user.type(screen.getByLabelText("User ID"), "bob");

    expect(screen.getByText("Select at least one role.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();

    await user.click(screen.getByRole("checkbox", { name: /reader/ }));
    expect(screen.getByRole("button", { name: "Add" })).toBeEnabled();
  });

  it("shows the server-rendered name lookup snippet after leaving the field", async () => {
    const user = userEvent.setup();
    vi.mocked(checkSidName).mockResolvedValue(
      "<div class='rsp-table__cell'><span class='rsp-entry-not-found'>ghost</span></div>",
    );
    renderPage();

    await user.click(addButton);
    await user.type(screen.getByLabelText("User ID"), "ghost");
    await user.tab();

    await waitFor(() =>
      expect(checkSidName).toHaveBeenCalledWith(
        "/checkSidName",
        "ghost",
        "USER",
      ),
    );
    const struck = await screen.findByText("ghost", {
      selector: ".rsp-entry-not-found",
    });
    expect(struck).toBeInTheDocument();
  });

  it("re-runs the name lookup when the type changes", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(addButton);
    await user.type(screen.getByLabelText("User ID"), "corp/sally");
    await user.click(screen.getByRole("radio", { name: "Group" }));

    await waitFor(() =>
      expect(checkSidName).toHaveBeenCalledWith(
        "/checkSidName",
        "corp/sally",
        "GROUP",
      ),
    );
  });

  it("adds a user with roles selected in the dialog", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(addButton);
    await user.type(screen.getByLabelText("User ID"), "bob");
    await user.click(screen.getByRole("checkbox", { name: /reader/ }));
    await user.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() =>
      expect(client.assignSidRole).toHaveBeenCalledWith(
        "globalRoles",
        "reader",
        "bob",
        "USER",
      ),
    );
    expect(
      within(cardOf("bob")).getByText("reader", {
        selector: ".rsp-card__summary",
      }),
    ).toBeInTheDocument();
    // The new card is collapsed like any other.
    expect(cardOf("bob").getAttribute("data-expanded")).toBe("false");
  });

  it("adds a group by switching the type in the dialog", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(addButton);
    await user.click(screen.getByRole("radio", { name: "Group" }));
    await user.type(screen.getByLabelText("Group name"), "ops");
    await user.click(screen.getByRole("checkbox", { name: /admin/ }));
    await user.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() =>
      expect(client.assignSidRole).toHaveBeenCalledWith(
        "globalRoles",
        "admin",
        "ops",
        "GROUP",
      ),
    );
    expect(
      screen.getByText("ops", { selector: "[data-sid]" }),
    ).toBeInTheDocument();
  });

  it("filters ambiguous entries from the filter dropdown", async () => {
    const user = userEvent.setup();
    renderPage(
      bootstrap({
        globalRoles: typeBootstrap({
          entries: [
            { name: "alice", type: "USER", roles: ["admin"] },
            { name: "old", type: "EITHER", roles: ["admin"] },
          ],
        }),
      }),
    );

    await user.click(screen.getByTitle("Filter by role"));
    expect(screen.getByPlaceholderText("Search roles")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ambiguous" }));

    expect(screen.getByText("old")).toBeInTheDocument();
    expect(screen.queryByText("alice")).not.toBeInTheDocument();
    expect(screen.queryByText("anonymous")).not.toBeInTheDocument();
  });

  it("rejects adding a duplicate user", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(addButton);
    await user.type(screen.getByLabelText("User ID"), "alice");

    expect(
      screen.getByText("An entry for this user already exists."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();
  });

  it("marks ambiguous entries and migrates them to a user", async () => {
    const user = userEvent.setup();
    const client = createClient();
    renderPage(
      bootstrap({
        globalRoles: typeBootstrap({
          roles: [
            { name: "admin", permissionIds: [] },
            { name: "reader", permissionIds: [] },
          ],
          entries: [
            { name: "alice", type: "USER", roles: ["admin"] },
            { name: "old", type: "EITHER", roles: ["admin", "reader"] },
          ],
        }),
      }),
      client,
    );

    expect(screen.getByText("Ambiguous")).toBeInTheDocument();
    expect(screen.getByText(/Some entries are ambiguous/)).toBeInTheDocument();

    await user.click(within(cardOf("old")).getByLabelText("Migrate to user"));

    await waitFor(() =>
      expect(client.deleteSidEntry).toHaveBeenCalledWith(
        "globalRoles",
        "old",
        "EITHER",
      ),
    );
    expect(client.assignSidRole).toHaveBeenCalledWith(
      "globalRoles",
      "admin",
      "old",
      "USER",
    );
    expect(client.assignSidRole).toHaveBeenCalledWith(
      "globalRoles",
      "reader",
      "old",
      "USER",
    );
    await waitFor(() =>
      expect(screen.queryByText("Ambiguous")).not.toBeInTheDocument(),
    );
  });

  it("merges a migration into an existing entry of the target type", async () => {
    const user = userEvent.setup();
    const client = createClient();
    renderPage(
      bootstrap({
        globalRoles: typeBootstrap({
          roles: [
            { name: "admin", permissionIds: [] },
            { name: "reader", permissionIds: [] },
          ],
          entries: [
            { name: "alice", type: "USER", roles: ["admin"] },
            { name: "alice", type: "EITHER", roles: ["reader"] },
          ],
        }),
      }),
      client,
    );

    const ambiguousCard = screen
      .getAllByText("alice", { selector: "[data-sid]" })
      .map((n) => n.closest(".rsp-card"))
      .find((c) => c && within(c as HTMLElement).queryByText("Ambiguous"));
    await user.click(
      within(ambiguousCard as HTMLElement).getByLabelText("Migrate to user"),
    );

    await waitFor(() =>
      expect(client.deleteSidEntry).toHaveBeenCalledWith(
        "globalRoles",
        "alice",
        "EITHER",
      ),
    );
    // The two cards collapse into one carrying the union of the roles.
    await waitFor(() =>
      expect(
        screen.getAllByText("alice", { selector: "[data-sid]" }),
      ).toHaveLength(1),
    );
    expect(
      within(cardOf("alice")).getByText("admin, reader"),
    ).toBeInTheDocument();
  });

  it("renders read-only when the type is not editable", () => {
    renderPage(
      bootstrap({
        globalRoles: typeBootstrap({
          canEdit: false,
          entries: [{ name: "alice", type: "USER", roles: ["admin"] }],
        }),
      }),
    );

    expect(screen.queryByLabelText(/Remove/)).not.toBeInTheDocument();
    expect(addButton).not.toBeVisible();
  });

  it("shows resolved display names from the realm lookup", async () => {
    const client = createClient();
    vi.mocked(client.getSidsInfo).mockResolvedValue([
      {
        sid: "alice",
        type: "USER",
        resolution: "found",
        foundAs: "USER",
        displayName: "Alice Smith",
      },
    ]);
    renderPage(bootstrap(), client);

    expect(await screen.findByText("Alice Smith (alice)")).toBeInTheDocument();
    await waitFor(() =>
      expect(client.getSidsInfo).toHaveBeenCalledWith(
        [{ sid: "alice", type: "USER" }],
        expect.any(AbortSignal),
      ),
    );
  });

  it("filters entries by search", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(
      screen.getByPlaceholderText("Search users and groups"),
      "alice",
    );

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.queryByText("anonymous")).not.toBeInTheDocument();
  });

  describe("pagination", () => {
    // 120 users plus the two built-in entries: three pages of 50.
    const manyUsersBootstrap = () =>
      bootstrap({
        globalRoles: typeBootstrap({
          roles: [{ name: "admin", permissionIds: [] }],
          entries: Array.from({ length: 120 }, (_, i) => ({
            name: `user${String(i).padStart(3, "0")}`,
            type: "USER" as const,
            roles: [],
          })),
        }),
      });

    const cardCount = () => document.querySelectorAll(".rsp-card").length;

    it("pages long lists and navigates between pages", async () => {
      const user = userEvent.setup();
      renderPage(manyUsersBootstrap());

      expect(cardCount()).toBe(50);
      expect(screen.getByText("Showing 1-50 of 122")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();

      await user.click(screen.getByRole("button", { name: "Next" }));
      expect(screen.getByText("Showing 51-100 of 122")).toBeInTheDocument();
      expect(screen.getByText("user048")).toBeInTheDocument();
      expect(screen.queryByText("user000")).not.toBeInTheDocument();

      await user.selectOptions(screen.getByLabelText("Page"), "2");
      expect(screen.getByText("Showing 101-122 of 122")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    });

    it("resets to the first page when searching", async () => {
      const user = userEvent.setup();
      renderPage(manyUsersBootstrap());

      await user.click(screen.getByRole("button", { name: "Next" }));
      await user.type(
        screen.getByPlaceholderText("Search users and groups"),
        "user000",
      );

      expect(screen.getByText("user000")).toBeInTheDocument();
      // A single match fits on one page, so the pager disappears.
      expect(
        screen.queryByRole("button", { name: "Next" }),
      ).not.toBeInTheDocument();
    });

    it("resolves sids one page at a time", async () => {
      const user = userEvent.setup();
      const client = renderPage(manyUsersBootstrap());

      // First page: 48 users (the two built-in entries are skipped).
      await waitFor(() => expect(client.getSidsInfo).toHaveBeenCalled());
      expect(vi.mocked(client.getSidsInfo).mock.calls[0][0]).toHaveLength(48);

      await user.click(screen.getByRole("button", { name: "Next" }));
      await waitFor(() => expect(client.getSidsInfo).toHaveBeenCalledTimes(2));
      expect(vi.mocked(client.getSidsInfo).mock.calls[1][0]).toHaveLength(50);
    });

    it("jumps to the page containing a newly added entry", async () => {
      const user = userEvent.setup();
      renderPage(manyUsersBootstrap());

      await user.click(addButton);
      await user.type(screen.getByLabelText("User ID"), "zzz");
      await user.click(screen.getByRole("checkbox", { name: "admin" }));
      await user.click(screen.getByRole("button", { name: "Add" }));

      // "zzz" sorts last, onto the third page.
      expect(
        screen.getByText("zzz", { selector: "[data-sid]" }),
      ).toBeInTheDocument();
      expect(screen.getByText("Showing 101-123 of 123")).toBeInTheDocument();
    });
  });
});
