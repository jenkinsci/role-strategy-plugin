package io.jenkins.plugins.rolestrategy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.Permission;
import hudson.security.SidACL;
import org.acegisecurity.acls.sid.Sid;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An immutable ACL object. Dispose off when no longer valid.
 * <p>
 * Creation of this object may be time intensive. Do NOT keep returning new instances of this object.
 */
class GlobalAclImpl extends SidACL {
    /**
     * After the initialization of this object is complete, this list contains all permissions
     * that the global rules imply for each sid. After that, checking for permissions is
     */
    private Map<String, Set<Permission>> permissionList = new ConcurrentHashMap<>();

    /**
     * Initializes the ACL objects and preemptively calculates all permissions for all sids.
     *
     * @param globalRoles set of roles from which to calculate the permissions.
     */
    GlobalAclImpl(Set<GlobalRole> globalRoles) {
        for (GlobalRole role : globalRoles) {
            Set<Permission> impliedPermissions = ConcurrentHashMap.newKeySet();

            role.permissions.parallelStream()
                    .forEach(p -> impliedPermissions.addAll(getImpliedPermissions(p)));

            role.sids.parallelStream().forEach(sid -> {
                Set<Permission> permissionsForSid = permissionList.get(sid);
                if (permissionsForSid == null) {
                    permissionsForSid = new HashSet<>();
                }
                permissionsForSid.addAll(impliedPermissions);
                permissionList.put(sid, permissionsForSid);
            });
        }
    }

    @Override
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL",
            justification = "hudson.security.SidACL requires null when unknown")
    @Nullable
    protected Boolean hasPermission(Sid p, Permission permission) {
        Set<Permission> permissions = permissionList.get(toString(p));
        if (permission != null && permissions.contains(permission)) {
            return true;
        }

        return null;
    }

    // TODO: 7/3/2019 Remove this when RoleMap has the implied permission cache (PR#83)
    private static Set<Permission> getImpliedPermissions(Permission p) {
        final Set<Permission> permissions = new HashSet<>();
        for (; p != null; p = p.impliedBy) {
            permissions.add(p);
        }
        return permissions;
    }
}
