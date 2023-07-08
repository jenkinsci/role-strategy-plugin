package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.junit.Assert.assertFalse;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.HudsonPrivateSecurityRealm;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GrantingDisabledPermissionTest {

  @Rule
  public JenkinsRule r = new JenkinsRule();

  @Test
  public void grantDisabledPermissionTest() throws Exception {
    HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(true, false, null);
    realm.createAccount("admin", "admin");
    realm.createAccount("alice", "alice");
    r.jenkins.setSecurityRealm(realm);

    RoleMap roleMap = new RoleMap();
    Role adminRole = new Role("admin-role", new HashSet<>(Collections.singletonList(Jenkins.ADMINISTER)));
    roleMap.addRole(adminRole);
    Role manage = new Role("manage-role", new HashSet<>(Collections.singletonList(Jenkins.MANAGE)));
    roleMap.addRole(manage);
    roleMap.assignRole(adminRole, "admin");
    roleMap.assignRole(manage, "alice");

    Map<String, RoleMap> constructorArg = new HashMap<>();
    constructorArg.put("globalRoles", roleMap);

    r.jenkins.setAuthorizationStrategy(new RoleBasedAuthorizationStrategy(constructorArg));

    try (ACLContext ctx = ACL.as2(User.get("alice").impersonate2())) {
      assertFalse(Jenkins.get().hasPermission(Jenkins.MANAGE));
    }
  }
}
