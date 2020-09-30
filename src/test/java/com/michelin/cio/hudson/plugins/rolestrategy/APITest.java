package com.michelin.cio.hudson.plugins.rolestrategy;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.User;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Logger;

/**
 * Tests for {@link RoleBasedAuthorizationStrategy} Web API Methods
 */
public class APITest {

    @Rule
    public final JenkinsRule jenkinsRule = new JenkinsRule();
    public static Logger LOGGER = Logger.getLogger(APITest.class.getName());

    @Before
    public void setUp() throws IOException {
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
    }

    @Test
    public void testAddRole() throws IOException {
        // Adding role via curl command
        String roleName = "new-role";
        String pattern = "test-folder.*";
        String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole";
        String curlCmd = "curl -u adminUser:adminUser -X POST " + url + " --data type="+ RoleType.Project.getStringType()
                + "&roleName=" + roleName + "&permissionIds=hudson.model.Item.Configure&overwrite=false"
                + "&pattern=" + pattern;
        String output = execCmd(curlCmd);
        LOGGER.info("Output of addRole: " + output);
        // Verifying that the role is in
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
        SortedMap<Role, Set<String>> grantedRoles = strategy.getGrantedRoles(RoleType.Project);
        boolean foundRole = false;
        for (Map.Entry<Role, Set<String>> entry : grantedRoles.entrySet()) {
            Role role = entry.getKey();
            if (role.getName().equals("new-role") && role.getPattern().pattern().equals(pattern)) {
                foundRole = true;
            }
        }
        assertTrue("Checking if the role is found.", foundRole);
    }

    @Test
    public void testGetRole() throws IOException {
        String curlCmd = "curl -u adminUser:adminUser -X GET " + jenkinsRule.jenkins.getRootUrl()
                + "role-strategy/strategy/getRole?" + "type=" + RoleType.Global.getStringType() + "&roleName=adminRole";
        String roleString = execCmd(curlCmd);
        LOGGER.info("response: " + roleString);

        assertTrue(roleString.length() > 2);
        assertNotEquals("{}", roleString);
    }

    @Test
    public void testAssignRole() throws IOException, InterruptedException {
        // adding role
        String roleName = "new-role";
        String pattern = "test-folder.*";
        String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/addRole";
        String curlCmd = "curl -u adminUser:adminUser -X POST " + url + " --data type="+ RoleType.Project.getStringType()
                + "&roleName=" + roleName + "&permissionIds=hudson.model.Item.Configure&overwrite=false"
                + "&pattern=" + pattern;
        execCmd(curlCmd);
        // Assigning role
        String sid = "alice";
        url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/assignRole";
        curlCmd = "curl -u adminUser:adminUser -X POST " + url + " --data type=" + RoleType.Project.getStringType()
                + "&roleName=" + roleName + "&sid=" + sid;
        execCmd(curlCmd);
        Thread.sleep(1000);
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
    }

    @Test
    public void testUnassignRole() throws IOException, InterruptedException {
        testAssignRole();  // assign alice to a role named "new-role"
        String url = jenkinsRule.jenkins.getRootUrl() + "role-strategy/strategy/unassignRole";
        String curlCmd = "curl -u adminUser:adminUser -X POST " + url + " --data type="
                + RoleType.Project.getStringType() + "&roleName=new-role&sid=alice";
        String output = execCmd(curlCmd);
        LOGGER.info("UnassignRole output: " + output);
        // Verifying that alice no longer has permissions
        RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance();
        SortedMap<Role, Set<String>> roles = strategy.getGrantedRoles(RoleType.Project);
        for (Map.Entry<Role, Set<String>> entry : roles.entrySet()) {
            Role role = entry.getKey();
            Set<String> sids = entry.getValue();
            assertFalse("Checking if Alice is still assigned to new-role",
                    role.getName().equals("new-role") && sids.contains("alice"));
        }
    }

    /**
     * Util method for executing a terminal command and getting the output
     * @param cmd Command to be run
     * @return String containing the terminal output
     * @throws java.io.IOException
     */
    private static String execCmd(String cmd) throws java.io.IOException {
        java.util.Scanner s = new java.util.Scanner(Runtime.getRuntime().exec(cmd).getInputStream()).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
