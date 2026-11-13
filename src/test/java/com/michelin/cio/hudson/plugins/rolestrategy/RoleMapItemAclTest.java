package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.Permission;
import hudson.security.SidACL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@link RoleMap#getItemAcl} looks up the roles assigned to a sid and tests only those against the item. It must
 * answer exactly as the ACL of the map of roles covering the item did.
 */
@WithJenkins
class RoleMapItemAclTest {

  private static final String[] SIDS = {"alice", "Alice", "bob", "BOB", "devs", "Devs", "ops", "authenticated", "x@Example.com"};

  private static final String[] NAMES = {
    "team1", "team1/job", "team1/sub/job", "team10", "team10/job", "Team1/job", "team2/job", "other", "team1x", "sub",
  };

  private static final Permission[] PERMISSIONS = {Item.READ, Item.BUILD, Item.CONFIGURE, Item.DISCOVER};

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
    compareWithMatchingRoleMaps(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive(), false, 1);
  }

  @Test
  void caseSensitiveRealm() {
    compareWithMatchingRoleMaps(new IdStrategy.CaseSensitive(), new IdStrategy.CaseSensitive(), false, 2);
  }

  @Test
  void emailAddressUsersWithCaseInsensitiveGroups() {
    compareWithMatchingRoleMaps(new IdStrategy.CaseSensitiveEmailAddress(), new IdStrategy.CaseInsensitive(), false, 3);
  }

  @Test
  void forcedCaseSensitivity() {
    compareWithMatchingRoleMaps(new IdStrategy.CaseInsensitive(), new IdStrategy.CaseInsensitive(), true, 4);
  }

  /** Strategies outside core are not indexed, so checks go through the map of roles covering the item. */
  @Test
  void customStrategy() {
    compareWithMatchingRoleMaps(new LengthIdStrategy(), new LengthIdStrategy(), false, 5);
  }

  private void compareWithMatchingRoleMaps(IdStrategy users, IdStrategy groups, boolean caseSensitive, long seed) {
    j.jenkins.setSecurityRealm(new Realm(users, groups));
    RoleMap.FORCE_CASE_SENSITIVE = caseSensitive;
    Random random = new Random(seed);
    AuthorizationType[] types = AuthorizationType.values();
    SidACL root = new RoleMap().getACL(RoleType.Global, null);
    int granted = 0;
    for (int round = 0; round < 100; round++) {
      SortedMap<Role, Set<PermissionEntry>> roles = new TreeMap<>();
      List<Role> created = new ArrayList<>();
      for (int r = 0, count = 1 + random.nextInt(12); r < count; r++) {
        Set<Permission> permissions = new HashSet<>();
        for (int p = 0, n = 1 + random.nextInt(2); p < n; p++) {
          permissions.add(PERMISSIONS[random.nextInt(PERMISSIONS.length)]);
        }
        Role role = randomRole(random, r, permissions);
        Set<PermissionEntry> entries = new HashSet<>();
        for (int e = 0, n = random.nextInt(4); e < n; e++) {
          entries.add(new PermissionEntry(types[random.nextInt(types.length)], SIDS[random.nextInt(SIDS.length)]));
        }
        roles.put(role, entries);
        created.add(role);
      }
      RoleMap map = new RoleMap(roles);
      for (int step = 0; step < 3; step++) {
        for (int q = 0; q < 40; q++) {
          String name = NAMES[random.nextInt(NAMES.length)];
          Authentication user = user(random);
          Permission permission = PERMISSIONS[random.nextInt(PERMISSIONS.length)];
          ACL expected = map.newMatchingRoleMap(name).getACL(RoleType.Project, null).newInheritingACL(root);
          ACL actual = map.getItemAcl(RoleType.Project, null, name, root);
          boolean answer = expected.hasPermission2(user, permission);
          granted += answer ? 1 : 0;
          assertEquals(answer, actual.hasPermission2(user, permission),
              () -> user.getName() + " " + user.getAuthorities() + " " + permission.getId() + " on " + name + " in "
                  + map.getGrantedRolesEntries());
        }
        // Answers must follow changes to the sids assigned.
        Role role = created.get(random.nextInt(created.size()));
        PermissionEntry entry = new PermissionEntry(types[random.nextInt(types.length)], SIDS[random.nextInt(SIDS.length)]);
        if (random.nextBoolean()) {
          map.assignRole(role, entry);
        } else {
          map.deleteSids(entry);
        }
      }
    }
    // Most checks are denied; enough must be granted for the comparison to mean something.
    int total = granted;
    assertTrue(granted > 1000, () -> "only " + total + " of 12000 checks granted");
  }

  private static Role randomRole(Random random, int index, Set<Permission> permissions) {
    String name = random.nextInt(8) == 0 ? "@Stub" + index : "role" + index;
    return switch (random.nextInt(4)) {
      case 0 -> new Role(name, Pattern.compile("team1"), permissions, null, null);
      case 1 -> new Role(name, Pattern.compile("(team1/sub|team10|other)(/.*)?"), permissions, null, null);
      case 2 -> new Role(name, Pattern.compile("team1(/.*)?"), permissions, null, null);
      default -> new Role(name, Pattern.compile(random.nextBoolean() ? ".*" : "(?i)team[0-9]+/.*"), permissions, null, null);
    };
  }

  private static Authentication user(Random random) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (int g = 0, n = random.nextInt(3); g < n; g++) {
      authorities.add(new SimpleGrantedAuthority(SIDS[random.nextInt(SIDS.length)]));
    }
    return new UsernamePasswordAuthenticationToken(SIDS[random.nextInt(SIDS.length)], "", authorities);
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
