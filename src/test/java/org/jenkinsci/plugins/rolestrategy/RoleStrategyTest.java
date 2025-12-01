package org.jenkinsci.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class RoleStrategyTest {

  private JenkinsRule jenkinsRule;

  @BeforeEach
  void initSecurityRealm(JenkinsRule jenkinsRule) {
    this.jenkinsRule = jenkinsRule;
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
  }

  @LocalData
  @Test
  void testRoleAssignment() {
    RoleMap.FORCE_CASE_SENSITIVE = false;
    try (ACLContext c = ACL.as(User.getById("alice", true))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

  @LocalData
  @Test
  void testRoleAssignmentCaseInsensitiveNoMatchSucceeds() {
    RoleMap.FORCE_CASE_SENSITIVE = false;
    try (ACLContext c = ACL.as(User.getById("Alice", true))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

  @LocalData
  @Test
  void testRoleAssignmentCaseSensitiveMatch() {
    RoleMap.FORCE_CASE_SENSITIVE = true;
    try (ACLContext c = ACL.as(User.getById("alice", true))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

  @LocalData
  @Test
  void testRoleAssignmentCaseSensitiveNoMatchFails() {
    RoleMap.FORCE_CASE_SENSITIVE = true;
    try (ACLContext c = ACL.as(User.getById("Alice", true))) {
      assertFalse(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

}
