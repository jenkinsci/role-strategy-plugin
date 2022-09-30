package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
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
public class RoleDefinition implements Comparable<RoleDefinition> {

  public static final Logger LOGGER = Logger.getLogger(RoleDefinition.class.getName());
  private transient Role role;

  @NonNull
  private final String name;
  @CheckForNull
  private final String description;
  @CheckForNull
  private final String pattern;

  private String templateName;
  private final Set<String> permissions;

  private SortedSet<RoleDefinitionEntry> entries = Collections.emptySortedSet();

  /**
   * Creates a RoleDefinition.
   *
   * @param name        Role name
   * @param description Role description
   * @param pattern     Role pattern
   * @param permissions Assigned permissions
   */
  @DataBoundConstructor
  public RoleDefinition(String name, String description, String pattern, Collection<String> permissions) {
    this.name = name;
    this.description = description;
    this.pattern = pattern;
    this.permissions = permissions != null ? new HashSet<>(permissions) : Collections.emptySet();
  }

  /**
   * Legacy setter for string based assignments.
   *
   * @param assignments The assigned sids
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

  /**
   * Setter for entries.
   *
   * @param entries The permission entries
   */
  @DataBoundSetter
  public void setEntries(Collection<RoleDefinitionEntry> entries) {
    this.entries = entries != null ? new TreeSet<>(entries) : Collections.emptySortedSet();
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
      role = new Role(name, p, resolvedPermissions, description, templateName, entries.stream()
              .map(RoleDefinitionEntry::asPermissionEntry).collect(Collectors.toSet()));
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

  public String getTemplateName() {
    return templateName;
  }

  @DataBoundSetter
  public void setTemplateName(String templateName) {
    this.templateName = templateName;
  }

  public Set<String> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }

  /**
   * Deprecated, always returns null.
   *
   * @return null
   */
  public Collection<String> getAssignments() {
    return null;
  }

  public SortedSet<RoleDefinitionEntry> getEntries() {
    return entries;
  }

  @Override
  public int compareTo(@NonNull RoleDefinition o) {
    return this.name.compareTo(o.name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RoleDefinition that = (RoleDefinition) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  /**
   * Maps a permission entry to the casc line.
   */
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

    /**
     * Creates a RoleDefinitionEntry from a PermissionNetry.
     *
     * @param entry {@link PermissionEntry}
     * @return RoleDefinitionEntry
     */
    public static RoleDefinitionEntry fromPermissionEntry(PermissionEntry entry) {
      final RoleDefinitionEntry roleDefinitionEntry = new RoleDefinitionEntry();
      roleDefinitionEntry.type = entry.getType();
      roleDefinitionEntry.name = entry.getSid();
      return roleDefinitionEntry;
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, name);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RoleDefinitionEntry that = (RoleDefinitionEntry) o;
      return type == that.type && name.equals(that.name);
    }

    @Override
    public int compareTo(@NonNull RoleDefinitionEntry o) {
      int typeCompare = this.type.compareTo(o.type);
      if (typeCompare == 0) {
        return this.name.compareTo(o.name);
      }
      return typeCompare;
    }
  }
}
