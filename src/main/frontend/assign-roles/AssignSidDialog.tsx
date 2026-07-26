import { type FormEvent, useId, useRef, useState } from "react";

import { ApiError } from "../common/api/client.ts";
import { checkSidName } from "../common/api/validation.ts";
import { Dialog } from "../common/components/Dialog.tsx";
import { SearchInput } from "../common/components/SearchInput.tsx";
import type { SidEntry, SidType } from "../common/types/assignment.ts";
import type { Role } from "../common/types/role.ts";

export interface AssignSidDialogResult {
  name: string;
  type: SidType;
  /** Selected role names, in catalogue order. */
  roles: string[];
}

interface AssignSidDialogProps {
  title: string;
  /** Initial sid type; when adding, the dialog offers a User/Group choice. */
  kind: SidType;
  /** False when editing an existing entry: the sid and its type are fixed. */
  allowNameEdit: boolean;
  /** For the duplicate check when adding. */
  existingEntries: SidEntry[];
  roles: Role[];
  checkSidNameUrl: string;
  initialName: string;
  initialRoles: string[];
  submitLabel: string;
  onCancel: () => void;
  onSubmit: (input: AssignSidDialogResult) => Promise<void>;
}

const nameLabel = (kind: SidType) => {
  switch (kind) {
    case "USER":
      return "User ID";
    case "GROUP":
      return "Group name";
    default:
      return "Name";
  }
};

/**
 * Add/edit dialog for a role assignment entry: sid name plus the roles it is
 * assigned to, mirroring the Manage Roles dialog layout.
 */
export function AssignSidDialog({
  title,
  kind: initialKind,
  allowNameEdit,
  existingEntries,
  roles,
  checkSidNameUrl,
  initialName,
  initialRoles,
  submitLabel,
  onCancel,
  onSubmit,
}: AssignSidDialogProps) {
  const idPrefix = useId();
  const [kind, setKind] = useState(initialKind);
  const [name, setName] = useState(initialName);
  // Server-rendered FormValidation snippet (icon + resolved display name).
  const [nameFeedbackHtml, setNameFeedbackHtml] = useState<string | null>(null);
  const [selected, setSelected] = useState<ReadonlySet<string>>(
    new Set(initialRoles),
  );
  const [roleFilter, setRoleFilter] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Invalidates in-flight name lookups once the field is edited again.
  const nameCheckSeq = useRef(0);

  const trimmed = name.trim();
  const taken =
    allowNameEdit &&
    existingEntries.some((e) => e.name === trimmed && e.type === kind);
  const ambiguousExists =
    allowNameEdit &&
    existingEntries.some((e) => e.name === trimmed && e.type === "EITHER");
  // An assignment entry only exists through its roles, so an empty selection
  // has nothing to save; removal is a separate card action.
  const canSubmit =
    trimmed !== "" && !taken && selected.size > 0 && !submitting;

  const kindWord = kind === "GROUP" ? "Group" : "User";

  const handleNameChange = (value: string) => {
    setName(value);
    setNameFeedbackHtml(null);
    nameCheckSeq.current++;
  };

  // Realm feedback runs outside typing (on blur and on type switches) and
  // never blocks submitting: sids that the realm cannot resolve (e.g. groups
  // from an external directory) are still legal assignments.
  const runNameCheck = async (kindToUse: SidType) => {
    const value = name.trim();
    const isTaken =
      allowNameEdit &&
      existingEntries.some((e) => e.name === value && e.type === kindToUse);
    if (value === "" || isTaken) return;
    const seq = ++nameCheckSeq.current;
    try {
      const html = await checkSidName(checkSidNameUrl, value, kindToUse);
      if (seq !== nameCheckSeq.current) return;
      setNameFeedbackHtml(html || null);
    } catch {
      // Lookup endpoint unreachable; the field simply shows no feedback.
    }
  };

  const handleNameBlur = () => runNameCheck(kind);

  // The type changes what the sid resolves to: drop stale feedback and, when
  // a name is already entered, look it up again as the new type.
  const handleKindChange = (next: SidType) => {
    setKind(next);
    setNameFeedbackHtml(null);
    runNameCheck(next);
  };

  const filterQ = roleFilter.trim().toLowerCase();
  const visibleRoles = filterQ
    ? roles.filter((r) => r.name.toLowerCase().includes(filterQ))
    : roles;

  const toggleRole = (roleName: string, next: boolean) => {
    setSelected((prev) => {
      const ns = new Set(prev);
      if (next) ns.add(roleName);
      else ns.delete(roleName);
      return ns;
    });
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        name: trimmed,
        type: kind,
        roles: roles.filter((r) => selected.has(r.name)).map((r) => r.name),
      });
    } catch (err) {
      const detail = err instanceof ApiError ? err.body.trim() : "";
      setError(
        detail
          ? `Failed to save the assignment: ${detail}`
          : "Failed to save the assignment.",
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
          form="rsp-assign-sid-form"
          className="jenkins-button jenkins-button--primary"
          disabled={!canSubmit}
        >
          {submitting ? "Saving…" : submitLabel}
        </button>
      }
    >
      <form
        id="rsp-assign-sid-form"
        onSubmit={handleSubmit}
        className="rsp-dialog__form"
      >
        {error && (
          <div className="jenkins-alert jenkins-alert-danger">{error}</div>
        )}
        {allowNameEdit && (
          <div className="jenkins-form-item">
            <div className="jenkins-form-label">Type</div>
            <div className="rsp-assign__kind">
              {(["USER", "GROUP"] as const).map((option) => (
                <span key={option} className="jenkins-radio">
                  <input
                    type="radio"
                    className="jenkins-radio__input"
                    id={`${idPrefix}-kind-${option}`}
                    name="rsp-sid-kind"
                    checked={kind === option}
                    onChange={() => handleKindChange(option)}
                  />
                  <label
                    className="jenkins-radio__label"
                    htmlFor={`${idPrefix}-kind-${option}`}
                  >
                    {option === "USER" ? "User" : "Group"}
                  </label>
                </span>
              ))}
            </div>
          </div>
        )}
        <div className="jenkins-form-item">
          <label className="jenkins-form-label" htmlFor="rsp-sid-name">
            {nameLabel(kind)}
          </label>
          <input
            id="rsp-sid-name"
            type="text"
            className="jenkins-input"
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
            onBlur={allowNameEdit ? handleNameBlur : undefined}
            disabled={!allowNameEdit}
            data-autofocus={allowNameEdit ? "true" : undefined}
            required
          />
          {taken && (
            <div className="jenkins-form-description jenkins-!-color-red">
              An entry for this {kindWord.toLowerCase()} already exists.
            </div>
          )}
          {!taken && ambiguousExists && (
            <div className="jenkins-form-description">
              An ambiguous entry with this name already exists. Consider
              migrating it instead of adding a second entry.
            </div>
          )}
          {!taken && nameFeedbackHtml && (
            <div
              className="jenkins-form-description rsp-assign__name-feedback"
              // Server-rendered FormValidation snippet from checkSidName; the
              // backend escapes the sid and display name.
              // biome-ignore lint/security/noDangerouslySetInnerHtml: trusted same-origin FormValidation payload
              dangerouslySetInnerHTML={{ __html: nameFeedbackHtml }}
            />
          )}
        </div>
        <div className="jenkins-form-item">
          <div className="jenkins-form-label">Roles</div>
          {roles.length === 0 ? (
            <div className="jenkins-form-description">
              No roles defined. Create roles on the Manage Roles page first.
            </div>
          ) : (
            <>
              <SearchInput
                className="jenkins-!-margin-bottom-2"
                placeholder="Filter roles"
                value={roleFilter}
                onChange={(e) => setRoleFilter(e.target.value)}
              />
              <div className="rsp-assign-dialog__roles">
                {visibleRoles.length === 0 && (
                  <div className="rsp-assign-dialog__no-results">
                    No matching roles
                  </div>
                )}
                <div className="rsp-assign-dialog__group">
                  {visibleRoles.map((role) => {
                    const inputId = `${idPrefix}-${role.name}`;
                    return (
                      <div
                        key={role.name}
                        className="rsp-assign-dialog__role-item jenkins-checkbox"
                        data-role-name={role.name}
                      >
                        <input
                          type="checkbox"
                          id={inputId}
                          checked={selected.has(role.name)}
                          onChange={(e) =>
                            toggleRole(role.name, e.target.checked)
                          }
                        />
                        <label htmlFor={inputId}>
                          {role.name}
                          {role.pattern !== undefined && (
                            <span className="rsp-card__pattern">
                              {role.pattern}
                            </span>
                          )}
                        </label>
                      </div>
                    );
                  })}
                </div>
              </div>
              {selected.size === 0 && (
                <div className="jenkins-form-description">
                  Select at least one role.
                </div>
              )}
            </>
          )}
        </div>
      </form>
    </Dialog>
  );
}
