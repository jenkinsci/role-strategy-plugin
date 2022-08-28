package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Role definition. Used for custom formatting
 *
 * @author Oleg Nenashev
 * @since 2.11
 */
@Restricted(NoExternalUse.class)
public class RoleDefinition {

  private transient Role role;

  @NonNull
  private final String name;
  @CheckForNull
  private final String description;
  @CheckForNull
  private final String pattern;

  private boolean generated;
  private final Set<String> permissions;
  private final Set<String> assignments;

  /**
   * Creates a RoleDefinition.
   *
   * @param name        Role name
   * @param description Role description
   * @param pattern     Role pattern
   * @param permissions Assigned permissions
   * @param assignments Assigned SIDs
   */
  @DataBoundConstructor
  public RoleDefinition(@NonNull String name, @CheckForNull String description, @CheckForNull String pattern,
      Collection<String> permissions, Collection<String> assignments) {
    this.name = name;
    this.description = description;
    this.pattern = pattern;
    this.permissions = permissions != null ? new HashSet<>(permissions) : Collections.emptySet();
    this.assignments = assignments != null ? new HashSet<>(assignments) : Collections.emptySet();
    this.role = getRole();
  }

  /**
   * Returns the corresponding Role object.
   *
   * @return Role
   */
  public final Role getRole() {
    if (role == null) {
      Set<Permission> resolvedPermissions = PermissionHelper.fromStrings(permissions, false);
      Pattern p = Pattern.compile(pattern != null ? pattern : Role.GLOBAL_ROLE_PATTERN);
      role = new Role(name, p, resolvedPermissions, description, generated);
    }
    return role;
  }

  @NonNull
  public String getName() {
    return name;
  }

  @CheckForNull
  public String getDescription() {
    return description;
  }

  @CheckForNull
  public String getPattern() {
    return pattern;
  }

  public boolean isGenerated() {
    return generated;
  }

  @DataBoundSetter
  public void setGenerated(boolean generated) {
    this.generated = generated;
  }

  public Set<String> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }

  public Set<String> getAssignments() {
    return Collections.unmodifiableSet(assignments);
  }

}
