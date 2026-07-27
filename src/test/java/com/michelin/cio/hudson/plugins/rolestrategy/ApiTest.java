package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.PluginManager;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.Authentication;

/**
 * Tests for {@link RoleBasedAuthorizationStrategy} Web API Methods.
 */
@WithJenkins
class ApiTest {

  // Note: Reading Global roles requires SYSTEM_READ, which itemAdminUser and agentAdminUser don't have
  private static final List<Map<String, Object>> getExecutions = Arrays.asList(
          Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Global),
          Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Project),
          Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Slave),
          Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
          Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Project),
          Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Slave),
          Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
          Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Project),
          Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Slave),
          Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
          Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Project),
          Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Slave)
  );
  private JenkinsRule jenkinsRule;
  private JenkinsRule.WebClient webClient;
  private DummySecurityRealm securityRealm;

  private RoleBasedAuthorizationStrategy rbas;

  private Map<String, String> roleTypeToPermissionIds = Map.of(
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
  @Issue("JENKINS-61470")
  void testAddRole() throws IOException {
    String roleName = "new-role";
    String pattern = "test-folder.*";
    // Adding role via web request
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()), new NameValuePair("roleName", roleName),
                    new NameValuePair("permissionIds",
                            "hudson.model.Item.Configure,hudson.model.Item.Discover,hudson.model.Item.Build,hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false"), new NameValuePair("pattern", pattern)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that the role is in
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    SortedMap<Role, Set<PermissionEntry>> grantedRoles = strategy.getGrantedRolesEntries(RoleType.Project);
    boolean foundRole = false;
    for (Map.Entry<Role, Set<PermissionEntry>> entry : grantedRoles.entrySet()) {
      Role role = entry.getKey();
      if (role.getName().equals("new-role") && role.getPattern().pattern().equals(pattern)) {
        foundRole = true;
        break;
      }
    }
    assertTrue(foundRole, "Checking if the role is found.");
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
  void testAddRoleWithTemplate() throws IOException {
    String roleName = "new-role";
    String pattern = "test-folder.*";
    String template = "developer";
    // Adding role via web request
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
                    new NameValuePair("roleName", roleName),
                    new NameValuePair("permissionIds", "hudson.model.Item.Configure,hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false"), new NameValuePair("pattern", pattern),
                    new NameValuePair("template", template)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that the role is in
    SortedMap<Role, Set<PermissionEntry>> grantedRoles = rbas.getGrantedRolesEntries(RoleType.Project);
    Role role = null;
    for (Map.Entry<Role, Set<PermissionEntry>> entry : grantedRoles.entrySet()) {
      role = entry.getKey();
      if (role.getName().equals("new-role") && role.getPattern().pattern().equals(pattern) && role.getTemplateName().equals(template)) {
        break;
      }
      role = null;
    }
    assertThat(role, notNullValue());
    assertThat(role.hasPermission(Item.CONFIGURE), equalTo(false));
    assertThat(role.hasPermission(Item.BUILD), equalTo(true));
  }

  @Test
  void testAddRoleWithMissingTemplate() throws IOException {
    String roleName = "new-role";
    String pattern = "test-folder.*";
    String template = "quality";
    // Adding role via web request
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()), new NameValuePair("roleName", roleName),
                    new NameValuePair("permissionIds",
                            "hudson.model.Item.Configure,hudson.model.Item.Discover,hudson.model.Item.Build,hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false"), new NameValuePair("pattern", pattern),
                    new NameValuePair("template", template)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, page.getWebResponse().getStatusCode(), "Testing if request failed");
  }

  @Test
  void testAddRoleWithOverwriteKeepsAssignments() throws IOException {
    // "developers" was created in setUp() with developerUser assigned
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
                    new NameValuePair("roleName", "developers"),
                    new NameValuePair("permissionIds", "hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "true"), new NameValuePair("pattern", "dev-.*")));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    RoleMap roleMap = rbas.getRoleMap(RoleType.Project);
    Role role = roleMap.getRole("developers");
    assertThat(role, notNullValue());
    assertThat(role.getPattern().pattern(), equalTo("dev-.*"));
    assertThat(role.hasPermission(Item.BUILD), equalTo(false));
    Set<PermissionEntry> sids = roleMap.getSidEntriesForRole("developers");
    assertThat(sids, notNullValue());
    assertTrue(sids.stream().anyMatch(entry -> entry.getSid().equals("developerUser")),
        "Overwriting a role must keep its assignments");
  }

  @Test
  void testAddRoleWithInvalidPattern() throws IOException {
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
                    new NameValuePair("roleName", "broken-pattern-role"),
                    new NameValuePair("permissionIds", "hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false"), new NameValuePair("pattern", "(")));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, page.getWebResponse().getStatusCode(), "Testing if request failed");
    assertThat(rbas.getRoleMap(RoleType.Project).getRole("broken-pattern-role"), nullValue());
  }

  @Test
  void testAddTemplate() throws IOException {
    String template = "quality";
    // Adding role via web request
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addTemplate");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("name", template),
                    new NameValuePair("permissionIds",
                            "hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false")));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that the role is in
    PermissionTemplate pt = rbas.getPermissionTemplate(template);
    assertThat(pt, notNullValue());
    assertThat(pt.getName(), equalTo(template));
    assertThat(pt.hasPermission(Item.READ), equalTo(true));
  }

  @Test
  void testAddExistingTemplate() throws IOException {
    String template = "developer";
    // Adding role via web request
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addTemplate");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("name", template),
                    new NameValuePair("permissionIds",
                            "hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false")));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, page.getWebResponse().getStatusCode(), "Testing if request is failed");
  }

  @Test
  void testGetTemplate() throws IOException {
    String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getTemplate?name=developer";
    URL apiUrl = new URL(url);
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);
    Page page = webClient.getPage(request);

    // Verifying that web request is successful and that the role is found
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");
    String templateString = page.getWebResponse().getContentAsString();
    JSONObject responseJson = JSONObject.fromObject(templateString);
    assertThat(responseJson.get("isUsed"), equalTo(true));
  }

  @Test
  void testRemoveTemplate() throws IOException {
    String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/removeTemplates";
    rbas.doAddTemplate("quality", "Job/Read,Job/Workspace", false);
    rbas.doAddTemplate("unused", "hudson.model.Item.Read", false);
    rbas.doAddRole("projectRoles", "qa",
            "", "false", ".*", "quality");

    URL apiUrl = new URL(url);
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("names", "unused,quality"),
                    new NameValuePair("force",
                            "false")));
    Page page = webClient.getPage(request);

    // Verifying that web request is successful
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");
    Role role = rbas.getRoleMap(RoleType.Project).getRole("qa");
    assertThat(role.getTemplateName(), is("quality"));
    assertThat(role.hasPermission(Item.WORKSPACE), is(true));
    assertThat(rbas.hasPermissionTemplate("unused"), is(false));
    assertThat(rbas.hasPermissionTemplate("quality"), is(true));
  }

  @Test
  void testForceRemoveTemplate() throws IOException {
    String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/removeTemplates";
    URL apiUrl = new URL(url);
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("names", "developer,unknown"),
                    new NameValuePair("force",
                            "true")));
    Page page = webClient.getPage(request);

    // Verifying that web request is successful
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");
    Role role = rbas.getRoleMap(RoleType.Project).getRole("developers");
    assertThat(role.getTemplateName(), is(nullValue()));
    assertThat(role.hasPermission(Item.BUILD), is(true));
    assertThat(rbas.hasPermissionTemplate("developer"), is(false));
  }

  @Test
  void testGetRole() throws IOException {
    String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getRole?type=" + RoleType.Global.getStringType()
            + "&roleName=adminRole";
    URL apiUrl = new URL(url);
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);
    Page page = webClient.getPage(request);

    // Verifying that web request is successful and that the role is found
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");
    String roleString = page.getWebResponse().getContentAsString();
    assertTrue(roleString.length() > 2);
    assertNotEquals("{}", roleString); // {} is returned when no role is found
  }

  @Test
  @Issue("JENKINS-61470")
  void testAssignRole() throws IOException {
    String roleName = "new-role";
    String sid = "alice";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.EITHER, sid);
    Authentication alice = User.getById(sid, true).impersonate2();
    // Confirming that alice does not have access before assigning
    MockFolder folder = jenkinsRule.createFolder("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));

    // Assigning role using web request
    testAddRole(); // adds a role "new-role" that has configure access on "test-folder.*"
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/assignRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
            new NameValuePair("roleName", roleName), new NameValuePair("sid", sid)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that alice is assigned to the role "new-role"
    SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
    boolean found = false;
    for (Map.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
      Role role = entry.getKey();
      Set<PermissionEntry> sids = entry.getValue();
      if (role.getName().equals(roleName) && sids.contains(sidEntry)) {
        found = true;
        break;
      }
    }
    assertTrue(found);
    // Verifying that ACL is updated
    assertTrue(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  @Issue("JENKINS-61470")
  void testUnassignRole() throws IOException {

    String roleName = "new-role";
    String sid = "alice";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.EITHER, sid);
    testAssignRole(); // assign alice to a role named "new-role" that has configure access to "test-folder.*"
    URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignRole");
    WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
            new NameValuePair("roleName", roleName), new NameValuePair("sid", sid)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that alice no longer has permissions
    SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
    for (Map.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
      Role role = entry.getKey();
      Set<PermissionEntry> sids = entry.getValue();
      assertFalse(role.getName().equals("new-role") && sids.contains(sidEntry), "Checking if Alice is still assigned to new-role");
    }
    // Verifying that ACL is updated
    Authentication alice = User.getById("alice", false).impersonate2();
    Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  void testAssignUserRole() throws IOException {
    String roleName = "new-role";
    String sid = "alice";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.USER, sid);
    Authentication alice = User.getById(sid, true).impersonate2();
    // Confirming that alice does not have access before assigning
    MockFolder folder = jenkinsRule.createFolder("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));

    // Assigning role using web request
    testAddRole(); // adds a role "new-role" that has configure access on "test-folder.*"
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/assignUserRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
            new NameValuePair("roleName", roleName), new NameValuePair("user", sid)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that alice is assigned to the role "new-role"
    SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
    boolean found = false;
    for (Map.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
      Role role = entry.getKey();
      Set<PermissionEntry> sids = entry.getValue();
      if (role.getName().equals(roleName) && sids.contains(sidEntry)) {
        found = true;
        break;
      }
    }
    assertTrue(found);
    // Verifying that ACL is updated
    assertTrue(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  void testUnassignUserRole() throws IOException {

    String roleName = "new-role";
    String sid = "alice";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.USER, sid);
    testAssignUserRole(); // assign alice to a role named "new-role" that has configure access to "test-folder.*"
    URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignUserRole");
    WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
            new NameValuePair("roleName", roleName), new NameValuePair("user", sid)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that alice no longer has permissions
    SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
    for (Map.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
      Role role = entry.getKey();
      Set<PermissionEntry> sids = entry.getValue();
      assertFalse(role.getName().equals("new-role") && sids.contains(sidEntry), "Checking if Alice is still assigned to new-role");
    }
    // Verifying that ACL is updated
    Authentication alice = User.getById("alice", false).impersonate2();
    Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  void testAssignGroupRole() throws IOException {
    String roleName = "new-role";
    String sid = "alice";
    String group = "group";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.GROUP, group);
    User user = User.getById(sid, true);
    securityRealm.addGroups(sid, group);
    Authentication alice = user.impersonate2();
    // Confirming that alice does not have access before assigning
    MockFolder folder = jenkinsRule.createFolder("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));

    // Assigning role using web request
    testAddRole(); // adds a role "new-role" that has configure access on "test-folder.*"
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/assignGroupRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
            new NameValuePair("roleName", roleName), new NameValuePair("group", group)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that alice is assigned to the role "new-role"
    SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
    boolean found = false;
    for (Map.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
      Role role = entry.getKey();
      Set<PermissionEntry> sids = entry.getValue();
      if (role.getName().equals(roleName) && sids.contains(sidEntry)) {
        found = true;
        break;
      }
    }
    assertTrue(found);
    // Verifying that ACL is updated
    assertTrue(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  void testUnassignGroupRole() throws IOException {

    String roleName = "new-role";
    String sid = "alice";
    String group = "group";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.USER, sid);
    testAssignGroupRole(); // assign alice to a role named "new-role" that has configure access to "test-folder.*"
    URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignGroupRole");
    WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
            new NameValuePair("roleName", roleName), new NameValuePair("group", group)));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that alice no longer has permissions
    SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
    for (Map.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
      Role role = entry.getKey();
      Set<PermissionEntry> sids = entry.getValue();
      assertFalse(role.getName().equals("new-role") && sids.contains(sidEntry), "Checking if Alice is still assigned to new-role");
    }
    // Verifying that ACL is updated
    Authentication alice = User.getById("alice", false).impersonate2();
    Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  void ignoreDangerousPermissionInAddRole() throws IOException {
    String roleName = "new-role";
    // Adding role via web request
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
            Arrays.asList(new NameValuePair("type", RoleType.Global.getStringType()), new NameValuePair("roleName", roleName),
                    new NameValuePair("permissionIds",
                            "hudson.model.Hudson.RunScripts,hudson.model.Hudson.ConfigureUpdateCenter,"
                                    + "hudson.model.Hudson.UploadPlugins,hudson.model.Item.Read"),
                    new NameValuePair("overwrite", "false")));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // Verifying that the role is in
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(PluginManager.CONFIGURE_UPDATECENTER), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(PluginManager.UPLOAD_PLUGINS), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(Jenkins.RUN_SCRIPTS), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(Item.READ), is(true));
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

  private void assignAllTypesToDevelopers(String sid) throws IOException {
    rbas.doAssignUserRole("projectRoles", "developers", sid);
    rbas.doAssignGroupRole("projectRoles", "developers", sid);
    rbas.doAssignRole("projectRoles", "developers", sid);
  }

  private Page postToStrategy(String endpoint, List<NameValuePair> params) throws IOException {
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/" + endpoint);
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(params);
    return webClient.getPage(request);
  }

  @Test
  void testDeleteUser() throws IOException {
    assignAllTypesToDevelopers("mixed");

    Page page = postToStrategy("deleteUser", Arrays.asList(
            new NameValuePair("type", RoleType.Project.getStringType()), new NameValuePair("user", "mixed")));
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    Set<PermissionEntry> sids = rbas.getRoleMap(RoleType.Project).getSidEntriesForRole("developers");
    assertFalse(sids.contains(new PermissionEntry(AuthorizationType.USER, "mixed")));
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.GROUP, "mixed")));
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.EITHER, "mixed")));
  }

  @Test
  void testDeleteGroup() throws IOException {
    assignAllTypesToDevelopers("mixed");

    Page page = postToStrategy("deleteGroup", Arrays.asList(
            new NameValuePair("type", RoleType.Project.getStringType()), new NameValuePair("group", "mixed")));
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    Set<PermissionEntry> sids = rbas.getRoleMap(RoleType.Project).getSidEntriesForRole("developers");
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.USER, "mixed")));
    assertFalse(sids.contains(new PermissionEntry(AuthorizationType.GROUP, "mixed")));
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.EITHER, "mixed")));
  }

  @Test
  void testDeleteSid() throws IOException {
    assignAllTypesToDevelopers("mixed");

    Page page = postToStrategy("deleteSid", Arrays.asList(
            new NameValuePair("type", RoleType.Project.getStringType()), new NameValuePair("sid", "mixed")));
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    // deleteSid only removes ambiguous (EITHER) entries and leaves the typed ones alone
    Set<PermissionEntry> sids = rbas.getRoleMap(RoleType.Project).getSidEntriesForRole("developers");
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.USER, "mixed")));
    assertTrue(sids.contains(new PermissionEntry(AuthorizationType.GROUP, "mixed")));
    assertFalse(sids.contains(new PermissionEntry(AuthorizationType.EITHER, "mixed")));
  }

  @Test
  void testDeleteEndpointsRequirePermission() throws Exception {
    webClient.login("developerUser", "developerUser");

    for (String endpoint : List.of("deleteUser", "deleteGroup", "deleteSid")) {
      String param = endpoint.equals("deleteUser") ? "user" : endpoint.equals("deleteGroup") ? "group" : "sid";
      Page page = postToStrategy(endpoint, Arrays.asList(
              new NameValuePair("type", RoleType.Project.getStringType()), new NameValuePair(param, "mixed")));
      assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode(),
              "HTTP code mismatch for endpoint " + endpoint);
    }
  }

  @Test
  void testGetSidsInfo() throws IOException {
    securityRealm.addGroups("groupMember", "devGroup");
    User.getById("alice", true).setFullName("Alice Smith");

    String sids = "["
            + "{\"sid\":\"alice\",\"type\":\"USER\"},"
            + "{\"sid\":\"devGroup\",\"type\":\"GROUP\"},"
            + "{\"sid\":\"ghosts\",\"type\":\"GROUP\"},"
            + "{\"sid\":\"bob\",\"type\":\"EITHER\"},"
            + "{\"sid\":\"authenticated\",\"type\":\"GROUP\"},"
            + "{\"sid\":\"anonymous\",\"type\":\"USER\"}"
            + "]";
    Page page = postToStrategy("getSidsInfo", List.of(new NameValuePair("sids", sids)));
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(), "Testing if request is successful");

    net.sf.json.JSONArray json = net.sf.json.JSONArray.fromObject(page.getWebResponse().getContentAsString());
    assertEquals(6, json.size());

    JSONObject alice = json.getJSONObject(0);
    assertEquals("found", alice.getString("resolution"));
    assertEquals("USER", alice.getString("foundAs"));
    assertEquals("Alice Smith", alice.getString("displayName"));

    JSONObject devGroup = json.getJSONObject(1);
    assertEquals("found", devGroup.getString("resolution"));
    assertEquals("GROUP", devGroup.getString("foundAs"));

    assertEquals("not-found", json.getJSONObject(2).getString("resolution"));

    // The dummy realm resolves every username, so an ambiguous sid resolves as a user
    JSONObject bob = json.getJSONObject(3);
    assertEquals("found", bob.getString("resolution"));
    assertEquals("USER", bob.getString("foundAs"));

    assertEquals("internal", json.getJSONObject(4).getString("resolution"));
    assertEquals("internal", json.getJSONObject(5).getString("resolution"));
  }

  @Test
  void testCheckSidName() throws IOException {
    securityRealm.addGroups("groupMember", "devGroup");
    User.getById("alice", true).setFullName("Alice Smith");

    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl()
            + "descriptor/" + RoleBasedAuthorizationStrategy.class.getName() + "/checkSidName");

    // a found user renders its resolved display name
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(
            new NameValuePair("value", "alice"), new NameValuePair("type", "USER")));
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
    String content = page.getWebResponse().getContentAsString();
    assertTrue(content.contains("Alice Smith"), "expected the display name in: " + content);
    assertFalse(content.contains("rsp-entry-not-found"));

    // a seeded group resolves without the not-found styling
    request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(
            new NameValuePair("value", "devGroup"), new NameValuePair("type", "GROUP")));
    content = webClient.getPage(request).getWebResponse().getContentAsString();
    assertTrue(content.contains("devGroup"));
    assertFalse(content.contains("rsp-entry-not-found"));

    // an unknown group renders struck through but still validates OK
    request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(
            new NameValuePair("value", "ghosts"), new NameValuePair("type", "GROUP")));
    page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
    content = page.getWebResponse().getContentAsString();
    assertTrue(content.contains("rsp-entry-not-found"), "expected not-found styling in: " + content);
  }

  @Test
  void testGetSidsInfoRequiresPermission() throws Exception {
    webClient.login("developerUser", "developerUser");
    Page page = postToStrategy("getSidsInfo",
            List.of(new NameValuePair("sids", "[{\"sid\":\"alice\",\"type\":\"USER\"}]")));
    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode());
  }

  @Test
  void testAssignRolesBootstrapJson() {
    try (ACLContext ignored = ACL.as(User.getById("adminUser", true))) {
      JSONObject json = JSONObject.fromObject(RoleStrategyConfig.get().getAssignRolesBootstrapJson());
      JSONObject global = json.getJSONObject(RoleBasedAuthorizationStrategy.GLOBAL);
      assertTrue(global.getBoolean("visible"));
      assertTrue(global.getBoolean("canEdit"));
      List<String> roleNames = new ArrayList<>();
      for (Object role : global.getJSONArray("roles")) {
        roleNames.add(((JSONObject) role).getString("name"));
      }
      assertTrue(roleNames.contains("adminRole"));
      boolean foundAdminEntry = false;
      for (Object entry : global.getJSONArray("entries")) {
        JSONObject entryJson = (JSONObject) entry;
        if (entryJson.getString("name").equals("adminUser") && entryJson.getString("type").equals("USER")) {
          foundAdminEntry = entryJson.getJSONArray("roles").contains("adminRole");
        }
      }
      assertTrue(foundAdminEntry, "adminUser entry with adminRole expected in the global bootstrap");
    }

    try (ACLContext ignored = ACL.as(User.getById("developerUser", true))) {
      JSONObject json = JSONObject.fromObject(RoleStrategyConfig.get().getAssignRolesBootstrapJson());
      for (String type : List.of(RoleBasedAuthorizationStrategy.GLOBAL, RoleBasedAuthorizationStrategy.PROJECT,
              RoleBasedAuthorizationStrategy.SLAVE)) {
        JSONObject typeJson = json.getJSONObject(type);
        assertFalse(typeJson.getBoolean("visible"), "developerUser must not see " + type);
        assertFalse(typeJson.getBoolean("canEdit"));
        assertTrue(typeJson.getJSONArray("roles").isEmpty());
        assertTrue(typeJson.getJSONArray("entries").isEmpty());
      }
    }
  }
}
