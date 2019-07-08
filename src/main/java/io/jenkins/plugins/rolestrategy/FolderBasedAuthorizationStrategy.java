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
import hudson.security.SidACL;
import io.jenkins.plugins.rolestrategy.acls.GlobalAclImpl;
import io.jenkins.plugins.rolestrategy.acls.JobAclImpl;
import io.jenkins.plugins.rolestrategy.misc.PermissionWrapper;
import io.jenkins.plugins.rolestrategy.roles.FolderRole;
import io.jenkins.plugins.rolestrategy.roles.GlobalRole;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@ParametersAreNonnullByDefault
public class FolderBasedAuthorizationStrategy extends AuthorizationStrategy {
    private static final Logger LOGGER = Logger.getLogger(FolderBasedAuthorizationStrategy.class.getName());
    private final Set<GlobalRole> globalRoles;
    private final Set<FolderRole> folderRoles;

    private transient GlobalAclImpl globalAcl;
    /**
     * Maps full name of jobs to their respective {@link ACL}s.
     */
    private transient ConcurrentHashMap<String, JobAclImpl> jobAcls = new ConcurrentHashMap<>();

    private static final String FOLDER_SEPARATOR = "/";

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
            HashSet<PermissionWrapper> adminPermissions = new HashSet<>();
            RoleBasedAuthorizationStrategy.getPermissionGroups(RoleType.Global)
                    .forEach(permissionGroup -> permissionGroup.getPermissions().stream()
                            .map(Permission::getId)
                            .map(PermissionWrapper::new)
                            .forEach(adminPermissions::add));

            GlobalRole adminRole = new GlobalRole("admin", adminPermissions);
            adminRole.assignSids(new PrincipalSid(Jenkins.getAuthentication()).getPrincipal());
            this.globalRoles.add(adminRole);
        }

        if (folderRoles != null) {
            this.folderRoles.addAll(folderRoles);
        }

        generateNewGlobalAcl();
        updateJobAcls(true);
    }

    private synchronized void updateJobAcls(boolean doClear) {
        if (doClear) {
            jobAcls.clear();
        }

        for (FolderRole role : folderRoles) {
            for (String name : role.getFolderNames()) {
                JobAclImpl acl = jobAcls.get(name);
                if (acl == null) {
                    acl = new JobAclImpl();
                }
                acl.assignPermissions(role.getSids(), role.getPermissions());
                jobAcls.put(name, acl);
            }
        }
    }

    @Nonnull
    @Override
    public GlobalAclImpl getRootACL() {
        return globalAcl;
    }

    /**
     * Used to initialize transient fields when loaded from disk
     *
     * @return {@code this}
     */
    @Nonnull
    @SuppressWarnings("unused")
    protected Object readResolve() {
        generateNewGlobalAcl();
        updateJobAcls(true);
        return this;
    }

    @Nonnull
    @Override
    public SidACL getACL(@Nonnull Job<?, ?> project) {
        return getACL((AbstractItem) project);
    }

    @Nonnull
    @Override
    public SidACL getACL(@Nonnull AbstractItem item) {
        String fullName = item.getFullName();
        String[] splits = fullName.split(FOLDER_SEPARATOR);
        StringBuilder sb = new StringBuilder(fullName.length());
        SidACL acl = globalAcl;

        // Roles on a folder are applicable to all children
        for (String str : splits) {
            sb.append(str);
            SidACL newAcl = jobAcls.get(sb.toString());
            if (newAcl != null) {
                acl = acl.newInheritingACL(newAcl);
            }
            sb.append(FOLDER_SEPARATOR);
        }
        // TODO cache these ACLs
        return acl;
    }

    @Nonnull
    @Override
    public Collection<String> getGroups() {
        return Collections.emptySet();
    }

    private synchronized void generateNewGlobalAcl() {
        globalAcl = new GlobalAclImpl(globalRoles);
    }

    public void addGlobalRole(@Nonnull GlobalRole globalRole) throws IOException {
        globalRoles.add(globalRole);
        try {
            Jenkins.getInstance().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to save config file, not adding global role", e);
            globalRoles.remove(globalRole);
            throw e;
        } finally {
            generateNewGlobalAcl();
        }
    }

    public Set<GlobalRole> getGlobalRoles() {
        return Collections.unmodifiableSet(globalRoles);
    }

    public void assignSidToGlobalRole(String roleName, String sid) throws IOException {
        // TODO maintain an index of roles according to their names
        Optional<GlobalRole> optionalRole = globalRoles.stream().filter(role -> role.getName().equals(roleName)).findAny();

        if (optionalRole.isPresent()) {
            GlobalRole role = optionalRole.get();
            role.assignSids(sid);
            try {
                Jenkins.getInstance().save();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to save config file, not assigning the sids.", e);
                role.unassignSids(sid);
                throw e;
            } finally {
                generateNewGlobalAcl();
            }
        }
    }

    /**
     * Returns the {@link FolderRole}s on which this {@link AuthorizationStrategy} works.
     *
     * @return {@link FolderRole}s on which this {@link AuthorizationStrategy} works
     */
    @Restricted(NoExternalUse.class)
    public Set<FolderRole> getFolderRoles() {
        return Collections.unmodifiableSet(folderRoles);
    }


    public void addFolderRole(@Nonnull FolderRole folderRole) throws IOException {
        folderRoles.add(folderRole);
        try {
            Jenkins.getInstance().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to save configuration when adding folder role.", e);
            folderRoles.remove(folderRole);
            throw e;
        } finally {
            // TODO cache.invalidateAll() when caching ACLs
            updateJobAcls(false);
        }
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
