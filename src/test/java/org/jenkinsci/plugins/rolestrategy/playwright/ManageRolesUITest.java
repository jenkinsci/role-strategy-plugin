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

  private static final String GLOBAL = "globalRoles";
  private static final String PROJECT = "projectRoles";
  private static final String AGENT = "slaveRoles";

  private static final String OVERALL_READ = "hudson.model.Hudson.Read";
  private static final String ITEM_READ = "hudson.model.Item.Read";
  private static final String ITEM_BUILD = "hudson.model.Item.Build";
  private static final String COMPUTER_BUILD = "hudson.model.Computer.Build";

  private ManageRolesPage open(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbasWithRoles(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    return new ManageRolesPage(page, baseUrl).goTo();
  }

  @Test
  @DisplayName("lists the seeded roles in their scope sections with patterns")
  void listsSeededRoles(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .hasRoleCard(GLOBAL, "auditor")
        .hasRoleCard(PROJECT, "developers")
        .roleCardHasPattern(PROJECT, "developers", "dev-.*")
        .hasRoleCard(AGENT, "operators")
        .roleCardHasPattern(AGENT, "operators", "node-.*");
  }

  @Test
  @DisplayName("adds a global role through the dialog")
  void addGlobalRole(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddRole()
        .waitForDialog()
        .dialogSelectScope(GLOBAL)
        .dialogSetName("viewer")
        .dialogTogglePermission(OVERALL_READ)
        .submitDialog()
        .hasRoleCard(GLOBAL, "viewer")
        .roleCardHasSummary(GLOBAL, "viewer", "Read");
  }

  @Test
  @DisplayName("adds an item role with a pattern through the dialog")
  void addItemRoleWithPattern(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddRole()
        .waitForDialog()
        .dialogSelectScope(PROJECT)
        .dialogSetName("qa")
        .dialogSetPattern("qa-.*")
        .dialogTogglePermission(ITEM_READ)
        .submitDialog()
        .hasRoleCard(PROJECT, "qa")
        .roleCardHasPattern(PROJECT, "qa", "qa-.*")
        .roleCardHasSummary(PROJECT, "qa", "Read");
  }

  @Test
  @DisplayName("adds an item role whose permissions are pre-filled from a template")
  void addItemRoleFromTemplate(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddRole()
        .waitForDialog()
        .dialogSelectScope(PROJECT)
        .dialogSetName("reviewers")
        .dialogSetPattern("review-.*")
        .dialogSelectTemplate("review-template")
        .submitDialog()
        .hasRoleCard(PROJECT, "reviewers")
        .roleCardHasTemplateBadge(PROJECT, "reviewers", "review-template")
        .roleCardHasSummary(PROJECT, "reviewers", "Read");
  }

  @Test
  @DisplayName("adds an agent role with a pattern through the dialog")
  void addAgentRole(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddRole()
        .waitForDialog()
        .dialogSelectScope(AGENT)
        .dialogSetName("linux-nodes")
        .dialogSetPattern("linux-.*")
        .dialogTogglePermission(COMPUTER_BUILD)
        .submitDialog()
        .hasRoleCard(AGENT, "linux-nodes")
        .roleCardHasPattern(AGENT, "linux-nodes", "linux-.*");
  }

  @Test
  @DisplayName("edits an existing role to grant an additional permission")
  void editRoleAddsPermission(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickEditRole(PROJECT, "developers")
        .waitForDialog()
        .dialogTogglePermission("hudson.model.Item.Configure")
        .submitDialog()
        .roleCardHasSummary(PROJECT, "developers", "Configure");
  }

  @Test
  @DisplayName("keeps the role when deletion is cancelled and removes it when confirmed")
  void deleteRoleCancelThenConfirm(JenkinsRule j, Page page) throws Exception {
    ManageRolesPage roles = open(j, page);

    roles.clickDeleteRole(AGENT, "operators")
        .confirmDialogContainsText("operators")
        .dismissConfirmDialog()
        .hasRoleCard(AGENT, "operators");

    roles.clickDeleteRole(AGENT, "operators")
        .acceptConfirmDialog()
        .hasNoRoleCard(AGENT, "operators");
  }

  @Test
  @DisplayName("filters the visible roles as the user types in the search box")
  void searchFiltersRoles(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .search("developers")
        .hasRoleCard(PROJECT, "developers")
        .hasNoRoleCard(GLOBAL, "auditor")
        .hasNoRoleCard(AGENT, "operators");
  }
}
