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

import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.collections.CollectionUtils;

/** 
 * Class representing a role, which holds a set of {@link Permission}s.
 * @author Thomas Maurel
 */
public final class Role implements Comparable {

  /**
   * Name of the role.
   */
  private final String name;

  /**
   * Pattern to match the {@link AccessControlled} object name.
   */
  private final Pattern pattern;

  /**
   * {@link Permission}s hold by the role.
   */
  private final Set < Permission > permissions = new HashSet < Permission > ();

  /**
   * Constructor for a global role with no pattern (which is then defaulted to
   * {@code .*}).
   * @param name The role name
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, Set < Permission > permissions) {
    this(name, ".*", permissions);
  }

  /**
   * Constructor for roles using a string pattern.
   * @param name The role name
   * @param pattern A string representing the pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, String pattern, Set < Permission > permissions) {
    this(name, Pattern.compile(pattern), permissions);
  }

  /**
   * @param name The role name
   * @param pattern The pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, Pattern pattern, Set < Permission > permissions) {
    this.name = name;
    this.pattern = pattern;
    this.permissions.addAll(permissions);
  }

  /**
   * Getter for the role name.
   * @return The role name
   */
  public final String getName() {
    return name;
  }

  /**
   * Getter for the regexp pattern.
   * @return The pattern associated to the role
   */
  public final Pattern getPattern() {
    return pattern;
  }

  /**
   * Getter for the {@link Permission}s set.
   * @return {@link Permission}s set
   */
  public final Set < Permission > getPermissions() {
    return permissions;
  }

  /**
   * Checks if the role holds the given {@link Permission}.
   * @param permission The permission you want to check
   * @return True if the role holds this permission
   */
  public final Boolean hasPermission(Permission permission) {
    return permissions.contains(permission);
  }

  /**
   * Checks if the role holds any of the given {@link Permission}.
   * @param permissions A {@link Permission}s set
   * @return True if the role holds any of the given {@link Permission}s
   */
  public final Boolean hasAnyPermission(Set<Permission> permissions) {
    return CollectionUtils.containsAny(this.permissions, permissions);
  }

  /**
   * Compare role names.
   * <p>Used to sort the set.</p>
   * @param o The object you want to compare this instance to
   * @return Comparison of role names
   */
  public int compareTo(Object o) {
    return this.name.compareTo(((Role) o).getName());
  }

}
