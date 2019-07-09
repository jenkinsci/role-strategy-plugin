package io.jenkins.plugins.rolestrategy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import io.jenkins.plugins.rolestrategy.roles.AbstractRole;
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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ParametersAreNonnullByDefault
public class FolderBasedAuthorizationStrategy extends AuthorizationStrategy {
    private static final Logger LOGGER = Logger.getLogger(FolderBasedAuthorizationStrategy.class.getName());
    private static final String ADMIN_ROLE_NAME = "admin";
    private static final String FOLDER_SEPARATOR = "/";
    private final Set<GlobalRole> globalRoles;
    private final Set<FolderRole> folderRoles;
    private transient GlobalAclImpl globalAcl;
    /**
     * Maps full name of jobs to their respective {@link ACL}s. The {@link ACL}s here do not
     * get inheritance from their parents.
     */
    private transient ConcurrentHashMap<String, JobAclImpl> jobAcls = new ConcurrentHashMap<>();
    /**
     * Contains the ACLs for projects that do not need any further inheritance.
     * <p>
     * Invalidate this cache whenever folder roles are updated.
     */
    private transient Cache<String, SidACL> aclCache;

    @DataBoundConstructor
    @SuppressWarnings("WeakerAccess")
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

            GlobalRole adminRole = new GlobalRole(ADMIN_ROLE_NAME, adminPermissions);
            adminRole.assignSids(new PrincipalSid(Jenkins.getAuthentication()).getPrincipal());
            this.globalRoles.add(adminRole);
        }

        if (folderRoles != null) {
            this.folderRoles.addAll(folderRoles);
        }

        initCache();
        generateNewGlobalAcl();
        updateJobAcls(true);
    }

    /**
     * Recalculates {@code jobAcls}.
     *
     * @param doClear if true, jobAcls will be cleared
     */
    private synchronized void updateJobAcls(boolean doClear) {
        if (doClear) {
            jobAcls.clear();
        }

        for (FolderRole role : folderRoles) {
            updateAclForFolderRole(role);
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
        jobAcls = new ConcurrentHashMap<>();
        initCache();
        generateNewGlobalAcl();
        updateJobAcls(true);
        return this;
    }

    /**
     * Gets the {@link ACL} for a {@link Job}
     *
     * @return the {@link ACL} for the {@link Job}
     */
    @Nonnull
    @Override
    public SidACL getACL(@Nonnull Job<?, ?> project) {
        return getACL((AbstractItem) project);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public SidACL getACL(@Nonnull AbstractItem item) {
        String fullName = item.getFullName();
        SidACL acl = aclCache.getIfPresent(fullName);

        if (acl != null) {
            return acl;
        }

        String[] splits = fullName.split(FOLDER_SEPARATOR);
        StringBuilder sb = new StringBuilder(fullName.length());
        acl = globalAcl;

        // Roles on a folder are applicable to all children
        for (String str : splits) {
            sb.append(str);
            SidACL newAcl = jobAcls.get(sb.toString());
            if (newAcl != null) {
                acl = acl.newInheritingACL(newAcl);
            }
            sb.append(FOLDER_SEPARATOR);
        }

        aclCache.put(fullName, acl);
        return acl;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Collection<String> getGroups() {
        Set<String> groups = ConcurrentHashMap.newKeySet();
        globalRoles.stream().parallel().map(AbstractRole::getSids).forEach(groups::addAll);
        folderRoles.stream().parallel().map(AbstractRole::getSids).forEach(groups::addAll);
        return Collections.unmodifiableCollection(groups);
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

    /**
     * Assigns SID to a {@link GlobalRole}.
     *
     * @param roleName the name of the {@link GlobalRole}
     * @param sid      the sid to be assigned
     * @throws IOException            when unable to save the configuration to disk
     * @throws NoSuchElementException when no {@link GlobalRole} with name equal to {@code roleName} exists
     */
    public void assignSidToGlobalRole(String roleName, String sid) throws IOException {
        // TODO maintain an index of roles according to their names
        GlobalRole role = globalRoles.stream()
                .filter(r -> r.getName().equals(roleName))
                .findAny().orElseThrow(() ->
                        new NoSuchElementException("No GlobalRole with the " + "name " + roleName + "exists."));

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

    /**
     * Returns the {@link FolderRole}s on which this {@link AuthorizationStrategy} works.
     *
     * @return {@link FolderRole}s on which this {@link AuthorizationStrategy} works
     */
    @Restricted(NoExternalUse.class)
    public Set<FolderRole> getFolderRoles() {
        return Collections.unmodifiableSet(folderRoles);
    }

    /**
     * Adds a FolderRole to this strategy
     *
     * @param folderRole the {@link FolderRole} to be added
     * @throws IOException when unable to save configuration to disk
     */
    public void addFolderRole(@Nonnull FolderRole folderRole) throws IOException {
        folderRoles.add(folderRole);
        try {
            Jenkins.getInstance().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to save configuration when adding folder role.", e);
            folderRoles.remove(folderRole);
            throw e;
        } finally {
            aclCache.invalidateAll();
            updateAclForFolderRole(folderRole);
        }
    }

    /**
     * Assigns a SID to a {@link FolderRole}.
     *
     * @param roleName the name of the {@link FolderRole}
     * @param sid      the sid to be assigned
     * @throws IOException            when unable to save the configuration to disk
     * @throws NoSuchElementException when no {@link GlobalRole} with name equal to {@code roleName} exists
     */
    public void assignSidToFolderRole(String roleName, String sid) throws IOException {
        // TODO maintain an index of roles according to their names
        FolderRole role = folderRoles.stream()
                .filter(r -> r.getName().equals(roleName))
                .findAny().orElseThrow(() ->
                        new NoSuchElementException("No GlobalRole with the name " + roleName + " exists."));

        role.assignSids(sid);
        try {
            Jenkins.getInstance().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to save config file, not assigning the sids.", e);
            role.unassignSids(sid);
            throw e;
        } finally {
            // no cache invalidation required here because the inheritance of
            // folder roles does not change and we're directly modifying the ACL
            // whose references are kept inside the inheriting SidACL.
            updateAclForFolderRole(role);
        }
    }

    /**
     * Updates the ACL for the folder role
     * <p>
     * <b>Note: does not invalidate the cache</b>
     * <p>
     * Should be called when a folderRole has been updated.
     *
     * @param role the role to be updated
     */
    private void updateAclForFolderRole(@Nonnull FolderRole role) {
        for (String name : role.getFolderNames()) {
            JobAclImpl acl = jobAcls.get(name);
            if (acl == null) {
                acl = new JobAclImpl();
            }
            acl.assignPermissions(role.getSids(), role.getPermissions());
            jobAcls.put(name, acl);
        }
    }

    /**
     * Deletes the global role identified by its name.
     *
     * @param roleName the name of the role to be deleted
     * @throws IOException              when unable to save the configuration
     * @throws NoSuchElementException   when no {@link GlobalRole} with name equal to {@code roleName} exists
     * @throws IllegalArgumentException when trying to delete the admin role
     */
    public void deleteGlobalRole(String roleName) throws IOException {
        if (roleName.equals(ADMIN_ROLE_NAME)) {
            // the admin role cannot be deleted
            throw new IllegalArgumentException("The admin role cannot be deleted.");
        }

        GlobalRole role = globalRoles.stream()
                .filter(r -> r.getName().equals(roleName))
                .findAny().orElseThrow(() ->
                        new NoSuchElementException("No GlobalRole with the name " + roleName + " exists."));

        globalRoles.remove(role);

        try {
            Jenkins.getInstance().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to save the config when deleting global role. " +
                    "The role was not deleted.", e);
            globalRoles.add(role);
            throw e;
        } finally {
            generateNewGlobalAcl();
        }
    }

    /**
     * Deletes the folder role identified by its name.
     *
     * @param roleName the name of the role to be deleted
     * @throws IOException            when unable to save the configuration
     * @throws NoSuchElementException when no {@link GlobalRole} with name equal to {@code roleName} exists
     */
    public void deleteFolderRole(String roleName) throws IOException {
        FolderRole role = folderRoles.stream()
                .filter(r -> r.getName().equals(roleName))
                .findAny().orElseThrow(() ->
                        new NoSuchElementException("No GlobalRole with the name " + roleName + " exists."));

        folderRoles.remove(role);

        try {
            Jenkins.getInstance().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to save the config when deleting folder role. " +
                    "The role was not deleted.", e);
            folderRoles.add(role);
            throw e;
        } finally {
            // TODO update jobACLs manually?
            updateJobAcls(true);
            aclCache.invalidateAll();
        }
    }

    private void initCache() {
        aclCache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(2048)
                .build();
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
