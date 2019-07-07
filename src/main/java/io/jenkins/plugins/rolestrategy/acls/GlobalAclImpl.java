package io.jenkins.plugins.rolestrategy.acls;

import hudson.security.Permission;
import io.jenkins.plugins.rolestrategy.roles.GlobalRole;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An immutable ACL object. Dispose off when no longer valid.
 * <p>
 * Creation of this object may be time intensive. Do NOT keep returning new instances of this object.
 */
public class GlobalAclImpl extends AbstractAcl {

    /**
     * Initializes the ACL objects and preemptively calculates all permissions for all sids.
     *
     * @param globalRoles set of roles from which to calculate the permissions.
     */
    public GlobalAclImpl(Set<GlobalRole> globalRoles) {
        for (GlobalRole role : globalRoles) {
            Set<Permission> impliedPermissions = ConcurrentHashMap.newKeySet();

            role.getPermissions().parallelStream().forEach(impliedPermissions::add);

            role.getSids().parallelStream().forEach(sid -> {
                Set<Permission> permissionsForSid = permissionList.get(sid);
                if (permissionsForSid == null) {
                    permissionsForSid = new HashSet<>();
                }
                permissionsForSid.addAll(impliedPermissions);
                permissionList.put(sid, permissionsForSid);
            });
        }
    }
}
