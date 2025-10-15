package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.PermissionTemplate;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.model.Item;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for permission-based access control on template API operations.
 * These tests verify that the ITEM_ROLES_ADMIN permission is properly enforced
 * for template management operations.
 */
@WithJenkins
class PermissionTemplatesApiTest {

  static {
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(true);
    RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.setEnabled(true);
  }

  private JenkinsRule jenkinsRule;
  private JenkinsRule.WebClient webClient;
  private RoleBasedAuthorizationStrategy rbas;
  // List of executions for different users and expected results
  private static final List<Map<String, Object>> getExecutions = Arrays.asList(
          Map.of("username", "adminUser", "expectedCode", HttpURLConnection.HTTP_OK),
          Map.of("username", "itemAdminUser", "expectedCode", HttpURLConnection.HTTP_OK),
          Map.of("username", "agentAdminUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN),
          Map.of("username", "developerUser", "expectedCode", HttpURLConnection.HTTP_FORBIDDEN)
  );

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

    // Adding agentAdmin role and assigning agentAdminUser (should NOT have template access)
    rbas.doAddRole("globalRoles", "agentAdminRole",
        "hudson.model.Hudson.Read," + RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.getId(),
        "false", "", "");
    rbas.doAssignUserRole("globalRoles", "agentAdminRole", "agentAdminUser");

    // Adding developer role (no admin permissions)
    rbas.doAddRole("projectRoles", "developers",
        "hudson.model.Item.Read,hudson.model.Item.Build", "false", ".*", "");
    rbas.doAssignUserRole("projectRoles", "developers", "developerUser");

    // Create a base template for testing
    rbas.doAddTemplate("baseTemplate", "hudson.model.Item.Read,hudson.model.Item.Build", false);

    webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    webClient.login("adminUser", "adminUser");
  }

  private void performAsAndExpect(String username, WebRequest request, int expectedCode, String expectedContent)
      throws Exception {
    webClient.login(username, username);
    Page page = webClient.getPage(request);

    assertEquals(expectedCode, page.getWebResponse().getStatusCode(), "HTTP code mismatch for user " + username);
    String body = page.getWebResponse().getContentAsString();

    if (expectedContent != null) {
      assertTrue(body.contains(expectedContent), "Expected content not found: " + expectedContent);
    }
  }

  @Test
  void testAddTemplateAs() throws Exception {
    // Loop through each execution and perform the request
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      String templateName = username + "_testTemplate";

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addTemplate");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
      request.setRequestParameters(
          Arrays.asList(
              new NameValuePair("name", templateName),
              new NameValuePair("permissionIds", "hudson.model.Item.Read,hudson.model.Item.Configure"),
              new NameValuePair("overwrite", "false")
          )
      );

      performAsAndExpect(username, request, expectedCode, null);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        // Verify that the template was created
        PermissionTemplate template = rbas.getPermissionTemplate(templateName);
        assertThat("Template should exist for user: " + username, template, notNullValue());
        assertThat("Template name should match for user: " + username, template.getName(), equalTo(templateName));
        assertThat("Template should have READ permission", template.hasPermission(Item.READ), is(true));
        assertThat("Template should have CONFIGURE permission", template.hasPermission(Item.CONFIGURE), is(true));
      } else {
        // Verify that the template was NOT created
        PermissionTemplate template = rbas.getPermissionTemplate(templateName);
        assertThat("Template should not exist for user: " + username, template, equalTo(null));
      }
    }
  }

  @Test
  void testRemoveTemplatesAs() throws Exception {
    // Create templates to remove
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      String templateName = username + "_testTemplateToRemove";
      rbas.doAddTemplate(templateName, "hudson.model.Item.Read", false);
    }
    // Loop through each execution and perform the request
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      String templateName = username + "_testTemplateToRemove";

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/removeTemplates");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
      request.setRequestParameters(
          Arrays.asList(
              new NameValuePair("names", templateName),
              new NameValuePair("force", "false")
          )
      );

      performAsAndExpect(username, request, expectedCode, null);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        // Verify that the template was removed
        assertThat("Template should be removed for user: " + username,
            rbas.hasPermissionTemplate(templateName), is(false));
      } else {
        // Verify that the template still exists
        assertThat("Template should still exist for user: " + username,
            rbas.hasPermissionTemplate(templateName), is(true));
      }
    }
  }

  @Test
  void testGetTemplateAs() throws Exception {
    // Loop through each execution and perform the request
    for (Map<String, Object> execution : getExecutions) {
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getTemplate?name=baseTemplate");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.GET);

      performAsAndExpect(username, request, expectedCode, null);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        // Verify the response contains template data
        webClient.login(username, username);
        Page page = webClient.getPage(request);
        String content = page.getWebResponse().getContentAsString();
        JSONObject json = JSONObject.fromObject(content);
        assertThat("Response should contain permissionIds for user: " + username,
            json.has("permissionIds"), is(true));
        assertThat("Response should contain isUsed for user: " + username,
            json.has("isUsed"), is(true));
      }
    }
  }

  @Test
  void testAddTemplateWithOverwrite() throws Exception {
    // Create a template
    String templateName = "overwriteTest";

    String lastSuccessfulPerms = "hudson.model.Item.Read";
    for (Map<String, Object> execution : getExecutions) {
      // reset if needed
      rbas.doAddTemplate(templateName, "hudson.model.Item.Read", true);
      String username = (String) execution.get("username");
      int expectedCode = (int) execution.get("expectedCode");
      String overwrite = "true";
      String newPerms = "hudson.model.Item.Configure";

      URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addTemplate");
      WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
      request.setRequestParameters(
          Arrays.asList(
              new NameValuePair("name", templateName),
              new NameValuePair("permissionIds", newPerms),
              new NameValuePair("overwrite", overwrite)
          )
      );

      performAsAndExpect(username, request, expectedCode, null);

      if (expectedCode == HttpURLConnection.HTTP_OK) {
        // Verify the template was updated
        PermissionTemplate template = rbas.getPermissionTemplate(templateName);
        Assertions.assertNotNull(template);
        assertThat("Template should have new permission for user: " + username,
            template.hasPermission(hudson.security.Permission.fromId(newPerms)), is(true));
      } else {
        // Verify the template was NOT updated (still has last successful permissions)
        PermissionTemplate template = rbas.getPermissionTemplate(templateName);
        Assertions.assertNotNull(template);
        assertThat("Template should still have old permission for user: " + username,
            template.hasPermission(hudson.security.Permission.fromId(lastSuccessfulPerms)), is(true));
        assertThat("Template should not have new permission for user: " + username,
            template.hasPermission(hudson.security.Permission.fromId(newPerms)), is(false));
      }
    }
  }

  @Test
  void testRemoveTemplateInUse() throws Exception {
    // Create a template and use it in a role
    rbas.doAddTemplate("inUseTemplate", "hudson.model.Item.Read,hudson.model.Item.Build", false);
    rbas.doAddRole("projectRoles", "roleUsingTemplate",
        "", "false", ".*", "inUseTemplate");

    // Test removing with force=false (should not remove)
    URL apiUrl = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/removeTemplates");
    WebRequest request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
        Arrays.asList(
            new NameValuePair("names", "inUseTemplate"),
            new NameValuePair("force", "false")
        )
    );

    webClient.login("itemAdminUser", "itemAdminUser");
    Page page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Template should still exist
    assertThat("Template should still exist when in use and force=false",
        rbas.hasPermissionTemplate("inUseTemplate"), is(true));

    // Test removing with force=true (should remove)
    request = new WebRequest(apiUrl, HttpMethod.POST);
    request.setRequestParameters(
        Arrays.asList(
            new NameValuePair("names", "inUseTemplate"),
            new NameValuePair("force", "true")
        )
    );

    page = webClient.getPage(request);
    assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

    // Template should be removed
    assertThat("Template should be removed when force=true",
        rbas.hasPermissionTemplate("inUseTemplate"), is(false));
  }
}
