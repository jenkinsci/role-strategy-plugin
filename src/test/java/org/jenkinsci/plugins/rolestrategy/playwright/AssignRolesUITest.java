package org.jenkinsci.plugins.rolestrategy.playwright;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import java.util.Set;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.AssignRolesPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * End-to-end UI tests for the React-based Assign Roles page.
 */
@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
@DisplayName("Assign Roles page")
class AssignRolesUITest {

  private RoleBasedAuthorizationStrategy rbas;

  private AssignRolesPage open(JenkinsRule j, Page page) throws Exception {
    rbas = UITestHelper.setupRbasWithAssignments(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    return new AssignRolesPage(page, baseUrl).goTo();
  }

  private Set<PermissionEntry> sidsForRole(RoleType type, String roleName) {
    return rbas.getRoleMap(type).getSidEntriesForRole(roleName);
  }

  @Test
  @DisplayName("lists the seeded assignments on their respective tabs")
  void listsSeededAssignmentsPerTab(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .hasSidCard("alice")
        .sidCardHasSummary("alice", "readers")
        .hasSidCard("legacy")
        .hasSidCard("anonymous")
        .hasSidCard("authenticated")
        .hasNoSidCard("devs")
        .selectTab("Item roles")
        .hasSidCard("devs")
        .sidCardHasSummary("devs", "dev-role")
        .selectTab("Agent roles")
        .hasSidCard("alice")
        .sidCardHasSummary("alice", "agent-role");
  }

  @Test
  @DisplayName("persists an edit-dialog role change immediately and across reloads")
  void editRolesPersists(JenkinsRule j, Page page) throws Exception {
    AssignRolesPage assign = open(j, page);

    assign.clickEditEntry("alice")
        .dialogRoleChecked("readers")
        .dialogToggleRole("admin")
        .submitDialog()
        .sidCardHasSummary("alice", "admin")
        .expandCard("alice")
        .cardShowsRoleChip("alice", "admin");
    // The change persists server-side without any page-level save action.
    assertTrue(sidsForRole(RoleType.Global, "admin")
        .contains(new PermissionEntry(AuthorizationType.USER, "alice")));

    assign.goTo().sidCardHasSummary("alice", "admin");
  }

  @Test
  @DisplayName("adds a user with a role selected in the dialog, surviving a reload")
  void addUserWithRoleSurvivesReload(JenkinsRule j, Page page) throws Exception {
    AssignRolesPage assign = open(j, page);

    assign.clickAddEntry()
        .dialogSetSidName("bob")
        .dialogToggleRole("readers")
        .submitDialog()
        .hasSidCard("bob")
        .sidCardHasSummary("bob", "readers");

    assign.goTo()
        .hasSidCard("bob")
        .sidCardHasSummary("bob", "readers");
  }

  @Test
  @DisplayName("filters down to ambiguous entries from the filter dropdown")
  void ambiguousFilterShowsOnlyAmbiguousEntries(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .openFilterDropdown()
        .clickFilterOption("Ambiguous")
        .hasSidCard("legacy")
        .hasNoSidCard("alice")
        .hasNoSidCard("anonymous");
  }

  @Test
  @DisplayName("requires at least one role before an entry can be added")
  void requiresAtLeastOneRole(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddEntry()
        .dialogSetSidName("ghost")
        .dialogContainsText("Select at least one role.")
        .dialogSubmitDisabled()
        .dialogToggleRole("readers")
        .submitDialog()
        .hasSidCard("ghost");
  }

  @Test
  @DisplayName("shows realm feedback for the name after leaving the field")
  void nameFeedbackFromRealm(JenkinsRule j, Page page) throws Exception {
    AssignRolesPage assign = open(j, page);

    // The dummy realm resolves every username: icon plus name snippet.
    assign.clickAddEntry()
        .dialogSetSidName("somebody")
        .dialogBlurSidName()
        .dialogNameFeedbackContains("somebody")
        // switching the type re-runs the lookup: as a group the sid is unknown
        .dialogSelectType("Group")
        .dialogNameFeedbackNotFound("somebody")
        .cancelDialog();

    // Groups only exist when explicitly seeded, so this one renders struck through.
    assign.clickAddEntry()
        .dialogSelectType("Group")
        .dialogSetSidName("ghosts")
        .dialogBlurSidName()
        .dialogNameFeedbackNotFound("ghosts")
        .cancelDialog();
  }

  @Test
  @DisplayName("rejects adding a duplicate user")
  void duplicateUserIsBlocked(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddEntry()
        .dialogSetSidName("alice")
        .dialogHasError("An entry for this user already exists.")
        .dialogSubmitDisabled()
        .cancelDialog();
  }

  @Test
  @DisplayName("keeps the entry when removal is cancelled and removes it when confirmed")
  void deleteEntryCancelKeepsItThenConfirmRemovesIt(JenkinsRule j, Page page) throws Exception {
    AssignRolesPage assign = open(j, page);

    assign.clickDeleteEntry("alice")
        .confirmDialogContainsText("alice")
        .dismissConfirmDialog()
        .hasSidCard("alice");

    assign.clickDeleteEntry("alice")
        .acceptConfirmDialog()
        .hasNoSidCard("alice");
    assertFalse(sidsForRole(RoleType.Global, "readers")
        .contains(new PermissionEntry(AuthorizationType.USER, "alice")));
  }

  @Test
  @DisplayName("flags ambiguous entries and migrates them to a user")
  void migratesAmbiguousEntryToUser(JenkinsRule j, Page page) throws Exception {
    AssignRolesPage assign = open(j, page);

    assign.hasAmbiguousAlert()
        .sidCardHasWarningBadge("legacy")
        .clickMigrateToUser("legacy")
        .confirmDialogContainsText("legacy")
        .acceptConfirmDialog()
        .sidCardHasNoWarningBadge("legacy")
        .hasNoAmbiguousAlert()
        .sidCardHasSummary("legacy", "readers");

    Set<PermissionEntry> sids = sidsForRole(RoleType.Global, "readers");
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.USER, "legacy")));
    assertFalse(sids.contains(new PermissionEntry(AuthorizationType.EITHER, "legacy")));

    assign.goTo().hasNoAmbiguousAlert();
  }

  @Test
  @DisplayName("filters the visible entries as the user types in the search box")
  void searchFiltersEntries(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .search("alice")
        .hasSidCard("alice")
        .hasNoSidCard("anonymous");
  }

  @Test
  @DisplayName("navigates to the Manage Roles page from the app bar")
  void manageRolesLinkNavigates(JenkinsRule j, Page page) throws Exception {
    open(j, page).clickManageRolesLink();
    page.waitForURL("**/manage/role-strategy/manage-roles*");
  }

  @Test
  @DisplayName("paginates long entry lists")
  void paginatesLongLists(JenkinsRule j, Page page) throws Exception {
    AssignRolesPage assign = open(j, page);
    // The five seeded global entries plus 60 users: two pages of 50 and 15.
    for (int i = 0; i < 60; i++) {
      rbas.doAssignUserRole("globalRoles", "readers", String.format("user%02d", i));
    }

    assign.goTo()
        .hasCardCount(50)
        .paginationStatusContains("Showing 1-50 of 65")
        .clickNextPage()
        .hasCardCount(15)
        .paginationStatusContains("Showing 51-65 of 65")
        .hasSidCard("user59")
        .hasNoSidCard("alice")
        .clickPreviousPage()
        .hasSidCard("alice");

    // Searching covers all entries and resets to the first page.
    assign.clickNextPage()
        .search("user59")
        .hasCardCount(1)
        .hasNoPagination();
  }
}
