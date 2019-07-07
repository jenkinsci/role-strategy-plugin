package io.jenkins.plugins.rolestrategy.roles;

import io.jenkins.plugins.rolestrategy.misc.PermissionWrapper;

import java.util.Set;

/**
 * An {@link AbstractRole} that's applicable everywhere inside Jenkins.
 */
public class GlobalRole extends AbstractRole {
    public GlobalRole(String name, Set<PermissionWrapper> permissions) {
        super(name, permissions);
    }
}
