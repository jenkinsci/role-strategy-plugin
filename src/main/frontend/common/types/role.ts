/** Wire names of the three role types, as used by the REST endpoints. */
export type RoleTypeKey = "globalRoles" | "projectRoles" | "slaveRoles";

export interface Role {
  name: string;
  /** Item/agent roles only. */
  pattern?: string;
  /** Item roles only; set when the role takes its permissions from a template. */
  templateName?: string;
  permissionIds: string[];
}
