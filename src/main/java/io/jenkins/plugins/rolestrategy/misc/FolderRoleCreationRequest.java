package io.jenkins.plugins.rolestrategy.misc;

import io.jenkins.plugins.rolestrategy.roles.FolderRole;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
@Restricted(NoExternalUse.class)
public class FolderRoleCreationRequest {
    public String name = "";
    public Set<String> folderNames = Collections.emptySet();
    public Set<String> permissions = Collections.emptySet();

    public FolderRole getFolderRole() {
        Set<PermissionWrapper> perms = permissions.stream().map(PermissionWrapper::new).collect(Collectors.toSet());
        return new FolderRole(name, perms, folderNames);
    }
}
