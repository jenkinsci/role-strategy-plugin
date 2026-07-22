import type { PermissionGroup } from "./permission.ts";
import type { Role } from "./role.ts";
import type { PermissionTemplate } from "./template.ts";

export interface TemplatesBootstrap {
  templates: PermissionTemplate[];
  permissionGroups: PermissionGroup[];
  canEdit: boolean;
}

export interface RoleTypeBootstrap {
  /** Whether the current user may see this role type at all. */
  visible: boolean;
  canEdit: boolean;
  permissionGroups: PermissionGroup[];
  roles: Role[];
}

export interface ManageRolesBootstrap {
  globalRoles: RoleTypeBootstrap;
  projectRoles: RoleTypeBootstrap;
  slaveRoles: RoleTypeBootstrap;
  /** All permission templates, for the item-role dialog's template selector. */
  templates: PermissionTemplate[];
}
