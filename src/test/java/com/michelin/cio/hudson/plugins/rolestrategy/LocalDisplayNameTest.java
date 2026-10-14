package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.User;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import java.net.URL;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Regression coverage for display names when an OIDC-style realm cannot look up users. */
@WithJenkins
class LocalDisplayNameTest {

  private JenkinsRule jenkinsRule;
  private SecurityRealm realm;
  private boolean itemRolesAdminEnabled;

  @BeforeEach
  void setUp(JenkinsRule rule) throws Exception {
    jenkinsRule = rule;
    itemRolesAdminEnabled = RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getEnabled();
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(true);
    SecurityRealm loginRealm = rule.createDummySecurityRealm();
    realm = new SecurityRealm() {
      @Override
      public SecurityComponents createSecurityComponents() {
        return loginRealm.createSecurityComponents();
      }

      @Override
      public UserDetails loadUserByUsername2(String username) {
        if ("missing".equals(username)) {
          throw new UsernameNotFoundException("User does not exist");
        }
        if ("broken".equals(username)) {
          throw new BadCredentialsException("Realm unavailable");
        }
        if ("known".equals(username)) {
          return loginRealm.loadUserByUsername2(username);
        }
        throw new UserMayOrMayNotExistException2("User lookup is not supported");
      }

      @Override
      public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) {
        if ("team".equals(groupname)) {
          return new GroupDetails() {
            @Override
            public String getName() {
              return "team";
            }

            @Override
            public String getDisplayName() {
              return "Team display name";
            }
          };
        }
        throw new UserMayOrMayNotExistException2("Group lookup is not supported");
      }
    };
    rule.jenkins.setSecurityRealm(realm);
    RoleBasedAuthorizationStrategy strategy = new RoleBasedAuthorizationStrategy();
    rule.jenkins.setAuthorizationStrategy(strategy);
    rule.jenkins.setCrumbIssuer(null);
    strategy.doAddRole("globalRoles", "admin", Jenkins.ADMINISTER.getId(), "false", "", "");
    strategy.doAssignUserRole("globalRoles", "admin", "admin");
    strategy.doAddRole("globalRoles", "roleAdmin",
        Jenkins.READ.getId() + "," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(), "false", "", "");
    strategy.doAssignUserRole("globalRoles", "roleAdmin", "roleAdmin");
  }

  @AfterEach
  void restorePermissions() {
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(itemRolesAdminEnabled);
  }

  @Test
  void unknownUserKeepsLocalNameWithoutResolvingIdentity() {
    User.getById("alice", true).setFullName("张三");
    ValidationUtil.SidResolution resolution = ValidationUtil.resolveUser("alice", realm);
    assertEquals(ValidationUtil.SidResolutionKind.UNKNOWN, resolution.getKind());
    assertEquals("张三", resolution.getDisplayName());
  }

  @Test
  void unknownUserDoesNotCreateLocalRecordOrRepeatId() {
    assertNull(User.getById("unrecorded", false));
    assertNull(ValidationUtil.resolveUser("unrecorded", realm).getDisplayName());
    assertNull(User.getById("unrecorded", false));
    User.getById("same-name", true).setFullName("same-name");
    assertNull(ValidationUtil.resolveUser("same-name", realm).getDisplayName());
  }

  @Test
  void knownUsersAndLongNamesKeepExistingBehavior() {
    User.getById("known", true).setFullName("Known user");
    ValidationUtil.SidResolution found = ValidationUtil.resolveUser("known", realm);
    assertEquals(ValidationUtil.SidResolutionKind.FOUND, found.getKind());
    assertEquals("Known user", found.getDisplayName());
    User.getById("long-name", true).setFullName("名".repeat(60));
    assertEquals("名".repeat(47) + "...", ValidationUtil.resolveUser("long-name", realm).getDisplayName());
  }

  @Test
  void missingUsersAndUnexpectedFailuresDoNotExposeStaleNames() {
    User.getById("missing", true).setFullName("Stale user");
    ValidationUtil.SidResolution missing = ValidationUtil.resolveUser("missing", realm);
    assertEquals(ValidationUtil.SidResolutionKind.NOT_FOUND, missing.getKind());
    assertNull(missing.getDisplayName());
    User.getById("broken", true).setFullName("Unavailable user");
    ValidationUtil.SidResolution broken = ValidationUtil.resolveUser("broken", realm);
    assertEquals(ValidationUtil.SidResolutionKind.UNKNOWN, broken.getKind());
    assertNull(broken.getDisplayName());
  }

  @Test
  void apiIncludesLocalNameButPreservesUnknownStatus() throws Exception {
    User.getById("alice", true).setFullName("张三");
    JSONArray result = resolveAs("admin", "["
        + "{\"sid\":\"alice\",\"type\":\"USER\"},"
        + "{\"sid\":\"alice\",\"type\":\"EITHER\"},"
        + "{\"sid\":\"unrecorded\",\"type\":\"USER\"}]");
    for (int index = 0; index < 2; index++) {
      JSONObject info = result.getJSONObject(index);
      assertEquals("unknown", info.getString("resolution"));
      assertEquals("张三", info.getString("displayName"));
      assertFalse(info.has("foundAs"));
      assertEquals("alice", info.getString("sid"));
    }
    assertFalse(result.getJSONObject(2).has("displayName"));
    assertNull(User.getById("unrecorded", false));
  }

  @Test
  void apiKeepsGroupResolutionAndReservedEntries() throws Exception {
    User.getById("team", true).setFullName("Local user name");
    User.getById("alice", true).setFullName("张三");
    JSONArray result = resolveAs("admin", "["
        + "{\"sid\":\"team\",\"type\":\"EITHER\"},"
        + "{\"sid\":\"alice\",\"type\":\"GROUP\"},"
        + "{\"sid\":\"anonymous\",\"type\":\"USER\"},"
        + "{\"sid\":\"authenticated\",\"type\":\"GROUP\"}]");
    JSONObject team = result.getJSONObject(0);
    assertEquals("found", team.getString("resolution"));
    assertEquals("GROUP", team.getString("foundAs"));
    assertEquals("Team display name", team.getString("displayName"));
    assertFalse(result.getJSONObject(1).has("displayName"));
    assertEquals("internal", result.getJSONObject(2).getString("resolution"));
    assertEquals("internal", result.getJSONObject(3).getString("resolution"));
  }

  @Test
  void roleAdminWithoutSystemReadCannotSeeLocalNames() throws Exception {
    User.getById("alice", true).setFullName("张三");
    JSONArray result = resolveAs("roleAdmin", "[{\"sid\":\"alice\",\"type\":\"USER\"}]");
    JSONObject info = result.getJSONObject(0);
    assertEquals("unknown", info.getString("resolution"));
    assertFalse(info.has("displayName"));
    assertFalse(info.has("foundAs"));
  }

  private JSONArray resolveAs(String username, String sids) throws Exception {
    try (JenkinsRule.WebClient client = jenkinsRule.createWebClient()) {
      client.login(username, username);
      WebRequest request = new WebRequest(new URL(jenkinsRule.getURL(), "role-strategy/strategy/getSidsInfo"), HttpMethod.POST);
      request.setRequestParameters(List.of(new NameValuePair("sids", sids)));
      Page page = client.getPage(request);
      assertEquals(200, page.getWebResponse().getStatusCode());
      return JSONArray.fromObject(page.getWebResponse().getContentAsString());
    }
  }
}
