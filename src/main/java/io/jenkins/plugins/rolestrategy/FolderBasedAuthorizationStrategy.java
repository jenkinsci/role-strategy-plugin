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
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FolderBasedAuthorizationStrategy extends AuthorizationStrategy {
    private Set<FolderRole> roles = ConcurrentHashMap.newKeySet();

    @DataBoundConstructor
    FolderBasedAuthorizationStrategy(Set<FolderRole> roles) {

    }

    @Nonnull
    @Override
    public ACL getRootACL() {
        return new ACL() {
            @Override
            public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
                return true;
            }
        };
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

    @Extension
    public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Folder AuthorizationLevel strategy";
        }

        @Override
        public AuthorizationStrategy newInstance(@Nullable StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }
    }
}
