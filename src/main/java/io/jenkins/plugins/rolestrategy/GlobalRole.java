package io.jenkins.plugins.rolestrategy;

import hudson.security.Permission;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

public class GlobalRole extends AbstractRole {
    @DataBoundConstructor
    public GlobalRole(String name, Set<Permission> permissions) {
        super(name, permissions);
    }
}
