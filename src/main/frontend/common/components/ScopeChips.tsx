import { Fragment } from "react";

import type { Role } from "../types/role.ts";
import { folderPattern, isFolderScoped } from "../utils/roleScope.ts";
import { Tooltip } from "./Tooltip.tsx";

/** What one chip covers, for listing the items or agents it matches. */
export interface ScopeMatch {
  kind: "items" | "agents";
  pattern: string;
  /** Shown instead of the pattern, e.g. a folder name. */
  label?: string;
}

interface ScopeChipsProps {
  role: Role;
  /** What the role's pattern matches; folders always hold items. */
  matchKind?: "items" | "agents";
  /** Makes each chip a button that lists what it matches. */
  onShowMatching?: (match: ScopeMatch) => void;
}

/**
 * What a role applies to, shown next to its name: one chip per folder for a
 * folder-scoped role, the same look as in the folder picker, or a single chip
 * with the pattern. Global roles render nothing.
 */
export function ScopeChips({
  role,
  matchKind = "items",
  onShowMatching,
}: ScopeChipsProps) {
  const folderScoped = isFolderScoped(role);
  const scopes: ScopeMatch[] = folderScoped
    ? (role.folders ?? []).map((folder) => ({
        kind: "items",
        pattern: folderPattern([folder]),
        label: folder,
      }))
    : role.pattern !== undefined
      ? [{ kind: matchKind, pattern: role.pattern }]
      : [];
  if (scopes.length === 0) return null;
  const chipClass = folderScoped
    ? "rsp-card__pattern rsp-card__folder"
    : "rsp-card__pattern";
  // The spaces keep the role name and each scope apart for screen readers;
  // whitespace inside a flex container takes no room on screen.
  return (
    <>
      {" "}
      <span className="rsp-scope-chips">
        {scopes.map((scope) => {
          const text = scope.label ?? scope.pattern;
          return (
            <Fragment key={text}>
              {" "}
              {onShowMatching ? (
                <Tooltip
                  content={`Show matching ${scope.kind}`}
                  placement="top"
                >
                  <button
                    type="button"
                    className={`${chipClass} rsp-card__pattern--button`}
                    onClick={(e) => {
                      // The chip sits inside a card that toggles on click.
                      e.stopPropagation();
                      onShowMatching(scope);
                    }}
                  >
                    {text}
                  </button>
                </Tooltip>
              ) : (
                <span className={chipClass}>{text}</span>
              )}
            </Fragment>
          );
        })}
      </span>
    </>
  );
}
