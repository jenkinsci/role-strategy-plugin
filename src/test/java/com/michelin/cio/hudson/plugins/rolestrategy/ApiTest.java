package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.PluginManager;
import hudson.model.Item;
import hudson.model.User;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.MockFolder;
import org.springframework.security.core.Authentication;

/**
 * Tests for {@link RoleBasedAuthorizationStrategy} Web API Methods.
 */
public class ApiTest {

  @Rule
  public final JenkinsRule jenkinsRule = new JenkinsRule();
  private JenkinsRule.WebClient webClient;
  private DummySecurityRealm securityRealm;

  @Before
  public void setUp() throws Exception {
    // Setting up jenkins configurations
    securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    jenkinsRule.jenkins.setAuthorizationStrategy(new RoleBasedAuthorizationStrategy());
    jenkinsRule.jenkins.setCrumbIssuer(null);
    // Adding admin role and assigning adminUser
    RoleBasedAuthorizationStrategy.getInstance().doAddRole("globalRoles", "adminRole",
        "hudson.model.Hudson.Read,hudson.model.Hudson.Administer,hudson.security.Permission.GenericRead", "false", "");
    RoleBasedAuthorizationStrategy.getInstance().doAssignUserRole("globalRoles", "adminRole", "adminUser");
    webClient = jenkinsRule.createWebClient();
    webClient.login("adminUser", "adminUser");
  }

  @Test
  @Issue("JENKINS-61470")
  public void testAddRole() throws IOException {
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
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that the role is in
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    boolean foundRole = false;
    for (Role role : roles) {
      if (role.getName().equals("new-role") && role.getPattern().pattern().equals(pattern)) {
        foundRole = true;
        break;
      }
    }
    assertTrue("Checking if the role is found.", foundRole);
  }

  @Test
  @Issue("JENKINS-61470")
  public void testGetRole() throws IOException {
    String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getRole?type=" + RoleType.Global.getStringType()
        + "&roleName=adminRole";
    URL apiUrl = new URL(url);
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);
    Page page = webClient.getPage(request);

    // Verifying that web request is successful and that the role is found
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
    String roleString = page.getWebResponse().getContentAsString();
    assertTrue(roleString.length() > 2);
    assertNotEquals("{}", roleString); // {} is returned when no role is found
  }

  @Test
  @Issue("JENKINS-61470")
  public void testAssignRole() throws IOException {
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
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that alice is assigned to the role "new-role"
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    boolean found = false;
    for (Role role : roles) {
      if (role.getName().equals(roleName) && role.containsPermissionEntry(sidEntry)) {
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
  public void testUnassignRole() throws IOException {

    String roleName = "new-role";
    String sid = "alice";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.EITHER, sid);
    testAssignRole(); // assign alice to a role named "new-role" that has configure access to "test-folder.*"
    URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignRole");
    WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
        new NameValuePair("roleName", roleName), new NameValuePair("sid", sid)));
    Page page = webClient.getPage(request);
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that alice no longer has permissions
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    for (Role role : roles) {
      Set<PermissionEntry> sids = role.getPermissionEntries();
      assertFalse("Checking if Alice is still assigned to new-role", role.getName().equals("new-role") && sids.contains(sidEntry));
    }
    // Verifying that ACL is updated
    Authentication alice = User.getById("alice", false).impersonate2();
    Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  public void testAssignUserRole() throws IOException {
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
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that alice is assigned to the role "new-role"
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    boolean found = false;
    for (Role role : roles) {
      Set<PermissionEntry> sids = role.getPermissionEntries();
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
  public void testUnassignUserRole() throws IOException {

    String roleName = "new-role";
    String sid = "alice";
    PermissionEntry sidEntry = new PermissionEntry(AuthorizationType.USER, sid);
    testAssignUserRole(); // assign alice to a role named "new-role" that has configure access to "test-folder.*"
    URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignUserRole");
    WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
    request.setRequestParameters(Arrays.asList(new NameValuePair("type", RoleType.Project.getStringType()),
        new NameValuePair("roleName", roleName), new NameValuePair("user", sid)));
    Page page = webClient.getPage(request);
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that alice no longer has permissions
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    for (Role role : roles) {
      Set<PermissionEntry> sids = role.getPermissionEntries();
      assertFalse("Checking if Alice is still assigned to new-role", role.getName().equals("new-role") && sids.contains(sidEntry));
    }
    // Verifying that ACL is updated
    Authentication alice = User.getById("alice", false).impersonate2();
    Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  public void testAssignGroupRole() throws IOException {
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
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that alice is assigned to the role "new-role"
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    boolean found = false;
    for (Role role : roles) {
      Set<PermissionEntry> sids = role.getPermissionEntries();
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
  public void testUnassignGroupRole() throws IOException {

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
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that alice no longer has permissions
    RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
    Set<Role> roles = strategy.getRoles(RoleType.Project);
    for (Role role : roles) {
      Set<PermissionEntry> sids = role.getPermissionEntries();
      assertFalse("Checking if Alice is still assigned to new-role", role.getName().equals("new-role") && sids.contains(sidEntry));
    }
    // Verifying that ACL is updated
    Authentication alice = User.getById("alice", false).impersonate2();
    Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
    assertFalse(folder.hasPermission2(alice, Item.CONFIGURE));
  }

  @Test
  public void ignoreDangerousPermissionInAddRole() throws IOException {
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
    assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Verifying that the role is in
    RoleBasedAuthorizationStrategy rbas = RoleBasedAuthorizationStrategy.getInstance();
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(PluginManager.CONFIGURE_UPDATECENTER), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(PluginManager.UPLOAD_PLUGINS), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(Jenkins.RUN_SCRIPTS), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole(roleName).hasPermission(Item.READ), is(true));
  }
}
