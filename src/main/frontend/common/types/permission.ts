export interface Permission {
  id: string;
  name: string;
  description: string;
  impliedByList: string[];
}

export interface PermissionGroup {
  title: string;
  permissions: Permission[];
}
