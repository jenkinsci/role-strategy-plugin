Developer notes
=====

This page contains links to the information for plugin developers.

### Building and testing the project

See the [Developer Documentation](https://www.jenkins.io/doc/developer/).

### Testing the UI
The React pages (`Assign Roles`, `Manage Roles`, `Permission Templates`) are covered by
Vitest component tests (`npm run test`) and Playwright end-to-end tests
(`AssignRolesUITest`, `ManageRolesUITest`, `PermissionTemplatesUITest`).

Creating some hundred item roles and assignments
Use the file [roles.groovy](roles.groovy) to generate 400 item roles and assign each role a user and a group

### Code details

* Top-level project overview: https://youtu.be/xLwXiDoFM2o
* Macros and Project Naming Strategy: https://www.youtube.com/watch?v=loXiY36QQS8
