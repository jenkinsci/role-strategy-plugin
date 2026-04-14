package org.jenkinsci.plugins.rolestrategy.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.ManageAssignmentsPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
class ManageAssignmentsUITest {

  @Test
  void assignAndEditUsers(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    ManageAssignmentsPage assignPage = new ManageAssignmentsPage(page, baseUrl);
    assignPage.goTo();

    // Verify existing assignments
    assignPage.hasUserCard("admin")
        .userCardHasSummary("admin", "admin");
    assignPage.hasUserCard("testuser1")
        .userCardHasSummary("testuser1", "reader");

    // Assign new role to a user
    assignPage.clickAssignRole()
        .waitForAssignDialog()
        .dialogSetName("newuser")
        .dialogSetTypeUser()
        .dialogToggleRole("reader")
        .dialogSubmit();
    assignPage.goTo()
        .hasUserCard("newuser")
        .userCardHasSummary("newuser", "reader");

    // Expand card and verify only assigned roles shown
    assignPage.expandUserCard("newuser")
        .expandedCardHasRole("newuser", "reader");

    // Delete the user
    assignPage.clickDeleteUser("newuser")
        .confirmDelete();
    page.waitForTimeout(500);
    assignPage.goTo()
        .hasNoUserCard("newuser");
  }

  @Test
  void builtInUsersAndEmptyCards(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbas(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    ManageAssignmentsPage assignPage = new ManageAssignmentsPage(page, baseUrl);
    assignPage.goTo();

    // Built-in users have edit but no delete
    assignPage.hasUserCard("anonymous")
        .userCardHasEditButton("anonymous")
        .userCardHasNoDeleteButton("anonymous");

    assignPage.hasUserCard("authenticated")
        .userCardHasEditButton("authenticated")
        .userCardHasNoDeleteButton("authenticated");

    // Empty assignment cards are not expandable
    assignPage.userCardNotExpandable("anonymous");

    // Search works
    assignPage.search("admin")
        .hasUserCard("admin");
  }
}
