package io.jenkins.plugins.rolestrategy.roles;

import io.jenkins.plugins.rolestrategy.misc.PermissionWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.Set;

/**
 * An {@link AbstractRole} that's applicable everywhere inside Jenkins.
 */
public class GlobalRole extends AbstractRole {
    @DataBoundConstructor
    @SuppressWarnings("WeakerAccess")
    public GlobalRole(String name, Set<PermissionWrapper> permissions, Set<String> sids) {
        super(name, permissions);
        this.sids.addAll(sids);
    }

    public GlobalRole(String name, Set<PermissionWrapper> permissions) {
        this(name, permissions, Collections.emptySet());
    }
}
