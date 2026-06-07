package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page object for the Manage Roles page. Each role scope renders as a
 * {@code section.rsp-container[data-role-type]} holding {@code .rsp-card} roles
 * identified by their {@code .rsp-card__name} text; cards are scoped to their
 * section because a name may be reused across scopes. Filtering removes
 * non-matching cards from the DOM, so a hidden card asserts as absent. Shared
 * modal handling lives in {@link RoleStrategyPage}.
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

  // --- Card assertions ---

  public ManageRolesPage hasRoleCard(String type, String name) {
    assertThat(card(type, name)).isVisible();
    return this;
  }

  public ManageRolesPage hasNoRoleCard(String type, String name) {
    assertThat(card(type, name)).hasCount(0);
    return this;
  }

  public ManageRolesPage roleCardHasPattern(String type, String name, String expected) {
    assertThat(card(type, name).locator(".rsp-card__pattern")).containsText(expected);
    return this;
  }

  public ManageRolesPage roleCardHasSummary(String type, String name, String expected) {
    assertThat(card(type, name).locator(".rsp-card__summary")).containsText(expected);
    return this;
  }

  public ManageRolesPage roleCardHasTemplateBadge(String type, String name, String templateName) {
    assertThat(card(type, name).locator(".rsp-card__template-badge")).containsText(templateName);
    return this;
  }

  // --- Actions ---

  public ManageRolesPage clickAddRole() {
    page.locator("#rsp-add-role-btn").click();
    return this;
  }

  public ManageRolesPage clickEditRole(String type, String name) {
    card(type, name).locator(".rsp-card__actions button").first().click();
    return this;
  }

  public ManageRolesPage clickDeleteRole(String type, String name) {
    card(type, name).locator(".rsp-card__actions button").last().click();
    return this;
  }

  public ManageRolesPage search(String query) {
    page.getByPlaceholder("Search roles").fill(query);
    return this;
  }

  // --- Add/Edit dialog ---

  public ManageRolesPage waitForDialog() {
    assertThat(dialog()).isVisible();
    dialog().locator(".rsp-assign-dialog__role-item").first()
        .waitFor(new Locator.WaitForOptions().setTimeout(60000));
    return this;
  }

  public ManageRolesPage dialogSelectScope(String type) {
    dialog().locator(".jenkins-radio:has(input[value='" + type + "']) label").click();
    return this;
  }

  public ManageRolesPage dialogSetName(String name) {
    dialog().locator("#rsp-add-role-name").fill(name);
    return this;
  }

  public ManageRolesPage dialogSetPattern(String pattern) {
    dialog().locator("#rsp-add-role-pattern").fill(pattern);
    return this;
  }

  public ManageRolesPage dialogSelectTemplate(String templateName) {
    dialog().locator("#rsp-add-role-template").selectOption(templateName);
    return this;
  }

  public ManageRolesPage dialogTogglePermission(String permissionId) {
    dialog().locator(".rsp-assign-dialog__role-item[data-permission-id='" + permissionId + "'] label")
        .click();
    return this;
  }

  // --- Locators ---

  private Locator section(String type) {
    return page.locator(".rsp-container[data-role-type='" + type + "']");
  }

  private Locator card(String type, String name) {
    return section(type)
        .locator(".rsp-card:has(.rsp-card__name:text-is(\"" + name + "\"))");
  }
}
