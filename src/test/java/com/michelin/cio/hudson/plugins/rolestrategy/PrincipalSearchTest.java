package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.SecurityRealm;
import io.jenkins.plugins.authcommons.Principal;
import io.jenkins.plugins.authcommons.PrincipalSearchProvider;
import io.jenkins.plugins.authcommons.PrincipalType;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PrincipalSearchTest {

  private static final String SEARCH_URL =
      "descriptor/" + RoleBasedAuthorizationStrategy.class.getName() + "/searchPrincipals";

  private JenkinsRule j;

  @BeforeEach
  void setUp(JenkinsRule j) throws Exception {
    this.j = j;
    JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
    realm.addGroups("u-alice", "g-alpha");
    j.jenkins.setSecurityRealm(realm);
    RoleBasedAuthorizationStrategy rbas = new RoleBasedAuthorizationStrategy();
    j.jenkins.setAuthorizationStrategy(rbas);
    rbas.doAddRole("globalRoles", "adminRole",
        "hudson.model.Hudson.Read,hudson.model.Hudson.Administer", "false", "", "");
    rbas.doAssignUserRole("globalRoles", "adminRole", "adminUser");
    rbas.doAddRole("globalRoles", "itemAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "itemAdminRole", "itemAdminUser");
    rbas.doAddRole("globalRoles", "readerRole", "hudson.model.Hudson.Read", "false", "", "");
    rbas.doAssignUserRole("globalRoles", "readerRole", "reader");
  }

  @Test
  void adminCanSearch() throws Exception {
    try (JenkinsRule.WebClient wc = j.createWebClient()) {
      wc.login("adminUser", "adminUser");
      Page page = wc.goTo(SEARCH_URL + "?q=al&type=GROUP", "application/json");
      JSONArray results = JSONObject.fromObject(page.getWebResponse().getContentAsString()).getJSONArray("results");
      assertEquals(1, results.size());
      assertEquals("g-alpha", results.getJSONObject(0).getString("sid"));
      assertEquals("GROUP", results.getJSONObject(0).getString("type"));

      page = wc.goTo(SEARCH_URL + "?q=al", "application/json");
      results = JSONObject.fromObject(page.getWebResponse().getContentAsString()).getJSONArray("results");
      assertEquals(2, results.size());
    }
  }

  @Test
  void searchRequiresRoleAdminAndSystemRead() throws Exception {
    try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
      // may assign item roles, but may not look sids up in the realm (no SYSTEM_READ)
      wc.login("itemAdminUser", "itemAdminUser");
      assertEquals(HttpURLConnection.HTTP_FORBIDDEN,
          wc.goTo(SEARCH_URL + "?q=al", null).getWebResponse().getStatusCode());

      wc.login("reader", "reader");
      assertEquals(HttpURLConnection.HTTP_FORBIDDEN,
          wc.goTo(SEARCH_URL + "?q=al", null).getWebResponse().getStatusCode());
    }
  }

  @Test
  void principalSearchAvailability() {
    RoleBasedAuthorizationStrategy.DescriptorImpl descriptor = RoleBasedAuthorizationStrategy.DESCRIPTOR;
    try (ACLContext ignored = ACL.as(User.getById("adminUser", true))) {
      assertTrue(descriptor.isPrincipalSearchAvailable());
    }
    try (ACLContext ignored = ACL.as(User.getById("itemAdminUser", true))) {
      assertFalse(descriptor.isPrincipalSearchAvailable());
    }
    try (ACLContext ignored = ACL.as(User.getById("reader", true))) {
      assertFalse(descriptor.isPrincipalSearchAvailable());
    }
  }

  @Test
  void displayNameFallsBackToProvider() {
    SecurityRealm realm = j.jenkins.getSecurityRealm();
    assertEquals("Alice Liddell", ValidationUtil.resolveUser("u-alice", realm).getDisplayName());
    assertEquals("Alpha Team", ValidationUtil.resolveGroup("g-alpha", realm).getDisplayName());
    assertNull(ValidationUtil.resolveUser("u-unknown", realm).getDisplayName());
  }

  @Test
  void directoryKnowsSidsTheRealmCannotResolve() {
    // e.g. directory users who never logged in
    SecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
    ValidationUtil.SidResolution alice = ValidationUtil.resolveUser("u-alice", realm);
    assertEquals(ValidationUtil.SidResolutionKind.FOUND, alice.getKind());
    assertEquals("Alice Liddell", alice.getDisplayName());
    ValidationUtil.SidResolution alpha = ValidationUtil.resolveGroup("g-alpha", realm);
    assertEquals(ValidationUtil.SidResolutionKind.FOUND, alpha.getKind());
    assertEquals("Alpha Team", alpha.getDisplayName());
    assertEquals(ValidationUtil.SidResolutionKind.NOT_FOUND, ValidationUtil.resolveUser("u-unknown", realm).getKind());
  }

  @TestExtension
  public static class TestProvider extends PrincipalSearchProvider {
    private static final List<Principal> DIRECTORY = List.of(
        Principal.user("u-alice", "Alice Liddell", "alice@example.com"),
        Principal.group("g-alpha", "Alpha Team", null));

    @Override
    public boolean isApplicable(@NonNull SecurityRealm realm) {
      return true;
    }

    @NonNull
    @Override
    public List<Principal> search(@NonNull String query, @NonNull Set<PrincipalType> types, int limit) {
      List<Principal> result = new ArrayList<>();
      for (Principal principal : DIRECTORY) {
        if (types.contains(principal.type()) && principal.displayName().toLowerCase().startsWith(query)) {
          result.add(principal);
        }
      }
      return result;
    }

    @Override
    public Principal lookup(@NonNull String sid, @NonNull PrincipalType type) {
      return DIRECTORY.stream()
          .filter(p -> p.sid().equals(sid) && p.type() == type)
          .findFirst()
          .orElse(null);
    }
  }
}
