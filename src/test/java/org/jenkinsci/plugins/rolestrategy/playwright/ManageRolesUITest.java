package org.jenkinsci.plugins.rolestrategy.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
class ManageRolesUITest {

  @Test
  void rolesPageShowsExistingRoles(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    UITestHelper.loginAsAdmin(page, j.jenkins.getRootUrl());
    ManageRolesPage rolesPage = new ManageRolesPage(page, j.jenkins.getRootUrl());
    rolesPage.goTo();

    // Verify existing roles
    rolesPage.hasRoleCard("admin")
        .roleCardHasSummary("admin", "Administer")
        .hasRoleCard("reader")
        .roleCardHasSummary("reader", "Read")
        .hasRoleCard("developer")
        .roleCardHasPattern("developer", "dev-.*");

    // Verify empty role cards are not expandable
    rolesPage.roleCardNotExpandable("empty-role");

    // Search for "admin"
    rolesPage.search("admin")
        .roleCardIsVisible("admin")
        .roleCardIsHidden("reader")
        .roleCardIsHidden("developer");

    // Clear search
    rolesPage.search("")
        .roleCardIsVisible("admin")
        .roleCardIsVisible("reader")
        .roleCardIsVisible("developer");
  }

  @Test
  void addGlobalRole(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    UITestHelper.loginAsAdmin(page, j.jenkins.getRootUrl());
    ManageRolesPage rolesPage = new ManageRolesPage(page, j.jenkins.getRootUrl());
    rolesPage.goTo();

    // Add a global role
    rolesPage.clickAddRole()
        .waitForAddRoleDialog()
        .dialogSetRoleName("new-global-role")
        .dialogTogglePermission("Read")
        .dialogSubmit();

    // Verify new role appears after page reload
    rolesPage.goTo()
        .hasRoleCard("new-global-role")
        .roleCardHasSummary("new-global-role", "Read");
  }

  @Test
  void addRoleValidation(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    UITestHelper.loginAsAdmin(page, j.jenkins.getRootUrl());
    ManageRolesPage rolesPage = new ManageRolesPage(page, j.jenkins.getRootUrl());
    rolesPage.goTo();

    // Try adding role with existing name
    rolesPage.clickAddRole()
        .waitForAddRoleDialog()
        .dialogSetRoleName("admin")
        .dialogTogglePermission("Read")
        .dialogSubmit();
    rolesPage.dialogHasAlert("already exists")
        .dismissAlert();

    // Verify implied permissions — check Administer
    rolesPage.closeDialog();
    rolesPage.clickAddRole()
        .waitForAddRoleDialog()
        .dialogTogglePermission("Administer")
        .dialogPermissionIsCheckedAndDisabled("Manage")
        .dialogPermissionHasImpliedLabel("Manage")
        .dialogPermissionIsCheckedAndDisabled("Read")
        .dialogPermissionHasImpliedLabel("Read");
  }
}
