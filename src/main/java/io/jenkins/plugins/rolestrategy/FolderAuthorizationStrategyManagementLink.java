package io.jenkins.plugins.rolestrategy;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class FolderAuthorizationStrategyManagementLink extends ManagementLink {
    @CheckForNull
    @Override
    public String getIconFileName() {
        return "secure.gif";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "folder-auth";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Folder Authorization Strategy";
    }

    @Nonnull
    public Set<Permission> getPermissions(/* TODO add RoleType */) {
        // TODO cleanup
        List<PermissionGroup> permissionGroups = new ArrayList<>(PermissionGroup.getAll());
        permissionGroups.remove(PermissionGroup.get(Permission.class));
        Set<Permission> permissions = new HashSet<>();
        permissionGroups.stream().map(PermissionGroup::getPermissions).forEach(permissions::addAll);
        return permissions.stream().filter(p -> !PermissionHelper.isDangerous(p)).collect(Collectors.toSet());
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAddGlobalRole(@QueryParameter GlobalRole globalRole) {
        AuthorizationStrategy strategy = Jenkins.getInstance().getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            ((FolderBasedAuthorizationStrategy) strategy).addGlobalRole(globalRole);
        }
    }
}
