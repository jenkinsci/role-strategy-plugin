package com.michelin.cio.hudson.plugins.rolestrategy;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import hudson.model.AbstractItem;
import hudson.security.SidACL;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * The authorization using regular expressions provided by {@link Role} and {@link RoleMap}
 */
class RegexAuthorizationEngine implements RoleBasedProjectAuthorizationEngine {
    private final RoleMap roleMap;

    RegexAuthorizationEngine(RoleMap roleMap) {
        this.roleMap = roleMap;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Collection<? extends String> getSids(boolean includeAnonymous) {
        return roleMap.getSids(includeAnonymous);
    }

    RegexAuthorizationEngine() {
        this(new RoleMap());
    }

    @Nonnull
    @Override
    public SidACL getACL(@Nonnull AbstractItem project) {
        return roleMap.newMatchingRoleMap(project.getFullName()).getACL(RoleType.Project, project);
    }

    @Nonnull
    @Override
    public RegexAuthorizationEngine configure(HierarchicalStreamReader reader) {
        return new RegexAuthorizationEngine(RoleMap.unmarshall(reader));
    }

    /**
     * Returns the {@link RoleMap} used internally by this engine
     *
     * @return the {@link RoleMap} used by this engine
     */
    @Override
    @Nonnull
    public RoleMap getRoleMap() {
        return roleMap;
    }
}
