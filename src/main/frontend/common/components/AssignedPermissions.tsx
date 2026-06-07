import { useMemo } from "react";

import type { PermissionGroup } from "../types/permission.ts";
import { computeImpliedPermissions } from "../utils/impliedPermissions.ts";

interface AssignedPermissionsProps {
  permissionGroups: PermissionGroup[];
  permissionIds: string[];
}

/**
 * Read-only chips listing the permissions a role or template grants, grouped by
 * category. Shows only the selected permissions (plus the ones they imply), so
 * card bodies stay compact regardless of how many permissions exist.
 */
export function AssignedPermissions({
  permissionGroups,
  permissionIds,
}: AssignedPermissionsProps) {
  const selectedIds = useMemo(() => new Set(permissionIds), [permissionIds]);
  const flat = useMemo(
    () => permissionGroups.flatMap((g) => g.permissions),
    [permissionGroups],
  );
  const implied = useMemo(
    () => computeImpliedPermissions(flat, selectedIds),
    [flat, selectedIds],
  );

  if (selectedIds.size === 0) {
    return (
      <div className="rsp-perm rsp-perm--empty">No permissions assigned</div>
    );
  }

  return (
    <div className="rsp-perm">
      {permissionGroups.map((group) => {
        const items = group.permissions.filter(
          (p) => selectedIds.has(p.id) || implied.has(p.id),
        );
        if (items.length === 0) return null;
        return (
          <fieldset key={group.title} className="rsp-perm__group">
            <legend className="rsp-perm__group-title">{group.title}</legend>
            <ul className="rsp-perm__assigned">
              {items.map((p) => (
                <li
                  key={p.id}
                  className="rsp-perm__assigned-item"
                  data-permission-id={p.id}
                >
                  <span className="rsp-perm__item-name">{p.name}</span>
                  {implied.has(p.id) && !selectedIds.has(p.id) && (
                    <span className="rsp-perm__item-implied">(implied)</span>
                  )}
                </li>
              ))}
            </ul>
          </fieldset>
        );
      })}
    </div>
  );
}
