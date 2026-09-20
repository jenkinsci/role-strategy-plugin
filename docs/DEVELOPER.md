Developer notes
=====

This page contains links to the information for plugin developers.

### Building and testing the project

See the [Developer Documentation](https://www.jenkins.io/doc/developer/).

### Testing the UI
The React pages (`Manage Roles`, `Permission Templates`) are covered by Vitest component
tests (`npm run test`) and Playwright end-to-end tests (`ManageRolesUITest`,
`PermissionTemplatesUITest`). The remaining jelly/vanilla-JS pages need manual testing.
After starting Jenkins locally via `mvn hpi:run` go to the `Manage and Assign Roles` page.

Verify that following things work on `Assign Roles`:
1. Adding a new user to global, item and agent role
2. Deleting a role by pressing on the red x deletes the role
3. Hovering over the checkboxes properly highlights the row and column and shows a tooltip
4. Entering html as user is printed as plain text in the field and in the tooltips.
5. Check that pagination works especially in combination with the filters.

Creating some hundred item roles and assignments
Use the file [roles.groovy](roles.groovy) to generate 400 item roles and assign each role a user and a group

### Code details

* Top-level project overview: https://youtu.be/xLwXiDoFM2o
* Macros and Project Naming Strategy: https://www.youtube.com/watch?v=loXiY36QQS8
