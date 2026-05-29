package org.jenkinsci.plugins.rolestrategy.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.ManageRolesPage;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.PermissionTemplatesPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
class PermissionTemplatesUITest {

  @Test
  void templateCrudLifecycle(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    PermissionTemplatesPage templatesPage = new PermissionTemplatesPage(page, baseUrl);
    templatesPage.goTo();

    // Verify existing templates from setup
    templatesPage.hasTemplateCard("dev-template")
        .templateCardHasSummary("dev-template", "Build")
        .templateCardHasSummary("dev-template", "Read");

    // Empty template should not be expandable
    templatesPage.templateCardNotExpandable("empty-template");

    // Add new template
    templatesPage.clickAddTemplate()
        .waitForAddTemplateDialog()
        .dialogSetTemplateName("test-template")
        .dialogTogglePermission("Configure")
        .dialogSubmit();
    templatesPage.goTo()
        .hasTemplateCard("test-template")
        .templateCardHasSummary("test-template", "Configure");

    // Edit template — add Build permission
    templatesPage.clickEditTemplate("test-template")
        .waitForEditTemplateDialog()
        .dialogTogglePermission("Build")
        .dialogSubmit();
    templatesPage.goTo()
        .templateCardHasSummary("test-template", "Build");

    // Delete template
    templatesPage.clickDeleteTemplate("test-template")
        .confirmDelete();
    page.waitForTimeout(500);
    templatesPage.goTo()
        .hasNoTemplateCard("test-template");

    // Search
    templatesPage.search("dev")
        .templateCardIsVisible("dev-template")
        .templateCardIsHidden("empty-template");
  }

  @Test
  void templateUsedByRole(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);

    // Verify "In use" badge
    PermissionTemplatesPage templatesPage = new PermissionTemplatesPage(page, baseUrl);
    templatesPage.goTo()
        .templateCardHasInUseBadge("dev-template");

    // Navigate to roles page and add a new role with the template
    ManageRolesPage rolesPage = new ManageRolesPage(page, baseUrl);
    rolesPage.goTo()
        .clickAddRole()
        .waitForAddRoleDialog()
        .dialogSetScope("projectRoles")
        .dialogSetRoleName("template-test-role")
        .dialogSetPattern("ttr-.*")
        .dialogSelectTemplate("dev-template");

    // Verify template permissions are populated and disabled (use permission IDs for uniqueness)
    rolesPage.dialogPermissionIdIsCheckedAndDisabled("hudson.model.Item.Read")
        .dialogPermissionIdIsCheckedAndDisabled("hudson.model.Item.Build")
        .dialogSubmit();

    // Verify template badge on role card
    rolesPage.goTo()
        .hasRoleCard("template-test-role")
        .roleCardHasTemplateBadge("template-test-role", "dev-template");

    // Delete in-use template shows warning
    templatesPage.goTo()
        .clickDeleteTemplate("dev-template")
        .deleteDialogContainsText("in use")
        .confirmDelete();
  }

  @Test
  void templateEndToEnd(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    assertNotNull(baseUrl);

    // Create a job matching the templated-role pattern "tmpl-.*"
    FreeStyleProject project = j.createFreeStyleProject("tmpl-project");
    assertNotNull(project);

    // testuser2 has templated-role (item) — assign reader for global read
    UITestHelper.loginAsAdmin(page, baseUrl);
    new org.jenkinsci.plugins.rolestrategy.playwright.pages.ManageAssignmentsPage(page, baseUrl)
        .goTo()
        .clickAssignRole()
        .waitForAssignDialog()
        .dialogSetName("testuser2")
        .dialogSetTypeUser()
        .dialogToggleRole("reader")
        .dialogSubmit();

    // Log in as testuser2
    UITestHelper.login(page, baseUrl, "testuser2", "testuser2");

    // Navigate to the job — should have access (Job/Read from dev-template)
    page.navigate(baseUrl + "job/tmpl-project/");
    page.waitForLoadState();
    assertThat(page.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("tmpl-project").setLevel(1))).isVisible();

    // Should see Build button (Job/Build from dev-template)
    assertThat(page.getByRole(AriaRole.LINK,
        new Page.GetByRoleOptions().setName("Build Now"))).isVisible();
  }
}
