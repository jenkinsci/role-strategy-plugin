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
import hudson.Util;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Class representing a role, which holds a set of {@link Permission}s.
 *
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
   * Full names of the items this role is scoped to, or {@code null} when the role matches by {@link #pattern}.
   *
   * <p>A folder-scoped role applies to every listed item and to everything nested below it, compared by whole path
   * segments, so no regular expression is evaluated when checking permissions. {@link #pattern} still holds an
   * equivalent regular expression so that callers of {@link #getPattern()} and older plugin versions reading the
   * stored configuration keep seeing the same scope.
   */
  @CheckForNull
  private final SortedSet<String> folders;

  /**
   * Role description (optional).
   */
  @CheckForNull
  private final String description;

  /**
   * Flag to indicate that the role is template based.
   */
  @CheckForNull
  private String templateName;

  /**
   * {@link Permission}s hold by the role.
   */
  private Set<Permission> permissions;

  private transient Integer cachedHashCode = null;

  /**
   * Constructor for a global role with no pattern (which is then defaulted to {@code .*}).
   *
   * @param name        The role name
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, Set<Permission> permissions) {
    this(name, GLOBAL_ROLE_PATTERN, permissions);
  }

  /**
   * Constructor for roles using a string pattern.
   *
   * @param name        The role name
   * @param pattern     A string representing the pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role
   */
  Role(String name, String pattern, Set<Permission> permissions) {
    this(name, Pattern.compile(pattern), permissions, null);
  }

  // TODO: comment is used for erasure cleanup only
  @DataBoundConstructor
  public Role(@NonNull String name, @CheckForNull String pattern, @CheckForNull Set<String> permissionIds,
      @CheckForNull String description, String templateName) {
    this(name, Pattern.compile(pattern != null ? pattern : GLOBAL_ROLE_PATTERN), PermissionHelper.fromStrings(permissionIds, true),
        description, templateName);
  }

  public Role(@NonNull String name, @CheckForNull String pattern, @CheckForNull Set<String> permissionIds,
      @CheckForNull String description) {
    this(name, Pattern.compile(pattern != null ? pattern : GLOBAL_ROLE_PATTERN), PermissionHelper.fromStrings(permissionIds, true),
        description, "");
  }

  /**
   * Create a Role.
   *
   * @param name        The role name
   * @param pattern     The pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role. {@code null} permissions will be ignored.
   * @param description A description for the role
   */
  public Role(String name, Pattern pattern, Set<Permission> permissions, @CheckForNull String description) {
    this(name, pattern, permissions, description, "");
  }

  /**
   * Create a Role.
   *
   * @param name        The role name
   * @param pattern     The pattern matching {@link AccessControlled} objects names
   * @param permissions The {@link Permission}s associated to the role. {@code null} permissions will be ignored.
   * @param description A description for the role
   * @param templateName   True to mark this role as generated
   */
  public Role(String name, Pattern pattern, Set<Permission> permissions, @CheckForNull String description, String templateName) {
    this(name, pattern, null, permissions, description, templateName);
  }

  /**
   * Create a role scoped to folders instead of a pattern.
   *
   * <p>The role applies to each listed item and to everything nested below it. Entries are full item names such as
   * {@code Team/Special}; leading and trailing slashes and surrounding whitespace are ignored.
   *
   * @param name         The role name
   * @param folders      Full names of the items the role applies to; must contain at least one non-blank entry
   * @param permissions  The {@link Permission}s associated to the role. {@code null} permissions will be ignored.
   * @param description  A description for the role
   * @param templateName Name of the permission template the role takes its permissions from, if any
   * @return the new role
   * @throws IllegalArgumentException if {@code folders} has no usable entry
   */
  @Restricted(NoExternalUse.class)
  @NonNull
  public static Role forFolders(String name, @NonNull Collection<String> folders, Set<Permission> permissions,
      @CheckForNull String description, @CheckForNull String templateName) {
    SortedSet<String> normalized = normalizeFolders(folders);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("A folder-scoped role needs at least one folder");
    }
    return new Role(name, folderPattern(normalized), normalized, permissions, description, templateName);
  }

  private Role(String name, Pattern pattern, @CheckForNull SortedSet<String> folders, Set<Permission> permissions,
      @CheckForNull String description, String templateName) {
    this.name = name;
    this.pattern = pattern;
    this.folders = folders;
    this.description = description;
    this.templateName = templateName;
    this.permissions = new HashSet<>();
    for (Permission perm : permissions) {
      if (perm == null) {
        LOGGER.log(Level.WARNING, "Found some null permission(s) in role " + this.name, new IllegalArgumentException());
      } else {
        this.permissions.add(perm);
      }
    }
    cachedHashCode = _hashCode();
  }

  public void setTemplateName(@CheckForNull String templateName) {
    this.templateName = templateName;
  }

  public String getTemplateName() {
    return templateName;
  }

  /**
   * Getter for the role name.
   *
   * @return The role name
   */
  public String getName() {
    return name;
  }

  /**
   * Getter for the regexp pattern.
   *
   * <p>For a folder-scoped role this is an equivalent pattern derived from its folders; use {@link #matches(String)}
   * to check whether the role applies to a name.
   *
   * @return The pattern associated to the role
   */
  public Pattern getPattern() {
    return pattern;
  }

  /**
   * Full names of the items a folder-scoped role applies to.
   *
   * @return the folders, or {@code null} when the role matches by {@link #getPattern()}
   */
  @Restricted(NoExternalUse.class)
  @CheckForNull
  public SortedSet<String> getFolders() {
    return folders == null ? null : Collections.unmodifiableSortedSet(folders);
  }

  /**
   * Whether this role is scoped to folders rather than to a pattern.
   *
   * @return {@code true} for a folder-scoped role
   */
  @Restricted(NoExternalUse.class)
  public boolean isFolderScoped() {
    return folders != null;
  }

  /**
   * Checks whether this role applies to the item or agent with the given name.
   *
   * @param name the full name of an item, or the name of an agent
   * @return {@code true} if the role applies to it
   */
  public boolean matches(@NonNull String name) {
    if (folders == null) {
      return pattern.matcher(name).matches();
    }
    for (String folder : folders) {
      if (name.startsWith(folder) && (name.length() == folder.length() || name.charAt(folder.length()) == '/')) {
        return true;
      }
    }
    return false;
  }

  /**
   * Human readable description of what this role applies to, for messages shown to users.
   *
   * @return the folder list for a folder-scoped role, otherwise the pattern
   */
  @Restricted(NoExternalUse.class)
  @NonNull
  public String getScopeDescription() {
    return folders == null ? pattern.pattern() : String.join(", ", folders);
  }

  /**
   * Normalizes folder names entered by users or read from configuration: trims whitespace, drops leading and
   * trailing slashes and empty entries.
   *
   * @param folders the raw folder names
   * @return the normalized, sorted, de-duplicated folder names
   */
  @NonNull
  @Restricted(NoExternalUse.class)
  public static SortedSet<String> normalizeFolders(@CheckForNull Collection<String> folders) {
    SortedSet<String> result = new TreeSet<>();
    if (folders == null) {
      return result;
    }
    for (String folder : folders) {
      if (folder == null) {
        continue;
      }
      String f = folder.trim();
      int start = 0;
      int end = f.length();
      while (start < end && f.charAt(start) == '/') {
        start++;
      }
      while (end > start && f.charAt(end - 1) == '/') {
        end--;
      }
      f = f.substring(start, end).trim();
      if (!f.isEmpty()) {
        result.add(f);
      }
    }
    return result;
  }

  /**
   * The regular expression equivalent to a folder scope, kept for {@link #getPattern()} callers and for older plugin
   * versions reading the stored configuration. The descendant part is DOTALL because item names can contain line
   * separators such as U+2028, which {@link #matches(String)} accepts.
   */
  private static Pattern folderPattern(SortedSet<String> folders) {
    String alternatives = folders.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    return Pattern.compile("^(?:" + alternatives + ")(?s:/.*)?$");
  }

  /**
   * Getter for the {@link Permission}s set.
   *
   * @return {@link Permission}s set
   */
  public Set<Permission> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }

  /**
   * Update the permissions of the role.
   *
   * Internal use only.
   */
  private synchronized void setPermissions(Set<Permission> permissions) {
    this.permissions = new HashSet<>(permissions);
    cachedHashCode = _hashCode();
  }

  /**
   * Updates the permissions from the template matching the name.
   *
   * @param permissionTemplates List of templates to look for
   * @deprecated Use {@link #refreshPermissionsFromTemplate(PermissionTemplate)}
   */
  @Deprecated
  public void refreshPermissionsFromTemplate(Collection<PermissionTemplate> permissionTemplates) {
    if (Util.fixEmptyAndTrim(templateName) != null) {
      boolean found = false;
      for (PermissionTemplate pt : permissionTemplates) {
        if (pt.getName().equals(templateName)) {
          setPermissions(pt.getPermissions());
          found = true;
          break;
        }
      }
      if (! found) {
        this.templateName = null;
      }
    }
  }

  /**
   * Updates the permissions from the given template.
   *
   * The name of the given template must match the configured template name in the role.
   *
   * @param permissionTemplate PermissionTemplate
   */
  @Restricted(NoExternalUse.class)
  public void refreshPermissionsFromTemplate(@CheckForNull PermissionTemplate permissionTemplate) {
    if (permissionTemplate != null && templateName != null && templateName.equals(permissionTemplate.getName())) {
      setPermissions(permissionTemplate.getPermissions());
    }
  }

  /**
   * Gets the role description.
   *
   * @return Role description. {@code null} if not set
   */
  @CheckForNull
  public String getDescription() {
    return description;
  }

  /**
   * Checks if the role holds the given {@link Permission}.
   *
   * @param permission The permission you want to check
   * @return True if the role holds this permission
   */
  public Boolean hasPermission(Permission permission) {
    return permissions.contains(permission);
  }

  /**
   * Checks if the role holds any of the given {@link Permission}.
   *
   * @param permissions A {@link Permission}s set
   * @return True if the role holds any of the given {@link Permission}s
   */
  public Boolean hasAnyPermission(Set<Permission> permissions) {
    return CollectionUtils.containsAny(this.permissions, permissions);
  }

  /**
   * Compare role names. Used to sort the sets. We presume that any role name is being used once and only once.
   *
   * @param o The object you want to compare this instance to
   * @return Comparison of role names
   */
  @Override
  public int compareTo(@NonNull Object o) {
    Objects.requireNonNull(o);

    if (o instanceof Role) {
      return name.compareTo(((Role) o).name);
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

  @SuppressWarnings("checkstyle:MethodName")
  private int _hashCode() {
    int hash = 7;
    hash = 53 * hash + (this.name != null ? this.name.hashCode() : 0);
    hash = 53 * hash + (this.pattern != null ? this.pattern.hashCode() : 0);
    hash = 53 * hash + Objects.hashCode(this.folders);
    hash = 53 * hash + this.permissions.hashCode();
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
    if (!Objects.equals(this.folders, other.folders)) {
      return false;
    }
    return Objects.equals(this.permissions, other.permissions);
  }
}
