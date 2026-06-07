import { type KeyboardEvent, useMemo, useState } from "react";

import type { StrategyClient } from "../common/api/strategy.ts";
import { AssignedPermissions } from "../common/components/AssignedPermissions.tsx";
import { Card } from "../common/components/Card.tsx";
import { IconButton } from "../common/components/IconButton.tsx";
import { EditIcon } from "../common/components/icons/EditIcon.tsx";
import { TrashIcon } from "../common/components/icons/TrashIcon.tsx";
import type { PermissionGroup } from "../common/types/permission.ts";
import type { Role, RoleType } from "../common/types/role.ts";
import type { PermissionTemplate } from "../common/types/template.ts";
import { confirmAction } from "../common/utils/confirm.ts";
import { EditRoleDialog } from "./EditRoleDialog.tsx";

interface RoleCardsProps {
  type: RoleType;
  title: string;
  showPattern: boolean;
  showTemplate: boolean;
  canEdit: boolean;
  permissionGroups: PermissionGroup[];
  roles: Role[];
  templates: PermissionTemplate[];
  search: string;
  filterIds: ReadonlySet<string>;
  emptyTitle: string;
  emptyBody: string;
  listMacrosUrl: string;
  onRoleChange: (next: Role[]) => void;
  onError: (message: string | null) => void;
  client: StrategyClient;
}

export function RoleCards({
  type,
  title,
  showPattern,
  showTemplate,
  canEdit,
  permissionGroups,
  roles,
  templates,
  search,
  filterIds,
  emptyTitle,
  emptyBody,
  listMacrosUrl,
  onRoleChange,
  onError,
  client,
}: RoleCardsProps) {
  const [editing, setEditing] = useState<Role | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const permissionsById = useMemo(() => {
    const m = new Map<string, { name: string; groupTitle: string }>();
    for (const g of permissionGroups) {
      for (const p of g.permissions) {
        m.set(p.id, { name: p.name, groupTitle: g.title });
      }
    }
    return m;
  }, [permissionGroups]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return roles.filter((role) => {
      const matchesSearch =
        !q ||
        role.name.toLowerCase().includes(q) ||
        (role.pattern ?? "").toLowerCase().includes(q);
      const matchesFilter =
        filterIds.size === 0 ||
        [...filterIds].every((id) => role.permissionIds.includes(id));
      return matchesSearch && matchesFilter;
    });
  }, [roles, search, filterIds]);

  const handleDelete = async (role: Role) => {
    const ok = await confirmAction(`Delete role "${role.name}"?`);
    if (!ok) return;
    onError(null);
    try {
      await client.removeRoles(type, [role.name]);
      onRoleChange(roles.filter((r) => r.name !== role.name));
    } catch (err) {
      onError((err as Error).message);
    }
  };

  const handleEditSubmit = async (
    role: Role,
    update: {
      pattern: string;
      permissionIds: string[];
      templateName?: string | null;
    },
  ) => {
    await client.addRole({
      type,
      roleName: role.name,
      permissionIds: update.permissionIds,
      overwrite: true,
      pattern: showPattern ? update.pattern : undefined,
      template: update.templateName ?? undefined,
    });
    onRoleChange(
      roles.map((r) =>
        r.name === role.name
          ? {
              ...r,
              pattern: update.pattern,
              permissionIds: update.permissionIds,
              templateName: update.templateName ?? null,
            }
          : r,
      ),
    );
    setEditing(null);
  };

  const buildSummary = (role: Role) => {
    if (role.permissionIds.length === 0) return null;
    return role.permissionIds
      .map((id) => permissionsById.get(id))
      .filter((p): p is { name: string; groupTitle: string } => !!p)
      .map((p) => `${p.groupTitle}/${p.name}`)
      .sort()
      .join(", ");
  };

  const isFiltering = search.trim() !== "" || filterIds.size > 0;
  const toggleCollapsed = () => setCollapsed((v) => !v);
  const onHeaderKeyDown = (e: KeyboardEvent<HTMLHeadingElement>) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      toggleCollapsed();
    }
  };

  return (
    <section
      className={`rsp-container rsp-section-collapsible${collapsed ? " rsp-section--collapsed" : ""}`}
      data-role-type={type}
    >
      <h2
        className="jenkins-section__title"
        role="button"
        tabIndex={0}
        aria-expanded={!collapsed}
        onClick={toggleCollapsed}
        onKeyDown={onHeaderKeyDown}
      >
        {title}
        <span className="rsp-section-count">
          {filtered.length}
          {isFiltering && filtered.length !== roles.length
            ? ` of ${roles.length}`
            : ""}
        </span>
        <SectionChevron />
      </h2>
      {roles.length === 0 && !isFiltering && (
        <div className="jenkins-notice">
          <div className="jenkins-notice__title">{emptyTitle}</div>
          <div>{emptyBody}</div>
        </div>
      )}
      {roles.length > 0 && filtered.length === 0 && (
        <div className="jenkins-notice rsp-empty-state">No matching roles</div>
      )}
      {filtered.length > 0 && (
        <div className="rsp-cards">
          {filtered.map((role) => {
            const templated = !!role.templateName;
            const badges = showTemplate && templated && (
              <span className="rsp-card__template-badge">
                {role.templateName}
              </span>
            );
            const actions = canEdit ? (
              <>
                <IconButton
                  tooltip="Edit role"
                  onClick={() => setEditing(role)}
                  icon={<EditIcon />}
                />
                <IconButton
                  tooltip="Delete role"
                  destructive
                  onClick={() => handleDelete(role)}
                  icon={<TrashIcon />}
                />
              </>
            ) : undefined;
            return (
              <Card
                key={role.name}
                name={role.name}
                pattern={showPattern ? role.pattern : undefined}
                badges={badges}
                summary={buildSummary(role)}
                actions={actions}
                readOnly={!canEdit}
                body={
                  <AssignedPermissions
                    permissionGroups={permissionGroups}
                    permissionIds={role.permissionIds}
                  />
                }
              />
            );
          })}
        </div>
      )}
      {editing && (
        <EditRoleDialog
          role={editing}
          permissionGroups={permissionGroups}
          templates={showTemplate ? templates : []}
          showPattern={showPattern}
          listMacrosUrl={listMacrosUrl}
          onCancel={() => setEditing(null)}
          onSubmit={(update) => handleEditSubmit(editing, update)}
        />
      )}
    </section>
  );
}

function SectionChevron() {
  return (
    <svg
      className="rsp-section-chevron"
      viewBox="0 0 512 512"
      fill="none"
      stroke="currentColor"
      strokeWidth="48"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M112 184l144 144 144-144" />
    </svg>
  );
}
