package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import java.net.HttpURLConnection;
import java.net.URL;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for permission-based access control on pattern matching API endpoints.
 * These endpoints are used by the UI to show which items/agents match a given pattern.
 * They require Jenkins.ADMINISTER permission.
 */
@WithJenkins
class PatternMatchingApiTest {

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

    // Adding itemAdmin role and assigning itemAdminUser (should NOT have access to pattern matching)
    rbas.doAddRole("globalRoles", "itemAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.getId(),
        "false", "", "");
    rbas.doAssignUserRole("globalRoles", "itemAdminRole", "itemAdminUser");

    // Adding agentAdmin role and assigning agentAdminUser (should NOT have access to pattern matching)
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
  void testGetMatchingJobsWithAdminUser() throws Exception {
    // Create some jobs
    jenkinsRule.createFreeStyleProject("test-job-1");
    jenkinsRule.createFreeStyleProject("test-job-2");
    jenkinsRule.createFreeStyleProject("other-job");

    // adminUser has Jenkins.ADMINISTER and should be able to call this endpoint
    webClient.login("adminUser", "adminUser");
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getMatchingJobs?pattern=.*");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    String content = page.getWebResponse().getContentAsString();
    JSONObject json = JSONObject.fromObject(content);

    assertTrue(json.has("matchingJobs"), "Response should contain matchingJobs");
    assertTrue(json.has("itemCount"), "Response should contain itemCount");

    // Verify response structure - should have at least the jobs we created
    int itemCount = json.getInt("itemCount");
    assertThat("Should have at least 3 items", itemCount >= 3, is(true));
  }

  @Test
  void testGetMatchingJobsPermissions() throws Exception {
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getMatchingJobs?pattern=.*");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

    // adminUser has Jenkins.ADMINISTER - should succeed
    performAsAndExpect("adminUser", request, HttpURLConnection.HTTP_OK);

    // itemAdminUser does NOT have Jenkins.ADMINISTER - should fail
    performAsAndExpect("itemAdminUser", request, HttpURLConnection.HTTP_FORBIDDEN);

    // agentAdminUser does NOT have Jenkins.ADMINISTER - should fail
    performAsAndExpect("agentAdminUser", request, HttpURLConnection.HTTP_FORBIDDEN);

    // developerUser has no admin permissions - should fail
    performAsAndExpect("developerUser", request, HttpURLConnection.HTTP_FORBIDDEN);
  }

  @Test
  void testGetMatchingAgentsWithAdminUser() throws Exception {
    // Create some agents
    jenkinsRule.createSlave("agent-1", null, null);
    jenkinsRule.createSlave("agent-2", null, null);
    jenkinsRule.createSlave("agent-3", null, null);

    // adminUser has Jenkins.ADMINISTER and should be able to call this endpoint
    webClient.login("adminUser", "adminUser");
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getMatchingAgents?pattern=.*");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    String content = page.getWebResponse().getContentAsString();
    JSONObject json = JSONObject.fromObject(content);

    assertTrue(json.has("matchingAgents"), "Response should contain matchingAgents");
    assertTrue(json.has("agentCount"), "Response should contain agentCount");

    // Verify response structure - should have at least the agents we created
    int agentCount = json.getInt("agentCount");
    assertThat("Should have at least 3 agents", agentCount >= 3, is(true));
  }

  @Test
  void testGetMatchingAgentsPermissions() throws Exception {
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getMatchingAgents?pattern=.*");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

    // adminUser has Jenkins.ADMINISTER - should succeed
    performAsAndExpect("adminUser", request, HttpURLConnection.HTTP_OK);

    // itemAdminUser does NOT have Jenkins.ADMINISTER - should fail
    performAsAndExpect("itemAdminUser", request, HttpURLConnection.HTTP_FORBIDDEN);

    // agentAdminUser does NOT have Jenkins.ADMINISTER - should fail
    performAsAndExpect("agentAdminUser", request, HttpURLConnection.HTTP_FORBIDDEN);

    // developerUser has no admin permissions - should fail
    performAsAndExpect("developerUser", request, HttpURLConnection.HTTP_FORBIDDEN);
  }

  @Test
  void testGetMatchingJobsWithMaxLimit() throws Exception {
    // Create more jobs than the limit
    for (int i = 0; i < 5; i++) {
      jenkinsRule.createFreeStyleProject("test-job-" + i);
    }

    webClient.login("adminUser", "adminUser");
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getMatchingJobs?pattern=^test.*&maxJobs=3");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    String content = page.getWebResponse().getContentAsString();
    JSONObject json = JSONObject.fromObject(content);

    // Should have at most 3 jobs in the response (limited by maxJobs parameter)
    int matchingJobsCount = json.getJSONArray("matchingJobs").size();
    assertThat("Should respect maxJobs limit", matchingJobsCount <= 3, is(true));

    // itemCount should reflect total number of matching items
    int itemCount = json.getInt("itemCount");
    assertThat("itemCount should be >= returned jobs", itemCount >= matchingJobsCount, is(true));
  }

  @Test
  void testGetMatchingAgentsWithMaxLimit() throws Exception {
    // Create more agents than the limit
    for (int i = 0; i < 5; i++) {
      jenkinsRule.createSlave("agent-" + i, null, null);
    }

    webClient.login("adminUser", "adminUser");
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getMatchingAgents?pattern=^agent.*&maxAgents=3");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    String content = page.getWebResponse().getContentAsString();
    JSONObject json = JSONObject.fromObject(content);

    // Should have at most 3 agents in the response (limited by maxAgents parameter)
    int matchingAgentsCount = json.getJSONArray("matchingAgents").size();
    assertThat("Should respect maxAgents limit", matchingAgentsCount <= 3, is(true));

    // agentCount should reflect total number of matching agents
    int agentCount = json.getInt("agentCount");
    assertThat("agentCount should be >= returned agents", agentCount >= matchingAgentsCount, is(true));
  }
}
