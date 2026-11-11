import { Fragment } from "react";

import type { Role } from "../types/role.ts";
import { isFolderScoped } from "../utils/roleScope.ts";

/**
 * What a role applies to, shown next to its name: one chip per folder for a
 * folder-scoped role, the same look as in the folder picker, or a single chip
 * with the pattern. Global roles render nothing.
 */
export function ScopeChips({ role }: { role: Role }) {
  const scopes = isFolderScoped(role)
    ? (role.folders ?? [])
    : role.pattern !== undefined
      ? [role.pattern]
      : [];
  if (scopes.length === 0) return null;
  // The spaces keep the role name and each scope apart for screen readers;
  // whitespace inside a flex container takes no room on screen.
  return (
    <>
      {" "}
      <span className="rsp-scope-chips">
        {scopes.map((scope) => (
          <Fragment key={scope}>
            {" "}
            <span className="rsp-card__pattern">{scope}</span>
          </Fragment>
        ))}
      </span>
    </>
  );
}
