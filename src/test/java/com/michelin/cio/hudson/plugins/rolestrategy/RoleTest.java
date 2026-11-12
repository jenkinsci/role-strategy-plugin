package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RoleTest {

  @Test
  void testHasPermission() {
    Role role = new Role("name", new HashSet<>(Arrays.asList(Permission.CREATE, Permission.READ, Permission.DELETE)));
    assertTrue(role.hasPermission(Permission.READ));
    assertFalse(role.hasPermission(Permission.WRITE));
  }

  @Test
  void testHasAnyPermissions() {
    Role role = new Role("name", new HashSet<>((Arrays.asList(Permission.READ, Permission.DELETE))));
    assertTrue(role.hasAnyPermission(new HashSet<>(Arrays.asList(Permission.READ, Permission.WRITE))));
    assertFalse(role.hasAnyPermission(new HashSet<>(Arrays.asList(Permission.UPDATE, Permission.WRITE))));
  }

  @Test
  void shouldNotAddNullPermToNewRole() {
    Permission permission = Permission.CREATE;
    Permission nullPerm = null;

    Set<Permission> perms = new HashSet<>(Arrays.asList(permission, nullPerm));

    // Test with modifiable collections
    Role role = new Role("name", perms);
    assertThat("With modifiable set", role.getPermissions(), not(hasItem(nullPerm)));

    // Test with unmodifiable collections
    role = new Role("name", Collections.unmodifiableSet(perms));
    assertThat("With unmodifiable set", role.getPermissions(), not(hasItem(nullPerm)));
  }

  private static Role folderRole(String... folders) {
    return Role.forFolders("folder-role", Arrays.asList(folders), Set.of(Permission.READ), null, null);
  }

  @Test
  void folderRoleCoversTheFolderAndEverythingInsideIt() {
    Role role = folderRole("Team/Special");
    assertTrue(role.isFolderScoped());
    assertTrue(role.matches("Team/Special"));
    assertTrue(role.matches("Team/Special/build"));
    assertTrue(role.matches("Team/Special/Nested/build"));
  }

  @Test
  void folderRoleComparesWholePathSegments() {
    Role role = folderRole("Team/Special");
    assertFalse(role.matches("Team/Special-old"), "sibling sharing the name prefix");
    assertFalse(role.matches("Team/Special-old/build"));
    assertFalse(role.matches("Team"), "parent folder");
    assertFalse(role.matches("Other/Team/Special"), "same name elsewhere");
    assertFalse(role.matches("team/special"), "item names are case sensitive");
  }

  @Test
  void folderRoleTreatsRegexCharactersLiterally() {
    Role role = folderRole("a.b", "c(d)");
    assertTrue(role.matches("a.b/job"));
    assertFalse(role.matches("aXb/job"));
    assertTrue(role.matches("c(d)"));
  }

  @Test
  void folderRoleMatchesAnyOfSeveralFolders() {
    Role role = folderRole("A", "B/C");
    assertTrue(role.matches("A/x"));
    assertTrue(role.matches("B/C/x"));
    assertFalse(role.matches("B/x"));
  }

  @Test
  void derivedPatternMatchesTheSameNames() {
    Role role = folderRole("Team/Special", "a.b");
    Pattern derived = role.getPattern();
    for (String name : List.of("Team/Special", "Team/Special/x", "Team/Special-old", "Team", "a.b/c", "aXb/c", "x/a.b",
        "Team/Special/build\u2028archive", "Team/Special\u2029x", "a.b/\u2028")) {
      assertEquals(role.matches(name), derived.matcher(name).matches(), name);
    }
  }

  @Test
  void derivedPatternMatchesDescendantsWithLineSeparatorsInTheirNames() {
    Role role = folderRole("Team");
    String name = "Team/build\u2028archive";
    assertTrue(role.matches(name));
    assertTrue(role.getPattern().matcher(name).matches());
    assertFalse(role.getPattern().matcher("Team\u2028x").matches());
  }

  @Test
  void roleWithManyFoldersComparesWholePathSegments() {
    Role role = folderRole("A", "B/C", "D", "E", "F", "G", "Team/Special", "x/y/z");
    assertTrue(role.matches("Team/Special"));
    assertTrue(role.matches("Team/Special/Nested/build"));
    assertTrue(role.matches("x/y/z/w"));
    assertTrue(role.matches("B/C"));
    assertFalse(role.matches("Team/Special-old"));
    assertFalse(role.matches("Team/Special-old/build"));
    assertFalse(role.matches("Team"));
    assertFalse(role.matches("x/y"));
    assertFalse(role.matches("B"));
    assertFalse(role.matches("AB/C"));
    assertFalse(role.matches("/A"));
    assertFalse(role.matches(""));
  }

  /**
   * Roles with many folders match by looking up the item's parent folders, roles with few compare every folder, and
   * the derived pattern is a third, independent implementation: all three must agree on every name.
   */
  @Test
  void everyFolderMatchingStrategyAgrees() {
    String[] parts = {"a", "b", "a-b", "a.b", "", "a b", "\u2028"};
    Random random = new Random(7);
    for (int round = 0; round < 2000; round++) {
      List<String> folders = new ArrayList<>();
      int count = 1 + random.nextInt(12);
      for (int i = 0; i < count; i++) {
        folders.add(randomPath(random, parts, 1 + random.nextInt(3)));
      }
      Role role;
      try {
        role = folderRole(folders.toArray(new String[0]));
      } catch (IllegalArgumentException e) {
        continue; // every folder normalized away
      }
      Pattern derived = role.getPattern();
      for (int n = 0; n < 50; n++) {
        String name = randomPath(random, parts, 1 + random.nextInt(5));
        boolean expected = role.getFolders().stream()
            .anyMatch(f -> name.equals(f) || name.startsWith(f + "/"));
        assertEquals(expected, role.matches(name), () -> role.getFolders() + " vs " + name);
        assertEquals(expected, derived.matcher(name).matches(), () -> role.getFolders() + " vs " + name);
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

  @Test
  void folderNamesKeepUnicodeSpaces() {
    assertThat(folderRole("\u00a0Team/Special").getFolders(), contains("\u00a0Team/Special"));
  }

  @Test
  void folderNamesAreNormalized() {
    Role role = folderRole(" /Team/Special/ ", "Team/Special", "", "  ", "/");
    assertThat(role.getFolders(), contains("Team/Special"));
    assertEquals("Team/Special", role.getScopeDescription());
  }

  @Test
  void folderRoleNeedsAFolder() {
    assertThrows(IllegalArgumentException.class, () -> folderRole(" ", "/"));
  }

  @Test
  void patternRoleIsNotFolderScoped() {
    Role role = new Role("r", Pattern.compile("Team/.*"), Set.of(Permission.READ), null);
    assertFalse(role.isFolderScoped());
    assertNull(role.getFolders());
    assertTrue(role.matches("Team/x"));
    assertEquals("Team/.*", role.getScopeDescription());
  }

  @Test
  void rolesWithDifferentFoldersAreNotEqual() {
    assertNotEquals(folderRole("A"), folderRole("B"));
  }
}
