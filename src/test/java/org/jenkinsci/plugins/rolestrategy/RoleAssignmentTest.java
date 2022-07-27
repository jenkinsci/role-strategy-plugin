package org.jenkinsci.plugins.rolestrategy;

import static org.junit.Assert.assertTrue;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class RoleAssignmentTest {

  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();

  @Before
  public void initSecurityRealm() {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
  }

  @LocalData
  @Test
  public void testRoleAssignment() {
    try (ACLContext c = ACL.as(User.getById("alice", true))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

}
