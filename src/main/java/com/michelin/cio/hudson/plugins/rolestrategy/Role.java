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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Class representing a role, which holds a set of {@link Permission}s.
 * @author Thomas Maurel
 */
public final class Role implements Comparable {
  public static final String GLOBAL_ROLE_PATTERN = ".*";

  private static final Logger LOGGER = Logger.getLogger(Role.class.getName());

  /**
   * Name of the role.
   */
  private final String name;

  /**
   * Pattern to match the {@link AccessControlled} object name.
   */
  private final Pattern pattern;

  /**
   * Role description (optional).
   */
  @CheckForNull
  private final String description;

  /**
   * {@link Permission}s hold by the role.
   */
  private final Set < Permission > permissions = new HashSet<>();

  private transient Integer cachedHashCode = null;

  /**
   * Constructor for a global role with no pattern (which is then defaulted to
   * {@code .*}).
   * @param name The role name
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, Set < Permission > permissions) {
    this(name, GLOBAL_ROLE_PATTERN, permissions);
  }

  /**
   * Constructor for roles using a string pattern.
   * @param name The role name
   * @param pattern A string representing the pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, String pattern, Set < Permission > permissions) {
    this(name, Pattern.compile(pattern), permissions, null);
  }

  //TODO: comment is used for erasure cleanup only
  @DataBoundConstructor
  public Role(@NonNull String name, @CheckForNull String pattern, @CheckForNull Set <String> permissionIds, @CheckForNull String description) {
      this(name,
           Pattern.compile(pattern != null ? pattern : GLOBAL_ROLE_PATTERN),
           PermissionHelper.fromStrings(permissionIds),
           description);
  }

  /**
   * @param name The role name
   * @param pattern The pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role.
   *                    {@code null} permissions will be ignored.
   */
  Role(String name, Pattern pattern, Set < Permission > permissions, @CheckForNull String description) {
    this.name = name;
    this.pattern = pattern;
    this.description = description;
    for(Permission perm : permissions) {
        if(perm == null ){
           LOGGER.log(Level.WARNING, "Found some null permission(s) in role " + this.name, new IllegalArgumentException());
        } else {
            this.permissions.add(perm);
        }
    }
  }

  /**
   * Getter for the role name.
   * @return The role name
   */
  public String getName() {
    return name;
  }

  /**
   * Getter for the regexp pattern.
   * @return The pattern associated to the role
   */
  public Pattern getPattern() {
    return pattern;
  }

  /**
   * Getter for the {@link Permission}s set.
   * @return {@link Permission}s set
   */
  public Set < Permission > getPermissions() {
    return permissions;
  }

    /**
     * Gets the role description.
     * @return Role description. {@code null} if not set
     */
    @CheckForNull
    public String getDescription() {
        return description;
    }

    /**
   * Checks if the role holds the given {@link Permission}.
   * @param permission The permission you want to check
   * @return True if the role holds this permission
   */
  public Boolean hasPermission(Permission permission) {
    return permissions.contains(permission);
  }

  /**
   * Checks if the role holds any of the given {@link Permission}.
   * @param permissions A {@link Permission}s set
   * @return True if the role holds any of the given {@link Permission}s
   */
  public Boolean hasAnyPermission(Set<Permission> permissions) {
    return CollectionUtils.containsAny(this.permissions, permissions);
  }

  /**
   * Compare role names.
   * Used to sort the sets.
   * We presume that any role name is being used once and only once.
   * @param o The object you want to compare this instance to
   * @return Comparison of role names
   */
  @Override
  public int compareTo(@NonNull Object o) {
    Objects.requireNonNull(o);

    if (o instanceof Role) {
        return name.compareTo(((Role)o).name);
    }
    return -1;
  }

    @Override
    public int hashCode() {
        if (cachedHashCode == null) {
            cachedHashCode = _hashCode();
        }
        return cachedHashCode;
    }

    private int _hashCode() {
        int hash = 7;
        hash = 53 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 53 * hash + (this.pattern != null ? this.pattern.hashCode() : 0);
        hash = 53 * hash + (this.permissions != null ? this.permissions.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Role other = (Role) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.pattern, other.pattern)) {
            return false;
        }
        if (!Objects.equals(this.permissions, other.permissions)) {
            return false;
        }
        return true;
    }
}
