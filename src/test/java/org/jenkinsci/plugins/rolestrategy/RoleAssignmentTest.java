package org.jenkinsci.plugins.rolestrategy;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import static org.junit.Assert.assertTrue;

public class RoleAssignmentTest {

    @Rule 
    public JenkinsRule j = new JenkinsRule();
    
    @Before
    public void initSecurityRealm() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }
    
    @LocalData
    @Test public void testRoleAssignment() {
        try (ACLContext c = ACL.as(User.getById("alice", true))) {
            assertTrue(j.jenkins.hasPermission(Permission.READ));
        } 
    }

}
