package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page object for the Manage Roles page. Role types render as tabs; roles as
 * {@code .rsp-card} elements identified by their {@code .rsp-card__name} text.
 * Shared modal handling lives in {@link RoleStrategyPage}.
 */
public class ManageRolesPage extends RoleStrategyPage<ManageRolesPage> {

  public ManageRolesPage(Page page, String baseUrl) {
    super(page, baseUrl + "manage/role-strategy/manage-roles");
  }

  @Override
  protected void waitForLoaded() {
    assertThat(page.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Roles"))).isVisible();
  }

  // --- Tabs ---

  public ManageRolesPage selectTab(String label) {
    page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label)).click();
    return this;
  }

  // --- Card assertions ---

  public ManageRolesPage hasRoleCard(String name) {
    assertThat(card(name)).isVisible();
    return this;
  }

  public ManageRolesPage hasNoRoleCard(String name) {
    assertThat(card(name)).hasCount(0);
    return this;
  }

  public ManageRolesPage roleCardHasSummary(String name, String expected) {
    assertThat(card(name).locator(".rsp-card__summary")).containsText(expected);
    return this;
  }

  public ManageRolesPage roleCardHasPattern(String name, String pattern) {
    assertThat(card(name).locator(".rsp-card__pattern")).containsText(pattern);
    return this;
  }

  public ManageRolesPage roleCardHasTemplateBadge(String name, String template) {
    assertThat(card(name).locator(".rsp-card__template-badge")).containsText(template);
    return this;
  }

  // --- Actions ---

  public ManageRolesPage clickAddRole() {
    page.locator("#rsp-add-role-btn").click();
    return this;
  }

  public ManageRolesPage clickEditRole(String name) {
    card(name).locator(".rsp-card__actions button").first().click();
    return this;
  }

  public ManageRolesPage clickDeleteRole(String name) {
    card(name).locator(".rsp-card__actions button").last().click();
    return this;
  }

  public ManageRolesPage search(String query) {
    page.locator("#rsp-roles-panel .jenkins-search__input").first().fill(query);
    return this;
  }

  /** Open the matching items/agents dialog from the card's pattern chip. */
  public ManageRolesPage clickPatternChip(String name) {
    card(name).locator("button.rsp-card__pattern").click();
    return this;
  }

  public ManageRolesPage matchingDialogContainsText(String text) {
    assertThat(dialog()).containsText(text);
    return this;
  }

  public ManageRolesPage closeMatchingDialog() {
    dialog().locator("button[aria-label='Close']").click();
    assertThat(dialog()).hasCount(0);
    return this;
  }

  // --- Add/Edit dialog fields (role-specific) ---

  public ManageRolesPage waitForDialog() {
    assertThat(dialog()).isVisible();
    dialog().locator(".rsp-assign-dialog__role-item").first()
        .waitFor(new Locator.WaitForOptions().setTimeout(60000));
    return this;
  }

  public ManageRolesPage dialogSetName(String name) {
    dialog().locator("#rsp-role-name").fill(name);
    return this;
  }

  public ManageRolesPage dialogSetPattern(String pattern) {
    // Blur after filling: pattern validation only runs when the field loses focus.
    dialog().locator("#rsp-role-pattern").fill(pattern);
    dialog().locator("#rsp-role-pattern").blur();
    return this;
  }

  public ManageRolesPage dialogSelectTemplate(String template) {
    dialog().locator("#rsp-role-template").selectOption(template);
    return this;
  }

  public ManageRolesPage dialogTogglePermission(String permissionId) {
    dialog().locator(".rsp-assign-dialog__role-item[data-permission-id='" + permissionId + "'] label")
        .click();
    return this;
  }

  public ManageRolesPage dialogPermissionDisabled(String permissionId) {
    assertThat(dialog()
        .locator(".rsp-assign-dialog__role-item[data-permission-id='" + permissionId + "'] input"))
        .isDisabled();
    return this;
  }

  public ManageRolesPage dialogHasError(String text) {
    assertThat(dialog()).containsText(text);
    return this;
  }

  public ManageRolesPage dialogSubmitDisabled() {
    assertThat(dialog().locator("button.jenkins-button--primary")).isDisabled();
    return this;
  }

  public ManageRolesPage cancelDialog() {
    dialog().locator("button[aria-label='Close']").click();
    assertThat(dialog()).hasCount(0);
    return this;
  }

  // --- Locators ---

  private Locator card(String name) {
    return page.locator(".rsp-card:has(.rsp-card__name:text-is(\"" + name + "\"))");
  }
}
