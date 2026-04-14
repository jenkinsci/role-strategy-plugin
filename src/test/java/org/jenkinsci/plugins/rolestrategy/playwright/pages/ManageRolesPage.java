package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManageRolesPage extends RoleStrategyPage<ManageRolesPage> {

  private static final Logger log = LoggerFactory.getLogger(ManageRolesPage.class);

  public ManageRolesPage(Page page, String baseUrl) {
    super(page, baseUrl + "manage/role-strategy/manage-roles");
  }

  @Override
  ManageRolesPage waitForLoaded() {
    super.waitForLoaded();
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Roles"))).isVisible();
    return this;
  }

  public ManageRolesPage hasRoleCard(String roleName) {
    log.info("Verifying role card exists: {}", roleName);
    assertThat(getRoleCardLocator(roleName)).isVisible();
    return this;
  }

  public ManageRolesPage hasNoRoleCard(String roleName) {
    log.info("Verifying role card does not exist: {}", roleName);
    assertThat(getRoleCardLocator(roleName)).hasCount(0);
    return this;
  }

  public ManageRolesPage roleCardHasSummary(String roleName, String expectedText) {
    log.info("Verifying role '{}' has summary containing '{}'", roleName, expectedText);
    Locator summary = getRoleCardLocator(roleName).locator(".rsp-card__summary");
    assertThat(summary).containsText(expectedText);
    return this;
  }

  public ManageRolesPage roleCardHasPattern(String roleName, String expectedPattern) {
    log.info("Verifying role '{}' has pattern '{}'", roleName, expectedPattern);
    Locator pattern = getRoleCardLocator(roleName).locator(".rsp-card__pattern");
    assertThat(pattern).containsText(expectedPattern);
    return this;
  }

  public ManageRolesPage search(String query) {
    log.info("Searching roles for '{}'", query);
    page.locator(".rsp-roles-search input").fill(query);
    return this;
  }

  public ManageRolesPage roleCardIsHidden(String roleName) {
    assertThat(getRoleCardLocator(roleName)).hasClass(java.util.regex.Pattern.compile(".*rsp-card--hidden.*"));
    return this;
  }

  public ManageRolesPage roleCardIsVisible(String roleName) {
    assertThat(getRoleCardLocator(roleName)).not().hasClass(java.util.regex.Pattern.compile(".*rsp-card--hidden.*"));
    return this;
  }

  public ManageRolesPage roleCardNotExpandable(String roleName) {
    Locator card = getRoleCardLocator(roleName);
    Locator toggle = card.locator(".rsp-card__toggle");
    assertThat(toggle).isHidden();
    return this;
  }

  public ManageRolesPage clickAddRole() {
    log.info("Clicking Add Role button");
    page.locator(".rsp-add-role-global").click();
    return this;
  }

  public ManageRolesPage clickEditRole(String roleName) {
    log.info("Clicking Edit on role '{}'", roleName);
    getRoleCardLocator(roleName).locator(".rsp-card__edit").click();
    return this;
  }

  public ManageRolesPage clickDeleteRole(String roleName) {
    log.info("Clicking Delete on role '{}'", roleName);
    getRoleCardLocator(roleName).locator(".rsp-card__delete").click();
    return this;
  }

  public ManageRolesPage confirmDelete() {
    log.info("Confirming delete dialog");
    // Jenkins confirm dialog has a primary/destructive button
    page.locator("dialog.jenkins-dialog .jenkins-button--primary, dialog.jenkins-dialog .jenkins-button--destructive").first().click();
    page.waitForTimeout(500);
    return this;
  }

  /** Wait for dialog form to load including permission checkboxes. */
  public ManageRolesPage waitForAddRoleDialog() {
    log.info("Waiting for add-role dialog to load");
    page.waitForSelector("form[name='add-role'] .rsp-assign-dialog__role-item",
        new Page.WaitForSelectorOptions().setTimeout(60000));
    return this;
  }

  public ManageRolesPage waitForEditRoleDialog() {
    log.info("Waiting for edit-role dialog to load");
    page.waitForSelector("form[name='edit-role'] .rsp-assign-dialog__role-item",
        new Page.WaitForSelectorOptions().setTimeout(60000));
    return this;
  }

  // --- Dialog interaction helpers ---

  public ManageRolesPage dialogSetScope(String scope) {
    String label = switch (scope) {
      case "globalRoles" -> "Global role";
      case "projectRoles" -> "Item role";
      case "slaveRoles" -> "Agent role";
      default -> scope;
    };
    page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(label)).check();
    return this;
  }

  public ManageRolesPage dialogHasScopeRadio(String label) {
    assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(label))).isVisible();
    return this;
  }

  public ManageRolesPage dialogHasNoScopeRadio(String label) {
    assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(label))).hasCount(0);
    return this;
  }

  public ManageRolesPage dialogSetRoleName(String name) {
    page.locator("form input[name='roleName']").fill(name);
    return this;
  }

  public ManageRolesPage dialogSetPattern(String pattern) {
    page.locator("form input[name='pattern']").fill(pattern);
    return this;
  }

  public ManageRolesPage dialogSelectTemplate(String templateName) {
    page.locator("form select[name='templateName']").selectOption(templateName);
    return this;
  }

  public ManageRolesPage dialogFilterPermissions(String query) {
    page.locator("form .rsp-perm-dialog-filter input").fill(query);
    return this;
  }

  public ManageRolesPage dialogTogglePermission(String permName) {
    // Use label click — more reliable for jenkins-checkbox items
    page.locator("form div:not(.jenkins-hidden) .rsp-assign-dialog__role-item[data-role-name='" + permName + "'] label").first().click();
    return this;
  }

  public ManageRolesPage dialogPermissionIsCheckedAndDisabled(String permName) {
    Locator cb = page.locator("form div:not(.jenkins-hidden) .rsp-assign-dialog__role-item[data-role-name='" + permName + "']")
        .first()
        .locator("input[type='checkbox']");
    assertThat(cb).isChecked();
    assertThat(cb).isDisabled();
    return this;
  }

  public ManageRolesPage dialogPermissionHasImpliedLabel(String permName) {
    Locator item = page.locator("form div:not(.jenkins-hidden) .rsp-assign-dialog__role-item[data-role-name='" + permName + "']").first();
    assertThat(item.locator(".rsp-implied-label")).isVisible();
    return this;
  }

  /**
   * Submit the dialog form. For add/edit role dialogs, this triggers a form POST
   * that redirects back to the manage-roles page.
   */
  public ManageRolesPage dialogSubmit() {
    page.locator("form button.jenkins-button--primary").click();
    // Form submits and redirects — wait for the redirect to complete
    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    return this;
  }

  public ManageRolesPage dialogHasAlert(String expectedText) {
    Locator dialog = page.locator("dialog[open]").last();
    assertThat(dialog).containsText(expectedText);
    return this;
  }

  public ManageRolesPage dismissAlert() {
    page.keyboard().press("Escape");
    page.waitForTimeout(300);
    return this;
  }

  public ManageRolesPage closeDialog() {
    Locator closeBtn = page.locator("dialog[open] button:has-text('Close')");
    if (closeBtn.count() > 0) {
      closeBtn.first().click();
    }
    return this;
  }

  private Locator getRoleCardLocator(String roleName) {
    return page.locator(".rsp-card[data-role-name='" + roleName + "']");
  }
}
