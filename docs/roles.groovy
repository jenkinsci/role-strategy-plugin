import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.michelin.cio.hudson.plugins.rolestrategy.Role
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType
import hudson.security.Permission


Jenkins jenkins = Jenkins.get()
def rbas = jenkins.getAuthorizationStrategy()

/* create admin role */
Set<Permission> permissions = new HashSet<>();
permissions.add(Item.READ)
permissions.add(Item.BUILD)


itemRoleMap = rbas.getRoleMap(RoleType.Project)

for (i = 101; i <=500; i++) {
    role = "role-"+i;
    user = "user-"+i
    group = "group-"+i
    //r = new Role(role, role, permissions)
    //itemRoleMap.addRole(r)
    //itemRoleMap.assignRole(r, new PermissionEntry(AuthorizationType.USER, user))
    //itemRoleMap.assignRole(r, new PermissionEntry(AuthorizationType.GROUP, group))
    r = itemRoleMap.getRole(role)
    itemRoleMap.removeRole(r)
}

jenkins.save()