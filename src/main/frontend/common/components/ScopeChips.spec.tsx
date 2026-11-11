import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ScopeChips } from "./ScopeChips.tsx";

describe("ScopeChips", () => {
  it("renders one chip per folder instead of the derived pattern", () => {
    render(
      <ScopeChips
        role={{
          name: "r",
          pattern: "^(?:\\QA\\E|\\QB\\E)(?:/.*)?$",
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
});
