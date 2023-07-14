package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.PermissionTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * PermissionTemplate definition. Used for custom formatting in Casc.
 */
@Restricted(NoExternalUse.class)
public class PermissionTemplateDefinition implements Comparable<PermissionTemplateDefinition> {

  private transient PermissionTemplate permissionTemplate;
  private final String name;

  private final Set<String> permissions;

  @DataBoundConstructor
  public PermissionTemplateDefinition(@NonNull String name, @CheckForNull Collection<String> permissions) {
    this.name = name;
    this.permissions = permissions != null ? new HashSet<>(permissions) : Collections.emptySet();
  }

  /**
   * Return the corresponding PermissionTemplate object.
   *
   * @return permission template
   */
  public final PermissionTemplate getPermissionTemplate() {
    if (permissionTemplate == null) {
      permissionTemplate = new PermissionTemplate(name, permissions);
    }
    return permissionTemplate;
  }

  public String getName() {
    return name;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  @Override
  public int compareTo(@NonNull PermissionTemplateDefinition o) {
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
    PermissionTemplateDefinition that = (PermissionTemplateDefinition) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

}
