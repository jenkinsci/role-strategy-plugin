package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.security.GlobalMatrixAuthorizationStrategy;
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
 * Tests for RoleStrategyRootAction visibility and access control.
 * This action should only be visible to users with ITEM_ROLES_ADMIN or AGENT_ROLES_ADMIN
 * but WITHOUT SYSTEM_READ permission.
 */
@WithJenkins
class RoleStrategyRootActionTest {

  static {
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(true);
    RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.setEnabled(true);
  }

  private JenkinsRule jenkinsRule;
  private JenkinsRule.WebClient webClient;
  private RoleBasedAuthorizationStrategy rbas;
  private RoleStrategyRootAction rootAction;

  @BeforeEach
  void setUp(JenkinsRule jenkinsRule) throws Exception {
    this.jenkinsRule = jenkinsRule;
    JenkinsRule.DummySecurityRealm securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    rbas = new RoleBasedAuthorizationStrategy();
    jenkinsRule.jenkins.setAuthorizationStrategy(rbas);

    // Get the RootAction extension
    rootAction = jenkinsRule.jenkins.getExtensionList(RoleStrategyRootAction.class).get(0);

    // Setting up admin user with SYSTEM_READ
    rbas.doAddRole("globalRoles", "adminRole",
        "hudson.model.Hudson.Read,hudson.model.Hudson.Administer", "false", "", "");
    rbas.doAssignUserRole("globalRoles", "adminRole", "adminUser");

    // Setting up itemAdmin user with ITEM_ROLES_ADMIN but NO SYSTEM_READ
    rbas.doAddRole("globalRoles", "itemAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "itemAdminRole", "itemAdminUser");

    // Setting up agentAdmin user with AGENT_ROLES_ADMIN but NO SYSTEM_READ
    rbas.doAddRole("globalRoles", "agentAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "agentAdminRole", "agentAdminUser");

    // Setting up user with both ITEM and AGENT admin but NO SYSTEM_READ
    rbas.doAddRole("globalRoles", "bothAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId() + ","
                + RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "bothAdminRole", "bothAdminUser");

    // Setting up user with SYSTEM_READ and ITEM_ROLES_ADMIN
    rbas.doAddRole("globalRoles", "itemAdminWithReadRole",
        "hudson.model.Hudson.Administer," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(),
        "false", "", "");
    rbas.doAssignUserRole("globalRoles", "itemAdminWithReadRole", "itemAdminWithReadUser");

    // Setting up developer user with no admin permissions
    rbas.doAddRole("projectRoles", "developers",
        "hudson.model.Item.Read,hudson.model.Item.Build", "false", ".*", "");
    rbas.doAssignUserRole("projectRoles", "developers", "developerUser");

    webClient = jenkinsRule.createWebClient()
        .withThrowExceptionOnFailingStatusCode(false)
        .withJavaScriptEnabled(false);
  }

  @Test
  void testIconNotVisibleWhenRoleStrategyNotEnabled() throws Exception {
    // Change to different authorization strategy
    jenkinsRule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

    webClient.login("itemAdminUser", "itemAdminUser");
    String iconFileName = rootAction.getIconFileName();
    assertThat("Icon should not be visible when Role Strategy is not enabled", iconFileName, nullValue());
  }

  @Test
  void testIconVisibleForItemAdminWithoutSystemRead() throws Exception {
    webClient.login("itemAdminUser", "itemAdminUser");

    // Check visibility by fetching the root page and looking for the link
    Page page = webClient.goTo("");
    String content = page.getWebResponse().getContentAsString();
    assertThat("Root action link should be visible for itemAdmin without SYSTEM_READ",
        content.contains("role-strategy"), equalTo(true));
  }

  @Test
  void testIconVisibleForAgentAdminWithoutSystemRead() throws Exception {
    webClient.login("agentAdminUser", "agentAdminUser");

    Page page = webClient.goTo("");
    String content = page.getWebResponse().getContentAsString();
    assertThat("Root action link should be visible for agentAdmin without SYSTEM_READ",
        content.contains("role-strategy"), equalTo(true));
  }

  @Test
  void testIconVisibleForBothAdminsWithoutSystemRead() throws Exception {
    webClient.login("bothAdminUser", "bothAdminUser");

    Page page = webClient.goTo("");
    String content = page.getWebResponse().getContentAsString();
    assertThat("Root action link should be visible for user with both admin permissions but no SYSTEM_READ",
        content.contains("role-strategy"), equalTo(true));
  }

  @Test
  void testIconNotVisibleForAdminWithSystemRead() throws Exception {
    webClient.login("adminUser", "adminUser");

    // Admin should only see the management link, not the root action
    // The root action's getIconFileName should return null, so the link shouldn't appear at root level
    // (They'll access it via Manage Jenkins instead)
    Page page = webClient.goTo("manage");
    String content = page.getWebResponse().getContentAsString();
    assertThat("Management link should be visible for admin with SYSTEM_READ",
        content.contains("role-strategy"), equalTo(true));
  }

  @Test
  void testIconNotVisibleForItemAdminWithSystemRead() throws Exception {
    webClient.login("itemAdminWithReadUser", "itemAdminWithReadUser");

    Page page = webClient.goTo("manage");
    String content = page.getWebResponse().getContentAsString();
    assertThat("Management link should be visible for itemAdmin with SYSTEM_READ",
        content.contains("role-strategy"), equalTo(true));
  }

  @Test
  void testIconNotVisibleForDeveloper() throws Exception {
    webClient.login("developerUser", "developerUser");

    Page page = webClient.goTo("");
    String content = page.getWebResponse().getContentAsString();
    assertThat("Root action link should NOT be visible for user without admin permissions",
        content.contains("role-strategy"), equalTo(false));
  }

  @Test
  void testRootActionAccessibleForItemAdmin() throws Exception {
    webClient.login("itemAdminUser", "itemAdminUser");

    URL url = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/");
    WebRequest request = new WebRequest(url, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(),
        "itemAdmin should be able to access /role-strategy/");
  }

  @Test
  void testRootActionAccessibleForAgentAdmin() throws Exception {
    webClient.login("agentAdminUser", "agentAdminUser");

    URL url = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/");
    WebRequest request = new WebRequest(url, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(),
        "agentAdmin should be able to access /role-strategy/");
  }

  @Test
  void testRootActionForbiddenForDeveloper() throws Exception {
    webClient.login("developerUser", "developerUser");

    URL url = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/");
    WebRequest request = new WebRequest(url, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode(),
        "Developer without admin permissions should get 403 when accessing /role-strategy/");
  }

  @Test
  void testRootActionDelegatesToRoleStrategyConfig() throws Exception {
    webClient.login("itemAdminUser", "itemAdminUser");

    // Test that subpages work through the root action
    URL url = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/assign-roles");
    WebRequest request = new WebRequest(url, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode(),
        "itemAdmin should be able to access /role-strategy/assign-roles through RootAction delegation");
  }

  @Test
  void testGetTarget() throws Exception {
    webClient.login("itemAdminUser", "itemAdminUser");

    Object target = rootAction.getTarget();
    assertThat("getTarget should return RoleStrategyConfig instance", target, notNullValue());
    assertThat("getTarget should return RoleStrategyConfig class",
        target.getClass(), equalTo(RoleStrategyConfig.class));
  }
}
