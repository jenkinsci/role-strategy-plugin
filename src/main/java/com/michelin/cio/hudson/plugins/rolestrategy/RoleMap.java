/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Thomas Maurel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.michelin.cio.hudson.plugins.rolestrategy;

import com.synopsys.arc.jenkins.plugins.rolestrategy.Macro;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleMacroExtension;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.User;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.SidACL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import org.acegisecurity.acls.sid.Sid;

/**
 * Class holding a map for each kind of {@link AccessControlled} object, associating
 * each {@link Role} with the concerned {@link User}s/groups.
 * @author Thomas Maurel
 */
public class RoleMap {

  /** Map associating each {@link Role} with the concerned {@link User}s/groups. */
  private final SortedMap <Role,Set<String>> grantedRoles;

  RoleMap() {
    this.grantedRoles = new TreeMap<Role, Set<String>>();
  }

  RoleMap(SortedMap<Role,Set<String>> grantedRoles) {
    this.grantedRoles = grantedRoles;
  }

  /**
   * Check if the given sid has the provided {@link Permission}.
   * @return True if the sid's granted permission
   */
  private boolean hasPermission(String sid, Permission p, RoleType roleType, AccessControlled controlledItem) {
    for(Role role : getRolesHavingPermission(p)) {     
        
        if(this.grantedRoles.get(role).contains(sid)) {            
            // Handle roles macro
            if (Macro.isMacro(role)) {
                Macro macro = RoleMacroExtension.getMacro(role.getName());
                if (macro != null) {
                    RoleMacroExtension macroExtension = RoleMacroExtension.getMacroExtension(macro.getName());
                    if (macroExtension.IsApplicable(roleType) && macroExtension.hasPermission(sid, p, roleType, controlledItem, macro)) {
                        return true;
                    }          
                }
            } // Default handling
            else {
                return true;
            }
        }
                    
        // TODO: Handle users macro
    }
    return false;
  }

  /**
   * Check if the {@link RoleMap} contains the given {@link Role}.
   * @return True if the {@link RoleMap} contains the given role
   */
  public boolean hasRole(Role role) {
    return this.grantedRoles.containsKey(role);
  }

  /**
   * Get the ACL for the current {@link RoleMap}.
   * @return ACL for the current {@link RoleMap}
   */
  public SidACL getACL(RoleType roleType, AccessControlled controlledItem, boolean ignoresCase) {
    return new AclImpl(roleType, controlledItem, ignoresCase);
  }

  /**
   * Add the given role to this {@link RoleMap}.
   * @param role The {@link Role} to add
   */
  public void addRole(Role role) {
    if(this.getRole(role.getName()) == null) {
      this.grantedRoles.put(role, new HashSet<String>());
    }
  }

  /**
   * Assign the sid to the given {@link Role}.
   * @param role The {@link Role} to assign the sid to
   * @param sid The sid to assign
   */
  public void assignRole(Role role, String sid) {
    if(this.hasRole(role)) {
      this.grantedRoles.get(role).add(sid);
    }
  }

  /**
   * Clear all the sids associated to the given {@link Role}.
   * @param role The {@link Role} for which you want to clear the sids
   */
  public void clearSidsForRole(Role role) {
    if(this.hasRole(role)) {
      this.grantedRoles.get(role).clear();
    }
  }

  /**
   * Clear all the sids for each {@link Role} of the {@link RoleMap}.
   */
  public void clearSids() {
    for(Map.Entry<Role, Set<String>> entry : this.grantedRoles.entrySet()) {
      Role role = entry.getKey();
      this.clearSidsForRole(role);
    }
  }

  /**
   * Get the {@link Role} object named after the given param.
   * @param name The name of the {@link Role}
   * @return The {@link Role} named after the given param
   */
  public Role getRole(String name) {
    for(Role role : this.getRoles()) {
      if(role.getName().equals(name)) {
        return role;
      }
    }
    return null;
  }

  /**
   * Get an unmodifiable sorted map containing {@link Role}s and their assigned sids.
   * @return An unmodifiable sorted map containing the {@link Role}s and their associated sids
   */
  public SortedMap<Role, Set<String>> getGrantedRoles() {
    return Collections.unmodifiableSortedMap(this.grantedRoles);
  }

  /**
   * Get an unmodifiable set containing all the {@link Role}s of this {@link RoleMap}.
   * @return An unmodifiable set containing the {@link Role}s
   */
  public Set<Role> getRoles() {
    return Collections.unmodifiableSet(this.grantedRoles.keySet());
  }

  /**
   * Get all the sids referenced in this {@link RoleMap}, minus the {@code Anonymous} sid.
   * @return A sorted set containing all the sids, minus the {@code Anonymous} sid
   */
  public SortedSet<String> getSids() {
    return this.getSids(false);
  }

  /**
   * Get all the sids referenced in this {@link RoleMap}.
   * @param includeAnonymous True if you want the {@code Anonymous} sid to be included in the set
   * @return A sorted set containing all the sids
   */
  public SortedSet<String> getSids(Boolean includeAnonymous) {
    TreeSet<String> sids = new TreeSet<String>();
    for(Map.Entry entry : this.grantedRoles.entrySet()) {
      sids.addAll((Set)entry.getValue());
    }
    // Remove the anonymous sid if asked to
    if(!includeAnonymous) {
      sids.remove("anonymous");
    }
    return Collections.unmodifiableSortedSet(sids);
  }

  /**
   * Get all the sids assigned to the {@link Role} named after the {@code roleName} param.
   * @param roleName The name of the role
   * @return A sorted set containing all the sids
   */
  public Set<String> getSidsForRole(String roleName) {
    Role role = this.getRole(roleName);
    if(role != null) {
      return Collections.unmodifiableSet(this.grantedRoles.get(role));
    }
    return null;
  }

  /**
   * Create a sub-map of the current {@link RoleMap} containing only the
   * {@link Role}s matching the given pattern.
   * @param namePattern The pattern to match
   * @return A {@link RoleMap} containing only {@link Role}s matching the given name
   */
  public RoleMap newMatchingRoleMap(String namePattern) {
    Set<Role> roles = getMatchingRoles(namePattern);
    SortedMap<Role, Set<String>> roleMap = new TreeMap<Role, Set<String>>();
    for(Role role : roles) {
      roleMap.put(role, this.grantedRoles.get(role));
    }
    return new RoleMap(roleMap);
  }

  /**
   * Get all the roles holding the given permission.
   * @param permission The permission you want to check
   * @return A Set of Roles holding the given permission
   */
  private Set<Role> getRolesHavingPermission(final Permission permission) {
    final Set<Role> roles = new HashSet<Role>();
    final Set<Permission> permissions = new HashSet<Permission>();
    Permission p = permission;

    // Get the implying permissions
    for(; p!=null; p=p.impliedBy) {
      permissions.add(p);
    }
    // Walk through the roles, and only add the roles having the given permission,
    // or a permission implying the given permission
    new RoleWalker() {
      public void perform(Role current) {
        if(current.hasAnyPermission(permissions)) {
          roles.add(current);
        }
      }
    };

    return roles;
  }

  /**
   * Get all the roles whose pattern match the given pattern.
   * @param namePattern The string to match
   * @return A Set of Roles matching the given name
   */
  private Set<Role> getMatchingRoles(final String namePattern) {
    final Set<Role> roles = new HashSet<Role>();

    // Walk through the roles and only add the Roles whose pattern matches the given string
    new RoleWalker() {
      public void perform(Role current) {
        Matcher m = current.getPattern().matcher(namePattern);
        if(m.matches()) {
          roles.add(current);
        }
      }
    };

    return roles;
  }

  /**
   * The Acl class that will delegate the permission check to the {@link RoleMap} object.
   */
  private final class AclImpl extends SidACL {

    AccessControlled item;
    RoleType roleType;
    /**Makes SID to convert all SIDs to lower-case*/
    boolean ignoresCase;

    public AclImpl(RoleType roleType, AccessControlled item, boolean ignoresCase) {
        this.item = item;
        this.roleType = roleType;
        this.ignoresCase = ignoresCase;
    }
      
    /**
     * Checks if the sid has the given permission.
     * <p>Actually only delegate the check to the {@link RoleMap} instance.</p>
     * @param p The sid to check
     * @param permission The permission to check
     * @return True if the sid has the given permission
     */
    @Override
    protected Boolean hasPermission(Sid p, Permission permission) {
      String effectiveSID = ignoresCase ? toString(p).toLowerCase() : toString(p);
      if(RoleMap.this.hasPermission(effectiveSID, permission, roleType, item)) {
        return true;
      }
      return null;
    }
  }

  /**
   * A class to walk through all the {@link RoleMap}'s roles and perform an
   * action on each one.
   */
  private abstract class RoleWalker {

    RoleWalker() {
      walk();
    }

    /**
     * Walk through the roles.
     */
    public void walk() {
      Set<Role> roles = RoleMap.this.getRoles();
      Iterator iter = roles.iterator();
      while (iter.hasNext()) {
        Role current = (Role) iter.next();
        perform(current);
      }
    }

    /**
     * The method to implement which will be called on each {@link Role}.
     */
    abstract public void perform(Role current);
  }

}
