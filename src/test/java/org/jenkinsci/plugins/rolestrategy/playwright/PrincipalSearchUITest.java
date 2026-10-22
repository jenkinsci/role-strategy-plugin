package org.jenkinsci.plugins.rolestrategy.playwright;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.SecurityRealm;
import io.jenkins.plugins.authcommons.Principal;
import io.jenkins.plugins.authcommons.PrincipalSearchProvider;
import io.jenkins.plugins.authcommons.PrincipalType;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.AssignRolesPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * End-to-end UI tests for user and group autocompletion in the Assign Roles add dialog.
 */
@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
@DisplayName("Assign Roles principal search")
class PrincipalSearchUITest {

  private RoleBasedAuthorizationStrategy rbas;

  private AssignRolesPage open(JenkinsRule j, Page page) throws Exception {
    rbas = UITestHelper.setupRbasWithAssignments(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    return new AssignRolesPage(page, baseUrl).goTo();
  }

  @Test
  @DisplayName("several users can be picked at once")
  void pickSeveralUsers(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddEntry()
        .dialogSearchPrincipal("pla")
        .dialogSearchHasOption("Plato")
        // only the chosen type is searched
        .dialogSearchHasNoOption("Platform Team")
        .dialogPickPrincipal("Plato")
        .dialogHasChip("Plato")
        // a chosen principal is not offered again
        .dialogSearchPrincipal("pla")
        .dialogSearchHasNoOption("Plato")
        .dialogPickPrincipal("Platon")
        .dialogHasChip("Platon")
        .dialogToggleRole("readers")
        .submitDialog()
        .hasSidCard("plato-oid")
        .hasSidCard("platon-oid");

    Set<PermissionEntry> sids = rbas.getRoleMap(RoleType.Global).getSidEntriesForRole("readers");
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.USER, "plato-oid")));
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.USER, "platon-oid")));
  }

  @Test
  @DisplayName("choosing Group searches for groups")
  void pickGroup(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddEntry()
        .dialogSelectType("Group")
        .dialogSearchPrincipal("pla")
        .dialogSearchHasNoOption("Plato")
        .dialogPickPrincipal("Platform Team")
        .dialogHasChip("Platform Team")
        .dialogToggleRole("readers")
        .submitDialog()
        .hasSidCard("platform-oid");

    assertTrue(rbas.getRoleMap(RoleType.Global).getSidEntriesForRole("readers")
        .contains(new PermissionEntry(AuthorizationType.GROUP, "platform-oid")));
  }

  @Test
  @DisplayName("text that matches nothing can still be added as the chosen type")
  void freeText(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddEntry()
        .dialogSearchPrincipal("nobody")
        .dialogContainsText("No matching users")
        .dialogSearchHasNoOption("Add “nobody” as a group")
        .dialogPickPrincipal("Add “nobody” as a user")
        .dialogHasChip("nobody")
        .dialogToggleRole("readers")
        .submitDialog()
        .hasSidCard("nobody");

    Set<PermissionEntry> sids = rbas.getRoleMap(RoleType.Global).getSidEntriesForRole("readers");
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.USER, "nobody")));
    assertFalse(sids.contains(new PermissionEntry(AuthorizationType.GROUP, "nobody")));
  }

  @Test
  @DisplayName("existing entries are not offered and chips can be removed")
  void existingAndRemove(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddEntry()
        // alice already has an entry
        .dialogSearchPrincipal("ali")
        .dialogSearchHasNoOption("Alice Liddell")
        .dialogSearchPrincipal("pla")
        .dialogPickPrincipal("Plato")
        .dialogToggleRole("readers")
        .dialogRemoveChip("Plato")
        .dialogHasNoChips()
        .dialogSubmitDisabled()
        .cancelDialog();
  }

  @TestExtension
  public static class TestProvider extends PrincipalSearchProvider {
    private static final List<Principal> DIRECTORY = List.of(
        Principal.user("alice", "Alice Liddell", "alice@example.com"),
        Principal.user("plato-oid", "Plato", "plato@example.com"),
        Principal.user("platon-oid", "Platon", "platon@example.com"),
        Principal.group("platform-oid", "Platform Team", "Security group"));

    @Override
    public boolean isApplicable(@NonNull SecurityRealm realm) {
      return realm instanceof JenkinsRule.DummySecurityRealm;
    }

    @NonNull
    @Override
    public List<Principal> search(@NonNull String query, @NonNull Set<PrincipalType> types, int limit) {
      return DIRECTORY.stream()
          .filter(p -> types.contains(p.type()) && p.displayName().toLowerCase().startsWith(query.toLowerCase()))
          .toList();
    }
  }
}
