package io.jenkins.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.security.SidACL;
import net.sf.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Objects;

/**
 * Authorization using regular expressions provided by {@link Role} and {@link RoleMap}
 *
 * @since TODO
 */
@Extension
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
    @Override
    public void assignRole(String roleName, String sid) {
        Role role = roleMap.getRole(roleName);
        if (Objects.nonNull(role)) {
            roleMap.assignRole(role, sid);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public RoleBasedProjectAuthorizationEngine configure(JSONObject formData, RoleBasedProjectAuthorizationEngine old) {
        if (old.getClass() != getClass()) {
            throw new IllegalArgumentException("Old RoleBasedProjectAuthorizationEngine is not of the same type.");
        }
        roleMap.clearSids();
        RoleMap.addRolesAndCopySids(old.getRoleMap(), roleMap, formData, RoleBasedAuthorizationStrategy.PROJECT);
        return this;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void assignRolesFromJson(@Nonnull JSONObject json) {
        roleMap.assignSidsFromJson(json, RoleType.Project);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeRoles(String[] roleNames) {
        roleMap.removeRoles(roleNames);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ParametersAreNonnullByDefault
    public void addRole(boolean shouldOverwrite, Object... params) {
        if (params.length == 1 && params[0] instanceof Role) {
            roleMap.addRole(shouldOverwrite, (Role) params[0]);
        }
    }
}
