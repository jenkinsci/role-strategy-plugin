package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for {@link RoleBasedAuthorizationStrategy} Web API Methods.
 */
@WithJenkins
class ApiWithFineGrainedRolesTest {

  static {
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(true);
    RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.setEnabled(true);
  }

  // Note: Reading Global roles requires SYSTEM_READ, which itemAdminUser and agentAdminUser don't have
  private static final List<Map<String, Object>> getExecutions = Arrays.asList(
      Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Global),
      Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Project),
      Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Slave),
      Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
      Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Project),
      Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Slave),
      Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
      Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Project),
      Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Slave),
      Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
      Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Project),
      Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Slave)
  );
  private JenkinsRule jenkinsRule;
  private JenkinsRule.WebClient webClient;
  private DummySecurityRealm securityRealm;

  private RoleBasedAuthorizationStrategy rbas;

  private final Map<String, String> roleTypeToPermissionIds = Map.of(
      RoleType.Global.getStringType(), "hudson.model.Hudson.Read,hudson.model.Hudson.Administer,hudson.security.Permission.GenericRead",
      RoleType.Project.getStringType(), "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Cancel",
      RoleType.Slave.getStringType(), "hudson.model.Computer.Connect,hudson.model.Computer.Create"
  );

  @BeforeEach
  void setUp(JenkinsRule jenkinsRule) throws Exception {
    this.jenkinsRule = jenkinsRule;
    // Setting up jenkins configurations
    securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    rbas = new RoleBasedAuthorizationStrategy();
    jenkinsRule.jenkins.setAuthorizationStrategy(rbas);
    jenkinsRule.jenkins.setCrumbIssuer(null);
    // Adding admin role and assigning adminUser
    rbas.doAddRole("globalRoles", "adminRole",
        "hudson.model.Hudson.Read,hudson.model.Hudson.Administer,hudson.security.Permission.GenericRead", "false", "", "");
    rbas.doAssignUserRole("globalRoles", "adminRole", "adminUser");
    // Adding itemAdmin and assigning itemAdminUser
    rbas.doAddRole("globalRoles", "itemAdminRole",
            "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "itemAdminRole", "itemAdminUser");
    // Adding agentAdmin and assigning agentAdminUser
    rbas.doAddRole("globalRoles", "agentAdminRole",
            "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "agentAdminRole", "agentAdminUser");
    // Adding developer role and assigning developerUser
    rbas.doAddTemplate("developer", "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Cancel", false);
    rbas.doAddRole("projectRoles", "developers",
            "", "false", ".*", "developer");
    rbas.doAssignUserRole("projectRoles", "developers", "developerUser");
    // Adding developerAgent role and assigning developerAgentUser
    rbas.doAddRole("slaveRoles", "developerAgentRole",
            "hudson.model.Computer.Connect", "false", ".*", "");
    rbas.doAssignUserRole("slaveRoles", "developerAgentRole", "developerUser");
    webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    webClient.login("adminUser", "adminUser");
  }

  private void performAsAndExpect(String username, WebRequest request, int expectedCode, String roleTypeStr) throws Exception {
    webClient.login(username, username);
    Page page = webClient.getPage(request);

    assertEquals(expectedCode, page.getWebResponse().getStatusCode(), "HTTP code mismatch for user " + username
            + " with roleType " + roleTypeStr);
  }

  @Test
  void testAddRoleAs() throws Exception {
    String pattern = "test-folder.*";
    // Loop through each execution and perform the request
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();
      String roleName = "testAddRoleAs" + username + roleType.getStringType();
      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
      request.setRequestParameters(
              Arrays.asList(new NameValuePair("type", roleTypeStr), new NameValuePair("roleName", roleName),
                      new NameValuePair("permissionIds", roleTypeToPermissionIds.get(roleTypeStr)),
                      new NameValuePair("overwrite", "false"), new NameValuePair("pattern", pattern)));
      performAsAndExpect(username, request, expectedCode, roleTypeStr);
      if (expectedCode == HttpURLConnection.HTTP_OK) {
        // Verifying that the role is in
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
        Assertions.assertNotNull(strategy);
        SortedMap<Role, Set<PermissionEntry>> grantedRoles = strategy.getGrantedRolesEntries(roleType);
        boolean foundRole = false;
        for (Map.Entry<Role, Set<PermissionEntry>> entry : grantedRoles.entrySet()) {
          Role role = entry.getKey();
          if (role.getName().equals(roleName)) {
            if (roleType != RoleType.Global && !role.getPattern().pattern().equals(pattern)) {
              // If the role is a project role, check if the pattern matches
              continue;
            }
            foundRole = true;
            break;
          }
        }
        assertTrue(foundRole, "Checking if the role is found for user: " + username);
      }
    }
  }

  @Test
  void testRemoveRolesAs() throws Exception {
    String pattern = "test-folder.*";

    // Create roles first
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();
      String roleName = "testRemoveRolesAs" + username + roleType.getStringType();
      rbas.doAddRole(roleTypeStr, roleName, roleTypeToPermissionIds.get(roleTypeStr), "false", pattern, "");
    }

    // Now test removal with different users
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();
      String roleName = "testRemoveRolesAs" + username + roleType.getStringType();

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/removeRoles");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
      request.setRequestParameters(
          Arrays.asList(
              new NameValuePair("type", roleTypeStr),
              new NameValuePair("roleNames", roleName)
          )
      );

      performAsAndExpect(username, request, expectedCode, roleTypeStr);

      // Verify the role state
      RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
      Assertions.assertNotNull(strategy);
      Role role = strategy.getRoleMap(roleType).getRole(roleName);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        assertThat("Role should be removed for user: " + username, role, nullValue());
      } else {
        assertThat("Role should still exist for user: " + username, role, notNullValue());
      }
    }
  }

  @Test
  void testGetRoleAs() throws Exception {

    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();

      // Use existing roles from setup
      String roleName = roleType == RoleType.Global ? "adminRole"
          : roleType == RoleType.Project ? "developers" : "developerAgentRole";

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl()
          + "role-strategy/strategy/getRole?type=" + roleTypeStr + "&roleName=" + roleName);
      WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

      performAsAndExpect(username, request, expectedCode, roleTypeStr);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        webClient.login(username, username);
        Page page = webClient.getPage(request);
        String content = page.getWebResponse().getContentAsString();
        JSONObject json = JSONObject.fromObject(content);
        assertThat("Response should contain permissionIds for user: " + username,
            json.has("permissionIds"), is(true));
        assertThat("Response should contain sids for user: " + username,
            json.has("sids"), is(true));
      }
    }
  }

  @Test
  void testGetAllRolesAs() throws Exception {

    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl()
          + "role-strategy/strategy/getAllRoles?type=" + roleTypeStr);
      WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

      performAsAndExpect(username, request, expectedCode, roleTypeStr);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        webClient.login(username, username);
        Page page = webClient.getPage(request);
        String content = page.getWebResponse().getContentAsString();
        JSONObject json = JSONObject.fromObject(content);
        assertThat("Response should be a JSON object for user: " + username,
            json.isEmpty(), is(false));
      }
    }
  }

  @Test
  void testGetRoleAssignmentsAs() throws Exception {

    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl()
          + "role-strategy/strategy/getRoleAssignments?type=" + roleTypeStr);
      WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

      performAsAndExpect(username, request, expectedCode, roleTypeStr);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        webClient.login(username, username);
        Page page = webClient.getPage(request);
        String content = page.getWebResponse().getContentAsString();
        net.sf.json.JSONArray jsonArray = net.sf.json.JSONArray.fromObject(content);
        // Should return an array of user/group assignments with their roles
        assertThat("Response should be a JSON array for user: " + username,
            jsonArray, notNullValue());
      }
    }
  }
}
