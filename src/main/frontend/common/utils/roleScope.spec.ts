import { describe, expect, it } from "vitest";

import { roleScope } from "./roleScope.ts";

describe("roleScope", () => {
  it("shows the folders of a folder-scoped role instead of its derived pattern", () => {
    expect(
      roleScope({
        name: "r",
        pattern: "^(?:\\QA\\E|\\QB/C\\E)(?s:/.*)?$",
        folders: ["A", "B/C"],
        permissionIds: [],
      }),
    ).toBe("A, B/C");
  });

  it("shows the pattern of a pattern role", () => {
    expect(roleScope({ name: "r", pattern: "dev-.*", permissionIds: [] })).toBe(
      "dev-.*",
    );
  });

  it("shows nothing for a global role", () => {
    expect(roleScope({ name: "r", permissionIds: [] })).toBeUndefined();
  });
});
