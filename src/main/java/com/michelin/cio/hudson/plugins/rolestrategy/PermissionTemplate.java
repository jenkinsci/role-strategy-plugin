package com.michelin.cio.hudson.plugins.rolestrategy;

import hudson.security.Permission;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Holds a set of permissions for the role generator.
 */
@Restricted(NoExternalUse.class)
public class PermissionTemplate {

  private static final Logger LOGGER = Logger.getLogger(PermissionTemplate.class.getName());

  private final String name;
  private final Set<Permission> permissions = new HashSet<>();

  /**
   * Create a new PermissionTemplate.
   *
   * @param name the name of the template
   * @param permissions the set of permissions of this template
   */
  @DataBoundConstructor
  public PermissionTemplate(String name, Set<String> permissions) {
    this(PermissionHelper.fromStrings(permissions, true), name);
  }

  /**
   * Create a new PermissionTemplate.
   *
   * @param name the name of the template
   * @param permissions the set of permissions of this template
   */
  public PermissionTemplate(Set<Permission> permissions, String name) {
    this.name = name;
    for (Permission perm : permissions) {
      if (perm == null) {
        LOGGER.log(Level.WARNING, "Found some null permission(s) in role " + this.name, new IllegalArgumentException());
      } else {
        this.permissions.add(perm);
      }
    }
  }

  public String getName() {
    return name;
  }

  public Set<Permission> getPermissions() {
    return Collections.unmodifiableSet(permissions);
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

  @Override
  public int hashCode() {
    return Objects.hash(name, permissions);
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
    final PermissionTemplate other = (PermissionTemplate) obj;
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    if (!Objects.equals(this.permissions, other.permissions)) {
      return false;
    }
    return true;
  }

}
