import "../common/styles/role-strategy.css";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import type { StrategyClient } from "../common/api/strategy.ts";
import { useAppBarButton } from "../common/components/AppBarButton.tsx";
import { CloseIcon } from "../common/components/icons/CloseIcon.tsx";
import { Pagination } from "../common/components/Pagination.tsx";
import { SearchWithFilter } from "../common/components/SearchWithFilter.tsx";
import { Tabs } from "../common/components/Tabs.tsx";
import type { SidEntry, SidInfo, SidType } from "../common/types/assignment.ts";
import type { AssignRolesBootstrap } from "../common/types/bootstrap.ts";
import type { RoleTypeKey } from "../common/types/role.ts";
import {
  AssignSidDialog,
  type AssignSidDialogResult,
} from "./AssignSidDialog.tsx";
import { SidCard } from "./SidCard.tsx";

interface AssignRolesPageProps {
  bootstrap: AssignRolesBootstrap;
  client: StrategyClient;
  checkSidNameUrl: string;
}

interface TabConfig {
  key: RoleTypeKey;
  label: string;
  hash: string;
}

const TABS: TabConfig[] = [
  { key: "globalRoles", label: "Global roles", hash: "#global" },
  { key: "projectRoles", label: "Item roles", hash: "#item" },
  { key: "slaveRoles", label: "Agent roles", hash: "#agent" },
];

/** The reserved sids Jenkins always defines; they cannot be removed. */
const isInternal = (entry: SidEntry) =>
  (entry.name === "anonymous" && entry.type === "USER") ||
  (entry.name === "authenticated" && entry.type === "GROUP");

const entryRank = (entry: SidEntry) => {
  if (!isInternal(entry)) return 2;
  return entry.name === "anonymous" ? 0 : 1;
};

const byEntry = (a: SidEntry, b: SidEntry) =>
  entryRank(a) - entryRank(b) ||
  a.name.localeCompare(b.name) ||
  a.type.localeCompare(b.type);

/** Pin the reserved anonymous/authenticated entries even when the server has
    no assignments for them, so their roles can be toggled without an explicit
    add. The server only stores sids that are attached to at least one role. */
const withFixedEntries = (entries: SidEntry[]): SidEntry[] => {
  const result = [...entries];
  if (!result.some((e) => e.name === "anonymous" && e.type === "USER")) {
    result.push({ name: "anonymous", type: "USER", roles: [] });
  }
  if (!result.some((e) => e.name === "authenticated" && e.type === "GROUP")) {
    result.push({ name: "authenticated", type: "GROUP", roles: [] });
  }
  return result.sort(byEntry);
};

const sameSid = (a: SidEntry, name: string, type: SidType) =>
  a.name === name && a.type === type;

const infoKey = (type: SidType, sid: string) => `${type}:${sid}`;

const PAGE_SIZE = 50;

/** Synthetic filter id for narrowing to ambiguous (EITHER) entries; it lives
    in the same dropdown as the role filters but matches on entry type. */
const AMBIGUOUS_FILTER_ID = "__rsp-ambiguous__";

export function AssignRolesPage({
  bootstrap,
  client,
  checkSidNameUrl,
}: AssignRolesPageProps) {
  const visibleTabs = useMemo(
    () => TABS.filter((t) => bootstrap[t.key].visible),
    [bootstrap],
  );

  const [activeKey, setActiveKey] = useState<RoleTypeKey>(() => {
    const fromHash = visibleTabs.find((t) => t.hash === window.location.hash);
    return (fromHash ?? visibleTabs[0])?.key ?? "globalRoles";
  });
  const [entriesByType, setEntriesByType] = useState<
    Record<RoleTypeKey, SidEntry[]>
  >(() => ({
    globalRoles: withFixedEntries(bootstrap.globalRoles.entries),
    projectRoles: withFixedEntries(bootstrap.projectRoles.entries),
    slaveRoles: withFixedEntries(bootstrap.slaveRoles.entries),
  }));
  const [sidInfo, setSidInfo] = useState<Record<string, SidInfo>>({});
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<ReadonlySet<string>>(new Set());
  const [mode, setMode] = useState<"closed" | "add" | { edit: string }>(
    "closed",
  );
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const active = bootstrap[activeKey];
  const entries = entriesByType[activeKey];
  const roles = active.roles;

  const openAdd = useCallback(() => setMode("add"), []);
  useAppBarButton("rsp-add-sid-btn", openAdd, {
    visible: active.canEdit,
  });

  const mergeSidInfo = useCallback((infos: SidInfo[]) => {
    setSidInfo((prev) => {
      const next = { ...prev };
      for (const info of infos) {
        next[infoKey(info.type, info.sid)] = info;
      }
      return next;
    });
  }, []);

  const selectTab = (key: string) => {
    const tab = visibleTabs.find((t) => t.key === key);
    if (!tab || tab.key === activeKey) return;
    setActiveKey(tab.key);
    // Search and role filter are scoped to a role type; the role catalogue
    // differs between types, so carrying them over would be wrong.
    setSearch("");
    setRoleFilter(new Set());
    setPage(0);
    setMode("closed");
    setError(null);
    history.replaceState(null, "", tab.hash);
  };

  const hasAmbiguous = entries.some((e) => e.type === "EITHER");

  const filterGroups = useMemo(() => {
    const groups = [];
    if (hasAmbiguous) {
      groups.push({
        title: "Entries",
        permissions: [
          {
            id: AMBIGUOUS_FILTER_ID,
            name: "Ambiguous",
            description: "Entries that apply to both a user and a group",
            impliedByList: [],
          },
        ],
      });
    }
    if (roles.length > 0) {
      groups.push({
        title: "Roles",
        permissions: roles.map((r) => ({
          id: r.name,
          name: r.name,
          description: r.pattern ?? "",
          impliedByList: [],
        })),
      });
    }
    return groups;
  }, [roles, hasAmbiguous]);

  const toggleRoleFilter = (roleName: string) => {
    setRoleFilter((prev) => {
      const next = new Set(prev);
      if (next.has(roleName)) next.delete(roleName);
      else next.add(roleName);
      return next;
    });
    setPage(0);
  };

  const changeSearch = (next: string) => {
    setSearch(next);
    setPage(0);
  };

  const clearFilters = () => {
    setSearch("");
    setRoleFilter(new Set());
    setPage(0);
  };

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return entries.filter((entry) => {
      const displayName =
        sidInfo[infoKey(entry.type, entry.name)]?.displayName ?? "";
      const matchesSearch =
        !q ||
        entry.name.toLowerCase().includes(q) ||
        displayName.toLowerCase().includes(q);
      const matchesAmbiguous =
        !roleFilter.has(AMBIGUOUS_FILTER_ID) || entry.type === "EITHER";
      const matchesRoles = [...roleFilter]
        .filter((id) => id !== AMBIGUOUS_FILTER_ID)
        .every((roleName) => entry.roles.includes(roleName));
      return matchesSearch && matchesAmbiguous && matchesRoles;
    });
  }, [entries, search, roleFilter, sidInfo]);

  // Rendering thousands of cards at once makes the page sluggish, so the list
  // is paged client-side; search and the role filter still cover all entries.
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const pageEntries = useMemo(
    () =>
      filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE),
    [filtered, currentPage],
  );

  // Realm lookups are scoped to the visible page so a large instance does not
  // fire thousands of lookups up front. The reserved entries are skipped: they
  // always exist and never carry a display name. A lookup whose page is left
  // before it lands (paging on, typing in search) is aborted and its sids are
  // released for a retry on their next appearance.
  const requestedInfo = useRef<Set<string>>(new Set());
  useEffect(() => {
    const items = pageEntries
      .filter(
        (e) =>
          !isInternal(e) && !requestedInfo.current.has(infoKey(e.type, e.name)),
      )
      .map((e) => ({ sid: e.name, type: e.type }));
    if (items.length === 0) return;
    for (const item of items) {
      requestedInfo.current.add(infoKey(item.type, item.sid));
    }
    const controller = new AbortController();
    client
      .getSidsInfo(items, controller.signal)
      .then(mergeSidInfo)
      .catch((err) => {
        for (const item of items) {
          requestedInfo.current.delete(infoKey(item.type, item.sid));
        }
        if (!controller.signal.aborted) {
          // Enrichment is best-effort; the page stays usable with raw sids.
          console.error("Failed to resolve sids", err);
        }
      });
    return () => controller.abort();
  }, [pageEntries, client, mergeSidInfo]);

  const updateEntries = (
    key: RoleTypeKey,
    update: (prev: SidEntry[]) => SidEntry[],
  ) => {
    setEntriesByType((prev) => ({ ...prev, [key]: update(prev[key]) }));
  };

  const resync = async (key: RoleTypeKey) => {
    try {
      const fresh = await client.getRoleAssignments(key);
      updateEntries(key, () => withFixedEntries(fresh));
    } catch (err) {
      console.error("Failed to reload role assignments", err);
    }
  };

  const handleAddSubmit = async (input: AssignSidDialogResult) => {
    const key = activeKey;
    for (const roleName of input.roles) {
      await client.assignSidRole(key, roleName, input.name, input.type);
    }
    const nextEntries = [
      ...entriesByType[key],
      { name: input.name, type: input.type, roles: input.roles },
    ].sort(byEntry);
    setEntriesByType((prev) => ({ ...prev, [key]: nextEntries }));
    // Drop any filtering and jump to the page the new entry lands on so it is
    // visible immediately.
    setSearch("");
    setRoleFilter(new Set());
    setPage(
      Math.floor(
        nextEntries.findIndex((e) => sameSid(e, input.name, input.type)) /
          PAGE_SIZE,
      ),
    );
    setMode("closed");
  };

  const handleEditSubmit = async (
    entry: SidEntry,
    input: AssignSidDialogResult,
  ) => {
    const key = activeKey;
    const before = new Set(entry.roles);
    const after = new Set(input.roles);
    const toAdd = input.roles.filter((r) => !before.has(r));
    const toRemove = entry.roles.filter((r) => !after.has(r));
    try {
      for (const roleName of toAdd) {
        await client.assignSidRole(key, roleName, entry.name, entry.type);
      }
      for (const roleName of toRemove) {
        await client.unassignSidRole(key, roleName, entry.name, entry.type);
      }
    } catch (err) {
      // Some changes may have been applied; realign with the server before
      // the dialog surfaces the error.
      await resync(key);
      throw err;
    }
    updateEntries(key, (prev) =>
      prev.map((e) =>
        sameSid(e, entry.name, entry.type) ? { ...e, roles: input.roles } : e,
      ),
    );
    setMode("closed");
  };

  const handleDelete = async (entry: SidEntry) => {
    // dialog.confirm rejects when the user cancels.
    const confirmed = await dialog
      .confirm(`Remove "${entry.name}" and all its role assignments?`, {
        type: "destructive",
        okText: "Remove",
      })
      .catch(() => false);
    if (!confirmed) return;
    setError(null);
    const key = activeKey;
    try {
      // An entry without roles only exists client-side; nothing to delete.
      if (entry.roles.length > 0) {
        await client.deleteSidEntry(key, entry.name, entry.type);
      }
      updateEntries(key, (prev) =>
        prev.filter((e) => !sameSid(e, entry.name, entry.type)),
      );
    } catch (err) {
      console.error("Failed to remove entry", entry.name, err);
      setError(`Failed to remove "${entry.name}".`);
      await resync(key);
    }
  };

  const handleMigrate = async (entry: SidEntry, to: "USER" | "GROUP") => {
    const kindWord = to === "USER" ? "user" : "group";
    const confirmed = await dialog
      .confirm(
        `Migrate the ambiguous entry "${entry.name}" to a ${kindWord}? Its role assignments are kept.`,
        { okText: "Migrate" },
      )
      .catch(() => false);
    if (!confirmed) return;
    setError(null);
    const key = activeKey;
    try {
      // Assign under the new type first, then drop the ambiguous entry, so a
      // mid-flow failure leaves extra grants rather than dropped ones.
      for (const roleName of entry.roles) {
        await client.assignSidRole(key, roleName, entry.name, to);
      }
      await client.deleteSidEntry(key, entry.name, "EITHER");
      updateEntries(key, (prev) => {
        const target = prev.find((e) => sameSid(e, entry.name, to));
        const withoutEither = prev.filter(
          (e) => !sameSid(e, entry.name, "EITHER"),
        );
        if (target) {
          // The server unions role assignments; mirror that in place.
          const merged = new Set([...target.roles, ...entry.roles]);
          return withoutEither.map((e) =>
            e === target ? { ...e, roles: [...merged] } : e,
          );
        }
        return [...withoutEither, { ...entry, type: to }].sort(byEntry);
      });
    } catch (err) {
      console.error("Failed to migrate entry", entry.name, err);
      setError(`Failed to migrate "${entry.name}".`);
      await resync(key);
    }
  };

  const editing =
    typeof mode === "object" && mode.edit
      ? (entries.find((e) => infoKey(e.type, e.name) === mode.edit) ?? null)
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
            panelId="rsp-assign-panel"
          />
        </div>
      )}
      <div
        id="rsp-assign-panel"
        role="tabpanel"
        aria-labelledby={`rsp-tab-${activeKey}`}
      >
        {hasAmbiguous && (
          <div className="jenkins-alert jenkins-alert-warning jenkins-!-margin-bottom-3">
            Some entries are ambiguous: they apply to both users and groups of
            the given name. If users can choose their own user name or create
            groups, they may be able to obtain greater permissions. Migrate each
            ambiguous entry to an explicit user or group entry.
          </div>
        )}
        <div className="jenkins-!-margin-bottom-3">
          <SearchWithFilter
            searchPlaceholder="Search users and groups"
            search={search}
            onSearchChange={changeSearch}
            filterGroups={filterGroups.length > 0 ? filterGroups : undefined}
            filterLabel="Filter by role"
            filterSearchPlaceholder="Search roles"
            selectedFilterIds={roleFilter}
            onFilterToggle={toggleRoleFilter}
            onFilterReset={clearFilters}
          />
        </div>
        {filtered.length === 0 ? (
          <div className="jenkins-notice">
            <div>No matching users or groups</div>
            <div className="jenkins-notice__description">
              <button
                type="button"
                className="jenkins-button"
                onClick={clearFilters}
              >
                Clear filters
              </button>
            </div>
          </div>
        ) : (
          <div className="rsp-cards">
            {pageEntries.map((entry) => (
              <SidCard
                key={`${entry.type}:${entry.name}`}
                entry={entry}
                info={sidInfo[infoKey(entry.type, entry.name)]}
                roles={roles}
                canEdit={active.canEdit}
                internal={isInternal(entry)}
                onEdit={(e) => setMode({ edit: infoKey(e.type, e.name) })}
                onDelete={handleDelete}
                onMigrate={handleMigrate}
              />
            ))}
          </div>
        )}
        <Pagination
          page={currentPage}
          pageCount={pageCount}
          totalItems={filtered.length}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
        />
      </div>
      {mode === "add" && (
        <AssignSidDialog
          title="Add user or group"
          kind="USER"
          allowNameEdit
          existingEntries={entries}
          roles={roles}
          checkSidNameUrl={checkSidNameUrl}
          initialName=""
          initialRoles={[]}
          submitLabel="Add"
          onCancel={() => setMode("closed")}
          onSubmit={handleAddSubmit}
        />
      )}
      {editing && (
        <AssignSidDialog
          title={`Edit roles: ${editing.name}`}
          kind={editing.type}
          allowNameEdit={false}
          existingEntries={entries}
          roles={roles}
          checkSidNameUrl={checkSidNameUrl}
          initialName={editing.name}
          initialRoles={editing.roles}
          submitLabel="Save"
          onCancel={() => setMode("closed")}
          onSubmit={(input) => handleEditSubmit(editing, input)}
        />
      )}
    </>
  );
}
