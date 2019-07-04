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
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.json.JsonBody;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Adds a {@link GlobalRole} to {@link FolderBasedAuthorizationStrategy}
     *
     * @param request the request to create the {@link GlobalRole}
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAddGlobalRole(@JsonBody GlobalRoleCreationRequest request) {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        AuthorizationStrategy strategy = jenkins.getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            ((FolderBasedAuthorizationStrategy) strategy).addGlobalRole(request.getGlobalRole());
        }
    }

    @Restricted(NoExternalUse.class)
    @Nonnull
    public Set<GlobalRole> getGlobalRoles() {
        AuthorizationStrategy strategy = Jenkins.getInstance().getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            return ((FolderBasedAuthorizationStrategy) strategy).getGlobalRoles();
        }
        return Collections.emptySet();
    }
}
