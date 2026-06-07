import { describe, expect, it } from "vitest";

import type { Permission } from "../types/permission.ts";
import { computeImpliedPermissions } from "./impliedPermissions.ts";

const perm = (id: string, impliedByList: string[] = []): Permission => ({
  id,
  name: id,
  description: "",
  impliedByList,
});

describe("computeImpliedPermissions", () => {
  it("returns empty set when nothing selected", () => {
    const perms = [perm("A"), perm("B", ["A"])];
    expect(computeImpliedPermissions(perms, new Set())).toEqual(new Set());
  });

  it("marks a permission as implied when its parent is selected", () => {
    const perms = [perm("A"), perm("B", ["A"])];
    expect(computeImpliedPermissions(perms, new Set(["A"]))).toEqual(
      new Set(["B"]),
    );
  });

  it("propagates through chains", () => {
    const perms = [perm("A"), perm("B", ["A"]), perm("C", ["B"])];
    expect(computeImpliedPermissions(perms, new Set(["A"]))).toEqual(
      new Set(["B", "C"]),
    );
  });

  it("does not include directly selected permissions in implied set", () => {
    const perms = [perm("A"), perm("B", ["A"])];
    expect(computeImpliedPermissions(perms, new Set(["A", "B"]))).toEqual(
      new Set(),
    );
  });
});
