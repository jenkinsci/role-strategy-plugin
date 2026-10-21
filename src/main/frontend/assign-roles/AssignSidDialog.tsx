import { type FormEvent, useEffect, useId, useRef, useState } from "react";

import { ApiError } from "../common/api/client.ts";
import { checkSidName } from "../common/api/validation.ts";
import { Dialog } from "../common/components/Dialog.tsx";
import { HelpIcon } from "../common/components/HelpIcon.tsx";
import { SearchInput } from "../common/components/SearchInput.tsx";
import type { SidEntry, SidType } from "../common/types/assignment.ts";
import type {
  PrincipalSearchElement,
  SearchedPrincipal,
} from "../common/types/principalSearch.ts";
import type { Role } from "../common/types/role.ts";
import {
  buildPermissionSummary,
  type PermissionRef,
} from "../common/utils/permissionSummary.ts";

export interface AssignSidDialogResult {
  /** The entries to assign; several when picked through principal search. */
  sids: { name: string; type: SidType }[];
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
  /** Permission catalogue for the active role type, for the role permissions tooltip. */
  permissionsById: ReadonlyMap<string, PermissionRef>;
  checkSidNameUrl: string;
  /**
   * Principal search endpoint for autocompleting users and groups while
   * adding; empty when the security realm cannot be searched. With it,
   * several users (or groups, per the User/Group choice) can be picked at once.
   */
  searchPrincipalsUrl?: string;
  initialName: string;
  initialRoles: string[];
  submitLabel: string;
  /**
   * Allows saving with no roles selected. Used for the built-in
   * anonymous/authenticated entries, which always exist and have no delete
   * action, so unassigning their last role must stay reachable through Save.
   */
  allowEmptyRoles?: boolean;
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
  permissionsById,
  checkSidNameUrl,
  searchPrincipalsUrl = "",
  initialName,
  initialRoles,
  submitLabel,
  allowEmptyRoles = false,
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
  const usePrincipalSearch = allowNameEdit && searchPrincipalsUrl !== "";
  // Users and groups picked through principal search.
  const [principals, setPrincipals] = useState<SearchedPrincipal[]>([]);
  const principalSearchRef = useRef<PrincipalSearchElement | null>(null);

  const trimmed = name.trim();
  const taken =
    allowNameEdit &&
    existingEntries.some((e) => e.name === trimmed && e.type === kind);
  const ambiguousExists =
    allowNameEdit &&
    existingEntries.some((e) => e.name === trimmed && e.type === "EITHER");
  // An assignment entry only exists through its roles, so an empty selection
  // has nothing to save; removal is a separate card action. Built-in entries
  // are the exception: they always exist and have no delete action, so an
  // empty selection is how their last role gets revoked.
  const hasSids = usePrincipalSearch ? principals.length > 0 : trimmed !== "";
  const canSubmit =
    hasSids &&
    !(taken && !usePrincipalSearch) &&
    (selected.size > 0 || allowEmptyRoles) &&
    !submitting;

  const kindWord = kind === "GROUP" ? "Group" : "User";

  const handleNameChange = (value: string) => {
    setName(value);
    setNameFeedbackHtml(null);
    nameCheckSeq.current++;
  };

  // Realm feedback runs outside typing (on blur and on type switches) and
  // never blocks submitting: sids that the realm cannot resolve (e.g. groups
  // from an external directory) are still legal assignments.
  const runNameCheck = async (kindToUse: SidType, nameToCheck = name) => {
    // Invalidate any in-flight lookup for a previous type/value unconditionally,
    // even when this call itself has nothing to check: otherwise a stale
    // response could still pass the seq check below and render under the
    // new type.
    const seq = ++nameCheckSeq.current;
    const value = nameToCheck.trim();
    const isTaken =
      allowNameEdit &&
      existingEntries.some((e) => e.name === value && e.type === kindToUse);
    if (value === "" || isTaken) return;
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
    if (usePrincipalSearch) {
      // Picks are of the previous type; start over for the new one.
      // (the element may not be upgraded yet, before its adjunct loads)
      const element = principalSearchRef.current;
      if (typeof element?.clear === "function") element.clear();
      setPrincipals([]);
      return;
    }
    runNameCheck(next);
  };

  const existingEntriesRef = useRef(existingEntries);
  existingEntriesRef.current = existingEntries;

  useEffect(() => {
    const element = principalSearchRef.current;
    if (!usePrincipalSearch || !element) return;
    // Entries that already exist are not offered again.
    element.exclude = (p) =>
      existingEntriesRef.current.some(
        (e) => e.name === p.sid && e.type === p.type,
      );
    const onChange = () => setPrincipals(element.selectedPrincipals ?? []);
    element.addEventListener("change", onChange);
    // The auth-commons adjunct defines the element asynchronously, so the
    // dialog's initial autofocus may have reached it before it had an input to
    // focus. Focus it once it is upgraded, unless focus has moved elsewhere.
    let active = true;
    window.customElements
      ?.whenDefined("auth-commons-principal-search")
      .then(() => {
        const focused = document.activeElement;
        const dialogEl = element.closest("dialog");
        if (
          active &&
          element.isConnected &&
          (focused === null ||
            focused === document.body ||
            focused === dialogEl ||
            focused === element)
        ) {
          element.focus();
        }
      });
    return () => {
      active = false;
      element.removeEventListener("change", onChange);
    };
  }, [usePrincipalSearch]);

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
        sids: usePrincipalSearch
          ? principals.map((p) => ({ name: p.sid, type: p.type }))
          : [{ name: trimmed, type: kind }],
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
          {usePrincipalSearch ? (
            <>
              <div className="jenkins-form-label">
                {kind === "GROUP" ? "Groups" : "Users"}
              </div>
              <auth-commons-principal-search
                ref={principalSearchRef}
                id="rsp-sid-name"
                search-url={searchPrincipalsUrl}
                type={kind}
                placeholder={
                  kind === "GROUP"
                    ? "Search by name, or type a group name"
                    : "Search by name, or type a user ID"
                }
                multiple
                data-autofocus="true"
              />
            </>
          ) : (
            <>
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
            </>
          )}
          {taken && !usePrincipalSearch && (
            <div className="jenkins-form-description jenkins-!-color-red">
              An entry for this {kindWord.toLowerCase()} already exists.
            </div>
          )}
          {!taken && !usePrincipalSearch && ambiguousExists && (
            <div className="jenkins-form-description">
              An ambiguous entry with this name already exists. Consider
              migrating it instead of adding a second entry.
            </div>
          )}
          {!taken && !usePrincipalSearch && nameFeedbackHtml && (
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
                        <HelpIcon
                          description={
                            buildPermissionSummary(
                              role.permissionIds,
                              permissionsById,
                            ) ?? "No permissions"
                          }
                        />
                      </div>
                    );
                  })}
                </div>
              </div>
              {selected.size === 0 && !allowEmptyRoles && (
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
