import type { PermissionGroup } from "./permission.ts";
import type { PermissionTemplate } from "./template.ts";

export interface TemplatesBootstrap {
  templates: PermissionTemplate[];
  permissionGroups: PermissionGroup[];
  canEdit: boolean;
}
