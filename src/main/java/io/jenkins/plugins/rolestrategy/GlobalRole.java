package io.jenkins.plugins.rolestrategy;

import hudson.security.Permission;

import java.util.Set;

public class GlobalRole extends AbstractRole {
    public GlobalRole(String name, Set<Permission> permissions) {
        super(name, permissions);
    }
}
