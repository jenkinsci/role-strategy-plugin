import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { folderPattern } from "../utils/roleScope.ts";

import { ScopeChips } from "./ScopeChips.tsx";

describe("ScopeChips", () => {
  it("renders one chip per folder instead of the derived pattern", () => {
    render(
      <ScopeChips
        role={{
          name: "r",
          pattern: "^(?:\\QA\\E|\\QB\\E)(?s:/.*)?$",
          folders: ["A", "B"],
          permissionIds: [],
        }}
      />,
    );
    expect(screen.getAllByText(/^[AB]$/)).toHaveLength(2);
    expect(screen.queryByText(/\\Q/)).toBeNull();
  });

  it("renders a single chip for a pattern role", () => {
    const { container } = render(
      <ScopeChips role={{ name: "r", pattern: "dev-.*", permissionIds: [] }} />,
    );
    expect(container.querySelectorAll(".rsp-card__pattern")).toHaveLength(1);
    expect(screen.getByText("dev-.*")).toBeInTheDocument();
  });

  it("renders nothing for a global role", () => {
    const { container } = render(
      <ScopeChips role={{ name: "r", permissionIds: [] }} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders plain chips unless it can show what they match", () => {
    render(
      <ScopeChips role={{ name: "r", pattern: "dev-.*", permissionIds: [] }} />,
    );
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("shows the items one folder covers", async () => {
    const onShowMatching = vi.fn();
    render(
      <ScopeChips
        role={{
          name: "r",
          pattern: "^(?:\\QA\\E|\\QB\\E)(?s:/.*)?$",
          folders: ["A", "B"],
          permissionIds: [],
        }}
        matchKind="agents"
        onShowMatching={onShowMatching}
      />,
    );
    await userEvent.setup().click(screen.getByRole("button", { name: "B" }));
    expect(onShowMatching).toHaveBeenCalledWith({
      kind: "items",
      pattern: folderPattern(["B"]),
      label: "B",
    });
  });

  it("shows the agents a pattern matches", async () => {
    const onShowMatching = vi.fn();
    render(
      <ScopeChips
        role={{ name: "r", pattern: "linux-.*", permissionIds: [] }}
        matchKind="agents"
        onShowMatching={onShowMatching}
      />,
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "linux-.*" }));
    expect(onShowMatching).toHaveBeenCalledWith({
      kind: "agents",
      pattern: "linux-.*",
    });
  });
});
