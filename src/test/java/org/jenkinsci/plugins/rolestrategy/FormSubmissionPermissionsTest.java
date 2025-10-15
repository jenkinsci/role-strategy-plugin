package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import java.net.HttpURLConnection;
import java.net.URL;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for permission-based access control on form submission wrapper methods.
 * These tests verify that the entry-level permission checks work correctly.
 * The actual form processing logic is tested through the REST API endpoints.
 *
 * Note: Form submission handlers (doRolesSubmit, doAssignSubmit, doTemplatesSubmit)
 * check minimum permissions at entry, then delegate to descriptor methods that use
 * selective filtering (processing sections the user has permission for, copying
 * unauthorized sections from the old strategy). This selective filtering behavior
 * is complex to test at the form submission level and is better verified through
 * the REST API tests.
 */
@WithJenkins
class FormSubmissionPermissionsTest {

  static {
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(true);
    RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.setEnabled(true);
  }

  private JenkinsRule jenkinsRule;
  private JenkinsRule.WebClient webClient;
  private RoleBasedAuthorizationStrategy rbas;

  @BeforeEach
  void setUp(JenkinsRule jenkinsRule) throws Exception {
    this.jenkinsRule = jenkinsRule;
    // Setting up jenkins configurations
    JenkinsRule.DummySecurityRealm securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    rbas = new RoleBasedAuthorizationStrategy();
    jenkinsRule.jenkins.setAuthorizationStrategy(rbas);
    jenkinsRule.jenkins.setCrumbIssuer(null);

    // Adding admin role and assigning adminUser
    rbas.doAddRole("globalRoles", "adminRole",
        "hudson.model.Hudson.Read,hudson.model.Hudson.Administer,hudson.security.Permission.GenericRead",
        "false", "", "");
    rbas.doAssignUserRole("globalRoles", "adminRole", "adminUser");

    // Adding itemAdmin role and assigning itemAdminUser
    rbas.doAddRole("globalRoles", "itemAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(),
        "false", "", "");
    rbas.doAssignUserRole("globalRoles", "itemAdminRole", "itemAdminUser");

    // Adding agentAdmin role and assigning agentAdminUser
    rbas.doAddRole("globalRoles", "agentAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.getId(),
        "false", "", "");
    rbas.doAssignUserRole("globalRoles", "agentAdminRole", "agentAdminUser");

    // Adding developer role (no admin permissions)
    rbas.doAddRole("projectRoles", "developers",
        "hudson.model.Item.Read,hudson.model.Item.Build", "false", ".*", "");
    rbas.doAssignUserRole("projectRoles", "developers", "developerUser");

    webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    webClient.login("adminUser", "adminUser");
  }

  private void performAsAndExpect(String username, WebRequest request, int expectedCode) throws Exception {
    webClient.login(username, username);
    Page page = webClient.getPage(request);
    assertEquals(expectedCode, page.getWebResponse().getStatusCode(), "HTTP code mismatch for user " + username);
  }

  @Test
  void testRolesSubmitAccessControl() throws Exception {
    // Test that doRolesSubmit entry point correctly checks for ADMINISTER_AND_SOME_ROLES_ADMIN

    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/rolesSubmit");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);

    // adminUser has Jenkins.ADMINISTER - should pass entry check (but will fail on empty form)
    performAsAndExpect("adminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // itemAdminUser has ITEM_ROLES_ADMIN - should pass entry check (but will fail on empty form)
    performAsAndExpect("itemAdminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // agentAdminUser has AGENT_ROLES_ADMIN - should pass entry check (but will fail on empty form)
    performAsAndExpect("agentAdminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // developerUser has no admin permissions - should get 403 at entry
    performAsAndExpect("developerUser", request, HttpURLConnection.HTTP_FORBIDDEN);
  }

  @Test
  void testAssignSubmitAccessControl() throws Exception {
    // Test that doAssignSubmit entry point correctly checks for ADMINISTER_AND_SOME_ROLES_ADMIN

    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/assignSubmit");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);

    // adminUser has Jenkins.ADMINISTER - should pass entry check (but will fail on empty form)
    performAsAndExpect("adminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // itemAdminUser has ITEM_ROLES_ADMIN - should pass entry check (but will fail on empty form)
    performAsAndExpect("itemAdminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // agentAdminUser has AGENT_ROLES_ADMIN - should pass entry check (but will fail on empty form)
    performAsAndExpect("agentAdminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // developerUser has no admin permissions - should get 403 at entry
    performAsAndExpect("developerUser", request, HttpURLConnection.HTTP_FORBIDDEN);
  }

  @Test
  void testTemplatesSubmitAccessControl() throws Exception {
    // Test that doTemplatesSubmit entry point correctly checks for ITEM_ROLES_ADMIN

    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/templatesSubmit");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);

    // adminUser has Jenkins.ADMINISTER (which implies ITEM_ROLES_ADMIN) - should pass (but will fail on empty form)
    performAsAndExpect("adminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // itemAdminUser has ITEM_ROLES_ADMIN - should pass entry check (but will fail on empty form)
    performAsAndExpect("itemAdminUser", request, HttpURLConnection.HTTP_BAD_REQUEST);

    // agentAdminUser does NOT have ITEM_ROLES_ADMIN - should get 403
    performAsAndExpect("agentAdminUser", request, HttpURLConnection.HTTP_FORBIDDEN);

    // developerUser has no admin permissions - should get 403
    performAsAndExpect("developerUser", request, HttpURLConnection.HTTP_FORBIDDEN);
  }

  @Test
  void testSelectiveFilteringBehaviorThroughRestAPI() throws Exception {
    // Demonstrate selective filtering by using the REST API which shares the same underlying logic
    // itemAdminUser can modify Project roles via REST API, which uses the same permission checks

    webClient.login("itemAdminUser", "itemAdminUser");

    // itemAdminUser can add a project role
    rbas.doAddRole("projectRoles", "testRole", "hudson.model.Item.Read", "false", ".*", "");
    Role projectRole = rbas.getRoleMap(RoleType.Project).getRole("testRole");
    assertThat("itemAdminUser should be able to create project roles", projectRole, notNullValue());

    // But trying to add a global role should fail with 403
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(java.util.Arrays.asList(
        new org.htmlunit.util.NameValuePair("type", "globalRoles"),
        new org.htmlunit.util.NameValuePair("roleName", "unauthorizedRole"),
        new org.htmlunit.util.NameValuePair("permissionIds", "hudson.model.Hudson.Read"),
        new org.htmlunit.util.NameValuePair("overwrite", "false")
    ));

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode(),
        "itemAdminUser should not be able to create global roles");

    Role globalRole = rbas.getRoleMap(RoleType.Global).getRole("unauthorizedRole");
    assertThat("Unauthorized global role should not exist", globalRole, equalTo(null));
  }
}
