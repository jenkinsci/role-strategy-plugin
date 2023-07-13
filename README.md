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
* Creating **item roles**, allowing to set item specific permissions (e.g Job, Run or Credentials) on Jobs, Pipelines and Folders.
* Creating **agent roles**, allowing to set agent specific permissions.
* Assigning these roles to users and user groups
* Extending roles and permissions matching via [Macro extensions](./docs/MACROS.md)

## Usage

### Installing and enabling the plugin

The Role Strategy plugin can be installed from any Jenkins installation connected to the Internet using the **Plugin Manager** screen.
Activate the Role-Based Strategy by using the standard _Manage Jenkins > Configure Global Security_ screen:

![Configure Security](/docs/images/configureSecurity.png)

After the installation, the plugin can be configured using the _Manage and Assign Roles_ screen accessible from _Manage Jenkins_ .

### Configuring roles

You can define roles by using the _Manages Roles_ screen. It is possible to define global, item and agent specific roles.

* Global roles apply to any item in Jenkins and override *anything* you specify in the Item Roles. That is, when you give a role the
  right `Job/Read` in the Global Roles, then this role is allowed to read all Jobs, no matter what you specify in the Item Roles.
  Giving `Job/Create` in a global role will allow to create jobs of any name.
* For item and agent roles you can set a regular expression pattern for matching items. The regular expression aimes at matching the full item name.
  * For example, if you set the field to `Roger-.*`, then the role will match all jobs which name starts with `Roger-`. 
  * Patterns are case-sensitive. To perform a case-insensitive match, use `(?i)` notation: upper, `Roger-.*` vs. lower, `roger-.*` 
    vs. case-insensitive, `(?i)roger-.*`. 
  * Folders can be matched using expressions like `^foo/bar.*`. To access jobs inside a folder, the folder itself must also be accessible to the
    user. This can be achieved with a single pattern like `(?i)folder($|/.*)` when the permissions on the folder can be the same as for the jobs.
    If different permissions need to be configured 2 different roles need to be created, e.g. `(?i)folder` and `(?i)folder/.*`. Note that job names
    inside folders are case-sensitive, though this is probably a bug in the folders plugin [JENKINS-67695](https://issues.jenkins.io/browse/JENKINS-67695).
    Case sensitivity can be enabled with `(?-i)`, e.g. `(?i)folder/(?-i).*`
  * Create permissions on item level can only reliably work when the `Naming Strategy` is set to `Role-Based strategy` in the global configuration
    for `Restrict project naming`. You should see a warning in the administrative monitors if it is not enabled.
    Only jobs matching the pattern can be created. When granting `Job/Create` you should also grant `Job/Configure` and `Job/Read` otherwise you will
    be able to create new jobs but you will not be able to configure them. Global Permissions are not required.


![Managing roles](/docs/images/manageRoles.png)

#### Permission Templates
Permission Templates simplify the administration of roles when you need to maintain many roles with identical permissions but different patterns.
Templates are only available for _Item Roles_. The permissions of roles based on a template can't be modified directly. Modifying the template will
immediately modify the linked roles after saving the changes.

Deleting a template that is still in use requires confirmation. In case you still delete it, the roles stay with the given permissions but the
correlation to the template is removed.

### Assigning roles

You can assign roles to users and user groups using the _Assign Roles_ screen

* User groups represent authorities provided by the Security Realm (e.g. Active Directory or LDAP plugin can provide groups)
* There are also two built-in groups: `authenticated` (users who logged in) and `anonymous` (any user, including ones who have not logged in)
* Hovering over the header row will show a tooltip with the permissions associated to the role and the pattern.
* Hovering over a checkbox will show a tooltip with role, user/group and pattern.

![Assign roles](/docs/images/assignRoles.png)

### Rest API

The Rest API allows to query the current roles and assignments and to do changes to them.
Please see the [javadoc](https://javadoc.jenkins.io/plugin/role-strategy/com/michelin/cio/hudson/plugins/rolestrategy/RoleBasedAuthorizationStrategy.html) for details and examples.

### Config & Assign role by using Jenkins Script Console or Groovy Hook Script
Configuration management can be used via [Jenkins Script Console](https://www.jenkins.io/doc/book/managing/script-console/) or 
[Groovy Hook Scripts](https://www.jenkins.io/doc/book/managing/groovy-hook-scripts/), following example is creating an admin role & user based on plugin 3.1. 

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
permissions.add(Jenkins.ADMINISTER)
Role adminRole = new Role("admin",permissions)

/* assign admin role to admin user */
globalRoleMap = rbas.getRoleMaps()[RoleType.Global]
globalRoleMap.addRole(adminRole)
globalRoleMap.assignUserRole(adminRole, 'admin')
globalRoleMap.assignGroupRole(adminRole, 'administrators')

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
