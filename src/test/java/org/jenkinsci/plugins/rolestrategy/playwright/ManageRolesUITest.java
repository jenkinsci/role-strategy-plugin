package org.jenkinsci.plugins.rolestrategy.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.ManageRolesPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * End-to-end UI tests for the React-based Manage Roles page.
 */
@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
@DisplayName("Manage Roles page")
class ManageRolesUITest {

  private static final String OVERALL_READ = "hudson.model.Hudson.Read";
  private static final String ITEM_READ = "hudson.model.Item.Read";
  private static final String ITEM_BUILD = "hudson.model.Item.Build";
  private static final String COMPUTER_CONFIGURE = "hudson.model.Computer.Configure";

  private ManageRolesPage open(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbasWithRoles(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    return new ManageRolesPage(page, baseUrl).goTo();
  }

  @Test
  @DisplayName("lists the seeded roles on their respective tabs")
  void listsSeededRolesPerTab(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .hasRoleCard("admin")
        .hasRoleCard("readers")
        .roleCardHasSummary("readers", "Read")
        .hasNoRoleCard("dev-role")
        .selectTab("Item roles")
        .hasRoleCard("dev-role")
        .roleCardHasPattern("dev-role", "dev-.*")
        .roleCardHasTemplateBadge("templated-role", "dev-template")
        .selectTab("Agent roles")
        .hasRoleCard("agent-role")
        .roleCardHasPattern("agent-role", "agent-.*");
  }

  @Test
  @DisplayName("adds a global role through the dialog")
  void addGlobalRole(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddRole()
        .waitForDialog()
        .dialogSetName("auditors")
        .dialogTogglePermission(OVERALL_READ)
        .submitDialog()
        .hasRoleCard("auditors")
        .roleCardHasSummary("auditors", "Read");
  }

  @Test
  @DisplayName("adds an item role bound to a template, with read-only permissions")
  void addItemRoleWithTemplate(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .selectTab("Item roles")
        .clickAddRole()
        .waitForDialog()
        .dialogSetName("qa-role")
        .dialogSetPattern("qa-.*")
        .dialogSelectTemplate("dev-template")
        .dialogPermissionDisabled(ITEM_READ)
        .submitDialog()
        .hasRoleCard("qa-role")
        .roleCardHasPattern("qa-role", "qa-.*")
        .roleCardHasTemplateBadge("qa-role", "dev-template")
        .roleCardHasSummary("qa-role", "Build");
  }

  @Test
  @DisplayName("edits an existing role's pattern and permissions")
  void editRole(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .selectTab("Item roles")
        .clickEditRole("dev-role")
        .waitForDialog()
        .dialogSetPattern("staging-.*")
        .submitDialog()
        .roleCardHasPattern("dev-role", "staging-.*");
  }

  @Test
  @DisplayName("shows a validation error and blocks submit for an invalid pattern")
  void invalidPatternBlocksSubmit(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .selectTab("Agent roles")
        .clickAddRole()
        .waitForDialog()
        .dialogSetName("broken")
        .dialogTogglePermission(COMPUTER_CONFIGURE)
        .dialogSetPattern("(")
        .dialogSubmitDisabled()
        .cancelDialog();
  }

  @Test
  @DisplayName("keeps the role when deletion is cancelled and removes it when confirmed")
  void deleteRoleCancelKeepsItThenConfirmRemovesIt(JenkinsRule j, Page page) throws Exception {
    ManageRolesPage roles = open(j, page);

    roles.clickDeleteRole("readers")
        .confirmDialogContainsText("readers")
        .dismissConfirmDialog()
        .hasRoleCard("readers");

    roles.clickDeleteRole("readers")
        .acceptConfirmDialog()
        .hasNoRoleCard("readers");
  }

  @Test
  @DisplayName("shows the jobs matching an item role's pattern")
  void showMatchingJobs(JenkinsRule j, Page page) throws Exception {
    ManageRolesPage roles = open(j, page);
    j.createFreeStyleProject("dev-app");
    j.createFreeStyleProject("other-app");

    roles.selectTab("Item roles")
        .clickPatternChip("dev-role")
        .matchingDialogContainsText("dev-app")
        .closeMatchingDialog();
  }

  @Test
  @DisplayName("filters the visible roles as the user types in the search box")
  void searchFiltersRoles(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .search("read")
        .hasRoleCard("readers")
        .hasNoRoleCard("admin");
  }

  @Test
  @DisplayName("adds an item role with explicit permissions")
  void addItemRoleWithPermissions(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .selectTab("Item roles")
        .clickAddRole()
        .waitForDialog()
        .dialogSetName("builders")
        .dialogSetPattern("build-.*")
        .dialogTogglePermission(ITEM_BUILD)
        .submitDialog()
        .hasRoleCard("builders")
        .roleCardHasSummary("builders", "Build");
  }
}
