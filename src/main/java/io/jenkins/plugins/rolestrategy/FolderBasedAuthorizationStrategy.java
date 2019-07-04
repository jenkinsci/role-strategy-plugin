package io.jenkins.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ParametersAreNonnullByDefault
public class FolderBasedAuthorizationStrategy extends AuthorizationStrategy {
    private final Set<GlobalRole> globalRoles;
    private final Set<FolderRole> folderRoles;

    private transient GlobalAclImpl globalACL;

    @DataBoundConstructor
    @ParametersAreNullableByDefault
    public FolderBasedAuthorizationStrategy(Set<GlobalRole> globalRoles, Set<FolderRole> folderRoles) {
        this.globalRoles = ConcurrentHashMap.newKeySet();
        this.folderRoles = ConcurrentHashMap.newKeySet();

        if (globalRoles != null) {
            this.globalRoles.addAll(globalRoles);
        } else {
            /*
             * when this AuthorizationStrategy is selected for the first time, this makes the current
             * user admin (give all permissions) and prevents him/her from getting access denied.
             *
             * The same thing happens in RoleBasedAuthorizationStrategy. See RoleBasedStrategy.DESCRIPTOR.newInstance()
             */
            HashSet<Permission> adminPermissions = new HashSet<>();
            RoleBasedAuthorizationStrategy.getPermissionGroups(RoleType.Global)
                    .forEach(group -> adminPermissions.addAll(group.getPermissions()));

            GlobalRole adminRole = new GlobalRole("admin", adminPermissions);
            adminRole.assignSids(new PrincipalSid(Jenkins.getAuthentication()).getPrincipal());
            this.globalRoles.add(adminRole);
        }

        if (folderRoles != null) {
            this.folderRoles.addAll(folderRoles);
        }

        generateNewGlobalAcl();
    }

    @Nonnull
    @Override
    public GlobalAclImpl getRootACL() {
        return globalACL;
    }

    /**
     * Used to initialize transient fields when loaded from disk
     *
     * @return {@code this}
     */
    @SuppressWarnings("unused")
    protected Object readResolve() {
        generateNewGlobalAcl();
        return this;
    }

    @Nonnull
    @Override
    public ACL getACL(@Nonnull Job<?, ?> project) {
        return getACL((AbstractItem) project);
    }

    @Override
    @Nonnull
    public ACL getACL(@Nonnull AbstractItem item) {
        return new ACL() {
            @Override
            public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
                return true;
            }
        };
    }

    @Nonnull
    @Override
    public Collection<String> getGroups() {
        return Collections.emptySet();
    }

    private void generateNewGlobalAcl() {
        globalACL = new GlobalAclImpl(globalRoles);
    }

    public void addGlobalRole(@Nonnull GlobalRole globalRole) {
        globalRoles.add(globalRole);
        generateNewGlobalAcl();
    }

    public Set<GlobalRole> getGlobalRoles() {
        return Collections.unmodifiableSet(globalRoles);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Folder Authorization strategy";
        }
    }
}
