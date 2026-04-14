package org.jenkinsci.plugins.rolestrategy.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.ManageAssignmentsPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
class RoleEndToEndUITest {

  @Test
  void globalReadOnlyRole(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    assertNotNull(baseUrl);
    UITestHelper.loginAsAdmin(page, baseUrl);

    // Assign the "reader" role (Overall/Read + Job/Read + View/Read) to e2euser via UI
    ManageAssignmentsPage assignPage = new ManageAssignmentsPage(page, baseUrl);
    assignPage.goTo()
        .clickAssignRole()
        .waitForAssignDialog()
        .dialogSetName("e2euser")
        .dialogSetTypeUser()
        .dialogToggleRole("reader")
        .dialogSubmit();

    // Log in as e2euser
    UITestHelper.login(page, baseUrl, "e2euser", "e2euser");

    // Should be able to access Jenkins (has read permission)
    page.navigate(baseUrl);
    page.waitForLoadState();
    // Verify we're logged in and on the main page (not an error page)
    assertThat(page.locator("#main-panel")).isVisible();

    // Should NOT be able to access /manage (no admin permission)
    page.navigate(baseUrl + "manage");
    page.waitForLoadState();
    assertThat(page.locator("body")).not().containsText("Manage Jenkins");
  }

  @Test
  void itemRoleGrantsJobAccess(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    assertNotNull(baseUrl);

    // Create a job matching the "dev-.*" item role pattern
    FreeStyleProject project = j.createFreeStyleProject("dev-project");
    assertNotNull(project);

    // testuser2 has "developer" item role — also assign "reader" for global read
    UITestHelper.loginAsAdmin(page, baseUrl);
    ManageAssignmentsPage assignPage = new ManageAssignmentsPage(page, baseUrl);
    assignPage.goTo()
        .clickAssignRole()
        .waitForAssignDialog()
        .dialogSetName("testuser2")
        .dialogSetTypeUser()
        .dialogToggleRole("reader")
        .dialogSubmit();

    // Log in as testuser2
    UITestHelper.login(page, baseUrl, "testuser2", "testuser2");

    // Navigate to the job — should have access
    page.navigate(baseUrl + "job/dev-project/");
    page.waitForLoadState();
    assertThat(page.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("dev-project").setLevel(1))).isVisible();

    // Should see Build button (has Build permission from developer role)
    assertThat(page.getByRole(AriaRole.LINK,
        new Page.GetByRoleOptions().setName("Build Now"))).isVisible();
  }
}
