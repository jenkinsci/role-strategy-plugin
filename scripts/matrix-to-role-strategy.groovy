/**
 Script for converting matrix-based auth strategy to role-strategy. 
 Searches for the same set of global matrix permissions and creates role, groups people.
 Ideally should be rewritten in java and bundled into plugin code with tests. 
 
 @author Alina Karpovich
 @author Kanstantsin Shautsou
*/
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.michelin.cio.hudson.plugins.rolestrategy.Role
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap
import hudson.security.Permission
  
def oldStrategy = Jenkins.instance.authorizationStrategy

println "Users"
println oldStrategy.sids
println()

println "There is " +  oldStrategy.grantedPermissions.size() + " unique permissions in matrix-based strategy"
println()

//make sets of permissions for every user
Map<String, Set<Permission>> usersWithPermissions = new HashMap<String, Set<Permission>>()
for (perm in oldStrategy.grantedPermissions.keySet()) {
  def userSet = oldStrategy.grantedPermissions.get(perm)
  for (user in userSet) {
    def permSet = usersWithPermissions.get(user)
    if (permSet != null) {
      permSet.add(perm)
    } else {
      permSet = new HashSet<Permission>()
      permSet.add(perm)
      usersWithPermissions.put(user, permSet)
  	}  
  }
}

//search for users with equal sets of permissions
Map<Set<String>, Set<Permission>> reducedUsersWithPermissions = new HashMap<Set<String>, Set<Permission>>()
List<String> usersToCheck = new ArrayList<String>(usersWithPermissions.keySet())
for (int i = 0; i < usersToCheck.size(); i++) {
  def userI = usersToCheck.get(i)
  def permissionsToCompare = usersWithPermissions.get(userI)
  Set<String> usersWithOneRole = new HashSet<String>()
  usersWithOneRole.add(userI)
  for (int j = i+1; j < usersToCheck.size(); j++) {
    def userJ = usersToCheck.get(j)
    if (permissionsToCompare.equals(usersWithPermissions.get(userJ))) {      
      usersWithOneRole.add(userJ)
      usersToCheck.remove(userJ)
      j--
    }
  }
  reducedUsersWithPermissions.put(usersWithOneRole, permissionsToCompare)
}

println "Populating roles with current permissions"
println()

//prepare map for RoleMap constructor
SortedMap <Role,Set<String>> grantedRoles = new TreeMap <Role,Set<String>>()
int roleNumber = 1;
for (userSet in reducedUsersWithPermissions.keySet()) {
  def permSet = reducedUsersWithPermissions.get(userSet)
  def role = new Role("role" + roleNumber, permSet)
  grantedRoles.put(role, userSet)  
  println "Role called \"" + role.name + "\" for user(s) " + userSet + " was created. Permissions:"
  def newString = false;
  Map<String, Set<String>> itemPermissions = new HashMap<Class, Set<String>>()
  for (perm in permSet) {   
    def permNames = itemPermissions.get(perm.owner.simpleName)
    if (permNames != null) {
      permNames.add(perm.name)
    } else {
      permNames = new HashSet<String>()
      permNames.add(perm.name)
      itemPermissions.put(perm.owner.simpleName, permNames)
    }
  }
  println itemPermissions
  println()
  roleNumber++
}

def newStrategy = new RoleBasedAuthorizationStrategy()
//make RoleMap and save it into strategy with type GLOBAL (globalRoles)
newStrategy.grantedRoles.put("globalRoles", new RoleMap(grantedRoles));

//messages to show that new strategy is well-formed
println "New strategy was created, granted role map contains:"
for (roleMapItem in newStrategy.grantedRoles) {
  print roleMapItem.key + ": "
  for (roleUsers in roleMapItem.value.grantedRoles) {
    print "  \"" + roleUsers.key.name + "\" for user(s) " + roleUsers.value + " "
  }
  println()
}

Jenkins.getInstance().setAuthorizationStrategy(newStrategy);
println()
println "New strategy was set"
