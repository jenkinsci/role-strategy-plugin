import { type FormEvent, useMemo, useState } from "react";

import { Dialog } from "../common/components/Dialog.tsx";
import { PermissionList } from "../common/components/PermissionList.tsx";
import { RadioGroup } from "../common/components/RadioGroup.tsx";
import type {
  BootstrapPermissionGroups,
  ManageRolesBootstrap,
} from "../common/types/bootstrap.ts";
import type { RoleType } from "../common/types/role.ts";
import type { PermissionTemplate } from "../common/types/template.ts";

interface AddRoleDialogProps {
  permissionGroups: BootstrapPermissionGroups;
  templates: PermissionTemplate[];
  permissions: ManageRolesBootstrap["permissions"];
  listMacrosUrl: string;
  onCancel: () => void;
  onSubmit: (input: {
    type: RoleType;
    name: string;
    pattern: string;
    permissionIds: string[];
    templateName?: string;
  }) => Promise<void>;
}

export function AddRoleDialog({
  permissionGroups,
  templates,
  permissions,
  listMacrosUrl,
  onCancel,
  onSubmit,
}: AddRoleDialogProps) {
  const availableScopes = useMemo(() => {
    const scopes: { type: RoleType; label: string }[] = [];
    if (permissions.canEditGlobal) {
      scopes.push({ type: "globalRoles", label: "Global role" });
    }
    if (permissions.canEditProject) {
      scopes.push({ type: "projectRoles", label: "Item role" });
    }
    if (permissions.canEditAgent) {
      scopes.push({ type: "slaveRoles", label: "Agent role" });
    }
    return scopes;
  }, [permissions]);

  const [scope, setScope] = useState<RoleType>(
    availableScopes[0]?.type ?? "globalRoles",
  );
  const [name, setName] = useState("");
  const [pattern, setPattern] = useState(".*");
  const [templateName, setTemplateName] = useState("");
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const showPattern = scope !== "globalRoles";
  const showTemplate = scope === "projectRoles" && templates.length > 0;

  const togglePermission = (id: string, next: boolean) => {
    setSelected((prev) => {
      const ns = new Set(prev);
      if (next) ns.add(id);
      else ns.delete(id);
      return ns;
    });
  };

  // When a template is selected, pre-fill permissions from it.
  const onTemplateChange = (value: string) => {
    setTemplateName(value);
    if (!value) return;
    const tmpl = templates.find((t) => t.name === value);
    if (tmpl) {
      setSelected(new Set(tmpl.permissionIds));
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    if (showPattern && !pattern.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        type: scope,
        name: name.trim(),
        pattern: showPattern ? pattern.trim() : ".*",
        permissionIds: Array.from(selected),
        templateName: templateName || undefined,
      });
    } catch (err) {
      setError((err as Error).message);
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      title="Add role"
      onClose={onCancel}
      primaryAction={
        <button
          type="submit"
          form="rsp-add-role-form"
          className="jenkins-button jenkins-button--primary"
          disabled={
            !name.trim() || (showPattern && !pattern.trim()) || submitting
          }
        >
          {submitting ? "Adding…" : "Add"}
        </button>
      }
    >
      <form
        id="rsp-add-role-form"
        onSubmit={handleSubmit}
        className="rsp-dialog__form"
      >
        {error && (
          <div className="jenkins-alert jenkins-alert-danger">{error}</div>
        )}
        {availableScopes.length > 1 && (
          <div className="jenkins-form-item">
            <div className="jenkins-form-label">Scope</div>
            <RadioGroup
              name="scope"
              value={scope}
              options={availableScopes.map((s) => ({
                value: s.type,
                label: s.label,
              }))}
              onChange={(next) => setScope(next)}
            />
          </div>
        )}
        <div className="jenkins-form-item">
          <label className="jenkins-form-label" htmlFor="rsp-add-role-name">
            Role name
          </label>
          <input
            id="rsp-add-role-name"
            type="text"
            className="jenkins-input"
            value={name}
            onChange={(e) => setName(e.target.value)}
            data-autofocus="true"
            required
          />
        </div>
        {showPattern && (
          <div className="jenkins-form-item">
            <label
              className="jenkins-form-label"
              htmlFor="rsp-add-role-pattern"
            >
              Pattern
            </label>
            <a
              href={listMacrosUrl}
              target="_blank"
              rel="noreferrer"
              className="jenkins-form-description"
            >
              View available macros
            </a>
            <input
              id="rsp-add-role-pattern"
              type="text"
              className="jenkins-input"
              value={pattern}
              onChange={(e) => setPattern(e.target.value)}
              placeholder=".*"
              required
            />
          </div>
        )}
        {showTemplate && (
          <div className="jenkins-form-item">
            <label
              className="jenkins-form-label"
              htmlFor="rsp-add-role-template"
            >
              Permission template
            </label>
            <div className="jenkins-select">
              <select
                id="rsp-add-role-template"
                className="jenkins-select__input"
                value={templateName}
                onChange={(e) => onTemplateChange(e.target.value)}
              >
                <option value="">(none)</option>
                {templates.map((t) => (
                  <option key={t.name} value={t.name}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
        <div className="jenkins-form-item">
          <div className="jenkins-form-label">Permissions</div>
          <PermissionList
            groups={permissionGroups[scope]}
            selectedIds={selected}
            disabled={!!templateName && scope === "projectRoles"}
            onToggle={togglePermission}
          />
        </div>
      </form>
    </Dialog>
  );
}
