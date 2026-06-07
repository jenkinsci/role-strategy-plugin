export type RoleType = "globalRoles" | "projectRoles" | "slaveRoles";

export type AuthorizationType = "USER" | "GROUP" | "EITHER";

export interface PermissionEntry {
  sid: string;
  type: AuthorizationType;
}

export interface Role {
  name: string;
  pattern: string;
  permissionIds: string[];
  templateName?: string | null;
  sids: PermissionEntry[];
}
