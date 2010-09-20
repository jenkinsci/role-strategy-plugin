/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Thomas Maurel
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

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.security.AuthorizationStrategy;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Add the role management link to the Manage Hudson page.
 * @author Thomas Maurel
 */
@Extension
public class RoleStrategyConfig extends ManagementLink {

  /**
   * Provides the icon for the Manage Hudson page link
   * @return Path to the icon
   */
  @Override
  public String getIconFileName() {
    String icon = null;
    // Only show this link if the role-based authorization strategy has been enabled
    if(Hudson.getInstance().getAuthorizationStrategy() instanceof RoleBasedAuthorizationStrategy) {
      icon = "secure.gif";
    }
    return icon;
  }

  /**
   * URL name for the strategy management.
   * @return Path to the strategy admin panel
   */
  @Override
  public String getUrlName() {
    return "role-strategy";
  }

  /**
   * Text displayed in the Manage Hudson panel.
   * @return Link text in the Admin panel
   */
  public String getDisplayName() {
    return Messages.RoleBasedAuthorizationStrategy_Manage();
  }

  /**
   * Text displayed for the roles assignment panel.
   * @return Title of the Role assignment panel
   */
  public String getAssignRolesName() {
    return Messages.RoleBasedAuthorizationStrategy_Assign();
  }

  /**
   * The description of the link.
   * @return The description of the link
   */
  @Override
  public String getDescription() {
    return Messages.RoleBasedAuthorizationStrategy_Description();
  }

  /**
   * Retrieve the {@link RoleBasedAuthorizationStrategy} object from the Hudson instance.
   * <p>Used by the views to build matrix.</p>
   * @return The {@link RoleBasedAuthorizationStrategy} object
   */
  public AuthorizationStrategy getStrategy() {
    AuthorizationStrategy strategy = Hudson.getInstance().getAuthorizationStrategy();
    if(strategy instanceof RoleBasedAuthorizationStrategy) {
      return strategy;
    }
    else {
      return null;
    }
  }

  /**
   * Called on roles management form submission.
   */
  public void doRolesSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, UnsupportedEncodingException, ServletException, FormException {
    Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
    // Let the strategy descriptor handle the form
    RoleBasedAuthorizationStrategy.DESCRIPTOR.doRolesSubmit(req, rsp);
    // Redirect to the plugin index page
    rsp.sendRedirect(".");
  }

  /**
   * Called on role's assignment form submission.
   */
  public void doAssignSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, UnsupportedEncodingException, ServletException, FormException {
    Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
    // Let the strategy descriptor handle the form
    RoleBasedAuthorizationStrategy.DESCRIPTOR.doAssignSubmit(req, rsp);
    // Redirect to the plugin index page
    rsp.sendRedirect(".");
  }

}
