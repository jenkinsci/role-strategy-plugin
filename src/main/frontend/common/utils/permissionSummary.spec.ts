import { describe, expect, it } from "vitest";

import type { PermissionGroup } from "../types/permission.ts";
import {
  buildPermissionSummary,
  indexPermissions,
} from "./permissionSummary.ts";

const GROUPS: PermissionGroup[] = [
  {
    title: "Job",
    permissions: [
      { id: "job.read", name: "Read", description: "", impliedByList: [] },
      { id: "job.build", name: "Build", description: "", impliedByList: [] },
    ],
  },
];

describe("buildPermissionSummary", () => {
  const byId = indexPermissions(GROUPS);

  it("joins resolved permissions as sorted Group/Permission pairs", () => {
    expect(buildPermissionSummary(["job.build", "job.read"], byId)).toBe(
      "Job/Build, Job/Read",
    );
  });

  it("returns null for an empty id list", () => {
    expect(buildPermissionSummary([], byId)).toBeNull();
  });

  it("returns null when no id resolves, so callers show their placeholder", () => {
    expect(buildPermissionSummary(["stale.permission"], byId)).toBeNull();
  });

  it("ignores unresolvable ids among valid ones", () => {
    expect(buildPermissionSummary(["stale.permission", "job.read"], byId)).toBe(
      "Job/Read",
    );
  });
});
