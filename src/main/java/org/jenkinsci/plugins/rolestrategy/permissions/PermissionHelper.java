/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

package org.jenkinsci.plugins.rolestrategy.permissions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Helper methods for permission handling.
 *
 * @author Oleg Nenashev
 */
@Restricted(NoExternalUse.class)
public class PermissionHelper {

  private static final Logger LOGGER = Logger.getLogger(PermissionHelper.class.getName());

  private static final Pattern PERMISSION_PATTERN = Pattern.compile("^([^\\/]+)\\/(.+)$");

  private PermissionHelper() {
    // Cannot be constructed
  }

  /**
   * Convert a set of string to a collection of permissions.
   * Non-solvable permissions are ignored
   *
   * @param permissionStrings A list of Permission IDs or UI names.
   * @param allowPermissionId Allow to resolve the permission from the ID.
   * @return Created set of permissions
   */
  @NonNull
  public static Set<Permission> fromStrings(@CheckForNull Collection<String> permissionStrings, boolean allowPermissionId) {
    if (permissionStrings == null) {
      return Collections.emptySet();
    }

    HashSet<Permission> res = new HashSet<>(permissionStrings.size());
    for (String permission : permissionStrings) {
      final Permission p = allowPermissionId ? resolvePermissionFromString(permission) : findPermission(permission);
      if (p == null) {
        LOGGER.log(Level.WARNING, "Ignoring unresolved permission: "  + permission);
        // throw IllegalArgumentException?
        continue;
      }
      res.add(p);
    }
    return res;
  }

  /**
   * Attempt to match a given permission to what is defined in the UI.
   *
   * @param id String of the form "Title/Permission" (Look in the UI) for a particular permission
   * @return a matched permission
   */
  @CheckForNull
  public static Permission findPermission(String id) {
    final String resolvedId = findPermissionId(id);
    return resolvedId != null ? Permission.fromId(resolvedId) : null;
  }

  /**
   * Attempt to match a given permission to what is defined in the UI.
   *
   * @param id String of the form "Title/Permission" (Look in the UI) for a particular permission
   * @return a matched permission ID
   */
  @CheckForNull
  public static String findPermissionId(String id) {
    if (id == null) {
      return null;
    }
    List<PermissionGroup> pgs = PermissionGroup.getAll();
    Matcher m = PERMISSION_PATTERN.matcher(id);
    if (m.matches()) {
      String owner = m.group(1);
      String name = m.group(2);
      for (PermissionGroup pg : pgs) {
        if (pg.owner.equals(Permission.class)) {
          continue;
        }
        if (pg.getId().equals(owner)) {
          return pg.owner.getName() + "." + name;
        }
      }
    }
    return null;
  }

  private static @CheckForNull Permission getSafePermission(String id) {
    return Permission.fromId(id);
  }

  /**
   * Attempt to match a given permission to what is defined in the UI or from the ID representation used in the config.xml.
   *
   * @param id String of the form "Title/Permission" (Look in the UI) for a particular permission or in the form used in the config.xml
   * @return a matched permission, null if permission couldn't be resolved
   */
  @CheckForNull
  public static Permission resolvePermissionFromString(String id) {
    Permission permission = Permission.fromId(id);
    if (permission == null) {
      permission = PermissionHelper.findPermission(id);
    }
    return permission;
  }
}
