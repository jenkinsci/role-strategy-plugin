Role Strategy plugin
====================

[![Join the chat at https://gitter.im/jenkinsci/role-strategy-plugin](https://badges.gitter.im/jenkinsci/role-strategy-plugin.svg)](https://gitter.im/jenkinsci/role-strategy-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/role-strategy.svg)](https://plugins.jenkins.io/role-strategy)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/role-strategy-plugin.svg?label=release)](https://github.com/jenkinsci/role-strategy-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/role-strategy.svg?color=blue)](https://plugins.jenkins.io/role-strategy)

About this plugin
-----------------
The Role Strategy plugin is meant to be used from [Jenkins](https://jenkins.io) to add a new role-based mechanism to manager users' permissions. Please take a look at [Jenkins' wiki](http://wiki.jenkins-ci.org/display/JENKINS/Role+Strategy+Plugin) to get detailed information.

* Creating **global roles**, such as admin, job creator, anonymous, etc., allowing to set Overall, Slave, Job, Run, View and SCM permissions on a global basis.
* Creating **project roles**, allowing to set only Job and Run permissions on a project basis.
* Creating **agent roles**, allowing to set node-related permissions.
* Assigning these roles to users.

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

## Changelog

See the changelog [here](https://github.com/jenkinsci/role-strategy-plugin/releases)

## License

[MIT License](./LICENSE.md)

## More information

* [Developer documentation](./docs/DEVELOPER.md)
