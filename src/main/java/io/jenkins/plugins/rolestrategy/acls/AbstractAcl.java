package io.jenkins.plugins.rolestrategy.acls;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.Permission;
import hudson.security.SidACL;
import org.acegisecurity.acls.sid.Sid;
import org.apache.commons.collections.CollectionUtils;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractAcl extends SidACL {
    /**
     * Maps each sid to the set of permissions assigned to it.
     * <p>
     * The implementation should ensure that this list contains accurate permissions for each sid.
     */
    protected Map<String, Set<Permission>> permissionList = new ConcurrentHashMap<>();

    @Override
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL",
            justification = "hudson.security.SidACL requires null when unknown")
    @Nullable
    protected Boolean hasPermission(Sid sid, Permission permission) {
        Set<Permission> permissions = permissionList.get(toString(sid));
        if (permissions != null && CollectionUtils.containsAny(permissions, getImplyingPermissions(permission))) {
            return true;
        }

        return null;
    }

    // TODO Remove this when RoleMap has the implied permission cache (PR#83)
    private static Set<Permission> getImplyingPermissions(Permission p) {
        final Set<Permission> permissions = new HashSet<>();
        for (; p != null; p = p.impliedBy) {
            permissions.add(p);
        }
        return permissions;
    }
}
