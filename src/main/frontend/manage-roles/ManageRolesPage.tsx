import "../common/styles/role-strategy.css";

import { useCallback, useMemo, useState } from "react";

import type { StrategyClient } from "../common/api/strategy.ts";
import { useAppBarButton } from "../common/components/AppBarButton.tsx";
import { Card } from "../common/components/Card.tsx";
import { IconButton } from "../common/components/IconButton.tsx";
import { CloseIcon } from "../common/components/icons/CloseIcon.tsx";
import { EditIcon } from "../common/components/icons/EditIcon.tsx";
import { TrashIcon } from "../common/components/icons/TrashIcon.tsx";
import { PermissionGroups } from "../common/components/PermissionGroups.tsx";
import { SearchWithFilter } from "../common/components/SearchWithFilter.tsx";
import { Tabs } from "../common/components/Tabs.tsx";
import { Tooltip } from "../common/components/Tooltip.tsx";
import type { ManageRolesBootstrap } from "../common/types/bootstrap.ts";
import type { Role, RoleTypeKey } from "../common/types/role.ts";
import {
  buildPermissionSummary,
  indexPermissions,
} from "../common/utils/permissionSummary.ts";
import { MatchingDialog } from "./MatchingDialog.tsx";
import { RoleDialog, type RoleDialogResult } from "./RoleDialog.tsx";

interface ManageRolesPageProps {
  bootstrap: ManageRolesBootstrap;
  client: StrategyClient;
  rootUrl: string;
  checkPatternUrl: string;
}

interface TabConfig {
  key: RoleTypeKey;
  label: string;
  singular: string;
  hash: string;
}

const TABS: TabConfig[] = [
  {
    key: "globalRoles",
    label: "Global roles",
    singular: "global role",
    hash: "#global",
  },
  {
    key: "projectRoles",
    label: "Item roles",
    singular: "item role",
    hash: "#item",
  },
  {
    key: "slaveRoles",
    label: "Agent roles",
    singular: "agent role",
    hash: "#agent",
  },
];

const byName = (a: Role, b: Role) => a.name.localeCompare(b.name);

export function ManageRolesPage({
  bootstrap,
  client,
  rootUrl,
  checkPatternUrl,
}: ManageRolesPageProps) {
  const visibleTabs = useMemo(
    () => TABS.filter((t) => bootstrap[t.key].visible),
    [bootstrap],
  );

  const [activeKey, setActiveKey] = useState<RoleTypeKey>(() => {
    const fromHash = visibleTabs.find((t) => t.hash === window.location.hash);
    return (fromHash ?? visibleTabs[0])?.key ?? "globalRoles";
  });
  const [rolesByType, setRolesByType] = useState<Record<RoleTypeKey, Role[]>>(
    () => ({
      globalRoles: [...bootstrap.globalRoles.roles].sort(byName),
      projectRoles: [...bootstrap.projectRoles.roles].sort(byName),
      slaveRoles: [...bootstrap.slaveRoles.roles].sort(byName),
    }),
  );
  const [search, setSearch] = useState("");
  const [filterIds, setFilterIds] = useState<ReadonlySet<string>>(new Set());
  const [mode, setMode] = useState<"closed" | "add" | { edit: string }>(
    "closed",
  );
  const [matching, setMatching] = useState<{
    kind: "items" | "agents";
    pattern: string;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const activeTab =
    visibleTabs.find((t) => t.key === activeKey) ?? visibleTabs[0];
  const active = bootstrap[activeKey];
  const roles = rolesByType[activeKey];

  const openAdd = useCallback(() => setMode("add"), []);
  useAppBarButton("rsp-add-role-btn", openAdd, { visible: active.canEdit });

  const permissionsById = useMemo(
    () => indexPermissions(active.permissionGroups),
    [active.permissionGroups],
  );

  const selectTab = (key: string) => {
    const tab = visibleTabs.find((t) => t.key === key);
    if (!tab || tab.key === activeKey) return;
    setActiveKey(tab.key);
    // Search and permission filter are scoped to a role type; the permission
    // catalogue differs between types, so carrying them over would be wrong.
    setSearch("");
    setFilterIds(new Set());
    setMode("closed");
    setError(null);
    history.replaceState(null, "", tab.hash);
  };

  const toggleFilter = (id: string) => {
    setFilterIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return roles.filter((r) => {
      const matchesSearch =
        !q ||
        r.name.toLowerCase().includes(q) ||
        (r.pattern ?? "").toLowerCase().includes(q);
      const matchesFilter =
        filterIds.size === 0 ||
        [...filterIds].every((id) => r.permissionIds.includes(id));
      return matchesSearch && matchesFilter;
    });
  }, [roles, search, filterIds]);

  const updateRoles = (key: RoleTypeKey, update: (prev: Role[]) => Role[]) => {
    setRolesByType((prev) => ({ ...prev, [key]: update(prev[key]) }));
  };

  const toRole = (input: RoleDialogResult): Role => {
    // A bound template dictates the permissions; mirror what the backend stores.
    const template = input.template
      ? bootstrap.templates.find((t) => t.name === input.template)
      : undefined;
    return {
      name: input.name,
      pattern: input.pattern,
      templateName: template?.name,
      permissionIds: template ? template.permissionIds : input.permissionIds,
    };
  };

  const handleAdd = async (input: RoleDialogResult) => {
    await client.addRole(
      activeKey,
      input.name,
      input.permissionIds,
      false,
      input.pattern,
      input.template,
    );
    updateRoles(activeKey, (prev) => [...prev, toRole(input)].sort(byName));
    setMode("closed");
  };

  const handleEdit = async (input: RoleDialogResult) => {
    await client.addRole(
      activeKey,
      input.name,
      input.permissionIds,
      true,
      input.pattern,
      input.template,
    );
    updateRoles(activeKey, (prev) =>
      prev.map((r) => (r.name === input.name ? toRole(input) : r)),
    );
    setMode("closed");
  };

  const handleDelete = async (role: Role) => {
    // dialog.confirm rejects when the user cancels.
    const confirmed = await dialog
      .confirm(`Delete role "${role.name}"?`, {
        type: "destructive",
        okText: "Delete",
      })
      .catch(() => false);
    if (!confirmed) return;
    setError(null);
    const key = activeKey;
    try {
      await client.removeRoles(key, [role.name]);
      updateRoles(key, (prev) => prev.filter((r) => r.name !== role.name));
    } catch (err) {
      console.error("Failed to delete role", role.name, err);
      setError(`Failed to delete role "${role.name}".`);
    }
  };

  const buildBadges = (role: Role) => {
    if (!role.pattern && !role.templateName) return null;
    const matchKind = activeKey === "slaveRoles" ? "agents" : "items";
    return (
      <>
        {role.pattern !== undefined &&
          (active.canEdit ? (
            <Tooltip content={`Show matching ${matchKind}`} placement="top">
              <button
                type="button"
                className="rsp-card__pattern rsp-card__pattern--button"
                onClick={(e) => {
                  e.stopPropagation();
                  setMatching({
                    kind: matchKind,
                    pattern: role.pattern ?? "",
                  });
                }}
              >
                {role.pattern}
              </button>
            </Tooltip>
          ) : (
            <span className="rsp-card__pattern">{role.pattern}</span>
          ))}
        {role.templateName && (
          <span className="rsp-card__template-badge">
            Template: {role.templateName}
          </span>
        )}
      </>
    );
  };

  const editing =
    typeof mode === "object" && mode.edit
      ? (roles.find((r) => r.name === mode.edit) ?? null)
      : null;

  if (visibleTabs.length === 0) return null;

  return (
    <>
      {error && (
        <div
          role="alert"
          className="jenkins-alert jenkins-alert-danger rsp-alert jenkins-!-margin-bottom-3"
        >
          <span>{error}</span>
          <button
            type="button"
            className="jenkins-button jenkins-button--tertiary rsp-alert__dismiss"
            aria-label="Dismiss"
            onClick={() => setError(null)}
          >
            <CloseIcon />
          </button>
        </div>
      )}
      {visibleTabs.length > 1 && (
        <div className="jenkins-!-margin-bottom-3">
          <Tabs
            tabs={visibleTabs}
            activeKey={activeKey}
            onSelect={selectTab}
            panelId="rsp-roles-panel"
          />
        </div>
      )}
      <div
        id="rsp-roles-panel"
        role="tabpanel"
        aria-labelledby={`rsp-tab-${activeKey}`}
      >
        {roles.length > 0 && (
          <div className="jenkins-!-margin-bottom-3">
            <SearchWithFilter
              searchPlaceholder={`Search ${activeTab.label.toLowerCase()}`}
              search={search}
              onSearchChange={setSearch}
              filterGroups={active.permissionGroups}
              selectedFilterIds={filterIds}
              onFilterToggle={toggleFilter}
              onFilterReset={() => setFilterIds(new Set())}
            />
          </div>
        )}
        {roles.length === 0 ? (
          <div className="jenkins-notice">
            <div className="jenkins-notice__title">
              No {activeTab.label.toLowerCase()} defined
            </div>
            {active.canEdit && (
              <div className="jenkins-notice__description">
                Click Add Role to create one.
              </div>
            )}
          </div>
        ) : filtered.length === 0 ? (
          <div className="jenkins-notice">
            <div>No matching roles</div>
            <div className="jenkins-notice__description">
              <button
                type="button"
                className="jenkins-button"
                onClick={() => {
                  setSearch("");
                  setFilterIds(new Set());
                }}
              >
                Clear filters
              </button>
            </div>
          </div>
        ) : (
          <div className="rsp-cards">
            {filtered.map((role) => (
              <Card
                key={role.name}
                name={role.name}
                badges={buildBadges(role)}
                summary={buildPermissionSummary(
                  role.permissionIds,
                  permissionsById,
                )}
                summaryPlaceholder="No permissions"
                actions={
                  active.canEdit && (
                    <>
                      <IconButton
                        tooltip="Edit role"
                        onClick={() => setMode({ edit: role.name })}
                        icon={<EditIcon />}
                      />
                      <IconButton
                        tooltip="Delete role"
                        destructive
                        onClick={() => handleDelete(role)}
                        icon={<TrashIcon />}
                      />
                    </>
                  )
                }
                readOnly={!active.canEdit}
                body={
                  <PermissionGroups
                    groups={active.permissionGroups}
                    selectedIds={new Set(role.permissionIds)}
                    disabled
                    showOnlySelected
                  />
                }
              />
            ))}
          </div>
        )}
      </div>
      {mode === "add" && (
        <RoleDialog
          roleType={activeKey}
          title={`Add ${activeTab.singular}`}
          submitLabel="Add"
          allowNameEdit
          existingNames={new Set(roles.map((r) => r.name))}
          permissionGroups={active.permissionGroups}
          templates={bootstrap.templates}
          checkPatternUrl={checkPatternUrl}
          rootUrl={rootUrl}
          initialName=""
          initialPattern=".*"
          initialTemplateName=""
          initialPermissionIds={[]}
          onCancel={() => setMode("closed")}
          onSubmit={handleAdd}
        />
      )}
      {editing && (
        <RoleDialog
          roleType={activeKey}
          title={`Edit role: ${editing.name}`}
          submitLabel="Save"
          allowNameEdit={false}
          existingNames={new Set()}
          permissionGroups={active.permissionGroups}
          templates={bootstrap.templates}
          checkPatternUrl={checkPatternUrl}
          rootUrl={rootUrl}
          initialName={editing.name}
          initialPattern={editing.pattern ?? ".*"}
          initialTemplateName={editing.templateName ?? ""}
          initialPermissionIds={
            editing.templateName ? [] : editing.permissionIds
          }
          onCancel={() => setMode("closed")}
          onSubmit={handleEdit}
        />
      )}
      {matching && (
        <MatchingDialog
          kind={matching.kind}
          pattern={matching.pattern}
          client={client}
          onClose={() => setMatching(null)}
        />
      )}
    </>
  );
}
