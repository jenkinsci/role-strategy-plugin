package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.security.Permission;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RoleMapCachingTest {

  private static final PermissionEntry ALICE = new PermissionEntry(AuthorizationType.USER, "alice");

  @Test
  void disabledPermissionStopsCountingImmediately(JenkinsRule j) {
    Permission permission = RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN;
    boolean enabled = permission.getEnabled();
    try {
      RoleMap map = new RoleMap(new TreeMap<>(Map.of(
          new Role("admins", Pattern.compile(".*"), Set.of(permission), null, null), Set.of(ALICE))));
      permission.setEnabled(true);
      assertTrue(map.hasPermission(ALICE, permission, RoleType.Global, null));

      permission.setEnabled(false);
      assertFalse(map.hasPermission(ALICE, permission, RoleType.Global, null));
    } finally {
      permission.setEnabled(enabled);
    }
  }

  @Test
  void matchingCacheIsClearedEveryHour(JenkinsRule j) {
    Role role = new Role("builders", Pattern.compile("team/.*"), Set.of(Permission.READ), null, null);
    RoleMap map = new RoleMap(new TreeMap<>(Map.of(role, Set.of())));
    RoleMap matching = map.newMatchingRoleMap("team/job");
    assertSame(matching, map.newMatchingRoleMap("team/job"));

    // Changing the live sid set bypasses RoleMap, so only the hourly clear picks it up.
    map.getGrantedRolesEntries().get(role).add(ALICE);
    assertFalse(map.newMatchingRoleMap("team/job").getGrantedRolesEntries().get(role).contains(ALICE));

    new RoleMap.MatchingCacheExpiry().doRun();
    RoleMap refreshed = map.newMatchingRoleMap("team/job");
    assertNotSame(matching, refreshed);
    assertTrue(refreshed.getGrantedRolesEntries().get(role).contains(ALICE));
  }
}
