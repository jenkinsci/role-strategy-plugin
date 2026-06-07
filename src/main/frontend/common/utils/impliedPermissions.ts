import type { Permission } from "../types/permission.ts";

/**
 * Returns the permissions that are granted only because a parent permission is
 * selected, i.e. the transitive closure of the selection minus the directly
 * selected ids. A directly selected permission is never returned, so the UI can
 * keep it toggleable rather than disabling it as implied.
 */
export function computeImpliedPermissions(
  permissions: Permission[],
  selectedIds: ReadonlySet<string>,
): Set<string> {
  const byId = new Map(permissions.map((p) => [p.id, p]));

  // Transitive closure of everything granted by the current selection.
  const granted = new Set<string>(selectedIds);
  let changed = true;
  while (changed) {
    changed = false;
    for (const p of permissions) {
      if (granted.has(p.id)) continue;
      const parents = p.impliedByList ?? [];
      if (parents.some((parent) => byId.has(parent) && granted.has(parent))) {
        granted.add(p.id);
        changed = true;
      }
    }
  }

  const implied = new Set<string>();
  for (const id of granted) {
    if (!selectedIds.has(id)) implied.add(id);
  }
  return implied;
}
