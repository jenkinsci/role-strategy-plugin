import type { SidEntry, SidInfo, SidType } from "../types/assignment.ts";
import type { RoleTypeKey } from "../types/role.ts";
import { getJson, postForm, postFormJson } from "./client.ts";

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
  /**
   * Assign a sid to a role, dispatching to the endpoint matching the sid type.
   * EITHER goes through the deprecated ambiguous endpoint on purpose: it is the
   * only way to edit a legacy ambiguous entry in place without migrating it.
   */
  assignSidRole(
    type: RoleTypeKey,
    roleName: string,
    sid: string,
    sidType: SidType,
  ): Promise<void>;
  unassignSidRole(
    type: RoleTypeKey,
    roleName: string,
    sid: string,
    sidType: SidType,
  ): Promise<void>;
  /** Remove a sid from all roles of the type. */
  deleteSidEntry(
    type: RoleTypeKey,
    sid: string,
    sidType: SidType,
  ): Promise<void>;
  getRoleAssignments(type: RoleTypeKey): Promise<SidEntry[]>;
  /**
   * Resolve sids against the security realm (existence, display name). The
   * signal lets callers abort a lookup that is no longer needed, e.g. when
   * the user pages on before it finishes.
   */
  getSidsInfo(
    items: { sid: string; type: SidType }[],
    signal?: AbortSignal,
  ): Promise<SidInfo[]>;
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
    assignSidRole: (type, roleName, sid, sidType) => {
      switch (sidType) {
        case "USER":
          return postForm(url("assignUserRole"), { type, roleName, user: sid });
        case "GROUP":
          return postForm(url("assignGroupRole"), {
            type,
            roleName,
            group: sid,
          });
        default:
          return postForm(url("assignRole"), { type, roleName, sid });
      }
    },
    unassignSidRole: (type, roleName, sid, sidType) => {
      switch (sidType) {
        case "USER":
          return postForm(url("unassignUserRole"), {
            type,
            roleName,
            user: sid,
          });
        case "GROUP":
          return postForm(url("unassignGroupRole"), {
            type,
            roleName,
            group: sid,
          });
        default:
          return postForm(url("unassignRole"), { type, roleName, sid });
      }
    },
    deleteSidEntry: (type, sid, sidType) => {
      switch (sidType) {
        case "USER":
          return postForm(url("deleteUser"), { type, user: sid });
        case "GROUP":
          return postForm(url("deleteGroup"), { type, group: sid });
        default:
          return postForm(url("deleteSid"), { type, sid });
      }
    },
    getRoleAssignments: (type) => getJson(url("getRoleAssignments"), { type }),
    getSidsInfo: (items, signal) =>
      // A form field rather than repeated query parameters: sids may contain
      // arbitrary characters (e.g. LDAP DNs with commas).
      postFormJson(url("getSidsInfo"), { sids: JSON.stringify(items) }, signal),
  };
}
