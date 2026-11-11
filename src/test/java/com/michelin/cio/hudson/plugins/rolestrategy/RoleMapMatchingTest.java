package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RoleMapMatchingTest {

  private static Role folderRole(String name, String... folders) {
    return Role.forFolders(name, List.of(folders), Set.of(Permission.READ), null, null);
  }

  private static Role patternRole(String name, String pattern) {
    return new Role(name, Pattern.compile(pattern), Set.of(Permission.READ), null, null);
  }

  private static Set<String> matching(RoleMap map, String item) {
    return map.newMatchingRoleMap(item).getRoles().stream().map(Role::getName).collect(Collectors.toSet());
  }

  @Test
  void matchesFolderAndPatternRoles() {
    SortedMap<Role, Set<PermissionEntry>> roles = new TreeMap<>();
    roles.put(folderRole("special", "Team/Special"), Set.of(new PermissionEntry(AuthorizationType.USER, "alice")));
    roles.put(folderRole("team", "Team", "Other"), Set.of());
    roles.put(patternRole("builds", ".*/build"), Set.of());
    RoleMap map = new RoleMap(roles);

    assertThat(matching(map, "Team/Special/build"), containsInAnyOrder("special", "team", "builds"));
    assertThat(matching(map, "Team/Special"), containsInAnyOrder("special", "team"));
    assertThat(matching(map, "Team/Special-old"), containsInAnyOrder("team"));
    assertThat(matching(map, "Other"), containsInAnyOrder("team"));
    assertThat(matching(map, "Unrelated"), empty());
    assertEquals(Set.of(new PermissionEntry(AuthorizationType.USER, "alice")),
        map.newMatchingRoleMap("Team/Special/x").getGrantedRolesEntries().get(folderRole("special", "Team/Special")));
  }

  @Test
  void addingAndRemovingRolesChangesWhatMatches() {
    RoleMap map = new RoleMap(new TreeMap<>());
    Role a = folderRole("a", "A");
    Role b = folderRole("b", "B");
    map.addRole(a);
    assertThat(matching(map, "A/x"), containsInAnyOrder("a"));
    assertThat(matching(map, "B/y"), empty());

    map.addRole(b, Set.of(new PermissionEntry(AuthorizationType.USER, "bob")));
    assertThat(matching(map, "B/y"), containsInAnyOrder("b"));

    map.removeRole(a);
    assertThat(matching(map, "A/x"), empty());
    assertThat(matching(map, "B/y"), containsInAnyOrder("b"));

    map.removeRole(b);
    assertThat(matching(map, "B/y"), empty());
  }

  /**
   * The indexed lookup must select exactly the roles whose own {@link Role#matches(String)} accepts the item, for
   * random mixes of folder and pattern roles and random item names.
   */
  @Test
  void indexedLookupSelectsExactlyTheMatchingRoles() {
    String[] parts = {"a", "b", "a-b", "a.b", "", " "};
    Random random = new Random(11);
    for (int round = 0; round < 300; round++) {
      SortedMap<Role, Set<PermissionEntry>> roles = new TreeMap<>();
      int count = 1 + random.nextInt(30);
      for (int i = 0; i < count; i++) {
        if (random.nextInt(4) == 0) {
          roles.put(patternRole("p" + i, randomPath(random, parts, 1 + random.nextInt(2)) + ".*"), Set.of());
        } else {
          List<String> folders = new ArrayList<>();
          for (int k = 0, n = 1 + random.nextInt(10); k < n; k++) {
            folders.add(randomPath(random, parts, 1 + random.nextInt(3)));
          }
          try {
            roles.put(folderRole("f" + i, folders.toArray(new String[0])), Set.of());
          } catch (IllegalArgumentException e) {
            // every folder normalized away
          }
        }
      }
      RoleMap map = new RoleMap(roles);
      for (int n = 0; n < 40; n++) {
        String item = randomPath(random, parts, 1 + random.nextInt(5));
        Set<String> expected = roles.keySet().stream().filter(r -> r.matches(item)).map(Role::getName)
            .collect(Collectors.toSet());
        assertEquals(expected, matching(map, item), () -> roles.keySet() + " vs " + item);
      }
    }
  }

  private static String randomPath(Random random, String[] parts, int segments) {
    StringBuilder path = new StringBuilder();
    for (int i = 0; i < segments; i++) {
      if (i > 0) {
        path.append('/');
      }
      path.append(parts[random.nextInt(parts.length)]);
    }
    return path.toString();
  }
}
