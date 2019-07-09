package io.jenkins.plugins.rolestrategy;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.collect.ImmutableSet;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import io.jenkins.plugins.rolestrategy.roles.FolderRole;
import io.jenkins.plugins.rolestrategy.roles.GlobalRole;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.HashSet;

import static io.jenkins.plugins.rolestrategy.misc.PermissionWrapper.wrapPermissions;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class FolderBasedAuthorizationStrategyTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private Folder root;
    private Folder child1;
    private Folder child2;
    private Folder child3;

    private FreeStyleProject job1;
    private FreeStyleProject job2;

    private User admin;
    private User user1;
    private User user2;

    @Before
    public void setUp() throws Exception {
        Jenkins jenkins = jenkinsRule.jenkins;
        jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());

        FolderBasedAuthorizationStrategy strategy = new FolderBasedAuthorizationStrategy(Collections.emptySet(),
                Collections.emptySet());
        jenkins.setAuthorizationStrategy(strategy);

        final String adminRoleName = "adminRole";
        final String overallReadRoleName = "overallRead";

        strategy.addGlobalRole(new GlobalRole(adminRoleName,
                wrapPermissions(FolderAuthorizationStrategyManagementLink.getSafePermissions(
                        new HashSet<>(PermissionGroup.getAll())))));

        strategy.assignSidToGlobalRole(adminRoleName, "admin");

        strategy.addGlobalRole(new GlobalRole(overallReadRoleName, wrapPermissions(Permission.READ)));
        strategy.assignSidToGlobalRole(overallReadRoleName, "authenticated");

        strategy.addFolderRole(new FolderRole("folderRole1", wrapPermissions(Item.READ),
                ImmutableSet.of("root")));
        strategy.assignSidToFolderRole("folderRole1", "user1");
        strategy.assignSidToFolderRole("folderRole1", "user2");

        strategy.addFolderRole(new FolderRole("folderRole2", wrapPermissions(Item.CONFIGURE, Item.DELETE),
                ImmutableSet.of("root/child1")));
        strategy.assignSidToFolderRole("folderRole2", "user2");

        /*
         * Folder hierarchy for the test
         *
         *             root
         *             /  \
         *        child1   child2
         *          /        \
         *        child3     job1
         *         /
         *        job2
         */

        root = jenkins.createProject(Folder.class, "root");
        child1 = root.createProject(Folder.class, "child1");
        child2 = root.createProject(Folder.class, "child2");
        child3 = child1.createProject(Folder.class, "child3");

        job1 = child2.createProject(FreeStyleProject.class, "job1");
        job2 = child3.createProject(FreeStyleProject.class, "job2");

        admin = User.get("admin");
        user1 = User.get("user1");
        user2 = User.get("user2");
    }

    @Test
    public void permissionTest() {
        Jenkins jenkins = jenkinsRule.jenkins;

        try (ACLContext ignored = ACL.as(admin)) {
            assertTrue(jenkins.hasPermission(Jenkins.ADMINISTER));
            assertTrue(child3.hasPermission(Item.CONFIGURE));
            assertTrue(job1.hasPermission(Item.READ));
            assertTrue(job2.hasPermission(Item.CREATE));
        }

        try (ACLContext ignored = ACL.as(user1)) {
            assertTrue(jenkins.hasPermission(Permission.READ));
            assertTrue(root.hasPermission(Item.READ));
            assertTrue(job1.hasPermission(Item.READ));
            assertTrue(job2.hasPermission(Item.READ));

            assertFalse(job1.hasPermission(Item.CREATE));
            assertFalse(job1.hasPermission(Item.DELETE));
            assertFalse(job1.hasPermission(Item.CONFIGURE));
            assertFalse(job2.hasPermission(Item.CREATE));
            assertFalse(job2.hasPermission(Item.CONFIGURE));
        }

        try (ACLContext ignored = ACL.as(user2)) {
            assertTrue(jenkins.hasPermission(Permission.READ));
            assertTrue(child2.hasPermission(Item.READ));
            assertTrue(child1.hasPermission(Item.READ));
            assertTrue(job2.hasPermission(Item.CONFIGURE));
            assertFalse(job1.hasPermission(Item.CONFIGURE));
        }
    }
}
