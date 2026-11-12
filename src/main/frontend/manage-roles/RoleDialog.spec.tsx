import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { checkPattern } from "../common/api/validation.ts";
import type { PermissionGroup } from "../common/types/permission.ts";
import type { PermissionTemplate } from "../common/types/template.ts";
import { RoleDialog } from "./RoleDialog.tsx";

vi.mock("../common/api/validation.ts", () => ({
  checkPattern: vi.fn().mockResolvedValue({ ok: true }),
}));

const checkPatternMock = vi.mocked(checkPattern);

const GROUPS: PermissionGroup[] = [
  {
    title: "Job",
    permissions: [
      { id: "job.read", name: "Read", description: "", impliedByList: [] },
      { id: "job.build", name: "Build", description: "", impliedByList: [] },
    ],
  },
];

const TEMPLATES: PermissionTemplate[] = [
  { name: "developer", permissionIds: ["job.build"], isUsed: false },
];

const baseProps = {
  title: "Add item role",
  submitLabel: "Add",
  allowNameEdit: true,
  existingNames: new Set<string>(),
  permissionGroups: GROUPS,
  templates: TEMPLATES,
  checkPatternUrl: "/checkPattern",
  rootUrl: "/jenkins",
  initialName: "",
  initialPattern: ".*",
  initialTemplateName: "",
  initialPermissionIds: [],
  onCancel: () => {},
};

describe("RoleDialog", () => {
  beforeEach(() => {
    checkPatternMock.mockClear();
    checkPatternMock.mockResolvedValue({ ok: true });
  });

  it("disables submit until the name is filled", async () => {
    const user = userEvent.setup();
    render(
      <RoleDialog {...baseProps} roleType="projectRoles" onSubmit={vi.fn()} />,
    );

    const submit = screen.getByRole("button", { name: "Add" });
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText("Name"), "dev");
    expect(submit).toBeEnabled();
  });

  it("validates the pattern on blur, not while typing", async () => {
    const user = userEvent.setup();
    render(
      <RoleDialog {...baseProps} roleType="projectRoles" onSubmit={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Pattern"), "x");
    expect(checkPatternMock).not.toHaveBeenCalled();

    await user.tab();
    await waitFor(() =>
      expect(checkPatternMock).toHaveBeenCalledWith("/checkPattern", ".*x"),
    );
  });

  it("keeps submit enabled while a pattern check is still in flight", async () => {
    // A check that never resolves stands in for a slow round trip.
    checkPatternMock.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    render(
      <RoleDialog {...baseProps} roleType="projectRoles" onSubmit={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Name"), "dev");
    await user.type(screen.getByLabelText("Pattern"), "x");
    await user.tab();

    expect(screen.getByRole("button", { name: "Add" })).toBeEnabled();
  });

  it("shows the server message on blur for an invalid pattern and clears it on edit", async () => {
    checkPatternMock.mockResolvedValue({
      ok: false,
      message: "Unclosed group",
    });
    const user = userEvent.setup();
    render(
      <RoleDialog {...baseProps} roleType="projectRoles" onSubmit={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Name"), "dev");
    await user.type(screen.getByLabelText("Pattern"), "(");
    await user.tab();

    await waitFor(() =>
      expect(screen.getByText("Unclosed group")).toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();

    // Editing the field clears the error until the next blur.
    await user.type(screen.getByLabelText("Pattern"), "x");
    expect(screen.queryByText("Unclosed group")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add" })).toBeEnabled();
  });

  it("does not render a pattern field for global roles", () => {
    render(
      <RoleDialog {...baseProps} roleType="globalRoles" onSubmit={vi.fn()} />,
    );
    expect(screen.queryByLabelText("Pattern")).not.toBeInTheDocument();
  });

  it("rejects names containing commas", async () => {
    const user = userEvent.setup();
    render(
      <RoleDialog {...baseProps} roleType="globalRoles" onSubmit={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Name"), "a,b");
    expect(
      screen.getByText("Role names must not contain commas."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();
  });

  it("rejects names that already exist", async () => {
    const user = userEvent.setup();
    render(
      <RoleDialog
        {...baseProps}
        roleType="globalRoles"
        existingNames={new Set(["admin"])}
        onSubmit={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText("Name"), "admin");
    expect(
      screen.getByText("A role with this name already exists."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();
  });

  it("binding a template shows its permissions read-only and submits the template", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <RoleDialog {...baseProps} roleType="projectRoles" onSubmit={onSubmit} />,
    );

    await user.type(screen.getByLabelText("Name"), "dev");
    await user.selectOptions(
      screen.getByLabelText("Permission template"),
      "developer",
    );

    // The template's permission is shown checked but not editable.
    const buildCheckbox = screen.getByLabelText("Build");
    expect(buildCheckbox).toBeChecked();
    expect(buildCheckbox).toBeDisabled();

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Add" })).toBeEnabled(),
    );
    await user.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit).toHaveBeenCalledWith({
      name: "dev",
      pattern: ".*",
      template: "developer",
      permissionIds: [],
    });
  });

  describe("folder scope", () => {
    const loadFolders = vi.fn().mockResolvedValue(["Team", "Team/Special"]);

    it("is offered for item roles only", () => {
      const { unmount } = render(
        <RoleDialog
          {...baseProps}
          roleType="slaveRoles"
          loadFolders={loadFolders}
          onSubmit={vi.fn()}
        />,
      );
      expect(
        screen.queryByRole("radio", { name: "Folders" }),
      ).not.toBeInTheDocument();
      unmount();

      render(
        <RoleDialog
          {...baseProps}
          roleType="projectRoles"
          loadFolders={loadFolders}
          onSubmit={vi.fn()}
        />,
      );
      expect(screen.getByRole("radio", { name: "Pattern" })).toBeChecked();
    });

    it("needs a folder, adds typed folders on Enter and submits them instead of a pattern", async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn().mockResolvedValue(undefined);
      render(
        <RoleDialog
          {...baseProps}
          roleType="projectRoles"
          loadFolders={loadFolders}
          onSubmit={onSubmit}
        />,
      );

      await user.type(screen.getByLabelText("Name"), "builders");
      await user.click(screen.getByRole("radio", { name: "Folders" }));
      const submit = screen.getByRole("button", { name: "Add" });
      expect(submit).toBeDisabled();

      const input = screen.getByRole("combobox", { name: "Folders" });
      await user.type(input, " /Team/Special/ {Enter}");
      // Enter adds the folder; it must not submit the dialog.
      expect(onSubmit).not.toHaveBeenCalled();
      await user.type(input, "Team/Special{Enter}");
      expect(
        screen.getAllByRole("button", { name: "Remove Team/Special" }),
      ).toHaveLength(1);
      await user.type(input, "Ops{Enter}");
      await user.click(screen.getByRole("button", { name: "Remove Ops" }));

      await user.click(submit);
      expect(onSubmit).toHaveBeenCalledWith({
        name: "builders",
        pattern: undefined,
        folders: ["Team/Special"],
        template: undefined,
        permissionIds: [],
      });
    });

    it("opens an existing folder role in folder mode", () => {
      render(
        <RoleDialog
          {...baseProps}
          roleType="projectRoles"
          initialFolders={["Team/Special"]}
          loadFolders={loadFolders}
          onSubmit={vi.fn()}
        />,
      );
      expect(screen.getByRole("radio", { name: "Folders" })).toBeChecked();
      expect(screen.queryByRole("textbox", { name: "Pattern" })).toBeNull();
    });

    it("saves a folder that was typed but not added, and explains an empty list", async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn().mockResolvedValue(undefined);
      render(
        <RoleDialog
          {...baseProps}
          roleType="projectRoles"
          loadFolders={loadFolders}
          onSubmit={onSubmit}
        />,
      );

      await user.type(screen.getByLabelText("Name"), "builders");
      await user.click(screen.getByRole("radio", { name: "Folders" }));
      expect(screen.getByText(/Add at least one folder/)).toBeInTheDocument();

      await user.type(screen.getByRole("combobox", { name: "Folders" }), "Ops");
      expect(screen.queryByText(/Add at least one folder/)).toBeNull();
      await user.click(screen.getByRole("button", { name: "Add" }));

      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ folders: ["Ops"], pattern: undefined }),
      );
    });

    it("loads suggestions only once the folder field is used", async () => {
      const user = userEvent.setup();
      loadFolders.mockClear();
      render(
        <RoleDialog
          {...baseProps}
          roleType="projectRoles"
          initialFolders={["Team/Special"]}
          loadFolders={loadFolders}
          onSubmit={vi.fn()}
        />,
      );
      await new Promise((r) => setTimeout(r, 300));
      expect(loadFolders).not.toHaveBeenCalled();

      await user.click(screen.getByRole("combobox", { name: "Folders" }));
      await waitFor(() => expect(loadFolders).toHaveBeenCalledWith("", 20));
    });

    it("moves focus to the folder field after removing a folder", async () => {
      const user = userEvent.setup();
      render(
        <RoleDialog
          {...baseProps}
          roleType="projectRoles"
          initialFolders={["A", "B"]}
          loadFolders={loadFolders}
          onSubmit={vi.fn()}
        />,
      );

      await user.click(screen.getByRole("button", { name: "Remove A" }));

      expect(screen.getByRole("combobox", { name: "Folders" })).toHaveFocus();
      expect(screen.getByRole("status")).toHaveTextContent("Removed A");
    });
  });
});
