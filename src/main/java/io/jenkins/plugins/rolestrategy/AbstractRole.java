package io.jenkins.plugins.rolestrategy;

import hudson.security.Permission;

import java.util.Set;

public abstract class AbstractRole {
    /**
     * Return the name of the {@link AbstractRole}
     *
     * @return the name of the {@link AbstractRole}
     */
    public abstract String getName();

    /**
     * Get the permissions assigned to this role.
     *
     * @return set of permissions that this role authorizes.
     */
    public abstract Set<Permission> getPermissions();
}
