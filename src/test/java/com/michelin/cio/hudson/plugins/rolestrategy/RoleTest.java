package com.michelin.cio.hudson.plugins.rolestrategy;

import hudson.security.Permission;
import hudson.security.PermissionGroup;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    public void shouldNotAddNullPermToNewRole(){
        Permission aRealPerm = Permission.CREATE;
        Permission nullPerm = null;

        Set<Permission> perms = new HashSet<Permission>(Arrays.asList(aRealPerm, nullPerm));
        Role role = new Role("name", perms);

        assertThat(role.getPermissions(), not( hasItem( nullPerm ) ) );
    }
}
