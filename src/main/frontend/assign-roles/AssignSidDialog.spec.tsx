import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { checkSidName } from "../common/api/validation.ts";
import type { SidEntry } from "../common/types/assignment.ts";
import type {
  PrincipalSearchElement,
  SearchedPrincipal,
} from "../common/types/principalSearch.ts";
import { AssignSidDialog } from "./AssignSidDialog.tsx";

vi.mock("../common/api/validation.ts", () => ({
  checkSidName: vi.fn().mockResolvedValue(""),
}));

const renderDialog = (
  searchPrincipalsUrl?: string,
  existingEntries: SidEntry[] = [],
) => {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  render(
    <AssignSidDialog
      title="Add user or group"
      kind="USER"
      allowNameEdit
      existingEntries={existingEntries}
      roles={[{ name: "admin", permissionIds: [] }]}
      permissionsById={new Map()}
      checkSidNameUrl="/checkSidName"
      searchPrincipalsUrl={searchPrincipalsUrl}
      initialName=""
      initialRoles={["admin"]}
      submitLabel="Add"
      onCancel={vi.fn()}
      onSubmit={onSubmit}
    />,
  );
  return onSubmit;
};

const principalSearch = () => {
  const element = document.querySelector("auth-commons-principal-search");
  if (!element) throw new Error("No principal search element");
  return element as PrincipalSearchElement;
};

// The custom element is not defined under jsdom; stand in for its picking.
const pick = (principals: SearchedPrincipal[]) => {
  const element = principalSearch();
  act(() => {
    Object.defineProperty(element, "selectedPrincipals", {
      configurable: true,
      get: () => principals,
    });
    element.dispatchEvent(new Event("change", { bubbles: true }));
  });
};

describe("AssignSidDialog principal search", () => {
  afterEach(() => {
    vi.mocked(checkSidName).mockClear();
  });

  it("uses a plain text field when principal search is unavailable", () => {
    renderDialog();
    expect(
      document.querySelector("auth-commons-principal-search"),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "User ID" })).toHaveAttribute(
      "id",
      "rsp-sid-name",
    );
  });

  it("renders the principal search element when a search URL is given", () => {
    renderDialog("/searchPrincipals");
    const element = principalSearch();
    expect(element).toHaveAttribute("id", "rsp-sid-name");
    expect(element).toHaveAttribute("search-url", "/searchPrincipals");
  });

  it("searches for the chosen type, several at a time", async () => {
    renderDialog("/searchPrincipals");
    const element = principalSearch();
    expect(element).toHaveAttribute("type", "USER");
    expect(element).toHaveAttribute("multiple");
    expect(screen.getByText("Users")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "Group" }));
    expect(element).toHaveAttribute("type", "GROUP");
    expect(screen.getByText("Groups")).toBeInTheDocument();
  });

  it("adds every picked principal", async () => {
    const onSubmit = renderDialog("/searchPrincipals");
    const addButton = screen.getByRole("button", { name: "Add" });
    expect(addButton).toBeDisabled();

    pick([
      { sid: "0b1c2d3e", type: "USER", displayName: "Bob" },
      { sid: "alice", type: "USER", displayName: "Alice" },
    ]);

    await waitFor(() => expect(addButton).toBeEnabled());
    await userEvent.click(addButton);
    expect(onSubmit).toHaveBeenCalledWith({
      sids: [
        { name: "0b1c2d3e", type: "USER" },
        { name: "alice", type: "USER" },
      ],
      roles: ["admin"],
    });
    // chips carry the display name, so no realm lookup is needed
    expect(checkSidName).not.toHaveBeenCalled();
  });

  it("does not offer entries that already exist", () => {
    renderDialog("/searchPrincipals", [
      { name: "alice", type: "USER", roles: ["admin"] },
    ]);
    const exclude = principalSearch().exclude;
    expect(
      exclude?.({ sid: "alice", type: "USER", displayName: "Alice" }),
    ).toBe(true);
    expect(
      exclude?.({ sid: "alice", type: "GROUP", displayName: "Alice" }),
    ).toBe(false);
  });

  it("submits a single typed entry without principal search", async () => {
    const onSubmit = renderDialog();
    await userEvent.type(
      screen.getByRole("textbox", { name: "User ID" }),
      "bob",
    );
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(onSubmit).toHaveBeenCalledWith({
      sids: [{ name: "bob", type: "USER" }],
      roles: ["admin"],
    });
  });
});
