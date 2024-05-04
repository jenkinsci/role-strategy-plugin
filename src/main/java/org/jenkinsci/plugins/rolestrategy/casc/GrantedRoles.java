package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Casc wrapper for Roles.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @since 2.11
 */
@Restricted(NoExternalUse.class)
public class GrantedRoles {

  private final Set<RoleDefinition> global;

  private final Set<RoleDefinition> items;

  private final Set<RoleDefinition> agents;

  /**
   * Create GrantedRoles.
   *
   * @param global List of global Roles
   * @param items  List of item Roles
   * @param agents List of agent Roles
   */
  @DataBoundConstructor
  public GrantedRoles(Set<RoleDefinition> global, Set<RoleDefinition> items, Set<RoleDefinition> agents) {
    this.global = global != null ? new TreeSet<>(global) : Collections.emptySet();
    this.items = items != null ? new TreeSet<>(items) : Collections.emptySet();
    this.agents = agents != null ? new TreeSet<>(agents) : Collections.emptySet();
  }

  protected Map<String, RoleMap> toMap() {
    Map<String, RoleMap> grantedRoles = new HashMap<>();
    if (global != null) {
      grantedRoles.put(RoleBasedAuthorizationStrategy.GLOBAL, retrieveRoleMap(global));
    }
    if (items != null) {
      grantedRoles.put(RoleBasedAuthorizationStrategy.PROJECT, retrieveRoleMap(items));
    }
    if (agents != null) {
      grantedRoles.put(RoleBasedAuthorizationStrategy.SLAVE, retrieveRoleMap(agents));
    }
    return grantedRoles;
  }

  @NonNull
  private RoleMap retrieveRoleMap(Set<RoleDefinition> definitions) {
    TreeMap<Role, Set<PermissionEntry>> resMap = new TreeMap<>();
    for (RoleDefinition definition : definitions) {
      resMap.put(definition.getRole(),
              definition.getEntries().stream().map(RoleDefinition.RoleDefinitionEntry::asPermissionEntry).collect(Collectors.toSet()));
    }
    return new RoleMap(resMap);
  }

  public Set<RoleDefinition> getGlobal() {
    return global;
  }

  public Set<RoleDefinition> getItems() {
    return items;
  }

  public Set<RoleDefinition> getAgents() {
    return agents;
  }
}
