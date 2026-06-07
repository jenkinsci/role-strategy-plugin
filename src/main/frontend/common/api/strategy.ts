import type { RoleType } from "../types/role.ts";
import { postForm } from "./client.ts";

/**
 * Client for the role-strategy REST endpoints exposed on
 * RoleBasedAuthorizationStrategy (`<rootURL>/role-strategy/strategy`).
 * Only the endpoints used by the React pages are wrapped here; future
 * pages add methods as they need them.
 */
export interface StrategyClient {
  /**
   * Create or update a role. Pass {@code overwrite: false} when creating so the
   * backend rejects a name that already exists; pass {@code true} when editing.
   * {@code pattern} and {@code template} are ignored by the backend for global
   * roles, so callers may omit them there.
   */
  addRole(input: AddRoleInput): Promise<void>;
  removeRoles(type: RoleType, roleNames: string[]): Promise<void>;
  /**
   * Create or update a template. Pass {@code overwrite: false} when creating so
   * the backend rejects a name that already exists; pass {@code true} when
   * editing an existing template.
   */
  addTemplate(
    name: string,
    permissionIds: string[],
    overwrite: boolean,
  ): Promise<void>;
  removeTemplates(templateNames: string[]): Promise<void>;
}

export interface AddRoleInput {
  type: RoleType;
  roleName: string;
  permissionIds: string[];
  overwrite: boolean;
  pattern?: string;
  template?: string;
}

export function createStrategyClient(baseUrl: string): StrategyClient {
  const url = (endpoint: string) => `${baseUrl}/${endpoint}`;

  return {
    addRole: ({
      type,
      roleName,
      permissionIds,
      overwrite,
      pattern,
      template,
    }) =>
      postForm(url("addRole"), {
        type,
        roleName,
        permissionIds: permissionIds.join(","),
        overwrite: String(overwrite),
        pattern,
        template,
      }),
    removeRoles: (type, roleNames) =>
      postForm(url("removeRoles"), { type, roleNames: roleNames.join(",") }),
    addTemplate: (name, permissionIds, overwrite) =>
      postForm(url("addTemplate"), {
        name,
        permissionIds: permissionIds.join(","),
        overwrite: String(overwrite),
      }),
    removeTemplates: (templateNames) =>
      postForm(url("removeTemplates"), {
        names: templateNames.join(","),
      }),
  };
}
