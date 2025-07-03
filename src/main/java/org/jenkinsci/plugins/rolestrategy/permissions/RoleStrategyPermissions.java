package org.jenkinsci.plugins.rolestrategy.permissions;

import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public final class RoleStrategyPermissions {
    public static final PermissionGroup GROUP =
            new PermissionGroup(RoleStrategyPermissions.class, Messages._RoleBasedAuthorizationStrategy_PermissionGroupTitle());

    public static final Permission GLOBAL_ROLES_ADMIN = new Permission(
            GROUP,
            "GlobalRolesAdmin",
            Messages._RoleBasedAuthorizationStrategy_GlobalRolesAdminPermissionDescription(),
            Jenkins.ADMINISTER,
            PermissionScope.JENKINS);

    public static final Permission ITEM_ROLES_ADMIN = new Permission(
            GROUP,
            "ItemRolesAdmin",
            Messages._RoleBasedAuthorizationStrategy_ItemRolesAdminPermissionDescription(),
            GLOBAL_ROLES_ADMIN,
            PermissionScope.JENKINS);

    public static final Permission AGENT_ROLES_ADMIN = new Permission(
            GROUP,
            "AgentRolesAdmin",
            Messages._RoleBasedAuthorizationStrategy_AgentRolesAdminPermissionDescription(),
            GLOBAL_ROLES_ADMIN,
            PermissionScope.JENKINS);

    @Restricted(NoExternalUse.class) // called by jelly
    public static final Permission[] SYSTEM_READ_AND_ITEM_ROLES_ADMIN =
            new Permission[] { Jenkins.SYSTEM_READ, ITEM_ROLES_ADMIN };

    @Restricted(NoExternalUse.class) // called by jelly
    public static final Permission[] SYSTEM_READ_AND_SOME_ROLES_ADMIN =
            new Permission[] { Jenkins.SYSTEM_READ, GLOBAL_ROLES_ADMIN, ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN };

    @Restricted(NoExternalUse.class) // called by jelly
    public static final Permission[] ADMINISTER_AND_SOME_ROLES_ADMIN =
            new Permission[] { Jenkins.ADMINISTER, GLOBAL_ROLES_ADMIN, ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN };

    private RoleStrategyPermissions() {}

    @SuppressFBWarnings(
            value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
            justification = "getEnabled return value discarded")
    @Initializer(after = InitMilestone.PLUGINS_STARTED, before = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void ensurePermissionsRegistered() {
        GLOBAL_ROLES_ADMIN.getEnabled();
        ITEM_ROLES_ADMIN.getEnabled();
        AGENT_ROLES_ADMIN.getEnabled();
    }
}