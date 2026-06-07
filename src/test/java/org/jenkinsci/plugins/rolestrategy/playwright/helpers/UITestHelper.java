package org.jenkinsci.plugins.rolestrategy.playwright.helpers;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Shared setup for the Playwright UI tests.
 */
public final class UITestHelper {

  private UITestHelper() {}

  /** Log in to Jenkins as admin via the Playwright browser. */
  public static void loginAsAdmin(Page page, String baseUrl) {
    page.navigate(baseUrl + "login");
    page.locator("input[name='j_username']").fill("admin");
    page.locator("input[name='j_password']").fill("admin");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    page.waitForURL(baseUrl + "**");
  }

  /**
   * Configure Jenkins with a dummy security realm and a role-based authorization strategy.
   * Seeded with permission templates, the templates UI test relies on:
   *
   * <ul>
   *   <li>{@code dev-template} — Item Read + Build, referenced by an item role so it is "in use"</li>
   *   <li>{@code empty-template} — no permissions (non-expandable card)</li>
   * </ul>
   */
  public static RoleBasedAuthorizationStrategy setupRbasWithTemplates(JenkinsRule j) throws IOException {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    RoleBasedAuthorizationStrategy rbas = new RoleBasedAuthorizationStrategy();
    j.jenkins.setAuthorizationStrategy(rbas);

    rbas.doAddRole("globalRoles", "admin",
        "hudson.model.Hudson.Administer", "false", "", "");
    rbas.doAssignUserRole("globalRoles", "admin", "admin");

    rbas.doAddTemplate("dev-template",
        "hudson.model.Item.Read,hudson.model.Item.Build", false);
    rbas.doAddTemplate("empty-template", "", false);

    // An item role referencing dev-template marks it as "in use".
    rbas.doAddRole("projectRoles", "templated-role", "", "false", "tmpl-.*", "dev-template");

    return rbas;
  }

  /**
   * Configure Jenkins with a dummy security realm and a role-based authorization strategy.
   * Seeded with one role of each scope, the Manage Roles UI test lists:
   *
   * <ul>
   *   <li>global {@code admin} (Administer), assigned to the admin user</li>
   *   <li>global {@code auditor} (Overall/Read)</li>
   *   <li>item {@code developers} (pattern {@code dev-.*}, Item Read + Build)</li>
   *   <li>agent {@code operators} (pattern {@code node-.*}, Computer Build)</li>
   *   <li>{@code review-template} (Item Read), available in the Add dialog's template dropdown</li>
   * </ul>
   */
  public static RoleBasedAuthorizationStrategy setupRbasWithRoles(JenkinsRule j) throws IOException {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    RoleBasedAuthorizationStrategy rbas = new RoleBasedAuthorizationStrategy();
    j.jenkins.setAuthorizationStrategy(rbas);

    rbas.doAddRole("globalRoles", "admin",
        "hudson.model.Hudson.Administer", "false", "", "");
    rbas.doAssignUserRole("globalRoles", "admin", "admin");

    rbas.doAddRole("globalRoles", "auditor",
        "hudson.model.Hudson.Read", "false", "", "");
    rbas.doAddRole("projectRoles", "developers",
        "hudson.model.Item.Read,hudson.model.Item.Build", "false", "dev-.*", "");
    rbas.doAddRole("slaveRoles", "operators",
        "hudson.model.Computer.Build", "false", "node-.*", "");

    rbas.doAddTemplate("review-template", "hudson.model.Item.Read", false);

    return rbas;
  }
}
