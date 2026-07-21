import { type FormEvent, useState } from "react";

import { ApiError } from "../common/api/client.ts";
import { Dialog } from "../common/components/Dialog.tsx";
import { PermissionList } from "../common/components/PermissionList.tsx";
import type { PermissionGroup } from "../common/types/permission.ts";

interface TemplateDialogProps {
  title: string;
  submitLabel: string;
  allowNameEdit: boolean;
  existingNames: ReadonlySet<string>;
  permissionGroups: PermissionGroup[];
  initialName: string;
  initialPermissionIds: string[];
  onCancel: () => void;
  onSubmit: (input: { name: string; permissionIds: string[] }) => Promise<void>;
}

export function TemplateDialog({
  title,
  submitLabel,
  allowNameEdit,
  existingNames,
  permissionGroups,
  initialName,
  initialPermissionIds,
  onCancel,
  onSubmit,
}: TemplateDialogProps) {
  const [name, setName] = useState(initialName);
  const [selected, setSelected] = useState<ReadonlySet<string>>(
    new Set(initialPermissionIds),
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const togglePermission = (id: string, next: boolean) => {
    setSelected((prev) => {
      const ns = new Set(prev);
      if (next) ns.add(id);
      else ns.delete(id);
      return ns;
    });
  };

  const nameTaken =
    allowNameEdit && existingNames.has(name.trim()) && name.trim() !== "";
  const canSubmit = name.trim() !== "" && !nameTaken && !submitting;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        name: name.trim(),
        permissionIds: Array.from(selected),
      });
    } catch (err) {
      const detail = err instanceof ApiError ? err.body.trim() : "";
      setError(
        detail
          ? `Failed to save template: ${detail}`
          : "Failed to save template.",
      );
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      title={title}
      onClose={onCancel}
      primaryAction={
        <button
          type="submit"
          form="rsp-template-form"
          className="jenkins-button jenkins-button--primary"
          disabled={!canSubmit}
        >
          {submitting ? "Saving…" : submitLabel}
        </button>
      }
    >
      <form
        id="rsp-template-form"
        onSubmit={handleSubmit}
        className="rsp-dialog__form"
      >
        {error && (
          <div className="jenkins-alert jenkins-alert-danger">{error}</div>
        )}
        <div className="jenkins-form-item">
          <label className="jenkins-form-label" htmlFor="rsp-template-name">
            Name
          </label>
          <input
            id="rsp-template-name"
            type="text"
            className="jenkins-input"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={!allowNameEdit}
            data-autofocus={allowNameEdit ? "true" : undefined}
            required
          />
          {nameTaken && (
            <div className="jenkins-form-description jenkins-!-color-red">
              A template with this name already exists.
            </div>
          )}
        </div>
        <div className="jenkins-form-item">
          <div className="jenkins-form-label">Permissions</div>
          <PermissionList
            groups={permissionGroups}
            selectedIds={selected}
            onToggle={togglePermission}
            filterAutoFocus={!allowNameEdit}
          />
        </div>
      </form>
    </Dialog>
  );
}
