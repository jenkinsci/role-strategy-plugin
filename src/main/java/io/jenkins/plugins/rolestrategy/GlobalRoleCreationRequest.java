package io.jenkins.plugins.rolestrategy;

import hudson.security.Permission;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GlobalRoleCreationRequest {
    public String name;
    public List<String> permissions;

    GlobalRole getGlobalRole() {
        return new GlobalRole(name, permissions.stream().map(Permission::fromId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
    }
}
