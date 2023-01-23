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
  private User userWithGroup;
  private User groupAsUser;

  @Before
  public void setup() {
    DummySecurityRealm securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    user = User.getById("user", true);
    group = User.getById("group", true);
    userWithGroup = User.getById("userWithGroup", true);
    groupAsUser = User.getById("groupAsUser", true);
    securityRealm.addGroups("userWithGroup", "group");
    securityRealm.addGroups("groupAsUser", "user");
  }

  /**
   * A user that matches an entry of type user should be granted access
   */
  @LocalData
  @Test
  public void userAsUserHasAccess() {
    try (ACLContext c = ACL.as(User.getById("user", false))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that is in a group that matches an entry of type group should be granted access.
   */
  @LocalData
  @Test
  public void userInGroupHasAccess() {
    try (ACLContext c = ACL.as(User.getById("userWithGroup", false))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that has a name matching a group should not have access
   */
  @LocalData
  @Test
  public void groupAsUserHasNoAccess() {
    try (ACLContext c = ACL.as(User.getById("group", false))) {
      assertFalse(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }

  /**
   * A user that is in a group that matches an entry of type user should not have access
   */
  @LocalData
  @Test
  public void user_group_with_user_HasNoAccess() {
    try (ACLContext c = ACL.as(User.getById("groupAsUser", false))) {
      assertFalse(jenkinsRule.jenkins.hasPermission(Jenkins.ADMINISTER));
    }
  }
}
