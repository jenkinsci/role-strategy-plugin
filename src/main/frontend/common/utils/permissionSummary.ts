import type { PermissionGroup } from "../types/permission.ts";

export interface PermissionRef {
  name: string;
  groupTitle: string;
}

/** Index permissions by id for summary building. */
export function indexPermissions(
  groups: PermissionGroup[],
): Map<string, PermissionRef> {
  const m = new Map<string, PermissionRef>();
  for (const g of groups) {
    for (const p of g.permissions) {
      m.set(p.id, { name: p.name, groupTitle: g.title });
    }
  }
  return m;
}

/**
 * Build the "Group/Permission, …" card summary line for a set of permission
 * ids. Returns null when nothing is granted so cards can show a placeholder.
 */
export function buildPermissionSummary(
  permissionIds: string[],
  permissionsById: ReadonlyMap<string, PermissionRef>,
): string | null {
  const resolved = permissionIds
    .map((id) => permissionsById.get(id))
    .filter((p): p is PermissionRef => !!p);
  // Also null when no id resolved (e.g. stale permission ids), so callers show
  // their placeholder instead of an empty summary.
  if (resolved.length === 0) return null;
  return resolved
    .map((p) => `${p.groupTitle}/${p.name}`)
    .sort()
    .join(", ");
}
