package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page object for the Permission Templates page. Templates render as
 * {@code .rsp-card} elements identified by their {@code .rsp-card__name} text;
 * filtering removes non-matching cards from the DOM, so a hidden card asserts as
 * absent. Shared modal handling lives in {@link RoleStrategyPage}.
 */
public class PermissionTemplatesPage extends RoleStrategyPage<PermissionTemplatesPage> {

  public PermissionTemplatesPage(Page page, String baseUrl) {
    super(page, baseUrl + "manage/role-strategy/permission-templates");
  }

  @Override
  protected void waitForLoaded() {
    assertThat(page.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Permission Templates"))).isVisible();
  }

  // --- Card assertions ---

  public PermissionTemplatesPage hasTemplateCard(String name) {
    assertThat(card(name)).isVisible();
    return this;
  }

  public PermissionTemplatesPage hasNoTemplateCard(String name) {
    assertThat(card(name)).hasCount(0);
    return this;
  }

  public PermissionTemplatesPage templateCardHasSummary(String name, String expected) {
    assertThat(card(name).locator(".rsp-card__summary")).containsText(expected);
    return this;
  }

  public PermissionTemplatesPage templateCardHasInUseBadge(String name) {
    assertThat(card(name).locator(".rsp-card__template-badge")).containsText("In use");
    return this;
  }

  public PermissionTemplatesPage templateCardNotExpandable(String name) {
    // The toggle's slot stays in place to keep actions aligned, but it carries
    // no chevron and the header is not a button when there is nothing to expand.
    assertThat(card(name).locator(".rsp-card__toggle svg")).hasCount(0);
    assertThat(card(name).locator(".rsp-card__header"))
        .not()
        .hasAttribute("role", "button");
    return this;
  }

  public PermissionTemplatesPage deleteButtonDisabled(String name) {
    assertThat(deleteButton(name)).isDisabled();
    return this;
  }

  // --- Actions ---

  public PermissionTemplatesPage clickAddTemplate() {
    page.locator("#rsp-add-template-btn").click();
    return this;
  }

  public PermissionTemplatesPage clickEditTemplate(String name) {
    card(name).locator(".rsp-card__actions button").first().click();
    return this;
  }

  public PermissionTemplatesPage clickDeleteTemplate(String name) {
    deleteButton(name).click();
    return this;
  }

  public PermissionTemplatesPage search(String query) {
    page.getByPlaceholder("Search templates").fill(query);
    return this;
  }

  // --- Add/Edit dialog fields (template-specific) ---

  public PermissionTemplatesPage waitForDialog() {
    assertThat(dialog()).isVisible();
    dialog().locator(".rsp-assign-dialog__role-item").first()
        .waitFor(new Locator.WaitForOptions().setTimeout(60000));
    return this;
  }

  public PermissionTemplatesPage dialogSetName(String name) {
    dialog().locator("#rsp-template-name").fill(name);
    return this;
  }

  /** Submit the dialog by pressing Enter from the name field. */
  public PermissionTemplatesPage submitDialogWithEnter() {
    dialog().locator("#rsp-template-name").press("Enter");
    assertThat(dialog()).hasCount(0);
    return this;
  }

  public PermissionTemplatesPage dialogTogglePermission(String permissionId) {
    dialog().locator(".rsp-assign-dialog__role-item[data-permission-id='" + permissionId + "'] label")
        .click();
    return this;
  }

  // --- Locators ---

  private Locator card(String name) {
    return page.locator(".rsp-card:has(.rsp-card__name:text-is(\"" + name + "\"))");
  }

  private Locator deleteButton(String name) {
    return card(name).locator(".rsp-card__actions button").last();
  }
}
