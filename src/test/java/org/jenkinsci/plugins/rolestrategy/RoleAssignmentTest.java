package org.jenkinsci.plugins.rolestrategy;

import hudson.model.User;
import hudson.security.Permission;
import junit.framework.Assert;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class RoleAssignmentTest {

    @Rule 
    public JenkinsRule j = new JenkinsRule();
    
    @Before
    public void initSecurityRealm() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }
    
    @LocalData
    @Test public void testRoleAssignment() throws Exception {
        SecurityContext seccon = SecurityContextHolder.getContext();
        Authentication orig = seccon.getAuthentication();
        seccon.setAuthentication(User.get("alice").impersonate());
        try {
            Assert.assertTrue(j.jenkins.hasPermission(Permission.READ));
        } finally {
            seccon.setAuthentication(orig);
        }
    }

}
