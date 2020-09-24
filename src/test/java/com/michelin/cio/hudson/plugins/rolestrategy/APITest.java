package com.michelin.cio.hudson.plugins.rolestrategy;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.Permission;
import org.acegisecurity.Authentication;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Logger;

/**
 * Test suite for {@link RoleBasedAuthorizationStrategy} Web API Methods
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
    }

    @Test
    public void testAddRole() throws IOException {
        addRole("projectRoles", "new-role", "hudson.model.Item.Configure",
                false, "test-folder.*");
        assertTrue("Checking if the role is found.", checkIfRoleExists("new-role"));
    }

    @Test
    public void testGetRole() throws IOException {
        String roleString = getRole(RoleType.Global.getStringType(), "adminRole");
        LOGGER.info("response: " + roleString);
        assertTrue(roleString.length() > 2);
    }

    @Test
    public void testAssignRole() throws IOException {
        // Verifying that alice does not have permission by default
        MockFolder testFolder = jenkinsRule.createFolder("test-folder");
        Authentication auth = User.getById("alice", true).impersonate();
        assertFalse(testFolder.hasPermission(auth, Permission.CONFIGURE));
        // Testing creating role
        addRole(RoleType.Project.getStringType(), "new-role", "hudson.model.Item.Configure",
                false, "test-folder.*");
        assertTrue("Checking if the role is found.", checkIfRoleExists("new-role"));
        // Testing assign role
        assignRole(RoleType.Project.getStringType(), "new-role", "alice");
    }

    @Test
    public void testUnassignRole() throws IOException {
        testAssignRole();  // assign alice to a role named "new-role"
        String url = jenkinsRule.jenkins.getRootUrl() + "/role-strategy/strategy/unassignRole";
        String curlCmd = url + " --data type=" + RoleType.Project.getStringType() + "&roleName=new-role&sid=alice";
        String output = execCmd(curlCmd);
        LOGGER.info("UnassignRole output: " + output);
        // Verifying that alice no longer has permissions
        Authentication auth = User.getById("alice", false).impersonate();
        Item testFolder = jenkinsRule.jenkins.getItemByFullName("test-folder");
        assertFalse(testFolder.hasPermission(auth, Permission.CONFIGURE));
        // TODO - check on the backend whether the role still exists and is unassigned
    }

    /**
     * Method to check if a role exists via the role strategy instead of via web API method
     * @param roleName Name of the role
     * @return true if role exists
     */
    private boolean checkIfRoleExists(String roleName) {
        RoleBasedAuthorizationStrategy roleBasedAuthorizationStrategy = RoleBasedAuthorizationStrategy.getInstance();
        assertNotNull(roleBasedAuthorizationStrategy);
        SortedMap<Role, Set<String>> grantedRoles = roleBasedAuthorizationStrategy.getGrantedRoles(RoleType.Project);
        boolean foundRole = false;
        for (Map.Entry<Role, Set<String>> entry : grantedRoles.entrySet()) {
            Role role = entry.getKey();
            if (role.getName().equals(roleName)) {
                foundRole = true;
            }
        }
        return foundRole;
    }

    /**
     * Assigns a user to a role via the web API
     * @param roleType Type of the role - see {@link RoleType}
     * @param roleName Name of the role
     * @param sid User id of jenkins user
     */
    private void assignRole(String roleType, String roleName, String sid) throws IOException {
        String url = jenkinsRule.jenkins.getRootUrl() + "/role-strategy/strategy/assignRole";
        String curlCmd = "curl -u adminUser:adminUser -X POST " + url + "  --data type="+ roleType +
                "&roleName=" + roleName;
        String output = execCmd(curlCmd);
        LOGGER.info("assignRole output: " + output);
    }

    /**
     * Overloaded method without the pattern argument. See {@link APITest#addRole(String, String, String, boolean, String)}
     */
    private void addRole(String roleType, String roleName, String permissions, boolean overwrite) throws IOException {
        addRole(roleType, roleName, permissions, overwrite, null);
    }

    /**
     * Adds a Jenkins role using the web API method
     * @param roleType Type of Jenkins role - see {@link RoleType}
     * @param roleName Name of role
     * @param permissions Permissions of the role
     * @param overwrite Whether to overwrite an existing role
     * @param pattern Any pattern
     * @throws IOException
     */
    private void addRole(String roleType, String roleName, String permissions, boolean overwrite, String pattern)
            throws IOException {
        String url = jenkinsRule.jenkins.getRootUrl() + "/role-strategy/strategy/addRole";
        String curlCmd;

        if (pattern != null) {
            curlCmd = "curl -u adminUser:adminUser -X POST " + url + "  --data type="+ roleType + "&roleName=" + roleName
                    + "&permissionIds=" + permissions + "&overwrite=" + overwrite + "&pattern=" + pattern;
        } else {
            curlCmd = "curl -u adminUser:adminUser -X POST " + jenkinsRule.jenkins.getRootUrl() +
                    "/role-strategy/strategy/assignRole" + "  --data type="+ roleType + "&roleName=" + roleName
                    + "&permissionIds=" + permissions + "&overwrite=" + overwrite;
        }
        String output = execCmd(curlCmd);
        LOGGER.info("Output of addRole: " + output);
    }

    /**
     * Gets a Jenkins role via the Web API method
     * @param roleType Type of Jenkins role - see {@link RoleType}
     * @param roleName Name of Jenkins role
     * @return Json string containing the role
     * @throws IOException
     */
    private String getRole(String roleType, String roleName) throws IOException {
        String curlCmd = "curl -u adminUser:adminUser -X GET " + jenkinsRule.jenkins.getRootUrl() + "/role-strategy/strategy/getRole?"
                + "type=" + roleType + "&roleName=" + roleName;
        String output = execCmd(curlCmd);
        return output;
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
