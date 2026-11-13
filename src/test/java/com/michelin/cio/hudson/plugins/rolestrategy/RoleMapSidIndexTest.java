package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.Item;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.Permission;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import jenkins.model.IdStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@link RoleMap#hasPermission} looks sids up in an index for the id strategies in Jenkins core and compares them role
 * by role otherwise. Both must give the answers of the matching rules checked here independently.
 */
@WithJenkins
class RoleMapSidIndexTest {

  private static final String[] SIDS = {
    "alice", "Alice", "ALICE", "bob", "devs", "Devs", "DEVS", "x@example.com", "x@Example.COM", "X@example.com",
    "İnci", "inci", "i̇nci", "straße", "STRASSE", "authenticated", "",
  };

  private static final Permission[] PERMISSIONS = {Item.READ, Item.BUILD, Item.CONFIGURE, Item.DISCOVER, Item.CANCEL};

  private JenkinsRule j;
  private boolean forceCaseSensitive;

  @BeforeEach
  void setUp(JenkinsRule j) {
    this.j = j;
    forceCaseSensitive = RoleMap.FORCE_CASE_SENSITIVE;
  }

  @AfterEach
  void restore() {
    RoleMap.FORCE_CASE_SENSITIVE = forceCaseSensitive;
  }

  @Test
  void caseInsensitiveRealm() {
    compareWithMatchingRules(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive(), false, 1);
  }

  @Test
  void caseSensitiveRealm() {
    compareWithMatchingRules(new IdStrategy.CaseSensitive(), new IdStrategy.CaseSensitive(), false, 2);
  }

  @Test
  void emailAddressUsersWithCaseInsensitiveGroups() {
    compareWithMatchingRules(new IdStrategy.CaseSensitiveEmailAddress(), new IdStrategy.CaseInsensitive(), false, 3);
  }

  @Test
  void forcedCaseSensitivity() {
    compareWithMatchingRules(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive(), true, 4);
  }

  /** A strategy outside core does not promise that equal ids have equal keys, so sids are compared role by role. */
  @Test
  void customStrategy() {
    compareWithMatchingRules(new LengthIdStrategy(), new LengthIdStrategy(), false, 5);
  }

  @Test
  void assigningAndRemovingSidsChangesTheAnswer() {
    j.jenkins.setSecurityRealm(new Realm(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive()));
    Role builders = role("builders", Item.BUILD);
    RoleMap map = new RoleMap(new TreeMap<>(Map.of(builders, Set.of())));
    PermissionEntry alice = new PermissionEntry(AuthorizationType.USER, "alice");
    assertFalse(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));

    map.assignRole(builders, alice);
    assertTrue(map.hasPermission(new PermissionEntry(AuthorizationType.USER, "ALICE"), Item.BUILD, RoleType.Project, null));

    map.unAssignRole(builders, alice);
    assertFalse(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));

    map.assignRole(builders, new PermissionEntry(AuthorizationType.EITHER, "alice"));
    assertTrue(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));
    map.deleteSids(new PermissionEntry(AuthorizationType.EITHER, "alice"));
    assertFalse(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));

    map.removeRole(builders);
    map.addRole(role("other", Item.BUILD), Set.of(alice));
    assertTrue(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));
  }

  @Test
  void templatePermissionChangesApplyImmediately() {
    j.jenkins.setSecurityRealm(new Realm(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive()));
    Role builders = new Role("builders", Pattern.compile(".*"), Set.of(Item.BUILD), null, "readers");
    PermissionEntry alice = new PermissionEntry(AuthorizationType.USER, "alice");
    RoleMap map = new RoleMap(new TreeMap<>(Map.of(builders, Set.of(alice))));
    assertTrue(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));

    builders.refreshPermissionsFromTemplate(new PermissionTemplate(Set.of(Item.READ), "readers"));
    assertFalse(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));
  }

  @Test
  void changingTheRealmsStrategyRebuildsTheIndex() {
    j.jenkins.setSecurityRealm(new Realm(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive()));
    RoleMap map = new RoleMap(new TreeMap<>(Map.of(role("builders", Item.BUILD),
        Set.of(new PermissionEntry(AuthorizationType.USER, "alice")))));
    PermissionEntry upper = new PermissionEntry(AuthorizationType.USER, "ALICE");
    assertTrue(map.hasPermission(upper, Item.BUILD, RoleType.Project, null));

    j.jenkins.setSecurityRealm(new Realm(new IdStrategy.CaseSensitive(), new IdStrategy.CaseSensitive()));
    assertFalse(map.hasPermission(upper, Item.BUILD, RoleType.Project, null));
  }

  /** Changes to the live sid sets bypass RoleMap, so like the matching cache the index picks them up every hour. */
  @Test
  void indexIsRebuiltEveryHour() {
    j.jenkins.setSecurityRealm(new Realm(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive()));
    Role builders = role("builders", Item.BUILD);
    RoleMap map = new RoleMap(new TreeMap<>(Map.of(builders, Set.of())));
    PermissionEntry alice = new PermissionEntry(AuthorizationType.USER, "alice");
    assertFalse(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));

    map.getGrantedRolesEntries().get(builders).add(alice);
    assertFalse(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));

    new RoleMap.MatchingCacheExpiry().doRun();
    assertTrue(map.hasPermission(alice, Item.BUILD, RoleType.Project, null));
  }

  private void compareWithMatchingRules(IdStrategy users, IdStrategy groups, boolean caseSensitive, long seed) {
    j.jenkins.setSecurityRealm(new Realm(users, groups));
    RoleMap.FORCE_CASE_SENSITIVE = caseSensitive;
    Random random = new Random(seed);
    AuthorizationType[] types = AuthorizationType.values();
    for (int round = 0; round < 200; round++) {
      SortedMap<Role, Set<PermissionEntry>> roles = new TreeMap<>();
      for (int r = 0, count = 1 + random.nextInt(15); r < count; r++) {
        Set<Permission> permissions = new HashSet<>();
        for (int p = 0, n = 1 + random.nextInt(3); p < n; p++) {
          permissions.add(PERMISSIONS[random.nextInt(PERMISSIONS.length)]);
        }
        Set<PermissionEntry> entries = new HashSet<>();
        for (int e = 0, n = random.nextInt(5); e < n; e++) {
          entries.add(new PermissionEntry(types[random.nextInt(types.length)], SIDS[random.nextInt(SIDS.length)]));
        }
        String name = random.nextInt(8) == 0 ? "@Stub" + r : "role" + r;
        roles.put(new Role(name, Pattern.compile(".*"), permissions, null, null), entries);
      }
      RoleMap map = new RoleMap(roles);
      for (int q = 0; q < 60; q++) {
        PermissionEntry sid = new PermissionEntry(types[random.nextInt(types.length)], SIDS[random.nextInt(SIDS.length)]);
        Permission permission = PERMISSIONS[random.nextInt(PERMISSIONS.length)];
        boolean expected = matchingRules(roles, sid, permission, users, groups, caseSensitive);
        assertEquals(expected, map.hasPermission(sid, permission, RoleType.Project, null),
            () -> sid + " " + permission.getId() + " in " + roles);
      }
    }
  }

  /** The rules role-strategy has always matched sids by, applied to every role. */
  private static boolean matchingRules(SortedMap<Role, Set<PermissionEntry>> roles, PermissionEntry sid,
      Permission permission, IdStrategy users, IdStrategy groups, boolean caseSensitive) {
    Set<Permission> implying = new HashSet<>();
    for (Permission p = permission; p != null; p = p.impliedBy) {
      if (p.getEnabled()) {
        implying.add(p);
      }
    }
    boolean principal = sid.getType() == AuthorizationType.USER;
    IdStrategy strategy = principal ? users : groups;
    for (var granted : roles.entrySet()) {
      Role role = granted.getKey();
      // The stub macro is never applicable, so macro roles grant nothing here.
      if (role.getName().startsWith("@") || !role.hasAnyPermission(implying)) {
        continue;
      }
      for (PermissionEntry entry : granted.getValue()) {
        boolean exact = entry.equals(sid) || entry.equals(new PermissionEntry(AuthorizationType.EITHER, sid.getSid()));
        boolean applicable = entry.getType() == AuthorizationType.EITHER
            || entry.getType() == (principal ? AuthorizationType.USER : AuthorizationType.GROUP);
        if (exact || !caseSensitive && applicable && strategy.equals(entry.getSid(), sid.getSid())) {
          return true;
        }
      }
    }
    return false;
  }

  private static Role role(String name, Permission... permissions) {
    return new Role(name, Pattern.compile(".*"), Set.of(permissions), null, null);
  }

  /** Treats ids of the same length as equal while keeping them as distinct keys. */
  private static final class LengthIdStrategy extends IdStrategy {
    @Override
    public int compare(String id1, String id2) {
      return Integer.compare(id1.length(), id2.length());
    }
  }

  private static final class Realm extends AbstractPasswordBasedSecurityRealm {
    private final IdStrategy users;
    private final IdStrategy groups;

    Realm(IdStrategy users, IdStrategy groups) {
      this.users = users;
      this.groups = groups;
    }

    @Override
    public IdStrategy getUserIdStrategy() {
      return users;
    }

    @Override
    public IdStrategy getGroupIdStrategy() {
      return groups;
    }

    @Override
    protected UserDetails authenticate2(String username, String password) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UserDetails loadUserByUsername2(String username) {
      throw new UnsupportedOperationException();
    }

    @Override
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) {
      throw new UnsupportedOperationException();
    }
  }
}
