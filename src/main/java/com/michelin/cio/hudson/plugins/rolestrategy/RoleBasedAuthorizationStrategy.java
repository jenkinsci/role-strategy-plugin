/*
 * The MIT License
 *
 * Copyright (c) 2010-2017, Manufacture Française des Pneumatiques Michelin,
 * Thomas Maurel, Romain Seguy, Synopsys Inc., Oleg Nenashev and contributors
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

import static com.michelin.cio.hudson.plugins.rolestrategy.ValidationUtil.formatNonExistentUserGroupValidationResponse;
import static com.michelin.cio.hudson.plugins.rolestrategy.ValidationUtil.formatUserGroupValidationResponse;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractItem;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.View;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.security.SecurityRealm;
import hudson.security.SidACL;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.rolestrategy.AmbiguousSidsAdminMonitor;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;

/**
 * Role-based authorization strategy.
 *
 * @author Thomas Maurel
 */
public class RoleBasedAuthorizationStrategy extends AuthorizationStrategy {

  private static Logger LOGGER = Logger.getLogger(RoleBasedAuthorizationStrategy.class.getName());

  public static final String GLOBAL = "globalRoles";
  public static final String PROJECT = "projectRoles";
  public static final String SLAVE = "slaveRoles";
  public static final String PERMISSION_TEMPLATES = "permissionTemplates";

  public static final String MACRO_ROLE = "roleMacros";
  public static final String MACRO_USER = "userMacros";

  private final RoleMap agentRoles;
  private final RoleMap globalRoles;
  private final RoleMap itemRoles;
  private Map<String, PermissionTemplate> permissionTemplates;

  private static final boolean USE_ITEM_AND_AGENT_ROLES = SystemProperties.getBoolean(
            RoleBasedAuthorizationStrategy.class.getName() + ".useItemAndAgentRoles", false);

  /**
   * Create new RoleBasedAuthorizationStrategy.
   */
  public RoleBasedAuthorizationStrategy() {
    agentRoles = new RoleMap();
    globalRoles = new RoleMap();
    itemRoles = new RoleMap();
    permissionTemplates = new TreeMap<>();
  }

  /**
   * Creates a new {@link RoleBasedAuthorizationStrategy}.
   *
   * @param grantedRoles the roles in the strategy
   */
  public RoleBasedAuthorizationStrategy(Map<String, RoleMap> grantedRoles) {
    this(grantedRoles, null);
  }

  /**
   * Creates a new {@link RoleBasedAuthorizationStrategy}.
   *
   * @param grantedRoles the roles in the strategy
   * @param permissionTemplates the permission templates in the strategy
   */
  public RoleBasedAuthorizationStrategy(Map<String, RoleMap> grantedRoles, @CheckForNull Set<PermissionTemplate> permissionTemplates) {

    this.permissionTemplates = new TreeMap<>();
    if (permissionTemplates != null) {
      for (PermissionTemplate template : permissionTemplates) {
        this.permissionTemplates.put(template.getName(), template);
      }
    }

    RoleMap map = grantedRoles.get(SLAVE);
    agentRoles = map == null ? new RoleMap() : map;

    map = grantedRoles.get(GLOBAL);
    globalRoles = map == null ? new RoleMap() : map;

    map = grantedRoles.get(PROJECT);
    itemRoles = map == null ? new RoleMap() : map;
    refreshPermissionsFromTemplate();
  }

  public static final PermissionGroup GROUP =
          new PermissionGroup(RoleBasedAuthorizationStrategy.class, Messages._RoleBasedAuthorizationStrategy_PermissionGroupTitle());

  public static final Permission ITEM_ROLES_ADMIN = new Permission(
          GROUP,
          "ItemRoles",
          Messages._RoleBasedAuthorizationStrategy_ItemRolesAdminPermissionDescription(),
          Jenkins.ADMINISTER,
          USE_ITEM_AND_AGENT_ROLES,
          new PermissionScope[]{PermissionScope.JENKINS});

  public static final Permission AGENT_ROLES_ADMIN = new Permission(
          GROUP,
          "AgentRoles",
          Messages._RoleBasedAuthorizationStrategy_AgentRolesAdminPermissionDescription(),
          Jenkins.ADMINISTER,
          USE_ITEM_AND_AGENT_ROLES,
          new PermissionScope[]{PermissionScope.JENKINS});

  @Restricted(NoExternalUse.class) // called by jelly
  public static final Permission[] SYSTEM_READ_AND_ITEM_ROLES_ADMIN =
          new Permission[] { Jenkins.SYSTEM_READ, ITEM_ROLES_ADMIN };

  @Restricted(NoExternalUse.class) // called by jelly
  public static final Permission[] SYSTEM_READ_AND_SOME_ROLES_ADMIN =
          new Permission[] { Jenkins.SYSTEM_READ, ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN };

  @SuppressFBWarnings(value = "MS_PKGPROTECT", justification = "Used by jelly pages")
  @Restricted(NoExternalUse.class) // called by jelly
  public static final Permission[] ADMINISTER_AND_SOME_ROLES_ADMIN =
          new Permission[] { Jenkins.ADMINISTER, ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN };

  /**
   * Refresh item permissions from templates.
   */
  private void refreshPermissionsFromTemplate() {
    SortedMap<Role, Set<PermissionEntry>> roles = getGrantedRolesEntries(RoleBasedAuthorizationStrategy.PROJECT);
    for (Role role : roles.keySet()) {
      if (Util.fixEmptyAndTrim(role.getTemplateName()) != null) {
        role.refreshPermissionsFromTemplate(permissionTemplates.get(role.getTemplateName()));
      }
    }
  }

  /**
   * Get the root ACL.
   *
   * @return The global ACL
   */
  @Override
  @NonNull
  public SidACL getRootACL() {
    return globalRoles.getACL(RoleType.Global, null);
  }

  /**
   * Get the {@link RoleMap} corresponding to the {@link RoleType}.
   *
   * @param roleType the type of the role
   * @return the {@link RoleMap} corresponding to the {@code roleType}
   * @throws IllegalArgumentException for an invalid {@code roleType}
   */
  @NonNull
  @Restricted(NoExternalUse.class)
  public RoleMap getRoleMap(RoleType roleType) {
    switch (roleType) {
      case Global:
        return globalRoles;
      case Project:
        return itemRoles;
      case Slave:
        return agentRoles;
      default:
        throw new IllegalArgumentException("Unknown RoleType: " + roleType);
    }
  }

  /**
   * Get the specific ACL for projects.
   *
   * @param project The access-controlled project
   * @return The project specific ACL
   */
  @Override
  @NonNull
  public ACL getACL(@NonNull Job<?, ?> project) {
    return getACL((AbstractItem) project);
  }

  @Override
  @NonNull
  public ACL getACL(@NonNull AbstractItem project) {
    return itemRoles.newMatchingRoleMap(project.getFullName()).getACL(RoleType.Project, project).newInheritingACL(getRootACL());
  }

  @Override
  @NonNull
  public ACL getACL(@NonNull Computer computer) {
    return agentRoles.newMatchingRoleMap(computer.getName()).getACL(RoleType.Slave, computer).newInheritingACL(getRootACL());
  }

  @Override
  @NonNull
  public ACL getACL(@NonNull Node node) {
    return agentRoles.newMatchingRoleMap(node.getNodeName()).getACL(RoleType.Slave, node).newInheritingACL(getRootACL());
  }

  /**
   * Used by the container realm.
   *
   * @return All the sids referenced by the strategy
   */
  @Override
  @NonNull
  public Collection<String> getGroups() {
    Set<String> sids = new HashSet<>();

    sids.addAll(filterRoleSids(globalRoles));
    sids.addAll(filterRoleSids(itemRoles));
    sids.addAll(filterRoleSids(agentRoles));
    return sids;
  }

  private Set<String> filterRoleSids(RoleMap roleMap) {
    return roleMap.getSidEntries(false).stream().filter(entry -> entry.getType() != AuthorizationType.USER)
        .map(PermissionEntry::getSid).collect(Collectors.toSet());
  }

  /**
   * Get the roles from the global {@link RoleMap}.
   * <p>The returned sorted map is unmodifiable.
   * </p>
   *
   * @param type The object type controlled by the {@link RoleMap}
   * @return All roles from the global {@link RoleMap}.
   * @deprecated Use {@link RoleBasedAuthorizationStrategy#getGrantedRolesEntries(RoleType)}
   */
  @Nullable
  @Deprecated
  public SortedMap<Role, Set<String>> getGrantedRoles(String type) {
    return getRoleMap(RoleType.fromString(type)).getGrantedRoles();
  }

  /**
   * Get the {@link Role}s and the sids assigned to them for the given {@link RoleType}.
   *
   * @param type the type of the role
   * @return roles mapped to the set of user sids assigned to that role
   * @since 2.12
   * @deprecated use {@link #getGrantedRolesEntries(RoleType)}
   */
  @Deprecated
  public SortedMap<Role, Set<String>> getGrantedRoles(@NonNull RoleType type) {
    return getRoleMap(type).getGrantedRoles();
  }

  /**
   * Get the permission templates.
   *
   * @return set of permission templates.
   */
  public Set<PermissionTemplate> getPermissionTemplates() {
    return Set.copyOf(permissionTemplates.values());
  }

  @CheckForNull
  public PermissionTemplate getPermissionTemplate(String templateName) {
    return permissionTemplates.get(templateName);
  }

  public boolean hasPermissionTemplate(String name) {
    return permissionTemplates.containsKey(name);
  }

  /**
   * Get the {@link Role}s and the sids assigned to them for the given {@link RoleType}.
   *
   * @param type the type of the role
   * @return roles mapped to the set of user sids assigned to that role
   */
  public SortedMap<Role, Set<PermissionEntry>> getGrantedRolesEntries(@NonNull String type) {
    return getGrantedRolesEntries(RoleType.fromString(type));
  }

  /**
   * Get the {@link Role}s and the sids assigned to them for the given {@link RoleType}.
   *
   * @param type the type of the role
   * @return roles mapped to the set of user sids assigned to that role
   */
  public SortedMap<Role, Set<PermissionEntry>> getGrantedRolesEntries(@NonNull RoleType type) {
    return getRoleMap(type).getGrantedRolesEntries();
  }


  /**
   * Get all the SIDs referenced by specified {@link RoleMap} type.
   *
   * @param type The object type controlled by the {@link RoleMap}
   * @return All SIDs from the specified {@link RoleMap}.
   */
  public Set<PermissionEntry> getSidEntries(String type) {
    return getRoleMap(RoleType.fromString(type)).getSidEntries();
  }


  /**
   * Get all the SIDs referenced by specified {@link RoleMap} type.
   *
   * @param type The object type controlled by the {@link RoleMap}
   * @return All SIDs from the specified {@link RoleMap}.
   * @deprecated use {@link #getSidEntries(String)}
   */
  @Deprecated
  @CheckForNull
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public Set<String> getSIDs(String type) {
    return getRoleMap(RoleType.fromString(type)).getSids();
  }

  /**
   * Returns a map associating a {@link RoleType} with each {@link RoleMap}.
   * <p>
   * This method is intended to be used for XML serialization purposes (take a look at the {@link ConverterImpl}) and, as
   * such, must remain private since it exposes all the security config.
   * </p>
   */
  @NonNull
  private Map<RoleType, RoleMap> getRoleMaps() {
    Map<RoleType, RoleMap> roleMaps = new HashMap<>();
    roleMaps.put(RoleType.Global, globalRoles);
    roleMaps.put(RoleType.Slave, agentRoles);
    roleMaps.put(RoleType.Project, itemRoles);
    return Collections.unmodifiableMap(roleMaps);
  }

  /**
   * Add the given {@link Role} to the {@link RoleMap} associated to the provided class.
   *
   * @param roleType The type of the {@link Role} to be added
   * @param role     The {@link Role} to add
   */
  private void addRole(RoleType roleType, Role role) {
    getRoleMap(roleType).addRole(role);
  }

  /**
   * Assign a role to a sid.
   *
   * @param type The type of role
   * @param role The role to assign
   * @param sid  The sid to assign to
   */
  private void assignRole(RoleType type, Role role, PermissionEntry sid) {
    RoleMap roleMap = getRoleMap(type);
    if (roleMap.hasRole(role)) {
      roleMap.assignRole(role, sid);
    }
  }

  private static void persistChanges() throws IOException {
    Jenkins j = instance();
    j.save();
    AuthorizationStrategy as = j.getAuthorizationStrategy();
    if (as instanceof RoleBasedAuthorizationStrategy) {
      RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) as;
      rbas.validateConfig();
    }
  }

  private static Jenkins instance() {
    return Jenkins.get();
  }

  private static void checkAdminPerm() {
    instance().checkPermission(Jenkins.ADMINISTER);
  }

  private static void checkPerms(@NonNull Permission... permission) {
    instance().checkAnyPermission(permission);
  }

  private static void checkPermByRoleTypeForUpdates(@NonNull String roleType) {
    switch (roleType) {
      case RoleBasedAuthorizationStrategy.GLOBAL:
        checkAdminPerm();
        break;
      case RoleBasedAuthorizationStrategy.PROJECT:
        checkPerms(ITEM_ROLES_ADMIN);
        break;
      case RoleBasedAuthorizationStrategy.SLAVE:
        checkPerms(AGENT_ROLES_ADMIN);
        break;
      default:
        throw new IllegalArgumentException("Unknown RoleType: " + roleType);
    }
  }

  private static void checkPermByRoleTypeForReading(@NonNull String roleType) {
    switch (roleType) {
      case RoleBasedAuthorizationStrategy.GLOBAL:
        checkPerms(Jenkins.SYSTEM_READ);
        break;
      case RoleBasedAuthorizationStrategy.PROJECT:
        checkPerms(Jenkins.SYSTEM_READ, ITEM_ROLES_ADMIN);
        break;
      case RoleBasedAuthorizationStrategy.SLAVE:
        checkPerms(Jenkins.SYSTEM_READ, AGENT_ROLES_ADMIN);
        break;
      default:
        throw new IllegalArgumentException("Unknown RoleType: " + roleType);
    }
  }

  /**
   * API method to add a permission template.
   *
   * An existing template with the same will only be replaced when overwrite is set. Otherwise, the request will fail with status
   * <code>400</code>
   *
   * @param name The template nae
   * @param permissionIds Comma separated list of permission IDs
   * @param overwrite If an existing template should be overwritten
   */
  @POST
  @Restricted(NoExternalUse.class)
  public void doAddTemplate(@QueryParameter(required = true) String name,
                            @QueryParameter(required = true) String permissionIds,
                            @QueryParameter(required = false) boolean overwrite)
          throws IOException {
    checkPermByRoleTypeForUpdates(PROJECT);
    List<String> permissionList = Arrays.asList(permissionIds.split(","));
    Set<Permission> permissionSet = PermissionHelper.fromStrings(permissionList, true);
    PermissionTemplate template = new PermissionTemplate(permissionSet, name);
    if (!overwrite && hasPermissionTemplate(name)) {
      Stapler.getCurrentResponse2().sendError(HttpServletResponse.SC_BAD_REQUEST, "A template with name " + name + " already exists.");
      return;
    }
    permissionTemplates.put(name, template);
    refreshPermissionsFromTemplate();
    persistChanges();
  }

  /**
   * API method to remove templates.
   *
   * <p>
   * Example: {@code curl -X POST localhost:8080/role-strategy/strategy/removeTemplates --data "templates=developer,qualits"}
   *
   * @param names  comma separated list of templates to remove
   * @param force  If templates that are in use should be removed
   * @throws IOException in case saving changes fails
   */
  @POST
  @Restricted(NoExternalUse.class)
  public void doRemoveTemplates(@QueryParameter(required = true) String names,
                                @QueryParameter(required = false) boolean force) throws IOException {
    checkPermByRoleTypeForUpdates(PROJECT);
    String[] split = names.split(",");
    for (String templateName : split) {
      templateName = templateName.trim();
      PermissionTemplate pt = getPermissionTemplate(templateName);
      if (pt != null && (!pt.isUsed() || force)) {
        permissionTemplates.remove(templateName);
        RoleMap roleMap = getRoleMap(RoleType.Project);
        for (Role role : roleMap.getRoles()) {
          if (templateName.equals(role.getTemplateName())) {
            role.setTemplateName(null);
          }
        }
      }
    }
    persistChanges();
  }

  /**
   * API method to add a role.
   *
   * <p>Unknown and dangerous permissions are ignored.
   *
   * When specifying a <code>template</code> for an item role, the given permissions are ignored. The named template must exist,
   * otherwise the request fails with status <code>400</code>.
   * The <code>template</code> is ignored when adding global or agent roles.
   *
   * <p>Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/addRole --data "type=globalRoles&amp;roleName=ADM&amp;
   * permissionIds=hudson.model.Item.Discover,hudson.model.Item.ExtendedRead&amp;overwrite=true"}
   *
   *
   * @param type          (globalRoles, projectRoles, slaveRoles)
   * @param roleName      Name of role
   * @param permissionIds Comma separated list of IDs for given roleName
   * @param overwrite     Overwrite existing role
   * @param pattern       Role pattern
   * @param template      Name of template
   * @throws IOException In case saving changes fails
   * @since 2.5.0
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doAddRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String permissionIds,
      @QueryParameter(required = true) String overwrite,
      @QueryParameter(required = false) String pattern,
      @QueryParameter(required = false) String template) throws IOException {
    checkPermByRoleTypeForUpdates(type);

    final boolean overwriteb = Boolean.parseBoolean(overwrite);
    String pttrn = ".*";
    String templateName = Util.fixEmptyAndTrim(template);

    if (!type.equals(RoleBasedAuthorizationStrategy.GLOBAL) && pattern != null) {
      pttrn = pattern;
    }
    List<String> permissionList = Arrays.asList(permissionIds.split(","));
    Set<Permission> permissionSet = PermissionHelper.fromStrings(permissionList, true);

    Role role = new Role(roleName, pttrn, permissionSet);

    if (RoleBasedAuthorizationStrategy.PROJECT.equals(type) && templateName != null) {
      if (!hasPermissionTemplate(template)) {
        Stapler.getCurrentResponse2().sendError(
                HttpServletResponse.SC_BAD_REQUEST, "A template with name " + template + " doesn't exists."
        );
        return;
      }
      role.setTemplateName(templateName);
      role.refreshPermissionsFromTemplate(getPermissionTemplate(templateName));
    }

    RoleType roleType = RoleType.fromString(type);
    if (overwriteb) {
      RoleMap roleMap = getRoleMap(roleType);
      Role role2 = roleMap.getRole(roleName);
      if (role2 != null) {
        roleMap.removeRole(role2);
      }
    }
    addRole(roleType, role);
    persistChanges();
  }

  /**
   * API method to remove roles.
   *
   * <p>
   * Example: {@code curl -X POST localhost:8080/role-strategy/strategy/removeRoles --data "type=globalRoles&amp;
   * roleNames=ADM,DEV"}
   *
   * @param type      (globalRoles, projectRoles, slaveRoles)
   * @param roleNames comma separated list of roles to remove from type
   * @throws IOException in case saving changes fails
   * @since 2.5.0
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doRemoveRoles(@QueryParameter(required = true) String type, @QueryParameter(required = true) String roleNames)
      throws IOException {
    checkPermByRoleTypeForUpdates(type);

    RoleMap roleMap = getRoleMap(RoleType.fromString(type));
    String[] split = roleNames.split(",");
    for (String roleName : split) {
      Role role = roleMap.getRole(roleName);
      if (role != null) {
        roleMap.removeRole(role);
      }
    }
    persistChanges();
  }

  /**
   * API method to assign a SID of type EITHER to role.
   *
   * This method should no longer be used.
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/assignRole --data "type=globalRoles&amp;roleName=ADM
   * &amp;sid=username"}
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName name of role (single, no list)
   * @param sid      user ID (single, no list)
   * @throws IOException in case saving changes fails
   * @since 2.5.0
   * @deprecated Use {@link #doAssignUserRole} or {@link #doAssignGroupRole} to create unambiguous entries
   */
  @Deprecated
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doAssignRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String sid) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    final RoleType roleType = RoleType.fromString(type);
    Role role = getRoleMap(roleType).getRole(roleName);
    if (role != null) {
      assignRole(roleType, role, new PermissionEntry(AuthorizationType.EITHER, sid));
    }
    persistChanges();
  }

  /**
   * API method to assign a User to role.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/assignUserRole --data "type=globalRoles&amp;roleName=ADM
   * &amp;user=username"}
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName name of role (single, no list)
   * @param user     user ID (single, no list)
   * @throws IOException in case saving changes fails
   * @since TODO
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doAssignUserRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String user) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    final RoleType roleType = RoleType.fromString(type);
    Role role = getRoleMap(roleType).getRole(roleName);
    if (role != null) {
      assignRole(roleType, role, new PermissionEntry(AuthorizationType.USER, user));
    }
    persistChanges();
  }

  /**
   * API method to assign a Group to role.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/assignGroupRole --data "type=globalRoles&amp;roleName=ADM
   * &amp;group=groupname"}
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName name of role (single, no list)
   * @param group    group ID (single, no list)
   * @throws IOException in case saving changes fails
   * @since TODO
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doAssignGroupRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String group) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    final RoleType roleType = RoleType.fromString(type);
    Role role = getRoleMap(roleType).getRole(roleName);
    if (role != null) {
      assignRole(roleType, role, new PermissionEntry(AuthorizationType.GROUP, group));
    }
    persistChanges();
  }

  /**
   * API method to delete a SID from all granted roles.
   * Only SIDS of type EITHER with the given name will be deleted.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/deleteSid --data "type=globalRoles&amp;sid=username"}
   *
   * @param type (globalRoles, projectRoles, slaveRoles)
   * @param sid  user/group ID to remove
   * @throws IOException in case saving changes fails
   * @since 2.4.1
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doDeleteSid(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String sid) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    getRoleMap(RoleType.fromString(type)).deleteSids(new PermissionEntry(AuthorizationType.EITHER, sid));
    persistChanges();
  }

  /**
   * API method to delete a user from all granted roles.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/deleteUser --data "type=globalRoles&amp;user=username"}
   *
   * @param type (globalRoles, projectRoles, slaveRoles)
   * @param user user ID to remove
   * @throws IOException in case saving changes fails
   * @since 2.4.1
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doDeleteUser(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String user) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    getRoleMap(RoleType.fromString(type)).deleteSids(new PermissionEntry(AuthorizationType.USER, user));
    persistChanges();
  }

  /**
   * API method to delete a group from all granted roles.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/deleteGroup --data "type=globalRoles&amp;group=groupname"}
   *
   * @param type  (globalRoles, projectRoles, slaveRoles)
   * @param group group ID to remove
   * @throws IOException in case saving changes fails
   * @since 2.4.1
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doDeleteGroup(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String group) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    getRoleMap(RoleType.fromString(type)).deleteSids(new PermissionEntry(AuthorizationType.GROUP, group));
    persistChanges();
  }

  /**
   * API method to remove a SID from a role.
   * Only entries of type EITHER will be removed.
   *
   * use {@link #doUnassignUserRole(String, String, String)} or {@link #doUnassignGroupRole(String, String, String)} to unassign a
   * User or a Group.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/unassignRole --data "type=globalRoles&amp;roleName=AMD&amp;sid=username"}
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName unassign role with sid
   * @param sid      user ID to remove
   * @throws IOException in case saving changes fails
   *
   * @since 2.6.0
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doUnassignRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String sid) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    RoleMap roleMap = getRoleMap(RoleType.fromString(type));
    Role role = roleMap.getRole(roleName);
    if (role != null) {
      roleMap.deleteRoleSid(new PermissionEntry(AuthorizationType.EITHER, sid), role.getName());
    }
    persistChanges();
  }

  /**
   * API method to remove a user from a role.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/unassignUserRole --data
   *   "type=globalRoles&amp;roleName=AMD&amp;user=username"}
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName unassign role with sid
   * @param user     user ID to remove
   * @throws IOException in case saving changes fails
   * @since TODO
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doUnassignUserRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String user) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    RoleMap roleMap = getRoleMap(RoleType.fromString(type));
    Role role = roleMap.getRole(roleName);
    if (role != null) {
      roleMap.deleteRoleSid(new PermissionEntry(AuthorizationType.USER, user), role.getName());
    }
    persistChanges();
  }

  /**
   * API method to remove a user from a role.
   *
   * <p>
   * Example:
   * {@code curl -X POST localhost:8080/role-strategy/strategy/unassignGroupRole --data
   *   "type=globalRoles&amp;roleName=AMD&amp;user=username"}
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName unassign role with sid
   * @param group    user ID to remove
   * @throws IOException in case saving changes fails
   * @since TODO
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doUnassignGroupRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName,
      @QueryParameter(required = true) String group) throws IOException {
    checkPermByRoleTypeForUpdates(type);
    RoleMap roleMap = getRoleMap(RoleType.fromString(type));
    Role role = roleMap.getRole(roleName);
    if (role != null) {
      roleMap.deleteRoleSid(new PermissionEntry(AuthorizationType.GROUP, group), role.getName());
    }
    persistChanges();
  }

  /**
   * API method to get the granted permissions of a template and if the template is used.
   *
   * <p>
   * Example: {@code curl -XGET 'http://localhost:8080/jenkins/role-strategy/strategy/getTemplate?name=developer'}
   *
   * <p>
   * Returns json with granted permissions and assigned sids.<br>
   * Example:
   *
   * <pre>{@code
   *   {
   *     "permissionIds": {
   *         "hudson.model.Item.Read":true,
   *         "hudson.model.Item.Build":true,
   *         "hudson.model.Item.Cancel":true,
   *      },
   *      "isUsed": true
   *   }
   * }
   * </pre>
   *
   */
  @GET
  @Restricted(NoExternalUse.class)
  public void doGetTemplate(@QueryParameter(required = true) String name) throws IOException {
    checkPermByRoleTypeForReading(PROJECT);
    JSONObject responseJson = new JSONObject();

    PermissionTemplate template = permissionTemplates.get(name);
    if (template != null) {
      Set<Permission> permissions = template.getPermissions();
      Map<String, Boolean> permissionsMap = new HashMap<>();
      for (Permission permission : permissions) {
        permissionsMap.put(permission.getId(), permission.getEnabled());
      }
      responseJson.put("permissionIds", permissionsMap);
      responseJson.put("isUsed", template.isUsed());
    }
    Stapler.getCurrentResponse2().setContentType("application/json;charset=UTF-8");
    Writer writer = Stapler.getCurrentResponse2().getWriter();
    responseJson.write(writer);
    writer.close();

  }

  /**
   * API method to get the granted permissions of a role and the SIDs assigned to it.
   *
   * <p>
   * Example: {@code curl -XGET 'http://localhost:8080/jenkins/role-strategy/strategy/getRole
   * ?type=projectRoles&roleName=admin'}
   *
   * <p>
   * Returns json with granted permissions and assigned sids.<br>
   * Example:
   *
   * <pre>{@code
   *   {
   *     "permissionIds": {
   *         "hudson.model.Item.Read":true,
   *         "hudson.model.Item.Build":true,
   *         "hudson.model.Item.Cancel":true,
   *      },
   *      "sids": [{"type":"USER","sid":"user1"}, {"type":"USER","sid":"user2"}]
   *      "pattern": ".*",
   *      "template": "developers",
   *   }
   * }
   * </pre>
   *
   *
   * @param type     (globalRoles, projectRoles, slaveRoles)
   * @param roleName name of role (single, no list)
   * @throws IOException In case write response failed
   * @since 2.8.3
   */
  @GET
  @Restricted(NoExternalUse.class)
  public void doGetRole(@QueryParameter(required = true) String type,
      @QueryParameter(required = true) String roleName) throws IOException {
    checkPermByRoleTypeForReading(type);
    JSONObject responseJson = new JSONObject();
    RoleMap roleMap = getRoleMap(RoleType.fromString(type));
    Role role = roleMap.getRole(roleName);
    if (role != null) {
      Set<Permission> permissions = role.getPermissions();
      Map<String, Boolean> permissionsMap = new HashMap<>();
      for (Permission permission : permissions) {
        permissionsMap.put(permission.getId(), permission.getEnabled());
      }
      responseJson.put("permissionIds", permissionsMap);
      if (!type.equals(RoleBasedAuthorizationStrategy.GLOBAL)) {
        responseJson.put("pattern", role.getPattern().pattern());
      }
      Map<Role, Set<PermissionEntry>> grantedRoleMap = roleMap.getGrantedRolesEntries();
      responseJson.put("sids", grantedRoleMap.get(role));
      if (type.equals(RoleBasedAuthorizationStrategy.PROJECT)) {
        responseJson.put("template", role.getTemplateName());
      }
    }

    Stapler.getCurrentResponse2().setContentType("application/json;charset=UTF-8");
    Writer writer = Stapler.getCurrentResponse2().getWriter();
    responseJson.write(writer);
    writer.close();
  }

  /**
   * API method to get all roles and the SIDs assigned to the roles for a roletype.
   *
   * <p>
   * Example: {@code curl -X GET localhost:8080/role-strategy/strategy/getAllRoles?type=projectRoles}
   *
   * <p>
   * Returns a json with roles and sids<br>
   * Example:
   *
   * <pre>{@code
   *   {
   *     "role2": [{"type":"USER","sid":"user1"}, {"type":"USER","sid":"user2"}],
   *     "role2": [{"type":"GROUP","sid":"group1"}, {"type":"USER","sid":"user2"}]
   *   }
   * }</pre>
   *
   * @param type (globalRoles by default, projectRoles, slaveRoles)
   *
   * @since 2.6.0
   */
  @GET
  @Restricted(NoExternalUse.class)
  public void doGetAllRoles(@QueryParameter(fixEmpty = true) String type) throws IOException {
    if (type == null) {
      type = RoleType.Global.getStringType();
    }
    checkPermByRoleTypeForReading(type);
    RoleMap roleMap = getRoleMap(RoleType.fromString(type));

    JSONObject responseJson = new JSONObject();
    for (Map.Entry<Role, Set<PermissionEntry>> grantedRole : roleMap.getGrantedRolesEntries().entrySet()) {
      responseJson.put(grantedRole.getKey().getName(), grantedRole.getValue());
    }

    Stapler.getCurrentResponse2().setContentType("application/json;charset=UTF-8");
    Writer writer = Stapler.getCurrentResponse2().getWriter();
    responseJson.write(writer);
    writer.close();
  }

  /**
   * API method to get all SIDs and the assigned roles for a roletype.
   *
   * <p>
   * Example: {@code curl -X GET localhost:8080/role-strategy/strategy/getRoleAssignments?type=projectRoles}
   *
   * <p>
   * Returns a json with sids and roles<br>
   * Example:
   *
   * <pre>{@code
   *   [
   *     {
   *       "name": "d032386",
   *       "type": "USER",
   *       "roles": ["admin"]
   *     },
   *     {
   *       "name": "tester",
   *       "type": "USER",
   *       "roles": ["reader", "tester"]
   *     }
   *   ]
   * }</pre>
   *
   * @param type (globalRoles by default, projectRoles, slaveRoles)
   *
   * @since 2.6.0
   */
  @GET
  @Restricted(NoExternalUse.class)
  public void doGetRoleAssignments(@QueryParameter(fixEmpty = true) String type) throws IOException {
    if (type == null) {
      type = RoleType.Global.getStringType();
    }
    checkPermByRoleTypeForReading(type);

    Set<PermissionEntry> sidEntries = getRoleMap(RoleType.fromString(type)).getSidEntries(true);

    JSONArray responseJson = new JSONArray();
    for (PermissionEntry entry : sidEntries) {
      JSONObject userEntry = new JSONObject();
      userEntry.put("name", entry.getSid());
      userEntry.put("type", entry.getType().toString());
      JSONArray roles = new JSONArray();
      SortedMap<Role, Set<PermissionEntry>> rolesEntries = getGrantedRolesEntries(type);
      for (Map.Entry<Role, Set<PermissionEntry>> roleEntry : rolesEntries.entrySet()) {
        if (roleEntry.getValue().contains(entry)) {
          roles.add(roleEntry.getKey().getName());
        }
      }
      userEntry.put("roles", roles);
      responseJson.add(userEntry);
    }
    Stapler.getCurrentResponse2().setContentType("application/json;charset=UTF-8");
    Writer writer = Stapler.getCurrentResponse2().getWriter();
    responseJson.write(writer);
    writer.close();
  }

  /**
   * API method to get a list of items matching a pattern.
   *
   * <p>
   * Example: {@code curl -X GET localhost:8080/role-strategy/strategy/getMatchingJobs?pattern=^staging.*}
   *
   * @param pattern Pattern to match against
   * @param maxJobs Maximum matching items to search for
   * @throws IOException when unable to write response
   */
  @GET
  @Restricted(NoExternalUse.class)
  public void doGetMatchingJobs(@QueryParameter(required = true) String pattern,
      @QueryParameter() int maxJobs) throws IOException {
    checkAdminPerm();
    List<String> matchingItems = new ArrayList<>();
    int itemCount = RoleMap.getMatchingItemNames(matchingItems, Pattern.compile(pattern), maxJobs);
    JSONObject responseJson = new JSONObject();
    responseJson.put("matchingJobs", matchingItems);
    responseJson.put("itemCount", itemCount);
    StaplerResponse2 response = Stapler.getCurrentResponse2();
    response.setContentType("application/json;charset=UTF-8");
    Writer writer = response.getWriter();
    responseJson.write(writer);
    writer.close();
  }

  /**
   * API method to get a list of agents matching a pattern.
   *
   * <p>
   * Example: {@code curl -X GET localhost:8080/role-strategy/strategy/getMatchingAgents?pattern=^linux.*}
   *
   * @param pattern   Pattern to match against
   * @param maxAgents Maximum matching agents to search for
   * @throws IOException when unable to write response
   */
  @GET
  @Restricted(NoExternalUse.class)
  public void doGetMatchingAgents(@QueryParameter(required = true) String pattern,
      @QueryParameter() int maxAgents) throws IOException {
    checkAdminPerm();
    List<String> matchingAgents = new ArrayList<>();
    int agentCount = RoleMap.getMatchingAgentNames(matchingAgents, Pattern.compile(pattern), maxAgents);
    JSONObject responseJson = new JSONObject();
    responseJson.put("matchingAgents", matchingAgents);
    responseJson.put("agentCount", agentCount);
    StaplerResponse2 response = Stapler.getCurrentResponse2();
    response.setContentType("application/json;charset=UTF-8");
    Writer writer = response.getWriter();
    responseJson.write(writer);
    writer.close();
  }

  /**
   * Checks if there are ambiguous entries and adds them to the monitor.
   */
  @Restricted(NoExternalUse.class)
  public void validateConfig() {
    List<PermissionEntry> sids = new ArrayList<>();
    sids.addAll(getSidEntries(RoleBasedAuthorizationStrategy.GLOBAL));
    sids.addAll(getSidEntries(RoleBasedAuthorizationStrategy.SLAVE));
    sids.addAll(getSidEntries(RoleBasedAuthorizationStrategy.PROJECT));
    AmbiguousSidsAdminMonitor.get().updateEntries(sids);
  }

  /**
   * Validate the config after System config was loaded.
   */
  @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
  public static void init() {
    Jenkins j = instance();
    AuthorizationStrategy as = j.getAuthorizationStrategy();
    if (as instanceof RoleBasedAuthorizationStrategy) {
      RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) as;
      rbas.validateConfig();
    }
  }

  @Extension
  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

  /**
   * Converter used to persist and retrieve the strategy from disk.
   *
   * <p>
   * This converter is there to manually handle the marshalling/unmarshalling of this strategy: Doing so is a little bit
   * dirty but allows to easily update the plugin when new access controlled object (for the moment: Job and Project) will
   * be introduced. If it's the case, there's only the need to update the getRoleMaps() method.
   * </p>
   */
  public static class ConverterImpl implements Converter {
    @Override
    public boolean canConvert(Class type) {
      return type == RoleBasedAuthorizationStrategy.class;
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
      RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy) source;

      writer.startNode(PERMISSION_TEMPLATES);
      for (PermissionTemplate permissionTemplate : strategy.permissionTemplates.values()) {
        writer.startNode("template");
        writer.addAttribute("name", permissionTemplate.getName());
        writer.startNode("permissions");
        for (Permission permission : permissionTemplate.getPermissions()) {
          writer.startNode("permission");
          writer.setValue(permission.getId());
          writer.endNode();
        }
        writer.endNode(); // end permissions
        writer.endNode(); // end template
      }
      writer.endNode(); // end permissionTemplates

      // Role maps
      Map<RoleType, RoleMap> maps = strategy.getRoleMaps();
      for (Map.Entry<RoleType, RoleMap> map : maps.entrySet()) {
        RoleMap roleMap = map.getValue();
        writer.startNode("roleMap");
        writer.addAttribute("type", map.getKey().getStringType());

        for (Map.Entry<Role, Set<PermissionEntry>> grantedRole : roleMap.getGrantedRolesEntries().entrySet()) {
          Role role = grantedRole.getKey();
          if (role != null) {
            writer.startNode("role");
            writer.addAttribute("name", role.getName());
            writer.addAttribute("pattern", role.getPattern().pattern());
            if (Util.fixEmptyAndTrim(role.getTemplateName()) != null) {
              writer.addAttribute("templateName", role.getTemplateName());
            }

            writer.startNode("permissions");
            for (Permission permission : role.getPermissions()) {
              writer.startNode("permission");
              writer.setValue(permission.getId());
              writer.endNode();
            }
            writer.endNode();

            writer.startNode("assignedSIDs");
            for (PermissionEntry entry : grantedRole.getValue()) {
              writer.startNode("sid");
              writer.addAttribute("type", entry.getType().toString());
              writer.setValue(entry.getSid());
              writer.endNode();
            }
            writer.endNode();

            writer.endNode();
          }
        }
        writer.endNode();
      }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
      final Map<String, RoleMap> roleMaps = new HashMap<>();
      final Set<PermissionTemplate> permissionTemplates = new HashSet<>();
      while (reader.hasMoreChildren()) {
        reader.moveDown();

        if (reader.getNodeName().equals(PERMISSION_TEMPLATES)) {
          while (reader.hasMoreChildren()) {
            reader.moveDown();
            Set<Permission> permissions = new HashSet<>();
            String name = reader.getAttribute("name");
            String next = ((ExtendedHierarchicalStreamReader) reader).peekNextChild();
            if (next != null && next.equals("permissions")) {
              reader.moveDown();
              while (reader.hasMoreChildren()) {
                reader.moveDown();
                Permission p = PermissionHelper.resolvePermissionFromString(reader.getValue());
                if (p != null) {
                  permissions.add(p);
                }
                reader.moveUp();
              }
              reader.moveUp();
            }
            permissionTemplates.add(new PermissionTemplate(permissions, name));
            reader.moveUp();
          }
        }

        // roleMaps
        if (reader.getNodeName().equals("roleMap")) {
          String type = reader.getAttribute("type");
          RoleMap map = new RoleMap();
          while (reader.hasMoreChildren()) {
            reader.moveDown();
            String name = reader.getAttribute("name");
            String pattern = reader.getAttribute("pattern");
            String templateName = reader.getAttribute("templateName");
            Set<Permission> permissions = new HashSet<>();

            String next = ((ExtendedHierarchicalStreamReader) reader).peekNextChild();
            if (next != null && next.equals("permissions")) {
              reader.moveDown();
              while (reader.hasMoreChildren()) {
                reader.moveDown();
                Permission p = PermissionHelper.resolvePermissionFromString(reader.getValue());
                if (p != null) {
                  permissions.add(p);
                }

                reader.moveUp();
              }
              reader.moveUp();
            }

            Role role = new Role(name, Pattern.compile(pattern), permissions, "", templateName);
            map.addRole(role);

            next = ((ExtendedHierarchicalStreamReader) reader).peekNextChild();
            if (next != null && next.equals("assignedSIDs")) {
              reader.moveDown();
              while (reader.hasMoreChildren()) {
                reader.moveDown();
                String entryTypeValue = reader.getAttribute("type");
                AuthorizationType authType = AuthorizationType.EITHER;
                String sid = reader.getValue();
                if (entryTypeValue != null) {
                  try {
                    authType = AuthorizationType.valueOf(entryTypeValue);
                  } catch (IllegalArgumentException ex) {
                    LOGGER.log(Level.WARNING, "Unknown AuthorizationType {0} for SID {1} in Role {2}/{3}",
                        new Object[] { entryTypeValue, sid, type, name });
                    throw ex;
                  }
                }
                PermissionEntry pe = new PermissionEntry(authType, sid);
                map.assignRole(role, pe);
                reader.moveUp();
              }
              reader.moveUp();
            }
            reader.moveUp();
          }
          roleMaps.put(type, map);
        }

        reader.moveUp();
      }

      return new RoleBasedAuthorizationStrategy(roleMaps, permissionTemplates);
    }

    protected RoleBasedAuthorizationStrategy create() {
      return new RoleBasedAuthorizationStrategy();
    }
  }

  /**
   * Retrieves instance of the strategy.
   *
   * @return Strategy instance or {@code null} if it is disabled.
   */
  @CheckForNull
  public static RoleBasedAuthorizationStrategy getInstance() {
    final Jenkins jenkins = Jenkins.getInstanceOrNull();
    final AuthorizationStrategy authStrategy = jenkins != null ? jenkins.getAuthorizationStrategy() : null;
    if (authStrategy instanceof RoleBasedAuthorizationStrategy) {
      return (RoleBasedAuthorizationStrategy) authStrategy;
    }

    // Nothing to do here, not a Role strategy
    return null;
  }

  /**
   * Control job create using {@link org.jenkinsci.plugins.rolestrategy.RoleBasedProjectNamingStrategy}.
   *
   * @since 2.2.0
   * @deprecated Always available since 1.566
   */
  @Deprecated
  public static boolean isCreateAllowed() {
    return true;
  }

  /**
   * Descriptor used to bind the strategy to the Web forms.
   */
  public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {

    @Override
    @NonNull
    public String getDisplayName() {
      return Messages.RoleBasedAuthorizationStrategy_DisplayName();
    }

    /**
     * Checks if the value contains whitespace at begin or end.
     *
     * @param value Value to check
     * @return FormValidation
     */
    @RequirePOST
    public FormValidation doCheckForWhitespace(@QueryParameter String value) {
      checkPerms(ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN);
      if (value == null || value.trim().equals(value)) {
        return FormValidation.ok();
      } else {
        return FormValidation.warning(Messages.RoleBasedProjectNamingStrategy_WhiteSpaceWillBeTrimmed());
      }
    }

    /**
     * Called on role management form's submission.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doRolesSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
      checkPerms(ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN);

      req.setCharacterEncoding("UTF-8");
      JSONObject json = req.getSubmittedForm();
      AuthorizationStrategy strategy = this.newInstance(req, json);
      instance().setAuthorizationStrategy(strategy);
      // Persist the data
      persistChanges();
    }

    /**
     * Called on role assignment form's submission.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAssignSubmit(JSONObject json) throws ServletException, IOException {
      checkPerms(ITEM_ROLES_ADMIN, AGENT_ROLES_ADMIN);

      AuthorizationStrategy oldStrategy = instance().getAuthorizationStrategy();

      if (json.has(GLOBAL) && json.has(PROJECT) && oldStrategy instanceof RoleBasedAuthorizationStrategy strategy) {
        Map<RoleType, RoleMap> maps = strategy.getRoleMaps();

        for (Map.Entry<RoleType, RoleMap> map : maps.entrySet()) {
          final String roleTypeAsString = map.getKey().getStringType();
          // if no permission, take the globalRoles from the oldStrategy
          try {
            checkPermByRoleTypeForUpdates(roleTypeAsString);
          } catch (AccessDeniedException ignore) {
            LOGGER.info("Not enough permissions to save assignments for " + roleTypeAsString + ". Skipping...");
            continue;
          }

          // Get roles and skip non-existent role entries (backward-comp)
          RoleMap roleMap = map.getValue();
          JSONArray userEntries = json.getJSONArray(map.getKey().getStringType());

          roleMap.clearSids();

          userEntries.forEach(e -> {
            JSONObject entry = (JSONObject) e;
            PermissionEntry pe = new PermissionEntry(AuthorizationType.valueOf(entry.getString("type")), entry.getString("name"));
            entry.getJSONArray("roles").forEach(r -> {
              Role role = roleMap.getRole((String) r);
              if (role != null) {
                roleMap.assignRole(role, pe);
              }
            });
          });
        }
        // Persist the data
        persistChanges();
      }
    }

    /**
     * Called on role generator form submission.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doTemplatesSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
      checkPermByRoleTypeForUpdates(PROJECT);
      req.setCharacterEncoding("UTF-8");
      JSONObject json = req.getSubmittedForm();
      AuthorizationStrategy oldStrategy = instance().getAuthorizationStrategy();
      if (json.has(PERMISSION_TEMPLATES) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy) oldStrategy;

        JSONObject permissionTemplatesJson = json.getJSONObject(PERMISSION_TEMPLATES);
        Map<String, PermissionTemplate> permissionTemplates = new TreeMap<>();
        for (Map.Entry<String, JSONObject> r : (Set<Map.Entry<String, JSONObject>>)
            permissionTemplatesJson.getJSONObject("data").entrySet()) {
          String templateName = r.getKey();
          Set<String> permissionStrings = new HashSet<>();
          for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String, Boolean>>) r.getValue().entrySet()) {
            if (e.getValue()) {
              permissionStrings.add(e.getKey());
            }
          }
          PermissionTemplate permissionTemplate = new PermissionTemplate(templateName, permissionStrings);
          permissionTemplates.put(templateName, permissionTemplate);
        }

        strategy.permissionTemplates = permissionTemplates;
        strategy.refreshPermissionsFromTemplate();
        persistChanges();
      }
    }

    /**
     * Method called on Jenkins Manage panel submission, and plugin specific forms to create the
     * {@link AuthorizationStrategy} object.
     */
    @Override
    public AuthorizationStrategy newInstance(StaplerRequest2 req, JSONObject formData) {
      AuthorizationStrategy oldStrategy = instance().getAuthorizationStrategy();
      RoleBasedAuthorizationStrategy strategy;

      // If the form contains data, it means the method has been called by plugin
      // specifics forms, and we need to handle it.
      if (formData.has(GLOBAL) && formData.has(PROJECT) && formData.has(SLAVE) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        strategy = new RoleBasedAuthorizationStrategy();
        readRoles(formData, RoleType.Global, strategy, (RoleBasedAuthorizationStrategy) oldStrategy);
        readRoles(formData, RoleType.Project, strategy, (RoleBasedAuthorizationStrategy) oldStrategy);
        readRoles(formData, RoleType.Slave, strategy, (RoleBasedAuthorizationStrategy) oldStrategy);
        strategy.permissionTemplates = ((RoleBasedAuthorizationStrategy) oldStrategy).permissionTemplates;
      } else if (oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        // When called from Hudson Manage panel, but was already on a role-based
        // strategy
        // Do nothing, keep the same strategy
        strategy = (RoleBasedAuthorizationStrategy) oldStrategy;
      } else {
        // When called from Hudson Manage panel, but when the previous strategy wasn't
        // role-based, it means we need to create an admin role, and assign it to the
        // current user to not throw him out of the webapp
        strategy = new RoleBasedAuthorizationStrategy();
        Role adminRole = createAdminRole();
        strategy.addRole(RoleType.Global, adminRole);
        strategy.assignRole(RoleType.Global, adminRole, new PermissionEntry(AuthorizationType.USER, getCurrentUser()));
      }

      return strategy;
    }

    private void copyRolesFromOldStrategy(final RoleType roleType, RoleBasedAuthorizationStrategy targetStrategy,
        RoleBasedAuthorizationStrategy oldStrategy) {
      RoleMap roleMap = oldStrategy.getRoleMap(roleType);
      for (Role role : roleMap.getRoles()) {
        targetStrategy.addRole(roleType, role);
        Set<PermissionEntry> sids = roleMap.getSidEntriesForRole(role.getName());
        if (sids != null) {
          for (PermissionEntry sid : sids) {
            targetStrategy.assignRole(roleType, role, sid);
          }
        }
      }
    }

    private void readRoles(JSONObject formData, final RoleType roleType, RoleBasedAuthorizationStrategy targetStrategy,
        RoleBasedAuthorizationStrategy oldStrategy) {
      final String roleTypeAsString = roleType.getStringType();
      JSONObject roles = formData.getJSONObject(roleTypeAsString);
      if (!roles.containsKey("data")) {
        assert false : "No data at role description";
        return;
      }
      // if no permission, take the roles from the oldStrategy
      try {
        checkPermByRoleTypeForUpdates(roleTypeAsString);
      } catch (AccessDeniedException ignore) {
        LOGGER.log(Level.INFO, "Not enough permissions to save roles for " + roleTypeAsString + ". Copying roles from old strategy.");
        copyRolesFromOldStrategy(roleType, targetStrategy, oldStrategy);
        return;
      }
      RoleMap roleMap = oldStrategy.getRoleMap(roleType);

      for (Map.Entry<String, JSONObject> r : (Set<Map.Entry<String, JSONObject>>) roles.getJSONObject("data").entrySet()) {
        String pattern = ".*";
        if (r.getValue().has("pattern")) {
          pattern = r.getValue().getString("pattern");
          r.getValue().remove("pattern");
        }
        if (pattern == null) {
          pattern = ".*";
        }
        String templateName = null;
        if (r.getValue().has("templateName")) {
          templateName = r.getValue().getString("templateName");
          r.getValue().remove("templateName");
        }
        Set<Permission> permissions = new HashSet<>();
        for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String, Boolean>>) r.getValue().entrySet()) {
          if (e.getValue()) {
            Permission p = Permission.fromId(e.getKey());
            permissions.add(p);
          }
        }
        String roleName = r.getKey();
        Role role = new Role(roleName, Pattern.compile(pattern), permissions, "", templateName);
        targetStrategy.addRole(roleType, role);

        Set<PermissionEntry> sids = roleMap.getSidEntriesForRole(roleName);
        if (sids != null) {
          for (PermissionEntry sid : sids) {
            targetStrategy.assignRole(roleType, role, sid);
          }
        }
      }
    }

    /**
     * Create an admin role.
     */
    private Role createAdminRole() {
      Set<Permission> permissions = new HashSet<>();
      permissions.add(Jenkins.ADMINISTER);
      return new Role("admin", permissions);
    }

    /**
     * Get the current user ({@code Anonymous} if not logged-in).
     *
     * @return Sid of the current user
     */
    private String getCurrentUser() {
      PrincipalSid currentUser = new PrincipalSid(Jenkins.getAuthentication2());
      return currentUser.getPrincipal();
    }

    /**
     * Get the needed permissions groups.
     *
     * @param type Role type
     * @return Groups, which should be displayed for a specific role type. {@code null} if an unsupported type is defined.
     */
    @Nullable
    public List<PermissionGroup> getGroups(@NonNull String type) {
      List<PermissionGroup> groups = new ArrayList<>();
      List<PermissionGroup> filterGroups = new ArrayList<>(PermissionGroup.getAll());
      switch (type) {
        case GLOBAL:
          break;
        case PROJECT:
          filterGroups.remove(PermissionGroup.get(Hudson.class));
          filterGroups.remove(PermissionGroup.get(Computer.class));

          // RoleStrategy permissions
          filterGroups.remove(PermissionGroup.get(RoleBasedAuthorizationStrategy.class));
          break;
        case SLAVE:
          filterGroups.remove(PermissionGroup.get(Permission.class));
          filterGroups.remove(PermissionGroup.get(Hudson.class));
          filterGroups.remove(PermissionGroup.get(View.class));

          // RoleStrategy permissions
          filterGroups.remove(PermissionGroup.get(RoleBasedAuthorizationStrategy.class));

          // Project, SCM and Run permissions
          filterGroups.remove(PermissionGroup.get(Item.class));
          filterGroups.remove(PermissionGroup.get(SCM.class));
          filterGroups.remove(PermissionGroup.get(Run.class));
          break;
        default:
          filterGroups = new ArrayList<>();
          break;
      }
      for (PermissionGroup group : filterGroups) {
        if (group == PermissionGroup.get(Permission.class)) {
          continue;
        }
        for (Permission p : group.getPermissions()) {
          if (p.getEnabled()) {
            groups.add(group);
            break;
          }
        }
      }
      return groups;
    }

    /**
     * Used from Jelly.
     *
     * @param type Role type
     * @param p    Permission
     * @return true if permission should be shown
     */
    @Restricted(NoExternalUse.class)
    public boolean showPermission(String type, Permission p) {
      switch (type) {
        case GLOBAL:
          if (PermissionHelper.isDangerous(p)) {
            return false;
          }
          return p.getEnabled();
        case PROJECT:
          return p.getEnabled();
        case SLAVE:
          return p != Computer.CREATE && p.getEnabled();
        default:
          return false;
      }
    }

    /**
     * Returns a String with the permissions that imply the given permission.
     *
     * @param p Permission
     * @return String with implying permission
     */
    @Restricted(DoNotUse.class)
    public String impliedByList(Permission p) {
      List<Permission> impliedBys = new ArrayList<>();
      while (p.impliedBy != null) {
        p = p.impliedBy;
        impliedBys.add(p);
      }
      return StringUtils.join(impliedBys.stream().map(Permission::getId).collect(Collectors.toList()), " ");
    }

    /**
     * Create PermissionEntry.
     *
     * @param type AuthorizationType
     * @param sid  SID
     * @return PermissionEntry
     */
    @Restricted(DoNotUse.class) // Jelly only
    public PermissionEntry entryFor(String type, String sid) {
      if (type == null) {
        return null; // template row only
      }
      return new PermissionEntry(AuthorizationType.valueOf(type), sid);
    }

    /**
     * Validate the pattern.
     *
     * @param value Pattern to validate
     * @return FormValidation object with result
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public FormValidation doCheckPattern(@QueryParameter String value) {
      try {
        Pattern.compile(value);
      } catch (PatternSyntaxException pse) {
        return FormValidation.error(pse.getMessage());
      }
      return FormValidation.ok();
    }

    /**
     * Check the given SID and look it up in the security realm and returns html snippet that will be displayed in the form
     * instead of the plain sid.
     *
     * <p>When the name matches an existing user the users full name will be shown, otherwise it will be just the sid.
     * For Existing users and groups, the corresponding icon will be used.
     *
     * @param value Name to validate
     * @return FormValidation object
     */
    @RequirePOST
    public FormValidation doCheckName(@QueryParameter String value) {
      final String unbracketedValue = value.substring(1, value.length() - 1);

      final int splitIndex = unbracketedValue.indexOf(':');
      if (splitIndex < 0) {
        return FormValidation.error("No type prefix: " + unbracketedValue);
      }

      final String typeString = unbracketedValue.substring(0, splitIndex);
      final AuthorizationType type;
      try {
        type = AuthorizationType.valueOf(typeString);
      } catch (Exception ex) {
        return FormValidation.error("Invalid type prefix: " + unbracketedValue);
      }
      String sid = unbracketedValue.substring(splitIndex + 1);
      String escapedSid = Functions.escape(sid);

      if (!Jenkins.get().hasPermission(Jenkins.SYSTEM_READ)) {
        return FormValidation.ok(escapedSid); // can't check
      }

      SecurityRealm sr = Jenkins.get().getSecurityRealm();

      if (sid.equals("authenticated") && type == AuthorizationType.EITHER) {
        // system reserved group
        return FormValidation.respond(FormValidation.Kind.OK,
                ValidationUtil.formatUserGroupValidationResponse(type, escapedSid,
            "Internal group found; but permissions would also be granted to a user of this name", true));
      }

      if (sid.equals("anonymous") && type == AuthorizationType.EITHER) {
        // system reserved user
        return FormValidation.respond(FormValidation.Kind.OK,
                formatUserGroupValidationResponse(type, escapedSid,
            "Internal user found; but permissions would also be granted to a group of this name", true));
      }

      try {
        FormValidation groupValidation;
        FormValidation userValidation;
        switch (type) {
          case GROUP:
            groupValidation = ValidationUtil.validateGroup(sid, sr, false);
            if (groupValidation != null) {
              return groupValidation;
            }
            return FormValidation.respond(FormValidation.Kind.OK,
                    formatNonExistentUserGroupValidationResponse(type, escapedSid, "Group not found"));
          case USER:
            userValidation = ValidationUtil.validateUser(sid, sr, false);
            if (userValidation != null) {
              return userValidation;
            }
            return FormValidation.respond(FormValidation.Kind.OK,
                    formatNonExistentUserGroupValidationResponse(type, escapedSid, "User not found"));
          case EITHER:
            userValidation = ValidationUtil.validateUser(sid, sr, true);
            if (userValidation != null) {
              return userValidation;
            }
            groupValidation = ValidationUtil.validateGroup(sid, sr, true);
            if (groupValidation != null) {
              return groupValidation;
            }
            return FormValidation.respond(FormValidation.Kind.OK,
                formatNonExistentUserGroupValidationResponse(type, escapedSid, "User or group not found", true));
          default:
            return FormValidation.error("Unexpected type: " + type);
        }
      } catch (Exception e) {
        // if the check fails miserably, we still want the user to be able to see the name of the user,
        // so use 'escapedSid' as the message
        return FormValidation.error(e, escapedSid);
      }
    }

    @Restricted(DoNotUse.class)
    public boolean hasAmbiguousEntries(SortedMap<Role, Set<PermissionEntry>> grantedRoles) {
      return grantedRoles.entrySet().stream()
          .anyMatch(entry -> entry.getValue().stream().anyMatch(pe -> pe.getType() == AuthorizationType.EITHER));
    }
  }
}
