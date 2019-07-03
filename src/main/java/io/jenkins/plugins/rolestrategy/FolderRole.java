package io.jenkins.plugins.rolestrategy;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.security.Permission;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FolderRole extends AbstractRole implements Comparable<FolderRole> {
    @Nonnull
    private final Set<Folder> folders;

    @DataBoundConstructor
    @ParametersAreNonnullByDefault
    public FolderRole(String name, Set<Permission> permissions, Set<Folder> folders) {
        super(name, permissions);
        this.folders = ConcurrentHashMap.newKeySet();
        this.folders.addAll(folders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sids, permissions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolderRole that = (FolderRole) o;
        return name.equals(that.name) &&
                sids.equals(that.sids) &&
                permissions.equals(that.permissions);
    }

    @Override
    public int compareTo(@Nonnull FolderRole other) {
        return name.compareTo(other.name);
    }

    /**
     * Returns the folders managed by this role
     *
     * @return the folders managed by this role
     */
    @Nonnull
    public Set<Folder> getFolders() {
        return Collections.unmodifiableSet(folders);
    }
}
