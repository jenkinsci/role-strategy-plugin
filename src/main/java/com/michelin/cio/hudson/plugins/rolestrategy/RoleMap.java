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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.synopsys.arc.jenkins.plugins.rolestrategy.Macro;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleMacroExtension;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.User;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.SidACL;
import jenkins.model.Jenkins;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.acls.sid.Sid;
import org.acegisecurity.userdetails.UserDetails;
import org.jenkinsci.plugins.rolestrategy.Settings;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class holding a map for each kind of {@link AccessControlled} object, associating
 * each {@link Role} with the concerned {@link User}s/groups.
 * @author Thomas Maurel
 */
public class RoleMap {

  /** Map associating each {@link Role} with the concerned {@link User}s/groups. */
  private final SortedMap <Role,Set<String>> grantedRoles;

  private static final Logger LOGGER = Logger.getLogger(RoleMap.class.getName());

  private static final ConcurrentMap<Permission, Set<Permission>> implyingPermissionCache = new ConcurrentHashMap<>();

  static {
    Permission.getAll().forEach(RoleMap::cacheImplyingPermissions);
  }

  private final Cache<String, UserDetails> cache = Caffeine.newBuilder()
          .maximumSize(Settings.USER_DETAILS_CACHE_MAX_SIZE)
          .expireAfterWrite(Settings.USER_DETAILS_CACHE_EXPIRATION_TIME_SEC, TimeUnit.SECONDS)
          .build();

  /**
   * {@link RoleMap}s are created again and again using {@link RoleMap#newMatchingRoleMap(String)}
   * for different permissions for the same {@code itemNamePrefix}, so cache them and avoid wasting time
   * matching regular expressions.
   */
  private final Cache<String, RoleMap> matchingRoleMapCache = Caffeine.newBuilder()
          .maximumSize(2048)
          .expireAfterWrite(1, TimeUnit.HOURS)
          .build();

  RoleMap() {
    this.grantedRoles = new ConcurrentSkipListMap<>();
  }

    /**
     * Constructor.
     * @param grantedRoles Roles to be granted.
     */
    @DataBoundConstructor
    public RoleMap(@NonNull SortedMap<Role,Set<String>> grantedRoles) {
        this();
        for (Map.Entry<Role,Set<String>> entry : grantedRoles.entrySet()) {
          this.grantedRoles.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }

  /**
   * Check if the given sid has the provided {@link Permission}.
   * @return True if the sid's granted permission
   */
  private boolean hasPermission(String sid, Permission permission, RoleType roleType, AccessControlled controlledItem) {
    final Set<Permission> permissions = getImplyingPermissions(permission);
    final boolean[] hasPermission = { false };

    // Walk through the roles, and only add the roles having the given permission,
    // or a permission implying the given permission
    new RoleWalker() {
      public void perform(Role current) {
        if (current.hasAnyPermission(permissions)) {
          if (grantedRoles.get(current).contains(sid)) {
            // Handle roles macro
            if (Macro.isMacro(current)) {
              Macro macro = RoleMacroExtension.getMacro(current.getName());
              if (macro != null) {
                RoleMacroExtension macroExtension = RoleMacroExtension.getMacroExtension(macro.getName());
                if (macroExtension.IsApplicable(roleType) && macroExtension.hasPermission(sid, permission, roleType, controlledItem, macro)) {
                  hasPermission[0] = true;
                  abort();
                }
              }
            } else {
              hasPermission[0] = true;
              abort();
            }
          } else if (Settings.TREAT_USER_AUTHORITIES_AS_ROLES) {
            try {
              UserDetails userDetails = cache.getIfPresent(sid);
              if (userDetails == null) {
                userDetails = Jenkins.get().getSecurityRealm().loadUserByUsername(sid);
                cache.put(sid, userDetails);
              }
              for (GrantedAuthority grantedAuthority : userDetails.getAuthorities()) {
                if (grantedAuthority.getAuthority().equals(current.getName())) {
                  hasPermission[0] = true;
                  abort();
                  return;
                }
              }
            } catch (BadCredentialsException e) {
                LOGGER.log(Level.FINE, "Bad credentials", e);
            } catch (DataAccessException e) {
                LOGGER.log(Level.FINE, "failed to access the data", e);
            } catch (RuntimeException ex) {
                // There maybe issues in the logic, which lead to IllegalStateException in Acegi Security (JENKINS-35652)
                // So we want to ensure this method does not fail horribly in such case
                LOGGER.log(Level.WARNING, "Unhandled exception during user authorities processing", ex);
            }
          }
        }
      }
    };
    return hasPermission[0];
  }

  /**
   * Get the set of permissions which imply the permission {@code p}
   *
   * @param p find permissions that imply this permission
   * @return set of permissions which imply {@code p}
   */
  private static Set<Permission> getImplyingPermissions(Permission p) {
    Set<Permission> implyingPermissions = implyingPermissionCache.get(p);
    if (implyingPermissions != null) {
      return implyingPermissions;
    } else {
      return cacheImplyingPermissions(p);
    }
  }

  /**
   * Finds the implying permissions and caches them for future use.
   *
   * @param permission the permission for which to cache implying permissions
   * @return a set of permissions that imply this permission (including itself)
   */
  private static Set<Permission> cacheImplyingPermissions(Permission permission) {
    Set<Permission> implyingPermissions;
    if (PermissionHelper.isDangerous(permission)) {
      /* if this is a dangerous permission, fall back to Administer unless we're in compat mode */
      implyingPermissions = getImplyingPermissions(Jenkins.ADMINISTER);
    } else {
      implyingPermissions = new HashSet<>();

      // Get the implying permissions
      for (Permission p = permission; p != null; p = p.impliedBy) {
        implyingPermissions.add(p);
      }
    }

    implyingPermissionCache.put(permission, implyingPermissions);
    return implyingPermissions;
  }

  /**
   * Check if the {@link RoleMap} contains the given {@link Role}.
   *
   * @param role Role to be checked
   * @return {@code true} if the {@link RoleMap} contains the given role
   */
  public boolean hasRole(@NonNull Role role) {
    return this.grantedRoles.containsKey(role);
  }

  /**
   * Get the ACL for the current {@link RoleMap}.
   * @return ACL for the current {@link RoleMap}
   */
  public SidACL getACL(RoleType roleType, AccessControlled controlledItem) {
    return new AclImpl(roleType, controlledItem);
  }

  /**
   * Add the given role to this {@link RoleMap}.
   * @param role The {@link Role} to add
   */
  public void addRole(Role role) {
      if (this.getRole(role.getName()) == null) {
          this.grantedRoles.put(role, new CopyOnWriteArraySet<>());
          matchingRoleMapCache.invalidateAll();
      }
  }

  /**
   * Assign the sid to the given {@link Role}.
   * @param role The {@link Role} to assign the sid to
   * @param sid The sid to assign
   */
  public void assignRole(Role role, String sid) {
    if (this.hasRole(role)) {
      this.grantedRoles.get(role).add(sid);
      matchingRoleMapCache.invalidateAll();
    }
  }

  /**
   * unAssign the sid to the given {@link Role}.
   * @param role The {@link Role} to unassign the sid to
   * @param sid The sid to assign
   * @since 2.6.0
   */
  public void unAssignRole(Role role, String sid) {
      Set<String> sids = grantedRoles.get(role);
      if (sids != null) {
        sids.remove(sid);
        matchingRoleMapCache.invalidateAll();
      }
  }

  /**
   * Clear all the sids associated to the given {@link Role}.
   * @param role The {@link Role} for which you want to clear the sids
   */
  public void clearSidsForRole(Role role) {
    if (this.hasRole(role)) {
      this.grantedRoles.get(role).clear();
      matchingRoleMapCache.invalidateAll();
    }
  }

  /**
   * Clear all the roles associated to the given sid
   * @param sid The sid for which you want to clear the {@link Role}s
   */
  public void deleteSids(String sid){
     for (Map.Entry<Role, Set<String>> entry: grantedRoles.entrySet()) {
       Set<String> sids = entry.getValue();
       sids.remove(sid);
     }
    matchingRoleMapCache.invalidateAll();
  }

  /**
   * Clear specific role associated to the given sid
   * @param sid The sid for thwich you want to clear the {@link Role}s
   * @param rolename The role for thwich you want to clear the {@link Role}s
   * @since 2.6.0
   */
  public void deleteRoleSid(String sid, String rolename){
     for (Map.Entry<Role, Set<String>> entry: grantedRoles.entrySet()) {
         Role role = entry.getKey();
         if (role.getName().equals(rolename)) {
            unAssignRole(role, sid);
            break;
         }
     }
  }

  /**
   * Clear all the sids for each {@link Role} of the {@link RoleMap}.
   */
  public void clearSids() {
    for (Map.Entry<Role, Set<String>> entry : this.grantedRoles.entrySet()) {
      Role role = entry.getKey();
      this.clearSidsForRole(role);
    }
  }

  /**
   * Get the {@link Role} object named after the given param.
   * @param name The name of the {@link Role}
   * @return The {@link Role} named after the given param.
   *         {@code null} if the role is missing.
   */
  @CheckForNull
  public Role getRole(String name) {
    for (Role role : this.getRoles()) {
      if (role.getName().equals(name)) {
        return role;
      }
    }
    return null;
  }

  /**
   * Removes a {@link Role}
   * @param role The {@link Role} which shall be removed
   */
  public void removeRole(Role role){
      this.grantedRoles.remove(role);
      matchingRoleMapCache.invalidateAll();
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
    TreeSet<String> sids = new TreeSet<>();
    for (Map.Entry<Role, Set<String>> entry : this.grantedRoles.entrySet()) {
      sids.addAll(entry.getValue());
    }
    // Remove the anonymous sid if asked to
    if (!includeAnonymous) {
      sids.remove("anonymous");
    }
    return Collections.unmodifiableSortedSet(sids);
  }

  /**
   * Get all the sids assigned to the {@link Role} named after the {@code roleName} param.
   * @param roleName The name of the role
   * @return A sorted set containing all the sids.
   *         {@code null} if the role is missing.
   */
  @CheckForNull
  public Set<String> getSidsForRole(String roleName) {
    Role role = this.getRole(roleName);
    if (role != null) {
      return Collections.unmodifiableSet(this.grantedRoles.get(role));
    }
    return null;
  }

  /**
   * Create a sub-map of this {@link RoleMap} containing {@link Role}s that are applicable
   * on the given {@code itemNamePrefix}.
   *
   * @param itemNamePrefix the name of the {@link hudson.model.AbstractItem} or {@link hudson.model.Computer}
   * @return A {@link RoleMap} containing roles that are applicable on the itemNamePrefix
   */
  public RoleMap newMatchingRoleMap(String itemNamePrefix) {
    return matchingRoleMapCache.get(itemNamePrefix, this::createMatchingRoleMap);
  }

  private RoleMap createMatchingRoleMap(String itemNamePrefix) {
    SortedMap<Role, Set<String>> roleMap = new TreeMap<>();
    new RoleWalker() {
      public void perform(Role current) {
        Matcher m = current.getPattern().matcher(itemNamePrefix);
        if (m.matches()) {
          roleMap.put(current, grantedRoles.get(current));
        }
      }
    };
    return new RoleMap(roleMap);
  }

  /**
   * Get all job names matching the given pattern, viewable to the requesting user
   * @param pattern Pattern to match against
   * @param maxJobs Max matching jobs to look for
   * @return List of matching job names
   */
  public static List<String> getMatchingJobNames(Pattern pattern, int maxJobs) {
      List<String> matchingJobNames = new ArrayList<>();
      for (Item i : Jenkins.get().allItems(Item.class, i -> pattern.matcher(i.getFullName()).matches())) {
        if (matchingJobNames.size() >= maxJobs) break;
        matchingJobNames.add(i.getFullName());
      }
      return matchingJobNames;
  }

  /**
   * The Acl class that will delegate the permission check to the {@link RoleMap} object.
   */
  private final class AclImpl extends SidACL {

    AccessControlled item;
    RoleType roleType;

    public AclImpl(RoleType roleType, AccessControlled item) {
        this.item = item;
        this.roleType = roleType;
    }

    /**
     * Checks if the sid has the given permission.
     * <p>Actually only delegate the check to the {@link RoleMap} instance.</p>
     * @param sid The sid to check
     * @param permission The permission to check
     * @return True if the sid has the given permission
     */
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "As declared in Jenkins API")
    @Override
    @CheckForNull
    protected Boolean hasPermission(Sid sid, Permission permission) {
      if (RoleMap.this.hasPermission(toString(sid), permission, roleType, item)) {
        if (item instanceof Item) {
          final ItemGroup parent = ((Item)item).getParent();
          if (parent instanceof Item && (Item.DISCOVER.equals(permission) || Item.READ.equals(permission)) && shouldCheckParentPermissions()) {
            // For READ and DISCOVER permission checks, do the same permission check on the parent
            Permission requiredPermissionOnParent = permission == Item.DISCOVER ? Item.DISCOVER : Item.READ;
            if (!((Item) parent).hasPermission(requiredPermissionOnParent)) {
              return null;
            }
          }
        }
        return true;
      }
      return null;
    }
  }

  private static boolean shouldCheckParentPermissions() {
    // TODO Switch to SystemProperties in 2.236+
    String propertyName = RoleMap.class.getName() + ".checkParentPermissions";
    String value = System.getProperty(propertyName);
    if (value == null) {
      return true;
    }
    return Boolean.parseBoolean(value);
  }

  /**
   * A class to walk through all the {@link RoleMap}'s roles and perform an
   * action on each one.
   */
  private abstract class RoleWalker {
    boolean shouldAbort=false;
    RoleWalker() {
      walk();
    }
    /**
     * Aborts the iterations.
     * The method can be used from RoleWalker callbacks to preemptively abort the execution loops on some conditions.
     * @since 2.10
     */
    public void abort() {
      this.shouldAbort=true;
    }

    /**
     * Walk through the roles.
     */
    public void walk() {
      Set<Role> roles = RoleMap.this.getRoles();
      for (Role current : roles) {
        perform(current);
        if (shouldAbort) {
          break;
        }
      }
    }

    /**
     * The method to implement which will be called on each {@link Role}.
     */
    abstract public void perform(Role current);
  }
}
