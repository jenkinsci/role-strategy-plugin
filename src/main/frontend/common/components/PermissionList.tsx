import { useId, useMemo, useState } from "react";

import type { PermissionGroup } from "../types/permission.ts";
import { computeImpliedPermissions } from "../utils/impliedPermissions.ts";
import { HelpIcon } from "./HelpIcon.tsx";
import { SearchInput } from "./SearchInput.tsx";

interface PermissionListProps {
  groups: PermissionGroup[];
  selectedIds: ReadonlySet<string>;
  disabled?: boolean;
  onToggle?: (permissionId: string, next: boolean) => void;
  filterPlaceholder?: string;
  /** Focus the filter input when the surrounding dialog opens. */
  filterAutoFocus?: boolean;
}

/**
 * Plain checkbox list of permissions grouped by category — used inside dialogs.
 * The card-body equivalent (pill toggles) is PermissionGroups.
 */
export function PermissionList({
  groups,
  selectedIds,
  disabled,
  onToggle,
  filterPlaceholder = "Filter permissions",
  filterAutoFocus,
}: PermissionListProps) {
  const idPrefix = useId();
  const [filter, setFilter] = useState("");
  const flat = useMemo(() => groups.flatMap((g) => g.permissions), [groups]);
  const implied = useMemo(
    () => computeImpliedPermissions(flat, selectedIds),
    [flat, selectedIds],
  );

  const q = filter.trim().toLowerCase();
  const visibleGroups = q
    ? groups
        .map((g) => ({
          ...g,
          permissions: g.permissions.filter(
            (p) =>
              p.name.toLowerCase().includes(q) ||
              g.title.toLowerCase().includes(q),
          ),
        }))
        .filter((g) => g.permissions.length > 0)
    : groups;

  return (
    <>
      <SearchInput
        className="jenkins-!-margin-bottom-2"
        placeholder={filterPlaceholder}
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        data-autofocus={filterAutoFocus ? "true" : undefined}
      />
      <div className="rsp-assign-dialog__roles">
        {visibleGroups.length === 0 && (
          <div className="rsp-assign-dialog__no-results">
            No matching permissions
          </div>
        )}
        {visibleGroups.map((group) => (
          <div key={group.title}>
            <div className="rsp-assign-dialog__group-title">{group.title}</div>
            <div className="rsp-assign-dialog__group">
              {group.permissions.map((p) => {
                const isImplied = implied.has(p.id);
                const isChecked = selectedIds.has(p.id) || isImplied;
                const inputId = `${idPrefix}-${p.id}`;
                return (
                  <div
                    key={p.id}
                    className="rsp-assign-dialog__role-item jenkins-checkbox"
                    data-permission-id={p.id}
                  >
                    <input
                      type="checkbox"
                      id={inputId}
                      checked={isChecked}
                      disabled={disabled || isImplied}
                      onChange={(e) => onToggle?.(p.id, e.target.checked)}
                    />
                    <label htmlFor={inputId}>
                      {p.name}
                      {isImplied && (
                        <span className="rsp-implied-label"> (implied)</span>
                      )}
                    </label>
                    {p.description && <HelpIcon description={p.description} />}
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
