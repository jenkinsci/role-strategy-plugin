package io.jenkins.plugins.rolestrategy;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import io.jenkins.plugins.rolestrategy.misc.GlobalRoleCreationRequest;
import io.jenkins.plugins.rolestrategy.misc.PermissionWrapper;
import io.jenkins.plugins.rolestrategy.roles.FolderRole;
import io.jenkins.plugins.rolestrategy.roles.GlobalRole;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.json.JsonBody;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy.getPermissionGroups;

@Extension
public class FolderAuthorizationStrategyManagementLink extends ManagementLink {
    private static final Logger LOGGER = Logger.getLogger(FolderAuthorizationStrategyManagementLink.class.getName());

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
    @Restricted(NoExternalUse.class)
    public Set<Permission> getGlobalPermissions() {
        return getSafePermissions(getPermissionGroups(RoleType.Global));
    }

    @Nonnull
    @Restricted(NoExternalUse.class)
    public Set<Permission> getFolderPermissions() {
        return getSafePermissions(getPermissionGroups(RoleType.Project));
    }

    /**
     * Adds a {@link GlobalRole} to {@link FolderBasedAuthorizationStrategy}
     *
     * @param request the request to create the {@link GlobalRole}
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAddGlobalRole(@JsonBody GlobalRoleCreationRequest request) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        AuthorizationStrategy strategy = jenkins.getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            ((FolderBasedAuthorizationStrategy) strategy).addGlobalRole(request.getGlobalRole());
        }
    }

    /**
     * Assigns {@code sid} to the global role identified by {@code roleName}.
     * <p>
     * Does not do anything if a role corresponding to the {@code roleName} does not exist.
     *
     * @param roleName the name of the global to which {@code sid} will be assigned to.
     * @param sid      the sid of the user/group to be assigned.
     * @throws IOException when unable to assign the Sid
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAssignSidToGlobalRole(@QueryParameter(required = true) String roleName,
                                        @QueryParameter(required = true) String sid) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        AuthorizationStrategy strategy = jenkins.getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            ((FolderBasedAuthorizationStrategy) strategy).assignSidToGlobalRole(roleName, sid);
        }
        try {
            Stapler.getCurrentResponse().forwardToPreviousPage(Stapler.getCurrentRequest());
        } catch (ServletException | IOException e) {
            LOGGER.log(Level.WARNING, "Unable to redirect to previous page.");
        }
    }


    /**
     * Adds a {@link FolderRole} to {@link FolderBasedAuthorizationStrategy}
     *
     * @param roleName    the name of the role to be added
     * @param folderNames the folders on which this role is applicable
     * @param permissions the permissions granted by this role
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAddFolderRole(@QueryParameter(required = true) String roleName,
                                @QueryParameter(required = true) String[] folderNames,
                                @QueryParameter(required = true) String[] permissions) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        AuthorizationStrategy strategy = jenkins.getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            Set<PermissionWrapper> permissionWrappers = Arrays.stream(permissions).map(PermissionWrapper::new)
                    .collect(Collectors.toSet());
            FolderRole folderRole = new FolderRole(roleName, permissionWrappers,
                    new HashSet<>(Arrays.asList(folderNames)));
            ((FolderBasedAuthorizationStrategy) strategy).addFolderRole(folderRole);
        }
    }

    @Nonnull
    @Restricted(NoExternalUse.class)
    public Set<GlobalRole> getGlobalRoles() {
        AuthorizationStrategy strategy = Jenkins.getInstance().getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            return ((FolderBasedAuthorizationStrategy) strategy).getGlobalRoles();
        }
        return Collections.emptySet();
    }

    /**
     * Get all {@link Folder}s in the system
     *
     * @return folders in the system
     */
    @Nonnull
    @Restricted(NoExternalUse.class)
    public List<Folder> getAllFolders() {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        List<Folder> folders;

        try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
            folders = jenkins.getAllItems(Folder.class);
        }

        return folders;
    }

    @Nonnull
    @Restricted(NoExternalUse.class)
    public Set<FolderRole> getFolderRoles() {
        AuthorizationStrategy strategy = Jenkins.getInstance().getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
            return ((FolderBasedAuthorizationStrategy) strategy).getFolderRoles();
        }
        return Collections.emptySet();
    }

    private static Set<Permission> getSafePermissions(Set<PermissionGroup> groups) {
        HashSet<Permission> safePermissions = new HashSet<>();
        groups.stream().map(PermissionGroup::getPermissions).forEach(safePermissions::addAll);
        safePermissions.removeAll(PermissionHelper.DANGEROUS_PERMISSIONS);
        return safePermissions;
    }
}
