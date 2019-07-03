package io.jenkins.plugins.rolestrategy;

import hudson.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A role as an object
 */
public abstract class AbstractRole {
    /**
     * The unique name of the role.
     */
    @Nonnull
    protected final String name;

    /**
     * The permissions that are assigned to this role.
     */
    @Nonnull
    protected final Set<Permission> permissions;

    /**
     * The sids on which this role is applicable.
     */
    @Nonnull
    protected final Set<String> sids;

    @ParametersAreNonnullByDefault
    public AbstractRole(String name, Set<Permission> permissions) {
        this.name = name;
        this.sids = ConcurrentHashMap.newKeySet();
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    /**
     * This {@link AbstractRole} will become applicable on the given {@code sids}.
     *
     * @param sids the user sids on which this role will become applicable
     */
    public void assignSids(String... sids) {
        assignSids(Arrays.asList(sids));
    }

    /**
     * This {@link AbstractRole} will become applicable on the given {@code sids}.
     *
     * @param sids the sids on which this role will become applicable
     */
    public void assignSids(@Nonnull List<String> sids) {
        this.sids.addAll(sids);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractRole role = (AbstractRole) o;
        return name.equals(role.name) &&
                permissions.equals(role.permissions) &&
                sids.equals(role.sids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, permissions, sids);
    }

    /**
     * The name of the Role
     *
     * @return the name of the role
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * The permissions assigned to the role.
     * <p>
     * This method, however, does not return all permissions implied by this {@link AbstractRole}
     *
     * @return the permissions assigned to the role.
     */
    @Nonnull
    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * List of sids on which the role is applicable.
     *
     * @return list of sids on which this role is applicable.
     */
    @Nonnull
    public Set<String> getSids() {
        return sids;
    }
}
