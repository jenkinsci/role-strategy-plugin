package org.jenkinsci.plugins.rolestrategy;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.recipes.LocalData;

public class UserGroupSeparationTest {
  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();

  private User user;
  private User group;
  private User either;
  private User userWithGroup;
  private User groupAsUser;
  private User eitherGroup;

  @Before
  public void setup() {
    DummySecurityRealm securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    user = User.getById("user", true);
    group = User.getById("group", true);
    userWithGroup = User.getById("userWithGroup", true);
    groupAsUser = User.getById("groupAsUser", true);
    either = User.getById("either", true);
    eitherGroup = User.getById("eitherGroup", true);
    securityRealm.addGroups("userWithGroup", "group");
    securityRealm.addGroups("groupAsUser", "user");
    securityRealm.addGroups("eitherGroup", "either");
  }

  /**
   * A user that matches an entry of type user should be granted access
   */
  @LocalData
  @Test
  public void user_matches_user_has_access() {
    try (ACLContext c = ACL.as(User.getById("user", false))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that is in a group that matches an entry of type group should be granted access.
   */
  @LocalData
  @Test
  public void usergroup_matches_group_has_acess() {
    try (ACLContext c = ACL.as(User.getById("userWithGroup", false))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that has a name matching a group should not have access
   */
  @LocalData
  @Test
  public void user_matches_group_has_no_access() {
    try (ACLContext c = ACL.as(User.getById("group", false))) {
      assertFalse(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that is in a group that matches an entry of type user should not have access
   */
  @LocalData
  @Test
  public void group_matches_user_has_no_acess() {
    try (ACLContext c = ACL.as(User.getById("groupAsUser", false))) {
      assertFalse(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that matches an entry of type either should have access
   */
  @LocalData
  @Test
  public void user_matches_either_has_access() {
    try (ACLContext c = ACL.as(User.getById("either", false))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that is in a group matches an entry of type either should have access
   */
  @LocalData
  @Test
  public void group_matches_either_has_access() {
    try (ACLContext c = ACL.as(User.getById("eitherGroup", false))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }
}
