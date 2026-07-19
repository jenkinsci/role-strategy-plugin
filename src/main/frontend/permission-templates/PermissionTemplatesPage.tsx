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
import type { TemplatesBootstrap } from "../common/types/bootstrap.ts";
import type { PermissionTemplate } from "../common/types/template.ts";
import { TemplateDialog } from "./TemplateDialog.tsx";

interface PermissionTemplatesPageProps {
  bootstrap: TemplatesBootstrap;
  client: StrategyClient;
}

const byName = (a: PermissionTemplate, b: PermissionTemplate) =>
  a.name.localeCompare(b.name);

export function PermissionTemplatesPage({
  bootstrap,
  client,
}: PermissionTemplatesPageProps) {
  const [templates, setTemplates] = useState<PermissionTemplate[]>(() =>
    [...bootstrap.templates].sort(byName),
  );
  const [search, setSearch] = useState("");
  const [filterIds, setFilterIds] = useState<ReadonlySet<string>>(new Set());
  const [mode, setMode] = useState<"closed" | "add" | { edit: string }>(
    "closed",
  );
  const [error, setError] = useState<string | null>(null);

  const openAdd = useCallback(() => setMode("add"), []);
  useAppBarButton("rsp-add-template-btn", openAdd);

  const permissionsById = useMemo(() => {
    const m = new Map<string, { name: string; groupTitle: string }>();
    for (const g of bootstrap.permissionGroups) {
      for (const p of g.permissions) {
        m.set(p.id, { name: p.name, groupTitle: g.title });
      }
    }
    return m;
  }, [bootstrap.permissionGroups]);

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
    return templates.filter((t) => {
      const matchesSearch = !q || t.name.toLowerCase().includes(q);
      const matchesFilter =
        filterIds.size === 0 ||
        [...filterIds].every((id) => t.permissionIds.includes(id));
      return matchesSearch && matchesFilter;
    });
  }, [templates, search, filterIds]);

  const buildSummary = (template: PermissionTemplate) => {
    if (template.permissionIds.length === 0) return null;
    return template.permissionIds
      .map((id) => permissionsById.get(id))
      .filter((p): p is { name: string; groupTitle: string } => !!p)
      .map((p) => `${p.groupTitle}/${p.name}`)
      .sort()
      .join(", ");
  };

  const handleAdd = async (input: {
    name: string;
    permissionIds: string[];
  }) => {
    await client.addTemplate(input.name, input.permissionIds, false);
    setTemplates((prev) =>
      [
        ...prev,
        { name: input.name, permissionIds: input.permissionIds, isUsed: false },
      ].sort(byName),
    );
    setMode("closed");
  };

  const handleEdit = async (
    target: PermissionTemplate,
    input: { permissionIds: string[] },
  ) => {
    await client.addTemplate(target.name, input.permissionIds, true);
    setTemplates((prev) =>
      prev.map((t) =>
        t.name === target.name
          ? { ...t, permissionIds: input.permissionIds }
          : t,
      ),
    );
    setMode("closed");
  };

  const handleDelete = async (template: PermissionTemplate) => {
    if (template.isUsed) {
      setError(`Template "${template.name}" is in use and cannot be deleted.`);
      return;
    }
    // dialog.confirm rejects when the user cancels.
    const confirmed = await dialog
      .confirm(`Delete template "${template.name}"?`, {
        type: "destructive",
        okText: "Delete",
      })
      .catch(() => false);
    if (!confirmed) return;
    setError(null);
    try {
      await client.removeTemplates([template.name]);
      setTemplates((prev) => prev.filter((t) => t.name !== template.name));
    } catch (err) {
      console.error("Failed to delete template", template.name, err);
      setError(`Failed to delete template "${template.name}".`);
    }
  };

  const editing =
    typeof mode === "object" && mode.edit
      ? (templates.find((t) => t.name === mode.edit) ?? null)
      : null;

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
      {templates.length > 0 && (
        <div className="jenkins-!-margin-bottom-3">
          <SearchWithFilter
            searchPlaceholder="Search templates"
            search={search}
            onSearchChange={setSearch}
            filterGroups={bootstrap.permissionGroups}
            selectedFilterIds={filterIds}
            onFilterToggle={toggleFilter}
            onFilterReset={() => setFilterIds(new Set())}
          />
        </div>
      )}
      {templates.length === 0 ? (
        <div className="jenkins-notice">
          <div className="jenkins-notice__title">
            No permission templates defined
          </div>
          {bootstrap.canEdit && (
            <div className="jenkins-notice__description">
              Click Add Template to create one.
            </div>
          )}
        </div>
      ) : filtered.length === 0 ? (
        <div className="jenkins-notice rsp-empty-state">
          <div>No matching templates</div>
          <button
            type="button"
            className="jenkins-button jenkins-!-margin-top-2"
            onClick={() => {
              setSearch("");
              setFilterIds(new Set());
            }}
          >
            Clear filters
          </button>
        </div>
      ) : (
        <div className="rsp-cards">
          {filtered.map((template) => (
            <Card
              key={template.name}
              name={template.name}
              badges={
                template.isUsed && (
                  <span className="rsp-card__template-badge">In use</span>
                )
              }
              summary={buildSummary(template)}
              summaryPlaceholder="No permissions"
              actions={
                bootstrap.canEdit && (
                  <>
                    <IconButton
                      tooltip="Edit template"
                      onClick={() => setMode({ edit: template.name })}
                      icon={<EditIcon />}
                    />
                    <IconButton
                      tooltip={
                        template.isUsed
                          ? "Cannot delete a template that is in use"
                          : "Delete template"
                      }
                      destructive
                      disabled={template.isUsed}
                      onClick={() => handleDelete(template)}
                      icon={<TrashIcon />}
                    />
                  </>
                )
              }
              readOnly={!bootstrap.canEdit}
              body={
                <PermissionGroups
                  groups={bootstrap.permissionGroups}
                  selectedIds={new Set(template.permissionIds)}
                  disabled
                  showOnlySelected
                />
              }
            />
          ))}
        </div>
      )}
      {mode === "add" && (
        <TemplateDialog
          title="Add permission template"
          submitLabel="Add"
          allowNameEdit
          existingNames={new Set(templates.map((t) => t.name))}
          permissionGroups={bootstrap.permissionGroups}
          initialName=""
          initialPermissionIds={[]}
          onCancel={() => setMode("closed")}
          onSubmit={async (input) => handleAdd(input)}
        />
      )}
      {editing && (
        <TemplateDialog
          title={`Edit template: ${editing.name}`}
          submitLabel="Save"
          allowNameEdit={false}
          existingNames={new Set()}
          permissionGroups={bootstrap.permissionGroups}
          initialName={editing.name}
          initialPermissionIds={editing.permissionIds}
          onCancel={() => setMode("closed")}
          onSubmit={async (input) =>
            handleEdit(editing, { permissionIds: input.permissionIds })
          }
        />
      )}
    </>
  );
}
