package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionTemplate;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.impl.attributes.MultivaluedAttribute;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Provides the configuration logic for Role Strategy plugin.
 *
 * @author Oleg Nenashev
 * @since 2.11
 */
@Extension(optional = true, ordinal = 2)
@Restricted({NoExternalUse.class})
public class RoleBasedAuthorizationStrategyConfigurator extends BaseConfigurator<RoleBasedAuthorizationStrategy> {

  @Override
  @NonNull
  public String getName() {
    return "roleStrategy";
  }

  @Override
  public Class<RoleBasedAuthorizationStrategy> getTarget() {
    return RoleBasedAuthorizationStrategy.class;
  }

  @NonNull
  @Override
  public Class getImplementedAPI() {
    return GrantedRoles.class;
  }

  @Override
  protected RoleBasedAuthorizationStrategy instance(Mapping map, ConfigurationContext context) throws ConfiguratorException {
    final Configurator<GrantedRoles> c = context.lookupOrFail(GrantedRoles.class);
    final GrantedRoles roles = c.configure(map.remove("roles"), context);
    final Set<PermissionTemplate> permissionTemplates = getPermissionTemplates(map, context);

    return new RoleBasedAuthorizationStrategy(roles.toMap(), permissionTemplates);
  }

  private static Set<PermissionTemplate> getPermissionTemplates(Mapping map, ConfigurationContext context) throws ConfiguratorException {
    final Configurator<PermissionTemplateDefinition> c = context.lookupOrFail(PermissionTemplateDefinition.class);
    Set<PermissionTemplate> permissionTemplates = new TreeSet<>();
    CNode sub = map.remove("permissionTemplates");
    if (sub != null) {
      for (CNode o : sub.asSequence()) {
        PermissionTemplateDefinition template = c.configure(o, context);
        permissionTemplates.add(template.getPermissionTemplate());
      }
    }
    return permissionTemplates;
  }

  @Override
  protected void configure(Mapping config, RoleBasedAuthorizationStrategy instance, boolean dryrun,
                           ConfigurationContext context) throws ConfiguratorException {
    super.configure(config, instance, dryrun, context);

    if (!dryrun) {
      instance.validateConfig();
    }
  }

  @Override
  @NonNull
  public Set<Attribute<RoleBasedAuthorizationStrategy, ?>> describe() {
    return new HashSet<>(Arrays.asList(
            new Attribute<RoleBasedAuthorizationStrategy, GrantedRoles>("roles", GrantedRoles.class).getter(target -> {
              List<RoleDefinition> globalRoles = getRoleDefinitions(target.getGrantedRolesEntries(RoleType.Global));
              List<RoleDefinition> agentRoles = getRoleDefinitions(target.getGrantedRolesEntries(RoleType.Slave));
              List<RoleDefinition> projectRoles = getRoleDefinitions(target.getGrantedRolesEntries(RoleType.Project));
              return new GrantedRoles(globalRoles, projectRoles, agentRoles);
            }),
            new MultivaluedAttribute<RoleBasedAuthorizationStrategy, PermissionTemplateDefinition>("permissionTemplates",
                PermissionTemplateDefinition.class).getter(target -> getPermissionTemplateDefinitions(target.getPermissionTemplates()))
    ));
  }

  @CheckForNull
  @Override
  public CNode describe(RoleBasedAuthorizationStrategy instance, ConfigurationContext context) throws Exception {
    return compare(instance, new RoleBasedAuthorizationStrategy(Collections.emptyMap()), context);
  }

  private List<PermissionTemplateDefinition> getPermissionTemplateDefinitions(Set<PermissionTemplate> permissionTemplates) {
    if (permissionTemplates == null) {
      return Collections.emptyList();
    }
    return permissionTemplates.stream().map(getPermissionTemplateDefinition()).collect(Collectors.toList());
  }

  private Function<PermissionTemplate, PermissionTemplateDefinition> getPermissionTemplateDefinition() {
    return permissionTemplate -> {
      List<String> permissions = permissionTemplate.getPermissions().stream()
              .map(permission -> permission.group.getId() + "/" + permission.name).collect(Collectors.toList());
      return new PermissionTemplateDefinition(permissionTemplate.getName(), permissions);
    };
  }

  private List<RoleDefinition> getRoleDefinitions(@CheckForNull SortedMap<Role, Set<PermissionEntry>> roleMap) {
    if (roleMap == null) {
      return Collections.emptyList();
    }
    return roleMap.entrySet().stream().map(getRoleDefinition()).collect(Collectors.toList());
  }

  private Function<Map.Entry<Role, Set<PermissionEntry>>, RoleDefinition> getRoleDefinition() {
    return roleSetEntry -> {
      Role role = roleSetEntry.getKey();
      List<String> permissions = role.getPermissions().stream()
              .map(permission -> permission.group.title.toString(
                      Locale.US) + "/" + permission.name).collect(Collectors.toList());
      Set<RoleDefinition.RoleDefinitionEntry> roleDefinitionEntries = roleSetEntry.getValue().stream()
              .map(RoleDefinition.RoleDefinitionEntry::fromPermissionEntry)
              .collect(Collectors.toSet());
      final RoleDefinition roleDefinition = new RoleDefinition(role.getName(), role.getDescription(),
              role.getPattern().pattern(), permissions);
      roleDefinition.setEntries(roleDefinitionEntries);
      roleDefinition.setTemplateName(role.getTemplateName());
      return roleDefinition;
    };
  }
}
