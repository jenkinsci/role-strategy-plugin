import { useMemo } from "react";

import type { PermissionGroup } from "../types/permission.ts";
import { computeImpliedPermissions } from "../utils/impliedPermissions.ts";
import { HelpIcon } from "./HelpIcon.tsx";

interface PermissionGroupsProps {
  groups: PermissionGroup[];
  selectedIds: ReadonlySet<string>;
  disabled?: boolean;
  onToggle?: (permissionId: string, next: boolean) => void;
  /** Render only granted (selected or implied) permissions — for view-only
      contexts where the full catalogue would be noise. */
  showOnlySelected?: boolean;
}

export function PermissionGroups({
  groups,
  selectedIds,
  disabled,
  onToggle,
  showOnlySelected,
}: PermissionGroupsProps) {
  const flat = useMemo(() => groups.flatMap((g) => g.permissions), [groups]);
  const implied = useMemo(
    () => computeImpliedPermissions(flat, selectedIds),
    [flat, selectedIds],
  );

  const visibleGroups = useMemo(() => {
    if (!showOnlySelected) return groups;
    return groups
      .map((g) => ({
        ...g,
        permissions: g.permissions.filter(
          (p) => selectedIds.has(p.id) || implied.has(p.id),
        ),
      }))
      .filter((g) => g.permissions.length > 0);
  }, [groups, selectedIds, implied, showOnlySelected]);

  return (
    <div className="rsp-perm">
      {visibleGroups.map((group) => (
        <fieldset key={group.title} className="rsp-perm__group">
          <legend className="rsp-perm__group-title">{group.title}</legend>
          <div className="rsp-perm__permissions">
            {group.permissions.map((p) => {
              const isImplied = implied.has(p.id);
              const isChecked = selectedIds.has(p.id) || isImplied;
              return (
                <label
                  key={p.id}
                  className="rsp-perm__item"
                  data-permission-id={p.id}
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    disabled={disabled || isImplied}
                    onChange={(e) => onToggle?.(p.id, e.target.checked)}
                  />
                  <span className="rsp-perm__item-name">{p.name}</span>
                  {isImplied && (
                    <span className="rsp-perm__item-implied">(implied)</span>
                  )}
                  {p.description && <HelpIcon description={p.description} />}
                </label>
              );
            })}
          </div>
        </fieldset>
      ))}
    </div>
  );
}
