import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { StrategyClient } from "../common/api/strategy.ts";
import type {
  ManageRolesBootstrap,
  RoleTypeBootstrap,
} from "../common/types/bootstrap.ts";
import { folderPattern } from "../common/utils/roleScope.ts";
import { ManageRolesPage } from "./ManageRolesPage.tsx";

vi.mock("../common/api/validation.ts", () => ({
  checkPattern: vi.fn().mockResolvedValue({ ok: true }),
}));

const typeBootstrap = (
  overrides: Partial<RoleTypeBootstrap> = {},
): RoleTypeBootstrap => ({
  visible: true,
  canEdit: true,
  permissionGroups: [
    {
      title: "Job",
      permissions: [
        { id: "job.read", name: "Read", description: "", impliedByList: [] },
      ],
    },
  ],
  roles: [],
  ...overrides,
});

const bootstrap = (
  overrides: Partial<ManageRolesBootstrap> = {},
): ManageRolesBootstrap => ({
  globalRoles: typeBootstrap({
    roles: [{ name: "admin", permissionIds: ["job.read"] }],
  }),
  projectRoles: typeBootstrap({
    roles: [
      { name: "dev", pattern: "dev-.*", permissionIds: ["job.read"] },
      {
        name: "templated",
        pattern: ".*",
        templateName: "developer",
        permissionIds: ["job.read"],
      },
      {
        name: "special-builders",
        pattern: "^(?:\\QTeam/Special\\E)(?:/.*)?$",
        folders: ["Team/Special"],
        permissionIds: ["job.read"],
      },
    ],
  }),
  slaveRoles: typeBootstrap(),
  templates: [{ name: "developer", permissionIds: ["job.read"], isUsed: true }],
  ...overrides,
});

const createClient = (): StrategyClient => ({
  addTemplate: vi.fn(),
  removeTemplates: vi.fn(),
  addRole: vi.fn().mockResolvedValue(undefined),
  removeRoles: vi.fn().mockResolvedValue(undefined),
  getMatchingJobs: vi
    .fn()
    .mockResolvedValue({ matchingJobs: ["dev-a"], itemCount: 1 }),
  getFolders: vi
    .fn()
    .mockResolvedValue({ folders: ["Team", "Team/Special"], count: 2 }),
  getMatchingAgents: vi
    .fn()
    .mockResolvedValue({ matchingAgents: [], agentCount: 0 }),
  assignSidRole: vi.fn(),
  unassignSidRole: vi.fn(),
  deleteSidEntry: vi.fn(),
  getRoleAssignments: vi.fn(),
  getSidsInfo: vi.fn(),
});

const renderPage = (
  b: ManageRolesBootstrap = bootstrap(),
  client: StrategyClient = createClient(),
) => {
  render(
    <ManageRolesPage
      bootstrap={b}
      client={client}
      rootUrl="/jenkins"
      checkPatternUrl="/checkPattern"
    />,
  );
  return client;
};

describe("ManageRolesPage", () => {
  let addButton: HTMLButtonElement;

  beforeEach(() => {
    // The add button is rendered by Jelly in the app-bar, outside React.
    addButton = document.createElement("button");
    addButton.id = "rsp-add-role-btn";
    document.body.appendChild(addButton);
    vi.stubGlobal("dialog", {
      confirm: vi.fn().mockResolvedValue(true),
    });
  });

  afterEach(() => {
    addButton.remove();
    vi.unstubAllGlobals();
    window.location.hash = "";
  });

  it("renders a tab per visible role type and switches between them", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getAllByRole("tab")).toHaveLength(3);
    expect(screen.getByText("admin")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    expect(screen.getByText("dev")).toBeInTheDocument();
    expect(screen.queryByText("admin")).not.toBeInTheDocument();
  });

  it("hides tabs for role types the user cannot see", () => {
    renderPage(
      bootstrap({
        globalRoles: typeBootstrap({ visible: false, canEdit: false }),
        slaveRoles: typeBootstrap({ visible: false, canEdit: false }),
      }),
    );
    // With a single visible type there is no tab bar at all.
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    expect(screen.getByText("dev")).toBeInTheDocument();
  });

  it("opens the item roles tab when deep-linked via hash", () => {
    window.location.hash = "#item";
    renderPage();
    expect(screen.getByRole("tab", { name: "Item roles" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByText("dev")).toBeInTheDocument();
  });

  it("hides edit and delete actions and the add button when not editable", async () => {
    const user = userEvent.setup();
    renderPage(
      bootstrap({
        globalRoles: typeBootstrap({
          canEdit: false,
          roles: [{ name: "admin", permissionIds: ["job.read"] }],
        }),
      }),
    );

    expect(screen.queryByLabelText("Edit role")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Delete role")).not.toBeInTheDocument();
    expect(addButton).not.toBeVisible();

    // An editable tab shows them again.
    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    expect(screen.getAllByLabelText("Edit role").length).toBeGreaterThan(0);
    expect(addButton).toBeVisible();
  });

  it("shows the template badge on template-bound roles", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    expect(screen.getByText("Template: developer")).toBeInTheDocument();
  });

  it("adds a role through the dialog and shows it in the list", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(addButton);
    await user.type(screen.getByLabelText("Name"), "reader");
    await user.click(screen.getByLabelText("Read"));
    await user.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() =>
      expect(client.addRole).toHaveBeenCalledWith(
        "globalRoles",
        "reader",
        ["job.read"],
        false,
        undefined,
        undefined,
        undefined,
      ),
    );
    expect(screen.getByText("reader")).toBeInTheDocument();
  });

  it("deletes a role after confirmation", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(screen.getByLabelText("Delete role"));

    await waitFor(() =>
      expect(client.removeRoles).toHaveBeenCalledWith("globalRoles", ["admin"]),
    );
    await waitFor(() =>
      expect(screen.queryByText("admin")).not.toBeInTheDocument(),
    );
  });

  it("keeps the role when the delete confirmation is cancelled", async () => {
    vi.stubGlobal("dialog", {
      confirm: vi.fn().mockRejectedValue(new Error("cancelled")),
    });
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(screen.getByLabelText("Delete role"));

    expect(client.removeRoles).not.toHaveBeenCalled();
    expect(screen.getByText("admin")).toBeInTheDocument();
  });

  it("resets search when switching tabs", async () => {
    const user = userEvent.setup();
    renderPage();

    const search = screen.getByPlaceholderText("Search global roles");
    await user.type(search, "admin");

    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    expect(screen.getByPlaceholderText("Search item roles")).toHaveValue("");
  });

  it("opens the matching dialog from the pattern chip with the keyboard", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    screen.getByRole("button", { name: "dev-.*" }).focus();
    await user.keyboard("{Enter}");

    await waitFor(() =>
      expect(client.getMatchingJobs).toHaveBeenCalledWith("dev-.*", 15),
    );
    // The key press must not also toggle the surrounding card open.
    expect(
      screen
        .getAllByRole("button", { name: /dev/ })
        .find((b) => b.classList.contains("rsp-card__header"))
        ?.getAttribute("aria-expanded") ?? "false",
    ).toBe("false");
  });

  it("activates the edit action with the keyboard", async () => {
    const user = userEvent.setup();
    renderPage();

    screen.getByLabelText("Edit role").focus();
    await user.keyboard("{Enter}");

    expect(await screen.findByText("Edit role: admin")).toBeInTheDocument();
  });

  it("opens the matching dialog from the pattern chip", async () => {
    const user = userEvent.setup();
    const client = renderPage();

    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    await user.click(screen.getByText("dev-.*"));

    await waitFor(() =>
      expect(client.getMatchingJobs).toHaveBeenCalledWith("dev-.*", 15),
    );
    expect(await screen.findByText("dev-a")).toBeInTheDocument();
  });

  describe("folder-scoped item roles", () => {
    it("shows folders instead of the derived pattern", async () => {
      renderPage();
      await userEvent
        .setup()
        .click(screen.getByRole("tab", { name: "Item roles" }));

      expect(
        screen.getByRole("button", { name: "Team/Special" }),
      ).toBeInTheDocument();
      expect(screen.queryByText(/\\QTeam\/Special/)).not.toBeInTheDocument();
    });

    it("opens the matching dialog for one folder", async () => {
      const user = userEvent.setup();
      const client = renderPage();
      await user.click(screen.getByRole("tab", { name: "Item roles" }));

      await user.click(screen.getByRole("button", { name: "Team/Special" }));

      await waitFor(() =>
        expect(client.getMatchingJobs).toHaveBeenCalledWith(
          folderPattern(["Team/Special"]),
          15,
        ),
      );
      expect(
        await screen.findByText(/matching "Team\/Special"/),
      ).toBeInTheDocument();
    });

    it("finds roles by folder name", async () => {
      const user = userEvent.setup();
      renderPage();
      await user.click(screen.getByRole("tab", { name: "Item roles" }));

      await user.type(screen.getByRole("searchbox"), "team/spec");

      expect(screen.getByText("special-builders")).toBeInTheDocument();
      expect(screen.queryByText("dev")).not.toBeInTheDocument();
    });

    it("adds a folder-scoped role without a pattern", async () => {
      const user = userEvent.setup();
      const client = renderPage();
      await user.click(screen.getByRole("tab", { name: "Item roles" }));
      await user.click(addButton);

      await user.type(screen.getByLabelText("Name"), "builders");
      await user.click(screen.getByRole("radio", { name: "Folders" }));
      await user.type(
        screen.getByRole("combobox", { name: "Folders" }),
        "Team/Other{Enter}",
      );
      await user.click(screen.getByRole("button", { name: "Add" }));

      await waitFor(() =>
        expect(client.addRole).toHaveBeenCalledWith(
          "projectRoles",
          "builders",
          [],
          false,
          undefined,
          undefined,
          ["Team/Other"],
        ),
      );
      expect(
        screen.getByRole("button", { name: "Team/Other" }),
      ).toBeInTheDocument();
    });

    it("edits a folder-scoped role with its folders preselected", async () => {
      const user = userEvent.setup();
      const client = renderPage();
      await user.click(screen.getByRole("tab", { name: "Item roles" }));

      // Item roles are sorted: dev, special-builders, templated.
      await user.click(screen.getAllByLabelText("Edit role")[1]);

      expect(screen.getByRole("radio", { name: "Folders" })).toBeChecked();
      expect(
        screen.getByRole("button", { name: "Remove Team/Special" }),
      ).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Save" }));

      await waitFor(() =>
        expect(client.addRole).toHaveBeenCalledWith(
          "projectRoles",
          "special-builders",
          ["job.read"],
          true,
          undefined,
          undefined,
          ["Team/Special"],
        ),
      );
    });
  });

  it("keeps a folder role's scope when it is switched to a pattern", async () => {
    const user = userEvent.setup();
    const client = renderPage();
    await user.click(screen.getByRole("tab", { name: "Item roles" }));
    // Item roles are sorted: dev, special-builders, templated.
    await user.click(screen.getAllByLabelText("Edit role")[1]);

    await user.click(screen.getByRole("radio", { name: "Pattern" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(client.addRole).toHaveBeenCalledWith(
        "projectRoles",
        "special-builders",
        ["job.read"],
        true,
        "^(?:\\QTeam/Special\\E)(?:/.*)?$",
        undefined,
        undefined,
      ),
    );
  });

  it("derives the same regex as java.util.regex.Pattern.quote", () => {
    expect(folderPattern(["a/b", "c"])).toBe("^(?:\\Qa/b\\E|\\Qc\\E)(?:/.*)?$");
    expect(folderPattern(["x\\Ey"])).toBe("^(?:\\Qx\\E\\\\E\\Qy\\E)(?:/.*)?$");
  });
});
