Role Strategy Macros
==============

Macros allow extending the permission model by custom logic in plugins 
(see [RoleMacroExtension](https://javadoc.jenkins.io/plugin/role-strategy/com/synopsys/arc/jenkins/plugins/rolestrategy/RoleMacroExtension.html)). 
If a user sid meets the criteria defined in Roles and Assignments, then the role membership check will be delegated to the extension, 
which makes decisions according to instance and parameters.

![Managing roles](/docs/images/macroDefinition.PNG)

## Available macros

* `@BuildableJob` - checks if the job is buildable
* `@Folder` - checks if the item is a folder
* `@ContainedInView` - Access items that are added to a ListView. Specify the views as parameter to the macro, e.g. `@ContainedInView(view1, view2)`.
  Prepend the folder name if the view is in a folder, e.g. `@ContainedInView(folder/view1)`. To access views inside a folder, access to the folder 
  itself is required.
  When enabling the *Recurse in subfolders* option, make sure to also check the folders themselves for which you add items.

  NestedView plugin is not supported currently as this allows to create ambiguous names for views.

  View names are case sensitive.

* Macros for integration with [Ownership Plugin](https://plugins.jenkins.io/ownership). 
  See [Ownership-based Security](https://github.com/jenkinsci/ownership-plugin/blob/master/doc/OwnershipBasedSecurity.md)

More macros can be implemented in other Jenkins plugins.
See [this page](https://jenkins.io/doc/developer/extensions/role-strategy/) for the full list.

## Defining Macros

Macros can be used in the role definition field. 
Format: `@macroName[:id][(parameter1, parameter2, ...)]`

* `macroName` - name of the macro (see available macros in the table below)
* `id` - identifier of the macro. Technical parameter, which allows to use same macros for multiple patterns
* `parameter` - additional parameters. At the current state, they don't support variables or TokenMacro

Examples:

* `@BuildableJob` - Primitive macro invocation. Such invocation can be used only once in each roles category.
* `@BuildableJob:1` - Macro with id
* `@ContainedInView(view1, folder/view1)` - Includes all items that are contained in "view1" and "folder/view1"
* `@ParameterizedMacro(param1)` - Invokes macro with one parameter
* `@ParameterizedMacro:2(param1,param2)` - Invokes macro with two parameters. Id prevents naming conflicts
