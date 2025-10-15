/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;

/**
 * Provides a root-level link for users with ITEM_ROLES_ADMIN or AGENT_ROLES_ADMIN permissions
 * who do not have SYSTEM_READ permission (and thus cannot access Manage Jenkins).
 * <p>
 * This action delegates to the RoleStrategyConfig for actual page handling.
 */
@Extension
public class RoleStrategyRootAction implements RootAction, StaplerProxy {

  @CheckForNull
  @Override
  public String getIconFileName() {
    // Only show this link if:
    // 1. The role-based authorization strategy is enabled
    // 2. User has ITEM_ROLES_ADMIN or AGENT_ROLES_ADMIN
    // 3. User does NOT have SYSTEM_READ (otherwise they can use ManagementLink)
    Jenkins jenkins = Jenkins.get();
    if (!(jenkins.getAuthorizationStrategy() instanceof RoleBasedAuthorizationStrategy)) {
      return null;
    }

    boolean hasRoleAdmin = jenkins.hasAnyPermission(
            RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN,
            RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN
    );
    boolean hasSystemRead = jenkins.hasPermission(Jenkins.SYSTEM_READ);

    // Only show if user has role admin permissions but NOT system read
    if (hasRoleAdmin && !hasSystemRead) {
      return "symbol-lock-closed-outline plugin-ionicons-api";
    }

    return null;
  }

  @CheckForNull
  @Override
  public String getDisplayName() {
    return Messages.RoleBasedAuthorizationStrategy_ManageAndAssign();
  }

  @CheckForNull
  @Override
  public String getUrlName() {
    return "role-strategy";
  }

  /**
   * Delegate all page handling to RoleStrategyConfig.
   * This allows the RootAction to serve at /role-strategy while reusing all the logic.
   */
  public Object getTarget() {
    Jenkins.get().checkAnyPermission(RoleBasedAuthorizationStrategy.ADMINISTER_AND_SOME_ROLES_ADMIN);
    return RoleStrategyConfig.get();
  }
}
