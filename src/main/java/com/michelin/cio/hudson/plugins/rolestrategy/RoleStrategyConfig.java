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
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
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
   * Called on roles management form submission.
   */
  @RequirePOST
  @Restricted(NoExternalUse.class)
  public void doRolesSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    // Let the strategy descriptor handle the form
    RoleBasedAuthorizationStrategy.DESCRIPTOR.doRolesSubmit(req, rsp);
    // Redirect to the plugin index page
    FormApply.success(".").generateResponse(req, rsp, this);
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
