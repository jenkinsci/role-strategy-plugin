import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { StrategyClient } from "../common/api/strategy.ts";
import type {
  ManageRolesBootstrap,
  RoleTypeBootstrap,
} from "../common/types/bootstrap.ts";
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
  getMatchingAgents: vi
    .fn()
    .mockResolvedValue({ matchingAgents: [], agentCount: 0 }),
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
});
