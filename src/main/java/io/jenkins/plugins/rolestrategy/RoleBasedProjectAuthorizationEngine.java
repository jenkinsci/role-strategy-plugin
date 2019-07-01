package io.jenkins.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractItem;
import hudson.security.SidACL;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * An engine that can be used for Item or Agent authorization inside a {@link RoleBasedAuthorizationStrategy}.
 * <p>
 * All subclasses need to satisfy the following requirements:
 * <ul>
 * <li>Have a public no-arg constructor
 * <Li>A {@code manage-project-roles.jelly} for managing the project roles
 * <li>A {@code assign-project-roles.jelly} for assigning these roles to users and groups
 * </ul>
 * <p>
 * For these Jelly views, the {@code it} object will be {@link RoleBasedAuthorizationStrategy.DescriptorImpl}
 *
 * @since TODO
 */
public interface RoleBasedProjectAuthorizationEngine extends ExtensionPoint {

    @Nonnull
    SidACL getACL(@Nonnull AbstractItem project);

    /**
     * Return the sids on which this engine works.
     *
     * @param includeAnonymous if true, the anonymous sid will be included
     * @return a collection of the sids on which this engine works
     */
    @Nonnull
    Collection<? extends String> getSids(boolean includeAnonymous);

    /**
     * The {@link RoleMap} it the {@link RoleBasedProjectAuthorizationEngine} supports it.
     *
     * @return the {@link RoleMap} used by this engine
     * @throws UnsupportedOperationException if the engine does not have a {@link RoleMap}
     */
    @Nonnull
    default RoleMap getRoleMap() {
        throw new UnsupportedOperationException("This engine does not use a RoleMap.");
    }

    /**
     * Configure a {@link RoleBasedProjectAuthorizationEngine} using the XML from the reader.
     *
     * @param reader the XML reader at the node containing the configuration.
     * @return RoleBasedProjectAuthorizationEngine that has been configured correctly.
     */
    @Nonnull
    RoleBasedProjectAuthorizationEngine configure(HierarchicalStreamReader reader);

    /**
     * Marshall the object to XML
     *
     * @param writer the XML writer
     */
    void marshal(HierarchicalStreamWriter writer);

    /**
     * Get all registered {@link RoleBasedProjectAuthorizationEngine}s.
     *
     * @return all registered {@link RoleBasedProjectAuthorizationEngine}
     */
    static ExtensionList<RoleBasedProjectAuthorizationEngine> all() {
        return Jenkins.getInstance().getExtensionList(RoleBasedProjectAuthorizationEngine.class);
    }
}
