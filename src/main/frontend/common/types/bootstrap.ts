import type { PermissionGroup } from "./permission.ts";
import type { Role } from "./role.ts";
import type { PermissionTemplate } from "./template.ts";

export interface TemplatesBootstrap {
  templates: PermissionTemplate[];
  permissionGroups: PermissionGroup[];
  canEdit: boolean;
}

export interface BootstrapRoles {
  globalRoles: Role[];
  projectRoles: Role[];
  slaveRoles: Role[];
}

export interface BootstrapPermissionGroups {
  globalRoles: PermissionGroup[];
  projectRoles: PermissionGroup[];
  slaveRoles: PermissionGroup[];
}

export interface ManageRolesBootstrap {
  roles: BootstrapRoles;
  permissionTemplates: PermissionTemplate[];
  permissions: {
    canEditGlobal: boolean;
    canEditProject: boolean;
    canEditAgent: boolean;
  };
}
