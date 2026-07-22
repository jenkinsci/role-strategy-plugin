import { type FormEvent, useMemo, useRef, useState } from "react";

import { ApiError } from "../common/api/client.ts";
import { checkPattern } from "../common/api/validation.ts";
import { Dialog } from "../common/components/Dialog.tsx";
import { PermissionList } from "../common/components/PermissionList.tsx";
import type { PermissionGroup } from "../common/types/permission.ts";
import type { RoleTypeKey } from "../common/types/role.ts";
import type { PermissionTemplate } from "../common/types/template.ts";

export interface RoleDialogResult {
  name: string;
  pattern?: string;
  template?: string;
  permissionIds: string[];
}

interface RoleDialogProps {
  roleType: RoleTypeKey;
  title: string;
  submitLabel: string;
  allowNameEdit: boolean;
  existingNames: ReadonlySet<string>;
  permissionGroups: PermissionGroup[];
  /** Selectable permission templates; item roles only. */
  templates: PermissionTemplate[];
  checkPatternUrl: string;
  rootUrl: string;
  initialName: string;
  initialPattern: string;
  initialTemplateName: string;
  initialPermissionIds: string[];
  onCancel: () => void;
  onSubmit: (input: RoleDialogResult) => Promise<void>;
}

export function RoleDialog({
  roleType,
  title,
  submitLabel,
  allowNameEdit,
  existingNames,
  permissionGroups,
  templates,
  checkPatternUrl,
  rootUrl,
  initialName,
  initialPattern,
  initialTemplateName,
  initialPermissionIds,
  onCancel,
  onSubmit,
}: RoleDialogProps) {
  const [name, setName] = useState(initialName);
  const [pattern, setPattern] = useState(initialPattern);
  const [patternError, setPatternError] = useState<string | null>(null);
  const [templateName, setTemplateName] = useState(initialTemplateName);
  const [selected, setSelected] = useState<ReadonlySet<string>>(
    new Set(initialPermissionIds),
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Invalidates in-flight pattern checks once the field is edited again.
  const patternCheckSeq = useRef(0);

  const hasPattern = roleType !== "globalRoles";
  const hasTemplates = roleType === "projectRoles" && templates.length > 0;

  // Validation runs on blur only, never mid-typing, and only ever disables
  // submit once a pattern is known to be bad. A bad pattern submitted before
  // the check lands is rejected by the backend and surfaced in the dialog.
  const handlePatternChange = (value: string) => {
    setPattern(value);
    setPatternError(null);
    patternCheckSeq.current++;
  };

  const handlePatternBlur = async () => {
    const value = pattern.trim();
    if (value === "") return;
    const seq = ++patternCheckSeq.current;
    try {
      const result = await checkPattern(checkPatternUrl, value);
      if (seq !== patternCheckSeq.current) return;
      setPatternError(result.ok ? null : (result.message ?? "Invalid pattern"));
    } catch {
      // Validation endpoint unreachable; let the backend reject on submit.
    }
  };

  const togglePermission = (id: string, next: boolean) => {
    setSelected((prev) => {
      const ns = new Set(prev);
      if (next) ns.add(id);
      else ns.delete(id);
      return ns;
    });
  };

  const trimmedName = name.trim();
  const nameTaken =
    allowNameEdit && existingNames.has(trimmedName) && trimmedName !== "";
  // removeRoles takes a comma-separated name list, so commas cannot round-trip.
  const nameHasComma = trimmedName.includes(",");
  const patternInvalid =
    hasPattern && (pattern.trim() === "" || patternError !== null);
  const canSubmit =
    trimmedName !== "" &&
    !nameTaken &&
    !nameHasComma &&
    !patternInvalid &&
    !submitting;

  // A bound template dictates the permissions; show its set read-only.
  const boundTemplate = templateName
    ? (templates.find((t) => t.name === templateName) ?? null)
    : null;
  const shownPermissionIds = useMemo(
    () => (boundTemplate ? new Set(boundTemplate.permissionIds) : selected),
    [boundTemplate, selected],
  );

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        name: trimmedName,
        pattern: hasPattern ? pattern.trim() : undefined,
        template: boundTemplate ? boundTemplate.name : undefined,
        permissionIds: boundTemplate ? [] : Array.from(selected),
      });
    } catch (err) {
      const detail = err instanceof ApiError ? err.body.trim() : "";
      setError(
        detail ? `Failed to save role: ${detail}` : "Failed to save role.",
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
          form="rsp-role-form"
          className="jenkins-button jenkins-button--primary"
          disabled={!canSubmit}
        >
          {submitting ? "Saving…" : submitLabel}
        </button>
      }
    >
      <form
        id="rsp-role-form"
        onSubmit={handleSubmit}
        className="rsp-dialog__form"
      >
        {error && (
          <div className="jenkins-alert jenkins-alert-danger">{error}</div>
        )}
        <div className="jenkins-form-item">
          <label className="jenkins-form-label" htmlFor="rsp-role-name">
            Name
          </label>
          <input
            id="rsp-role-name"
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
              A role with this name already exists.
            </div>
          )}
          {nameHasComma && (
            <div className="jenkins-form-description jenkins-!-color-red">
              Role names must not contain commas.
            </div>
          )}
        </div>
        {hasPattern && (
          <div className="jenkins-form-item">
            <label className="jenkins-form-label" htmlFor="rsp-role-pattern">
              Pattern
            </label>
            <input
              id="rsp-role-pattern"
              type="text"
              className="jenkins-input"
              value={pattern}
              onChange={(e) => handlePatternChange(e.target.value)}
              onBlur={handlePatternBlur}
              required
            />
            {patternError && (
              <div className="jenkins-form-description jenkins-!-color-red">
                {patternError}
              </div>
            )}
            <div className="jenkins-form-description">
              Regular expression matching{" "}
              {roleType === "projectRoles" ? "item" : "agent"} names, e.g.{" "}
              <code>prod-.*</code>.{" "}
              <a
                href={`${rootUrl}/manage/role-strategy/list-macros`}
                target="_blank"
                rel="noreferrer"
              >
                View available macros
              </a>
            </div>
          </div>
        )}
        {hasTemplates && (
          <div className="jenkins-form-item">
            <label className="jenkins-form-label" htmlFor="rsp-role-template">
              Permission template
            </label>
            <div className="jenkins-select">
              <select
                id="rsp-role-template"
                className="jenkins-select__input"
                value={templateName}
                onChange={(e) => setTemplateName(e.target.value)}
              >
                <option value="">None</option>
                {templates.map((t) => (
                  <option key={t.name} value={t.name}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
            {boundTemplate && (
              <div className="jenkins-form-description">
                Permissions are managed by the template and cannot be edited
                here.
              </div>
            )}
          </div>
        )}
        <div className="jenkins-form-item">
          <div className="jenkins-form-label">Permissions</div>
          <PermissionList
            groups={permissionGroups}
            selectedIds={shownPermissionIds}
            disabled={!!boundTemplate}
            onToggle={togglePermission}
            filterAutoFocus={!allowNameEdit}
          />
        </div>
      </form>
    </Dialog>
  );
}
