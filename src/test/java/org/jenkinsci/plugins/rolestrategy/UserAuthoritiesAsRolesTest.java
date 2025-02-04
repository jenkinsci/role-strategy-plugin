package org.jenkinsci.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.Permission;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@WithJenkins
class UserAuthoritiesAsRolesTest {

  private JenkinsRule jenkinsRule;

  @BeforeEach
  void enableUserAuthorities(JenkinsRule jenkinsRule) {
    this.jenkinsRule = jenkinsRule;
    Settings.TREAT_USER_AUTHORITIES_AS_ROLES = true;
  }

  @AfterEach
  void disableUserAuthorities() {
    Settings.TREAT_USER_AUTHORITIES_AS_ROLES = false;
  }

  @LocalData
  @Test
  void testRoleAuthority() {
    jenkinsRule.jenkins.setSecurityRealm(new MockSecurityRealm());

    try (ACLContext c = ACL.as(User.getById("alice", true))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

  private static class MockSecurityRealm extends AbstractPasswordBasedSecurityRealm {

    @Override
    protected org.springframework.security.core.userdetails.UserDetails authenticate2(String username, String password)
        throws org.springframework.security.core.AuthenticationException {
      // TODO Auto-generated method stub
      throw new UnsupportedOperationException();
    }

    @Override
    public UserDetails loadUserByUsername2(String username)
        throws org.springframework.security.core.userdetails.UsernameNotFoundException {
      return new org.springframework.security.core.userdetails.User(username, "",
          Collections.singletonList(new SimpleGrantedAuthority("USERS")));
    }

    @Override
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers)
        throws org.springframework.security.core.userdetails.UsernameNotFoundException {
      throw new UnsupportedOperationException();
    }
  }
}
