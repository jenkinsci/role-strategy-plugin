package org.jenkinsci.plugins.rolestrategy.playwright.helpers;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Sets up a standard RBAS configuration for UI tests.
 */
public final class UITestHelper {

  private UITestHelper() {}

  /** Log in to Jenkins as admin via the Playwright browser. */
  public static void loginAsAdmin(Page page, String baseUrl) {
    login(page, baseUrl, "admin", "admin");
  }

  /** Log in to Jenkins via the Playwright browser. */
  public static void login(Page page, String baseUrl, String username, String password) {
    navigateWithRetry(page, baseUrl + "login");
    page.locator("input[name='j_username']").fill(username);
    page.locator("input[name='j_password']").fill(password);
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    page.waitForURL(baseUrl + "**");
  }

  /** Navigate with retry on ERR_ABORTED (can happen after form submission redirects). */
  public static void navigateWithRetry(Page page, String url) {
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        page.navigate(url);
        return;
      } catch (PlaywrightException e) {
        if (e.getMessage().contains("ERR_ABORTED")) {
          page.waitForTimeout(1000);
        } else {
          throw e;
        }
      }
    }
  }

  /**
   * Configure Jenkins with RBAS and a dummy security realm.
   * Creates:
   * - Global roles: admin (Administer), reader (Read+Job/Read+View/Read), empty-role (no perms)
   * - Item roles: developer (dev-.*, Job/Read+Build+Configure)
   * - Agent roles: agent-admin (agent-.*, Agent/Build+Connect)
   * - Assignments: admin->admin, testuser1->reader, testuser2->developer (item)
   */
  public static RoleBasedAuthorizationStrategy setupRbas(JenkinsRule j) throws IOException {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    RoleBasedAuthorizationStrategy rbas = new RoleBasedAuthorizationStrategy();
    j.jenkins.setAuthorizationStrategy(rbas);

    // Global roles
    rbas.doAddRole("globalRoles", "admin",
        "hudson.model.Hudson.Administer", "false", "", "");
    rbas.doAddRole("globalRoles", "reader",
        "hudson.model.Hudson.Read,hudson.model.Item.Read,hudson.model.View.Read",
        "false", "", "");
    rbas.doAddRole("globalRoles", "empty-role",
        "", "false", "", "");

    // Item roles
    rbas.doAddRole("projectRoles", "developer",
        "hudson.model.Item.Read,hudson.model.Item.Build,hudson.model.Item.Configure",
        "false", "dev-.*", "");

    // Agent roles
    rbas.doAddRole("slaveRoles", "agent-admin",
        "hudson.model.Computer.Build,hudson.model.Computer.Connect",
        "false", "agent-.*", "");

    // Assignments
    rbas.doAssignUserRole("globalRoles", "admin", "admin");
    rbas.doAssignUserRole("globalRoles", "reader", "testuser1");
    rbas.doAssignUserRole("projectRoles", "developer", "testuser2");

    return rbas;
  }
}
