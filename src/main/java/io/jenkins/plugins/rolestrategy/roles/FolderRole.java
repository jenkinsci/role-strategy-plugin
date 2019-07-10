package io.jenkins.plugins.rolestrategy.roles;

import io.jenkins.plugins.rolestrategy.misc.PermissionWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FolderRole extends AbstractRole implements Comparable<FolderRole> {
    @Nonnull
    private final Set<String> folders;

    @DataBoundConstructor
    @ParametersAreNonnullByDefault
    @SuppressWarnings("WeakerAccess")
    public FolderRole(String name, Set<PermissionWrapper> permissions, Set<String> folders, Set<String> sids) {
        super(name, permissions);
        this.sids.addAll(sids);
        this.folders = ConcurrentHashMap.newKeySet();
        this.folders.addAll(folders);
    }

    public FolderRole(String name, Set<PermissionWrapper> permissions, Set<String> folders) {
        this(name, permissions, folders, Collections.emptySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sids, permissionWrappers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolderRole that = (FolderRole) o;
        return name.equals(that.name) &&
                sids.equals(that.sids) &&
                permissionWrappers.equals(that.permissionWrappers);
    }

    @Override
    public int compareTo(@Nonnull FolderRole other) {
        return name.compareTo(other.name);
    }

    /**
     * Returns the names of the folders managed by this role
     *
     * @return the names of the folders managed by this role
     */
    @Nonnull
    public Set<String> getFolderNames() {
        return Collections.unmodifiableSet(folders);
    }

    /**
     * Returns the folder names as a comma separated string list
     *
     * @return the folder names as a comma separated string list
     */
    @SuppressWarnings("unused") // used in index.jelly
    public String getFolderNamesCommaSeparated() {
        String csv = folders.toString();
        return csv.substring(1, csv.length() - 1);
    }
}
