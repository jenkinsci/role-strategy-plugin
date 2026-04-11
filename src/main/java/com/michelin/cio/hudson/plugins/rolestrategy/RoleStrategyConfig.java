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
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
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
      return "symbol-shield-outline plugin-ionicons-api";
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
   * Called when deleting a role via the UI.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doDeleteRoleSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    String redirectUrl = req.getContextPath() + "/manage/role-strategy/manage-roles";

    JSONObject json = getSubmittedFormOrRedirect(req, rsp, redirectUrl);
    if (json == null) {
      return;
    }

    String scope = json.optString("scope", "").trim();
    String roleName = json.optString("roleName", "").trim();
    if (scope.isEmpty() || roleName.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return;
    }

    checkScopePermission(scope);
    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      rbas.doRemoveRoles(scope, roleName);
    }

    rsp.sendRedirect(redirectUrl);
  }

  /**
   * Called when removing all role assignments for a user/group.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doDeleteAssignSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    String redirectUrl = req.getContextPath() + "/manage/role-strategy/";

    JSONObject json = getSubmittedFormOrRedirect(req, rsp, redirectUrl);
    if (json == null) {
      return;
    }

    String name = json.optString("name", "").trim();
    String type = json.optString("type", "USER").trim();
    if (name.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return;
    }

    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      String[] scopes = {
        RoleBasedAuthorizationStrategy.GLOBAL,
        RoleBasedAuthorizationStrategy.PROJECT,
        RoleBasedAuthorizationStrategy.SLAVE
      };
      for (String scope : scopes) {
        if (hasScopePermission(scope)) {
          switch (type) {
            case "USER" -> rbas.doDeleteUser(scope, name);
            case "GROUP" -> rbas.doDeleteGroup(scope, name);
            case "EITHER" -> rbas.doDeleteSid(scope, name);
            default -> { }
          }
        }
      }
    }

    rsp.sendRedirect(redirectUrl);
  }

  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doAddTemplateSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    handleTemplateSubmit(req, rsp, false);
  }

  /**
   * Called from the edit template dialog form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doEditTemplateSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    handleTemplateSubmit(req, rsp, true);
  }

  private void handleTemplateSubmit(StaplerRequest2 req, StaplerResponse2 rsp, boolean overwrite)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN);
    String redirectUrl = req.getContextPath() + "/manage/role-strategy/permission-templates";

    JSONObject json = getSubmittedFormOrRedirect(req, rsp, redirectUrl);
    if (json == null) {
      return;
    }

    String nameField = overwrite ? "originalTemplateName" : "templateName";
    String templateName = json.optString(nameField, "").trim();
    if (templateName.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return;
    }

    String permIds = String.join(",", collectPermissionIds(json));
    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      rbas.doAddTemplate(templateName, permIds, overwrite);
    }

    rsp.sendRedirect(redirectUrl);
  }

  /**
   * Called when deleting a permission template.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doDeleteTemplateSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    Jenkins.get().checkPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN);
    String redirectUrl = req.getContextPath() + "/manage/role-strategy/permission-templates";

    JSONObject json = getSubmittedFormOrRedirect(req, rsp, redirectUrl);
    if (json == null) {
      return;
    }

    String templateName = json.optString("templateName", "").trim();
    if (templateName.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return;
    }

    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      rbas.doRemoveTemplates(templateName, true);
    }

    rsp.sendRedirect(redirectUrl);
  }

  /**
   * Called from the assign role dialog form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  @SuppressWarnings("unchecked")
  public void doAssignRoleSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    AssignFormData data = parseAssignForm(req, rsp);
    if (data == null) {
      return;
    }

    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      for (String assignType : data.roles.keySet()) {
        if (!hasScopePermission(assignType)) {
          continue;
        }
        JSONObject roleEntries = data.roles.getJSONObject(assignType);
        for (String roleName : roleEntries.keySet()) {
          if (roleEntries.getBoolean(roleName)) {
            switch (data.type) {
              case "USER" -> rbas.doAssignUserRole(assignType, roleName, data.name);
              case "GROUP" -> rbas.doAssignGroupRole(assignType, roleName, data.name);
              default -> rbas.doAssignRole(assignType, roleName, data.name);
            }
          }
        }
      }
    }

    rsp.sendRedirect(req.getContextPath() + "/manage/role-strategy/");
  }

  /**
   * Called from the edit assignment dialog form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  @SuppressWarnings("unchecked")
  public void doEditAssignSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    AssignFormData data = parseAssignForm(req, rsp);
    if (data == null) {
      return;
    }

    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      PermissionEntry entry = switch (data.type) {
        case "GROUP" -> PermissionEntry.group(data.name);
        case "EITHER" -> new PermissionEntry(AuthorizationType.EITHER, data.name);
        default -> PermissionEntry.user(data.name);
      };

      for (String assignType : data.roles.keySet()) {
        // Skip scopes the user doesn't have permission for
        if (!hasScopePermission(assignType)) {
          continue;
        }
        RoleMap roleMap = rbas.getRoleMap(RoleType.fromString(assignType));
        JSONObject roleEntries = data.roles.getJSONObject(assignType);

        for (String roleName : roleEntries.keySet()) {
          Role role = roleMap.getRole(roleName);
          if (role == null) {
            continue;
          }
          boolean shouldBeAssigned = roleEntries.getBoolean(roleName);
          boolean isCurrentlyAssigned = roleMap.isAssigned(role, data.name, data.type);
          if (shouldBeAssigned && !isCurrentlyAssigned) {
            roleMap.assignRole(role, entry);
          } else if (!shouldBeAssigned && isCurrentlyAssigned) {
            roleMap.deleteRoleSid(entry, roleName);
          }
        }
      }

      try {
        Jenkins.get().save();
      } catch (Exception e) {
        throw new ServletException(e);
      }
    }

    rsp.sendRedirect(req.getContextPath() + "/manage/role-strategy/");
  }

  /**
   * Called from the add role dialog form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  @SuppressWarnings("unchecked")
  public void doAddRoleSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    RoleFormData data = parseRoleForm(req, rsp);
    if (data == null) {
      return;
    }

    Pattern compiledPattern = compilePatternOrError(data.pattern, rsp);
    if (compiledPattern == null) {
      return;
    }

    Set<Permission> permissions = collectPermissionsFromScoped(data.json, data.scope);
    String templateName = data.json.optString("templateName", "");
    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      String tmplName = templateName.isEmpty() ? null : templateName;
      Role role = new Role(data.roleName, compiledPattern, permissions, "", tmplName);
      rbas.getRoleMap(RoleType.fromString(data.scope)).addRole(role);
      saveJenkinsConfig();
    }

    rsp.sendRedirect(req.getContextPath() + "/manage/role-strategy/manage-roles");
  }

  /**
   * Called from the edit role dialog form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  @SuppressWarnings("unchecked")
  public void doEditRoleSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    RoleFormData data = parseRoleForm(req, rsp);
    if (data == null) {
      return;
    }

    Pattern compiledPattern = compilePatternOrError(data.pattern, rsp);
    if (compiledPattern == null) {
      return;
    }

    Set<Permission> permissions = collectPermissionsFromFlat(data.json);
    String templateName = data.json.optString("templateName", "");
    AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
    if (strategy instanceof RoleBasedAuthorizationStrategy rbas) {
      RoleType roleType = RoleType.fromString(data.scope);
      RoleMap roleMap = rbas.getRoleMap(roleType);
      Role existingRole = roleMap.getRole(data.roleName);
      if (existingRole != null) {
        Set<PermissionEntry> sids = roleMap.getGrantedRolesEntries().get(existingRole);
        roleMap.removeRole(existingRole);
        String tmplName = templateName.isEmpty() ? null : templateName;
        Role updatedRole = new Role(data.roleName, compiledPattern, permissions, "", tmplName);
        roleMap.addRole(updatedRole, sids != null ? sids : new HashSet<>());
        saveJenkinsConfig();
      }
    }

    rsp.sendRedirect(req.getContextPath() + "/manage/role-strategy/manage-roles");
  }

  // ============================================
  // Shared form-parsing helpers
  // ============================================

  /**
   * Compile a regex pattern, sending a 400 error response if invalid.
   *
   * @return the compiled pattern, or null if an error response was sent
   */
  @CheckForNull
  private static Pattern compilePatternOrError(String pattern, StaplerResponse2 rsp)
      throws IOException {
    try {
      return Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      rsp.sendError(400, "Invalid pattern: " + e.getDescription());
      return null;
    }
  }

  /**
   * Parse submitted form JSON, redirecting on failure.
   *
   * @return the parsed JSON, or null if redirect was sent
   */
  @CheckForNull
  private static JSONObject getSubmittedFormOrRedirect(
      StaplerRequest2 req, StaplerResponse2 rsp, String redirectUrl) throws IOException, ServletException {
    req.setCharacterEncoding("UTF-8");
    try {
      return req.getSubmittedForm();
    } catch (Exception e) {
      rsp.sendRedirect(redirectUrl);
      return null;
    }
  }

  private static void saveJenkinsConfig() throws ServletException {
    try {
      Jenkins.get().save();
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  /** Parsed data from an assign/edit-assign dialog submission. */
  private record AssignFormData(String name, String type, JSONObject roles) {}

  @CheckForNull
  private static AssignFormData parseAssignForm(StaplerRequest2 req, StaplerResponse2 rsp)
      throws IOException, ServletException {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    String redirectUrl = req.getContextPath() + "/manage/role-strategy/";

    JSONObject json = getSubmittedFormOrRedirect(req, rsp, redirectUrl);
    if (json == null) {
      return null;
    }

    String name = json.optString("name", "").trim();
    String type = json.optString("type", "USER");
    if (name.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return null;
    }

    JSONObject roles = json.optJSONObject("roles");
    if (roles == null) {
      rsp.sendRedirect(redirectUrl);
      return null;
    }

    return new AssignFormData(name, type, roles);
  }

  /** Parsed data from an add/edit role dialog submission. */
  private record RoleFormData(JSONObject json, String scope, String roleName, String pattern) {}

  @CheckForNull
  private static RoleFormData parseRoleForm(StaplerRequest2 req, StaplerResponse2 rsp)
      throws IOException, ServletException {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    String redirectUrl = req.getContextPath() + "/manage/role-strategy/manage-roles";

    JSONObject json = getSubmittedFormOrRedirect(req, rsp, redirectUrl);
    if (json == null) {
      return null;
    }

    String scope = json.optString("scope", "globalRoles");

    // Enforce per-scope permission
    checkScopePermission(scope);

    // Edit uses originalRoleName, add uses roleName
    String roleName = json.optString("originalRoleName", "").trim();
    if (roleName.isEmpty()) {
      roleName = json.optString("roleName", "").trim();
    }
    String pattern = json.optString("pattern", ".*").trim();

    if (roleName.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return null;
    }

    if ("globalRoles".equals(scope)) {
      pattern = ".*";
    } else if (pattern.isEmpty()) {
      rsp.sendRedirect(redirectUrl);
      return null;
    }

    return new RoleFormData(json, scope, roleName, pattern);
  }

  /**
   * Check that the current user has permission for the given role scope.
   * Throws AccessDeniedException if not.
   */
  private static void checkScopePermission(String scope) {
    switch (scope) {
      case RoleBasedAuthorizationStrategy.GLOBAL ->
          Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      case RoleBasedAuthorizationStrategy.PROJECT ->
          Jenkins.get().checkPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN);
      case RoleBasedAuthorizationStrategy.SLAVE, RoleBasedAuthorizationStrategy.AGENT ->
          Jenkins.get().checkPermission(RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN);
      default -> throw new IllegalArgumentException("Unknown scope: " + scope);
    }
  }

  /**
   * Check if the current user has permission for the given role scope.
   */
  private static boolean hasScopePermission(String scope) {
    return switch (scope) {
      case RoleBasedAuthorizationStrategy.GLOBAL ->
          Jenkins.get().hasPermission(Jenkins.ADMINISTER);
      case RoleBasedAuthorizationStrategy.PROJECT ->
          Jenkins.get().hasPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN);
      case RoleBasedAuthorizationStrategy.SLAVE, RoleBasedAuthorizationStrategy.AGENT ->
          Jenkins.get().hasPermission(RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN);
      default -> false;
    };
  }

  /**
   * Collect permissions from a flat "permissions" JSON object (edit role dialog).
   */
  @SuppressWarnings("unchecked")
  private static Set<Permission> collectPermissionsFromFlat(JSONObject json) {
    Set<Permission> permissions = new HashSet<>();
    JSONObject permsJson = json.optJSONObject("permissions");
    if (permsJson != null) {
      for (String rawKey : (Set<String>) permsJson.keySet()) {
        if (permsJson.optBoolean(rawKey, false)) {
          Permission p = Permission.fromId(rawKey);
          if (p != null) {
            permissions.add(p);
          }
        }
      }
    }
    return permissions;
  }

  /**
   * Collect permissions from a scope-nested "permissions" JSON object (add role dialog).
   */
  @SuppressWarnings("unchecked")
  private static Set<Permission> collectPermissionsFromScoped(JSONObject json, String scope) {
    Set<Permission> permissions = new HashSet<>();
    JSONObject permissionsJson = json.optJSONObject("permissions");
    if (permissionsJson != null) {
      JSONObject scopePerms = permissionsJson.optJSONObject(scope);
      if (scopePerms != null) {
        for (String rawKey : (Set<String>) scopePerms.keySet()) {
          if (scopePerms.optBoolean(rawKey, false)) {
            String permId = stripBrackets(rawKey);
            Permission p = Permission.fromId(permId);
            if (p != null) {
              permissions.add(p);
            }
          }
        }
      }
    }
    return permissions;
  }

  /**
   * Collect permission ID strings from a flat "permissions" JSON (template dialogs).
   */
  @SuppressWarnings("unchecked")
  private static Set<String> collectPermissionIds(JSONObject json) {
    Set<String> permIds = new HashSet<>();
    JSONObject permsJson = json.optJSONObject("permissions");
    if (permsJson != null) {
      for (String rawKey : (Set<String>) permsJson.keySet()) {
        if (permsJson.optBoolean(rawKey, false)) {
          String permId = stripBrackets(rawKey);
          if (Permission.fromId(permId) != null) {
            permIds.add(permId);
          }
        }
      }
    }
    return permIds;
  }

  private static String stripBrackets(String key) {
    if (key.startsWith("[") && key.endsWith("]")) {
      return key.substring(1, key.length() - 1);
    }
    return key;
  }

  public ExtensionList<RoleMacroExtension> getRoleMacroExtensions() {
    return RoleMacroExtension.all();
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
