package com.michelin.cio.hudson.plugins.rolestrategy;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.Item;
import hudson.model.User;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.MockFolder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;


/**
 * Tests for {@link RoleBasedAuthorizationStrategy} Web API Methods
 */
public class APITest {
    
    @Rule
    public final JenkinsRule jenkinsRule = new JenkinsRule();
    private JenkinsRule.WebClient webClient;

    @Before
    public void setUp() throws Exception {
        // Setting up jenkins configurations
        jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        jenkinsRule.jenkins.setAuthorizationStrategy(new RoleBasedAuthorizationStrategy());
        jenkinsRule.jenkins.setCrumbIssuer(null);
        // Adding admin role and assigning adminUser
        RoleBasedAuthorizationStrategy.getInstance().doAddRole("globalRoles", "adminRole",
                "hudson.model.Hudson.Read,hudson.model.Hudson.Administer,hudson.security.Permission.GenericRead" ,
                "false", "");
        RoleBasedAuthorizationStrategy.getInstance().doAssignRole("globalRoles", "adminRole", "adminUser");
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(User.getById("adminUser", true).impersonate());
        webClient = jenkinsRule.createWebClient();
        webClient.login("adminUser", "adminUser");
    }

    @Test
    @Issue("JENKINS-61470")
    public void testAddRole() throws IOException {
        String roleName = "new-role";
        String pattern = "test-folder.*";
        // Adding role via web request
        URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole");
        WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
        request.setRequestParameters(Arrays.asList(
                new NameValuePair("type", RoleType.Project.getStringType()),
                new NameValuePair("roleName", roleName),
                new NameValuePair("permissionIds", "hudson.model.Item.Configure,hudson.model.Item.Discover,hudson.model.Item.Build,hudson.model.Item.Read"),
                new NameValuePair("overwrite", "false"),
                new NameValuePair("pattern", pattern)
        ));
        Page page = webClient.getPage(request);
        assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

        // Verifying that the role is in
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
        SortedMap<Role, Set<String>> grantedRoles = strategy.getGrantedRoles(RoleType.Project);
        boolean foundRole = false;
        for (Map.Entry<Role, Set<String>> entry : grantedRoles.entrySet()) {
            Role role = entry.getKey();
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
        String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/getRole?type="
                + RoleType.Global.getStringType() + "&roleName=adminRole";
        URL apiURL = new URL(url);
        WebRequest request = new WebRequest(apiURL, HttpMethod.GET);
        Page page = webClient.getPage(request);

        // Verifying that web request is successful and that the role is found
        assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
        String roleString = page.getWebResponse().getContentAsString();
        assertTrue(roleString.length() > 2);
        assertNotEquals("{}", roleString);  // {} is returned when no role is found
    }

    @Test
    @Issue("JENKINS-61470")
    public void testAssignRole() throws IOException {
        String roleName = "new-role";
        String sid = "alice";
        Authentication alice = User.getById(sid, true).impersonate();
        // Confirming that alice does not have access before assigning
        MockFolder folder = jenkinsRule.createFolder("test-folder");
        assertFalse(folder.hasPermission(alice, Item.CONFIGURE));

        // Assigning role using web request
        testAddRole();  // adds a role "new-role" that has configure access on "test-folder.*"
        URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/assignRole");
        WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
        request.setRequestParameters(Arrays.asList(
                new NameValuePair("type", RoleType.Project.getStringType()),
                new NameValuePair("roleName", roleName),
                new NameValuePair("sid", sid)
        ));
        Page page = webClient.getPage(request);
        assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

        // Verifying that alice is assigned to the role "new-role"
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
        SortedMap<Role, Set<String>> roles = strategy.getGrantedRoles(RoleType.Project);
        boolean found = false;
        for (Map.Entry<Role, Set<String>> entry : roles.entrySet()) {
            Role role = entry.getKey();
            Set<String> sids = entry.getValue();
            if (role.getName().equals(roleName) && sids.contains(sid)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        // Verifying that ACL is updated
        assertTrue(folder.hasPermission(alice, Item.CONFIGURE));
    }

    @Test
    @Issue("JENKINS-61470")
    public void testUnassignRole() throws IOException {

        String roleName = "new-role";
        String sid = "alice";
        testAssignRole();  // assign alice to a role named "new-role" that has configure access to "test-folder.*"
        URL apiURL = new URL(jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignRole");
        WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
        request.setRequestParameters(Arrays.asList(
                new NameValuePair("type", RoleType.Project.getStringType()),
                new NameValuePair("roleName", roleName),
                new NameValuePair("sid", sid)
        ));
        Page page = webClient.getPage(request);
        assertEquals("Testing if request is successful", HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());

        // Verifying that alice no longer has permissions
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
        SortedMap<Role, Set<String>> roles = strategy.getGrantedRoles(RoleType.Project);
        for (Map.Entry<Role, Set<String>> entry : roles.entrySet()) {
            Role role = entry.getKey();
            Set<String> sids = entry.getValue();
            assertFalse("Checking if Alice is still assigned to new-role",
                    role.getName().equals("new-role") && sids.contains("alice"));
        }
        // Verifying that ACL is updated
        Authentication alice = User.getById("alice", false).impersonate();
        Item folder = jenkinsRule.jenkins.getItemByFullName("test-folder");
        assertFalse(folder.hasPermission(alice, Item.CONFIGURE));
    }
}
