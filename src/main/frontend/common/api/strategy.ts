import type { RoleTypeKey } from "../types/role.ts";
import { getJson, postForm } from "./client.ts";

export interface MatchingJobs {
  matchingJobs: string[];
  itemCount: number;
}

export interface MatchingAgents {
  matchingAgents: string[];
  agentCount: number;
}

/**
 * Client for the role-strategy REST endpoints exposed on
 * RoleBasedAuthorizationStrategy (`<rootURL>/role-strategy/strategy`).
 * Only the endpoints used by the React pages are wrapped here; future
 * pages add methods as they need them.
 */
export interface StrategyClient {
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
  /**
   * Create or update a role. Pass {@code overwrite: true} when editing an
   * existing role; its assignments are kept. A bound {@code template} takes
   * precedence over {@code permissionIds} (item roles only).
   */
  addRole(
    type: RoleTypeKey,
    roleName: string,
    permissionIds: string[],
    overwrite: boolean,
    pattern?: string,
    template?: string,
  ): Promise<void>;
  removeRoles(type: RoleTypeKey, roleNames: string[]): Promise<void>;
  getMatchingJobs(pattern: string, maxJobs: number): Promise<MatchingJobs>;
  getMatchingAgents(
    pattern: string,
    maxAgents: number,
  ): Promise<MatchingAgents>;
}

export function createStrategyClient(baseUrl: string): StrategyClient {
  const url = (endpoint: string) => `${baseUrl}/${endpoint}`;

  return {
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
    addRole: (type, roleName, permissionIds, overwrite, pattern, template) =>
      postForm(url("addRole"), {
        type,
        roleName,
        permissionIds: permissionIds.join(","),
        overwrite: String(overwrite),
        pattern,
        template,
      }),
    removeRoles: (type, roleNames) =>
      postForm(url("removeRoles"), {
        type,
        roleNames: roleNames.join(","),
      }),
    getMatchingJobs: (pattern, maxJobs) =>
      getJson(url("getMatchingJobs"), { pattern, maxJobs }),
    getMatchingAgents: (pattern, maxAgents) =>
      getJson(url("getMatchingAgents"), { pattern, maxAgents }),
  };
}
