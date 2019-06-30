package io.jenkins.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.AbstractItem;
import hudson.security.SidACL;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Authorization using regular expressions provided by {@link Role} and {@link RoleMap}
 *
 * @since TODO
 */
public class RegexAuthorizationEngine implements RoleBasedProjectAuthorizationEngine {
    private final RoleMap roleMap;

    public RegexAuthorizationEngine(@Nonnull RoleMap roleMap) {
        this.roleMap = roleMap;
    }

    public RegexAuthorizationEngine() {
        this(new RoleMap());
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Collection<? extends String> getSids(boolean includeAnonymous) {
        return roleMap.getSids(includeAnonymous);
    }

    @Nonnull
    @Override
    public SidACL getACL(@Nonnull AbstractItem project) {
        return roleMap.newMatchingRoleMap(project.getFullName()).getACL(RoleType.Project, project);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public RegexAuthorizationEngine configure(HierarchicalStreamReader reader) {
        return new RegexAuthorizationEngine(RoleMap.unmarshal(reader));
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void marshal(HierarchicalStreamWriter writer) {
        // Use RoleType.Project because this is a project authorization engine
        roleMap.marshal(writer, RoleType.Project);
    }
}
