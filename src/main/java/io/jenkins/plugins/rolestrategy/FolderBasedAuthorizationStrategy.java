package io.jenkins.plugins.rolestrategy;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ParametersAreNonnullByDefault
public class FolderBasedAuthorizationStrategy extends AuthorizationStrategy {
    private final Set<GlobalRole> globalRoles;
    private final Set<FolderRole> folderRoles;

    private transient GlobalAclImpl globalACL;

    @DataBoundConstructor
    public FolderBasedAuthorizationStrategy(Set<GlobalRole> globalRoles, Set<FolderRole> folderRoles) {
        this.globalRoles = ConcurrentHashMap.newKeySet();
        this.folderRoles = ConcurrentHashMap.newKeySet();

        this.globalRoles.addAll(globalRoles);
        this.folderRoles.addAll(folderRoles);

        generateNewGlobalAcl();
    }

    @Nonnull
    @Override
    public GlobalAclImpl getRootACL() {
        return globalACL;
    }

    @Nonnull
    @Override
    public ACL getACL(@Nonnull Job<?, ?> project) {
        return getACL((AbstractItem) project);
    }

    @Override
    @Nonnull
    public ACL getACL(@Nonnull AbstractItem item) {
        Jenkins.getInstance().getItems(Folder.class);
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

    @Extension
    public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Folder AuthorizationLevel strategy";
        }
    }
}
