/** Wire names of the three role types, as used by the REST endpoints. */
export type RoleTypeKey = "globalRoles" | "projectRoles" | "slaveRoles";

export interface Role {
  name: string;
  /**
   * Item/agent roles only. For a folder-scoped role this is the equivalent
   * regex the backend derives from {@link folders}.
   */
  pattern?: string;
  /**
   * Item roles only; set when the role is scoped to folders instead of a
   * pattern. It then applies to each folder and everything nested in it.
   */
  folders?: string[];
  /** Item roles only; set when the role takes its permissions from a template. */
  templateName?: string;
  permissionIds: string[];
}
