import type { Role } from "../types/role.ts";

/** Whether the role is scoped to folders rather than a pattern. */
export function isFolderScoped(role: Role): boolean {
  return (role.folders?.length ?? 0) > 0;
}

/**
 * What a role applies to, as shown next to its name: the folders of a
 * folder-scoped role, otherwise its pattern. Global roles have neither.
 */
export function roleScope(role: Role): string | undefined {
  if (isFolderScoped(role)) {
    return (role.folders ?? []).join(", ");
  }
  return role.pattern;
}

/** Mirrors java.util.regex.Pattern.quote. */
function quoteRegex(value: string): string {
  return `\\Q${value.split("\\E").join("\\E\\\\E\\Q")}\\E`;
}

/**
 * The regex the backend stores alongside a folder scope (Role.folderPattern),
 * so a role added in this session can show its matching items before reload.
 */
export function folderPattern(folders: string[]): string {
  return `^(?:${folders.map(quoteRegex).join("|")})(?:/.*)?$`;
}
