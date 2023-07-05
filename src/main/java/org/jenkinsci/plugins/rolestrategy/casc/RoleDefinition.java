package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import hudson.security.Permission;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Role definition.
 * Used for custom formatting
 * @author Oleg Nenashev
 * @since 2.11
 */
@Restricted(NoExternalUse.class)
public class RoleDefinition {

  public static final Logger LOGGER = Logger.getLogger(RoleDefinition.class.getName());
  private transient Role role;

  @NonNull
  private final String name;
  @CheckForNull
  private final String description;
  @CheckForNull
  private final String pattern;
  private final Set<String> permissions;

  /**
   * @since TODO (4.0?)
   */
  private SortedSet<RoleDefinitionEntry> entries = Collections.emptySortedSet();

  @DataBoundConstructor
  public RoleDefinition(String name, String description, String pattern, Collection<String> permissions) {
    this.name = name;
    this.description = description;
    this.pattern = pattern;
    this.permissions = permissions != null ? new HashSet<>(permissions) : Collections.emptySet();
    this.role = getRole();
  }

  /**
   * Legacy setter for string based assignments.
   *
   * @param assignments
   * @deprecated Use {@link #setEntries(java.util.Collection)} instead.
   */
  @DataBoundSetter
  @Deprecated
  public void setAssignments(Collection<String> assignments) {
    LOGGER.log(Level.WARNING, "Loading ambiguous role assignments via via configuration-as-code support");
    if (assignments != null) {
      SortedSet<RoleDefinitionEntry> entries = new TreeSet<>();
      for (String assignment : assignments) {
        final RoleDefinitionEntry rde = new RoleDefinitionEntry();
        rde.setEither(assignment);
        entries.add(rde);
      }
      this.entries = entries;
    }
  }

  @DataBoundSetter
  public void setEntries(Collection<RoleDefinitionEntry> entries) {
    this.entries = entries != null ? new TreeSet<>(entries) : Collections.emptySortedSet();
  }

  public final Role getRole() {
    if (role == null) {
      Set<Permission> resolvedPermissions = PermissionHelper.fromStrings(permissions, false);
      Pattern p = Pattern.compile(pattern != null ? pattern : Role.GLOBAL_ROLE_PATTERN);
      role = new Role(name, p, resolvedPermissions, description);
    }
    return role;
  }

  @NonNull
  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getPattern() {
    return pattern;
  }

  public Set<String> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }

  /**
   * Deprecated, always returns null
   * @return
   */
  public Collection<String> getAssignments() {
    return null;
  }

  public SortedSet<RoleDefinitionEntry> getEntries() {
    return entries;
  }

  public static class RoleDefinitionEntry implements Comparable<RoleDefinitionEntry> {
    private /* quasi-final */ AuthorizationType type;
    private /* quasi-final */ String name;

    @DataBoundConstructor
    public RoleDefinitionEntry() {
    }

    private void setTypeIfUndefined(AuthorizationType type) {
      if (this.type == null) {
        this.type = type;
      } else {
        throw new IllegalStateException("Cannot set two different types for '" + name + "'"); // TODO Add test for this
      }
    }

    @DataBoundSetter
    public void setUser(String name) {
      this.name = name;
      setTypeIfUndefined(AuthorizationType.USER);
    }

    @DataBoundSetter
    public void setGroup(String name) {
      this.name = name;
      setTypeIfUndefined(AuthorizationType.GROUP);
    }

    @DataBoundSetter
    public void setEither(String name) {
      this.name = name;
      setTypeIfUndefined(AuthorizationType.EITHER);
    }

    public String getUser() {
      return type == AuthorizationType.USER ? name : null;
    }
    public String getGroup() {
      return type == AuthorizationType.GROUP ? name : null;
    }
    public String getEither() {
      return type == AuthorizationType.EITHER ? name : null;
    }

    public PermissionEntry asPermissionEntry() {
      return new PermissionEntry(type, name);
    }

    public static RoleDefinitionEntry fromPermissionEntry(PermissionEntry entry) {
      final RoleDefinitionEntry roleDefinitionEntry = new RoleDefinitionEntry();
      roleDefinitionEntry.type = entry.getType();
      roleDefinitionEntry.name = entry.getSid();
      return roleDefinitionEntry;
    }

    @Override
    public int compareTo(@NonNull RoleDefinition.RoleDefinitionEntry o) {
      int typeCompare = this.type.compareTo(o.type);
      if (typeCompare == 0) {
        return this.name.compareTo(o.name);
      }
      return typeCompare;
    }
  }
}
