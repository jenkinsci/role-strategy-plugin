Role Strategy plugin
====================

[![Join the chat at https://gitter.im/jenkinsci/role-strategy-plugin](https://badges.gitter.im/jenkinsci/role-strategy-plugin.svg)](https://gitter.im/jenkinsci/role-strategy-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/role-strategy.svg)](https://plugins.jenkins.io/role-strategy)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/role-strategy-plugin.svg?label=changelog)](https://github.com/jenkinsci/role-strategy-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/role-strategy.svg?color=blue)](https://plugins.jenkins.io/role-strategy)

## About this plugin

The Role Strategy plugin is meant to be used from [Jenkins](https://jenkins.io) to add a new role-based mechanism to manage users' permissions. 
Supported features

* Creating **global roles**, such as admin, job creator, anonymous, etc., allowing to set Overall, Agent, Job, Run, View and SCM permissions on a global basis.
* Creating **project roles**, allowing to set only Job and Run permissions on a project basis.
* Creating **agent roles**, allowing to set node-related permissions.
* Assigning these roles to users and user groups
* Extending role and permissions matching via [Macro extensions](./docs/MACROS.md)

## Usage

### Installing and enabling the plugin

The Role Strategy plugin can be installed from any Jenkins installation connected to the Internet using the **Plugin Manager** screen.
Activate the Role-Based Strategy by using the standard _Manage Jenkins > Manage Global Security_ screen:

![Configure Security](/docs/images/configureSecurity.png)

After the installation, the plugin can be configured using the _Manage and Assign Roles_ screen accessible from _Manage Jenkins_ .

![Role Strategy Configuration](/docs/images/manageAndAssignRoles.png)

### Configuring roles

You can define roles by using the _Manages Roles_ screen. It is possible to define global and project/agent-specific roles.

* Global roles apply to any item in Jenkins and override *anything* you specify in the Project Roles. That is, when you give a role the right to Job-Read in the Global Roles, then this role is allowed to read all Jobs, no matter what you specify in the Project Roles.
* For project and agent roles you can set a regular expression pattern for matching items. The regular expression aimed at matching the full item name.
  * For example, if you set the field to `Roger-.*`, then the role will match all jobs which name starts with `Roger-`. 
  * Patterns are case-sensitive. To perform a case-insensitive match, use `(?i)` notation: upper, `Roger-.*` vs. lower, `roger-.*` vs. case-insensitive, `(?i)roger-.*`. 
  * Folders can be matched using expressions like `^foo/bar.*`
  
![Managing roles](/docs/images/manageRoles.png)

### Assigning roles

You can assign roles to users and user groups using the _Assign Roles_ screen

* User groups represent authorities provided by the Security Realm (e.g. LDAP plugin can provide groups)
* There are also two built-in groups: `authenticated ` (users who logged in) and `anonymous` (any users, including ones who have not logged in)

![Assign roles](/docs/images/assignRoles.png)

### Config & Assign role by using Jenkins Script Console or Groovy Hook Script
Configuration management can be used via [Jenkins Script Console](https://www.jenkins.io/doc/book/managing/script-console/) or [Groovy Hook Scripts](https://www.jenkins.io/doc/book/managing/groovy-hook-scripts/), following example is creating a admin role & user based on plugin 3.1. 

```groovy
import jenkins.model.Jenkins

import hudson.security.PermissionGroup
import hudson.security.Permission

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.michelin.cio.hudson.plugins.rolestrategy.Role
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType

import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper

Jenkins jenkins = Jenkins.get()
def rbas = new RoleBasedAuthorizationStrategy()

/* create admin role */
Set<Permission> permissions = new HashSet<>();
def groups = new ArrayList<>(PermissionGroup.getAll());
groups.remove(PermissionGroup.get(Permission.class));
Role adminRole = new Role("admin",permissions)

/* assign admin role to admin user */
globalRoleMap = rbas.getRoleMaps()[RoleType.Global]
globalRoleMap.addRole(adminRole)
globalRoleMap.assignRole(adminRole, 'admin')

jenkins.setAuthorizationStrategy(rbas)

jenkins.save()
```
## License

[MIT License](./LICENSE.md)

## More information

* [Changelog](https://github.com/jenkinsci/role-strategy-plugin/releases)
* [Macro extensions](./docs/MACROS.md)
* [Greasemonkey Addons for Web UI](./docs/USERSCRIPTS.md)
* [Developer documentation](./docs/DEVELOPER.md)
