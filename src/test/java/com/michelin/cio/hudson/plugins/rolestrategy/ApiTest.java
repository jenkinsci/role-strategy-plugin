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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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

  private void performAsAndExpect(String username, WebRequest request, int expectedCode, String expectedContent) throws Exception {
    webClient.login(username, username);
    Page page = webClient.getPage(request);

    assertEquals(expectedCode, page.getWebResponse().getStatusCode(), "HTTP code mismatch for user " + username);
    String body = page.getWebResponse().getContentAsString();

    if (expectedContent != null) {
      assertTrue(body.contains(expectedContent), "Expected content not found: " + expectedContent);
    }
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
    // List of Maps of executions for different users and expected results
    List<Map<String, Object>> roleExecutions = Arrays.asList(
            Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Global),
            Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Project),
            Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Slave),
            Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
            Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Project),
            Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Slave),
            Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Global),
            Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN, "roleType", RoleType.Project),
            Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_OK, "roleType", RoleType.Slave)
    );
    // Loop through each execution and perform the request
    for (Map<String, Object> execution : roleExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      String expectedContent = null;
      RoleType roleType = (RoleType) execution.get("roleType");
      String roleTypeStr = roleType.getStringType();
      String roleName = "testAddRoleAs" + username + roleType.getStringType();
      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
      request.setRequestParameters(
              Arrays.asList(new NameValuePair("type", roleTypeStr), new NameValuePair("roleName", roleName),
                      new NameValuePair("permissionIds", roleTypeToPermissionIds.get(roleTypeStr)),
                      new NameValuePair("overwrite", "false"), new NameValuePair("pattern", pattern)));
      performAsAndExpect(username, request, expectedCode, expectedContent);
      if (expectedCode == HttpURLConnection.HTTP_OK) {
        // Verifying that the role is in
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
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
}
