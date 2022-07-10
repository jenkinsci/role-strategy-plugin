package com.michelin.cio.hudson.plugins.rolestrategy;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.security.Permission;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class RoleTest {

  @Test
  public void testHasPermission() {
    Role role = new Role("name", new HashSet<>(Arrays.asList(Permission.CREATE, Permission.READ, Permission.DELETE)));
    assertTrue(role.hasPermission(Permission.READ));
    assertFalse(role.hasPermission(Permission.WRITE));
  }

  @Test
  public void testHasAnyPermissions() {
    Role role = new Role("name", new HashSet<>((Arrays.asList(Permission.READ, Permission.DELETE))));
    assertTrue(role.hasAnyPermission(new HashSet<>(Arrays.asList(Permission.READ, Permission.WRITE))));
    assertFalse(role.hasAnyPermission(new HashSet<>(Arrays.asList(Permission.UPDATE, Permission.WRITE))));
  }

  @Test
  public void shouldNotAddNullPermToNewRole() {
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
}
