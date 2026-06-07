import { type FormEvent, useState } from "react";

import { Dialog } from "../common/components/Dialog.tsx";
import { PermissionList } from "../common/components/PermissionList.tsx";
import type { PermissionGroup } from "../common/types/permission.ts";
import type { Role } from "../common/types/role.ts";
import type { PermissionTemplate } from "../common/types/template.ts";

interface EditRoleDialogProps {
  role: Role;
  permissionGroups: PermissionGroup[];
  templates: PermissionTemplate[];
  showPattern: boolean;
  listMacrosUrl: string;
  onCancel: () => void;
  onSubmit: (update: {
    pattern: string;
    permissionIds: string[];
    templateName?: string | null;
  }) => Promise<void>;
}

export function EditRoleDialog({
  role,
  permissionGroups,
  templates,
  showPattern,
  listMacrosUrl,
  onCancel,
  onSubmit,
}: EditRoleDialogProps) {
  const [pattern, setPattern] = useState(role.pattern);
  const [templateName, setTemplateName] = useState(role.templateName ?? "");
  const [selected, setSelected] = useState<ReadonlySet<string>>(
    new Set(role.permissionIds),
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const showTemplate = templates.length > 0;

  const togglePermission = (id: string, next: boolean) => {
    setSelected((prev) => {
      const ns = new Set(prev);
      if (next) ns.add(id);
      else ns.delete(id);
      return ns;
    });
  };

  const onTemplateChange = (value: string) => {
    setTemplateName(value);
    if (!value) return;
    const tmpl = templates.find((t) => t.name === value);
    if (tmpl) setSelected(new Set(tmpl.permissionIds));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (showPattern && !pattern.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        pattern: showPattern ? pattern.trim() : ".*",
        permissionIds: Array.from(selected),
        templateName: templateName || null,
      });
    } catch (err) {
      setError((err as Error).message);
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      title={`Edit role: ${role.name}`}
      onClose={onCancel}
      primaryAction={
        <button
          type="submit"
          form="rsp-edit-role-form"
          className="jenkins-button jenkins-button--primary"
          disabled={(showPattern && !pattern.trim()) || submitting}
        >
          {submitting ? "Saving…" : "Save"}
        </button>
      }
    >
      <form
        id="rsp-edit-role-form"
        onSubmit={handleSubmit}
        className="rsp-dialog__form"
      >
        {error && (
          <div className="jenkins-alert jenkins-alert-danger">{error}</div>
        )}
        <div className="jenkins-form-item">
          <div className="jenkins-form-label">Role name</div>
          <input
            type="text"
            className="jenkins-input"
            value={role.name}
            disabled
          />
        </div>
        {showPattern && (
          <div className="jenkins-form-item">
            <label
              className="jenkins-form-label"
              htmlFor="rsp-edit-role-pattern"
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
              id="rsp-edit-role-pattern"
              type="text"
              className="jenkins-input"
              value={pattern}
              onChange={(e) => setPattern(e.target.value)}
              data-autofocus="true"
              required
            />
          </div>
        )}
        {showTemplate && (
          <div className="jenkins-form-item">
            <label
              className="jenkins-form-label"
              htmlFor="rsp-edit-role-template"
            >
              Permission template
            </label>
            <div className="jenkins-select">
              <select
                id="rsp-edit-role-template"
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
            groups={permissionGroups}
            selectedIds={selected}
            disabled={!!templateName}
            onToggle={togglePermission}
          />
        </div>
      </form>
    </Dialog>
  );
}
