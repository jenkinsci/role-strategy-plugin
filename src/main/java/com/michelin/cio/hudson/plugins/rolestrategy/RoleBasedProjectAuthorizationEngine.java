package com.michelin.cio.hudson.plugins.rolestrategy;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import hudson.model.AbstractItem;
import hudson.security.SidACL;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * An engine that can be used for Item or Agent authorization inside a {@link RoleBasedAuthorizationStrategy}.
 * All subclasses should have a public no-argument constructor.
 */
interface RoleBasedProjectAuthorizationEngine {

    @Nonnull
    SidACL getACL(@Nonnull AbstractItem project);

    /**
     * Return the sids on which this engine works
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
     * Configure a {@link RoleBasedProjectAuthorizationEngine} using the XML from the reader
     *
     * @param reader the XML reader at the node containing the configuration.
     * @return RoleBasedProjectAuthorizationEngine that has been configured correctly.
     */
    @Nonnull
    RoleBasedProjectAuthorizationEngine configure(HierarchicalStreamReader reader);
}
