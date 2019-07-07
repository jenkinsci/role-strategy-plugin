package io.jenkins.plugins.rolestrategy.acls;

import hudson.security.Permission;

import java.util.HashSet;
import java.util.Set;

/**
 * An {@link hudson.security.ACL} for one {@link hudson.model.Job} or one {@link hudson.model.AbstractProject}.
 */
public class JobAclImpl extends AbstractAcl {

    /**
     * Assigns {@code permissions} to each sid in {@code sid}.
     *
     * @param sids        the sids to be assigned {@code permissions}
     * @param permissions the {@link Permission}s to be assigned
     */
    public void assignPermissions(Set<String> sids, Set<Permission> permissions) {
        sids.parallelStream().forEach(sid -> {
            Set<Permission> assignedPermissions = permissionList.get(sid);
            if (assignedPermissions == null) {
                assignedPermissions = new HashSet<>();
            }
            assignedPermissions.addAll(permissions);
            permissionList.put(sid, assignedPermissions);
        });
    }
}
