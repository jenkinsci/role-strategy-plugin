/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Française des Pneumatiques Michelin,
 * Thomas Maurel, Romain Seguy
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

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleMacroExtension;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Add the role management link to the Manage Hudson page.
 *
 * @author Thomas Maurel
 */
@Extension
public class RoleStrategyConfig extends ManagementLink {

  /**
   * Get the singleton instance of RoleStrategyConfig.
   *
   * @return The RoleStrategyConfig instance
   */
  @NonNull
  public static RoleStrategyConfig get() {
    return ExtensionList.lookupSingleton(RoleStrategyConfig.class);
  }

  public static int getMaxRows() {
    return SystemProperties.getInteger(RoleStrategyConfig.class.getName() + ".MAX_ROWS", 30);
  }

  /**
   * Provides the icon for the Manage Hudson page link.
   *
   * @return Path to the icon, or {@code null} if not enabled
   */
  @Override
  public String getIconFileName() {
    // Only show this link if the role-based authorization strategy has been enabled
    if (Jenkins.get().getAuthorizationStrategy() instanceof RoleBasedAuthorizationStrategy) {
      return "symbol-lock-closed-outline plugin-ionicons-api";
    }
    return null;
  }

  @NonNull
  @Override
  public Permission getRequiredPermission() {
    return Jenkins.SYSTEM_READ;
  }

  /**
   * URL name for the strategy management.
   *
   * @return Path to the strategy admin panel
   */
  @Override
  public String getUrlName() {
    return "role-strategy";
  }

  @NonNull
  @Override
  public String getCategoryName() {
    return "SECURITY";
  }

  /**
   * Text displayed in the Manage Hudson panel.
   *
   * @return Link text in the Admin panel
   */
  @Override
  public String getDisplayName() {
    return Messages.RoleBasedAuthorizationStrategy_ManageAndAssign();
  }

  /**
   * Text displayed for the roles assignment panel.
   *
   * @return Title of the Role assignment panel
   */
  public String getAssignRolesName() {
    return Messages.RoleBasedAuthorizationStrategy_Assign();
  }

  /**
   * Text displayed for the roles management panel.
   *
   * @return Title of the Role management panel
   */
  public String getManageRolesName() {
    return Messages.RoleBasedAuthorizationStrategy_Manage();
  }

  /**
   * The description of the link.
   *
   * @return The description of the link
   */
  @Override
  public String getDescription() {
    return Messages.RoleBasedAuthorizationStrategy_Description();
  }

  /**
   * Retrieve the {@link RoleBasedAuthorizationStrategy} object from the Hudson instance.
   * <p>
   * Used by the views to build matrix.
   * </p>
   *
   * @return The {@link RoleBasedAuthorizationStrategy} object. {@code null} if the strategy is not used.
   */
  @CheckForNull
  public AuthorizationStrategy getStrategy() {
    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy) {
      return strategy;
    } else {
      return null;
    }
  }

  /**
   * Called on roles generator form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doTemplatesSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    Jenkins.get().checkPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN);
    // Let the strategy descriptor handle the form
    RoleBasedAuthorizationStrategy.DESCRIPTOR.doTemplatesSubmit(req, rsp);
    // Redirect to the plugin index page
    FormApply.success(".").generateResponse(req, rsp, this);
  }

  // no configuration on this page for submission
  // public void doMacrosSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, UnsupportedEncodingException,
  // ServletException, FormException {
  // Hudson.getInstance().checkPermission(Jenkins.ADMINISTER);
  //
  // // TODO: Macros Enable/Disable
  //
  // // Redirect to the plugin index page
  // FormApply.success(".").generateResponse(req, rsp, this);
  // }

  /**
   * Called on role's assignment form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doAssignSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    // Let the strategy descriptor handle the form
    req.setCharacterEncoding("UTF-8");
    JSONObject json = req.getSubmittedForm();
    JSONObject rolesMapping;
    if (json.has("submit")) {
      String rm = json.getString("rolesMapping");
      rolesMapping = JSONObject.fromObject(rm);
    } else {
      rolesMapping = json.getJSONObject("rolesMapping");
    }
    if (rolesMapping.has("agentRoles")) {
      rolesMapping.put(RoleBasedAuthorizationStrategy.SLAVE, rolesMapping.getJSONArray("agentRoles"));
    }
    RoleBasedAuthorizationStrategy.DESCRIPTOR.doAssignSubmit(rolesMapping);
    FormApply.success(".").generateResponse(req, rsp, this);
  }

  public ExtensionList<RoleMacroExtension> getRoleMacroExtensions() {
    return RoleMacroExtension.all();
  }

  /**
   * Bootstrap JSON for the Permission Templates page, intended to be embedded in a {@code data-*}
   * attribute so the React UI can render immediately without an additional round trip.
   *
   * @return JSON string with templates, item-scope permission groups, and edit permissions
   */
  @Restricted(NoExternalUse.class)
  public String getPermissionTemplatesBootstrapJson() {
    AuthorizationStrategy raw = getStrategy();
    JSONObject result = new JSONObject();
    JSONArray templates = new JSONArray();
    if (raw instanceof RoleBasedAuthorizationStrategy strategy) {
      for (PermissionTemplate template : strategy.getPermissionTemplates()) {
        templates.add(templateToJson(template));
      }
    }
    result.put("templates", templates);
    result.put("permissionGroups", permissionGroupsToJson(
        RoleBasedAuthorizationStrategy.DESCRIPTOR, RoleBasedAuthorizationStrategy.PROJECT));
    result.put("canEdit", Jenkins.get().hasPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN));
    return result.toString();
  }

  /**
   * Bootstrap JSON for the Manage Roles page, intended to be embedded in a {@code data-*}
   * attribute so the React UI can render immediately without an additional round trip.
   *
   * @return JSON string with the roles, permission groups and edit permission per role type,
   *         plus the permission templates for the item-role dialog
   */
  @Restricted(NoExternalUse.class)
  public String getManageRolesBootstrapJson() {
    AuthorizationStrategy raw = getStrategy();
    RoleBasedAuthorizationStrategy strategy = raw instanceof RoleBasedAuthorizationStrategy rbas ? rbas : null;
    Jenkins jenkins = Jenkins.get();
    JSONObject result = new JSONObject();
    result.put(RoleBasedAuthorizationStrategy.GLOBAL, roleTypeToJson(strategy, RoleType.Global,
        jenkins.hasPermission(Jenkins.SYSTEM_READ),
        jenkins.hasPermission(Jenkins.ADMINISTER)));
    result.put(RoleBasedAuthorizationStrategy.PROJECT, roleTypeToJson(strategy, RoleType.Project,
        jenkins.hasAnyPermission(Jenkins.SYSTEM_READ, RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN),
        jenkins.hasPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN)));
    result.put(RoleBasedAuthorizationStrategy.SLAVE, roleTypeToJson(strategy, RoleType.Slave,
        jenkins.hasAnyPermission(Jenkins.SYSTEM_READ, RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN),
        jenkins.hasPermission(RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN)));
    JSONArray templates = new JSONArray();
    if (strategy != null) {
      for (PermissionTemplate template : strategy.getPermissionTemplates()) {
        templates.add(templateToJson(template));
      }
    }
    result.put("templates", templates);
    return result.toString();
  }

  private static JSONObject roleTypeToJson(@CheckForNull RoleBasedAuthorizationStrategy strategy, RoleType roleType,
      boolean visible, boolean canEdit) {
    JSONObject json = new JSONObject();
    json.put("visible", visible);
    json.put("canEdit", visible && canEdit);
    JSONArray groups = new JSONArray();
    JSONArray roles = new JSONArray();
    if (visible && strategy != null) {
      groups = permissionGroupsToJson(RoleBasedAuthorizationStrategy.DESCRIPTOR, roleType.getStringType());
      for (Role role : strategy.getRoleMap(roleType).getRoles()) {
        roles.add(roleToJson(role, roleType));
      }
    }
    json.put("permissionGroups", groups);
    json.put("roles", roles);
    return json;
  }

  private static JSONObject roleToJson(Role role, RoleType roleType) {
    JSONObject roleJson = new JSONObject();
    roleJson.put("name", role.getName());
    if (roleType != RoleType.Global) {
      roleJson.put("pattern", role.getPattern().pattern());
    }
    String templateName = role.getTemplateName();
    if (roleType == RoleType.Project && templateName != null && !templateName.isEmpty()) {
      roleJson.put("templateName", templateName);
    }
    JSONArray permIds = new JSONArray();
    for (Permission p : role.getPermissions()) {
      permIds.add(p.getId());
    }
    roleJson.put("permissionIds", permIds);
    return roleJson;
  }

  private static JSONObject templateToJson(PermissionTemplate template) {
    JSONObject templateJson = new JSONObject();
    templateJson.put("name", template.getName());
    JSONArray permIds = new JSONArray();
    for (Permission p : template.getPermissions()) {
      permIds.add(p.getId());
    }
    templateJson.put("permissionIds", permIds);
    templateJson.put("isUsed", template.isUsed());
    return templateJson;
  }

  private static JSONArray permissionGroupsToJson(RoleBasedAuthorizationStrategy.DescriptorImpl descriptor, String type) {
    JSONArray groupsArray = new JSONArray();
    List<PermissionGroup> groups = descriptor.getGroups(type);
    Set<PermissionGroup> deduped = new LinkedHashSet<>(groups);
    for (PermissionGroup group : deduped) {
      JSONObject g = new JSONObject();
      g.put("title", group.title.toString());
      JSONArray permsArray = new JSONArray();
      for (Permission p : group.getPermissions()) {
        if (!descriptor.showPermission(type, p)) {
          continue;
        }
        JSONObject permJson = new JSONObject();
        permJson.put("id", p.getId());
        permJson.put("name", p.name);
        permJson.put("description", p.description != null ? p.description.toString() : "");
        JSONArray impliedArr = new JSONArray();
        Permission cursor = p.impliedBy;
        while (cursor != null) {
          impliedArr.add(cursor.getId());
          cursor = cursor.impliedBy;
        }
        permJson.put("impliedByList", impliedArr);
        permsArray.add(permJson);
      }
      g.put("permissions", permsArray);
      groupsArray.add(g);
    }
    return groupsArray;
  }

  public final RoleType getGlobalRoleType() {
    return RoleType.Global;
  }

  public final RoleType getProjectRoleType() {
    return RoleType.Project;
  }

  public final RoleType getSlaveRoleType() {
    return RoleType.Slave;
  }
}
