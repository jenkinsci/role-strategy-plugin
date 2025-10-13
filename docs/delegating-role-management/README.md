# Delegating Role Management

This example demonstrates how to use the optional **Item Roles Admin** and **Agent Roles Admin** permissions to delegate role management responsibilities to non-admin users.

## Overview

By default, only users with Jenkins `ADMINISTER` permission can manage roles and role assignments. However, in large organizations, you may want to delegate this responsibility:

- **Item Roles Admin** (`Role Based Strategy/ItemRoles`): Allows users to manage item (job/folder) roles and their assignments
- **Agent Roles Admin** (`Role Based Strategy/AgentRoles`): Allows users to manage agent (node) roles and their assignments

These permissions enable you to create "role administrators" who can manage specific types of roles without having full Jenkins administrator access.

## How It Works

### The SYSTEM_READ permission

The `Jenkins.SYSTEM_READ` permission can be enabled either via a system property or by using the [Extended Read Permission Plugin](https://plugins.jenkins.io/extended-read-permission/).

### With SYSTEM_READ Permission

Users who have `ITEM_ROLES_ADMIN` or `AGENT_ROLES_ADMIN` **and** `SYSTEM_READ` (or `ADMINISTER`) can access role management through the standard "Manage Jenkins" menu. They will see:

- **Manage Jenkins -> Manage and Assign Roles**: Standard access path
- They can view all role sections in read-only mode, but can only edit the sections they have permission for

### Without SYSTEM_READ Permission

Users who have `ITEM_ROLES_ADMIN` or `AGENT_ROLES_ADMIN` **without** `SYSTEM_READ` cannot access "Manage Jenkins". For these users, a special root-level link is provided:

- **Root Dashboard -> Manage and Assign Roles**: Direct link at the Jenkins root level
- This link is only visible to users with role admin permissions but without `SYSTEM_READ`
- It provides the same functionality as the management link, but accessible without needing to access "Manage Jenkins"

This design ensures that:

1. Users with `SYSTEM_READ` don't see duplicate links (they use the management link)
2. Users without `SYSTEM_READ` can still access role management directly
3. Regular users without any admin permissions see no role management links

## Test Scenarios

This directory contains a Configuration-as-Code YAML file that sets up multiple test users to demonstrate all scenarios:

| User | Permissions | Access Method | Can Manage |
|------|------------|---------------|------------|
| `admin` | Overall/Administer | Manage Jenkins | All roles (global, item, agent) |
| `item-admin` | ItemRoles only | **Root-level link** | Item roles only |
| `item-admin-sysread` | ItemRoles + Overall/SystemRead | Manage Jenkins | Item roles only |
| `agent-admin` | AgentRoles only | **Root-level link** | Agent roles only |
| `agent-admin-sysread` | AgentRoles + Overall/SystemRead | Manage Jenkins | Agent roles only |
| `both-admin` | ItemRoles + AgentRoles | **Root-level link** | Item and agent roles |
| `both-admin-sysread` | ItemRoles + AgentRoles + Overall/SystemRead | Manage Jenkins | Item and agent roles |

### Key Differences to Test

1. **Link Visibility**:
   - Users with `SYSTEM_READ`: See link under "Manage Jenkins"
   - Users without `SYSTEM_READ`: See link at root level

2. **Page Access**:
   - All role admin users can access `/role-strategy/` pages
   - Different sections are editable based on their specific permissions

3. **Read-Only Sections**:
   - `item-admin` can edit Item roles but sees Global and Agent roles in read-only mode
   - `agent-admin` can edit Agent roles but sees Global and Item roles in read-only mode
   - `both-admin` can edit Item and Agent roles but sees Global roles in read-only mode

## Testing Instructions

### Prerequisites

Enable the optional permissions:

```bash
export _JAVA_OPTIONS="-Djenkins.security.SystemReadPermission=true -Dcom.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy.useItemAndAgentRoles=true -Dcasc.jenkins.config=$(pwd)/docs/delegating-role-management/jenkins-casc.yaml"
```

### Run Jenkins

```bash
mvn hpi:run
```

### Test Each User

1. **admin / admin**
   - Go to "Manage Jenkins" � "Manage and Assign Roles"
   - Can edit all three sections (Global, Item, Agent)
   - Has full control

2. **item-admin / item-admin**
   - **Note the root-level link** "Manage and Assign Roles" (not under "Manage Jenkins")
   - Click it to go to role management
   - Can edit Item roles (editable checkboxes, add/delete buttons visible)
   - Can **view** Global and Agent roles in read-only mode (checkboxes disabled, no add/delete buttons)

3. **item-admin-sysread / item-admin-sysread**
   - Go to "Manage Jenkins" � "Manage and Assign Roles"
   - Same editing capabilities as `item-admin`, but accessed via Manage Jenkins
   - No root-level link visible

4. **agent-admin / agent-admin**
   - **Note the root-level link** "Manage and Assign Roles"
   - Can edit Agent roles
   - Can **view** Global and Item roles in read-only mode

5. **agent-admin-sysread / agent-admin-sysread**
   - Go to "Manage Jenkins" � "Manage and Assign Roles"
   - Same editing capabilities as `agent-admin`, but accessed via Manage Jenkins

6. **both-admin / both-admin**
   - **Note the root-level link** "Manage and Assign Roles"
   - Can edit both Item and Agent roles
   - Can **view** Global roles in read-only mode

7. **both-admin-sysread / both-admin-sysread**
   - Go to "Manage Jenkins" � "Manage and Assign Roles"
   - Same editing capabilities as `both-admin`, but accessed via Manage Jenkins

## Expected Behavior

### Manage Roles Page
- **Editable sections**: Full table with add/delete buttons, editable checkboxes and patterns
- **Read-only sections**: Table visible but no add/delete buttons, checkboxes and patterns disabled

### Assign Roles Page
- **Editable sections**: Can add/remove users/groups, check/uncheck role assignments
- **Read-only sections**: Can view assignments but cannot modify (no add/delete buttons, checkboxes disabled)

### Permission Templates Page
- Only visible to users with `ITEM_ROLES_ADMIN` permission
- Users without this permission will get a 403 error when trying to access it

## Use Cases

### Large Enterprise
- Jenkins administrators focus on global security and infrastructure
- Team leads manage item roles for their projects
- Infrastructure team manages agent roles for build nodes

### Multi-Tenant Environment
- Each tenant has an "admin" user with `ITEM_ROLES_ADMIN`
- They can manage roles for their jobs without affecting other tenants
- Central IT retains global role management

### Compliance Requirements
- Separation of duties: role management delegated by domain
- Audit trail: different users responsible for different role types
- Principle of least privilege: users only have permissions they need

## Technical Details

The implementation uses three key components:

1. **Permission Definitions**: `ITEM_ROLES_ADMIN` and `AGENT_ROLES_ADMIN` permissions
2. **RoleStrategyConfig**: ManagementLink for users with `SYSTEM_READ`
3. **RoleStrategyRootAction**: Root-level link for users without `SYSTEM_READ`

Both access paths delegate to the same underlying implementation, ensuring consistent behavior regardless of how users access the role management pages.
