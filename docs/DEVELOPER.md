Developer notes
=====

This page contains links to the information for plugin developers.

### Building and testing the project

See the [Developer Documentation](https://www.jenkins.io/doc/developer/).

### Manual Testing of the UI
There are no tests available for the UI part. Mainly the javascript code and the interaction between html elements in the jelly files needs manual testing.
After starting Jenkins locally via `mvn hpi:run` go to the `Manage and Assigne Roles` page. 
Verify that following things work on `Manage Roles`:
1. Adding a global, item and agent role
2. Deleting a role by pressing on the red x deletes the role
3. Clicking on the pencil next to a pattern enables edit mode of the pattern (validate for both items and agents)
4. Pressing return key when in the input field of the pattern terminates edit mode and pattern has the new value
5. Pressing escape when in the input field of the pattern terminates edit mode and pattern has the old value
6. Clicking on the pencil next to the pattern disables edit mode of the pattern when it is enabled and pattern has new value
7. Clicking on the pattern opens a dialog box showing the matching items or agents
8. Hovering over the checkboxes properly highlights the row and column and shows a tooltip
9. Tooltips are properly formatted
10. Entering html as rolename is printed as plain text in the field and in the tooltips.
11. After changing the pattern, tooltips are properly updated
12. After pressing Save/Apply and page reload new data is there

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
