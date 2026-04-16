package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionTemplatesPage extends RoleStrategyPage<PermissionTemplatesPage> {

  private static final Logger log = LoggerFactory.getLogger(PermissionTemplatesPage.class);

  public PermissionTemplatesPage(Page page, String baseUrl) {
    super(page, baseUrl + "manage/role-strategy/permission-templates");
  }

  @Override
  PermissionTemplatesPage waitForLoaded() {
    super.waitForLoaded();
    assertThat(page.getByRole(
        AriaRole.HEADING, new Page.GetByRoleOptions().setName("Permission Templates"))).isVisible();
    return this;
  }

  public PermissionTemplatesPage hasTemplateCard(String templateName) {
    log.info("Verifying template card exists: {}", templateName);
    assertThat(getTemplateCardLocator(templateName)).isVisible();
    return this;
  }

  public PermissionTemplatesPage hasNoTemplateCard(String templateName) {
    log.info("Verifying template card does not exist: {}", templateName);
    assertThat(getTemplateCardLocator(templateName)).hasCount(0);
    return this;
  }

  public PermissionTemplatesPage templateCardHasSummary(String templateName, String expectedText) {
    log.info("Verifying template '{}' has summary containing '{}'", templateName, expectedText);
    Locator summary = getTemplateCardLocator(templateName).locator(".rsp-card__summary");
    assertThat(summary).containsText(expectedText);
    return this;
  }

  public PermissionTemplatesPage templateCardHasInUseBadge(String templateName) {
    log.info("Verifying template '{}' has 'In use' badge", templateName);
    Locator badge = getTemplateCardLocator(templateName).locator(".rsp-card__template-badge");
    assertThat(badge).isVisible();
    assertThat(badge).containsText("In use");
    return this;
  }

  public PermissionTemplatesPage templateCardNotExpandable(String templateName) {
    Locator card = getTemplateCardLocator(templateName);
    Locator toggle = card.locator(".rsp-card__toggle");
    assertThat(toggle).isHidden();
    return this;
  }

  public PermissionTemplatesPage templateCardIsHidden(String templateName) {
    assertThat(getTemplateCardLocator(templateName))
        .hasClass(java.util.regex.Pattern.compile(".*rsp-card--hidden.*"));
    return this;
  }

  public PermissionTemplatesPage templateCardIsVisible(String templateName) {
    assertThat(getTemplateCardLocator(templateName))
        .not().hasClass(java.util.regex.Pattern.compile(".*rsp-card--hidden.*"));
    return this;
  }

  public PermissionTemplatesPage search(String query) {
    log.info("Searching templates for '{}'", query);
    page.locator(".rsp-template-search input").fill(query);
    return this;
  }

  public PermissionTemplatesPage clickAddTemplate() {
    log.info("Clicking Add Template button");
    page.locator(".rsp-add-template-btn").click();
    return this;
  }

  public PermissionTemplatesPage clickEditTemplate(String templateName) {
    log.info("Clicking Edit on template '{}'", templateName);
    getTemplateCardLocator(templateName).locator(".rsp-template-edit").click();
    return this;
  }

  public PermissionTemplatesPage clickDeleteTemplate(String templateName) {
    log.info("Clicking Delete on template '{}'", templateName);
    getTemplateCardLocator(templateName).locator(".rsp-template-delete").click();
    return this;
  }

  public PermissionTemplatesPage confirmDelete() {
    log.info("Confirming delete dialog");
    page.locator(
        "dialog.jenkins-dialog .jenkins-button--primary,"
            + " dialog.jenkins-dialog .jenkins-button--destructive").first().click();
    page.waitForTimeout(500);
    return this;
  }

  public PermissionTemplatesPage deleteDialogContainsText(String text) {
    assertThat(page.locator("dialog.jenkins-dialog")).containsText(text);
    return this;
  }

  public PermissionTemplatesPage waitForAddTemplateDialog() {
    log.info("Waiting for add-template dialog to load");
    page.waitForSelector("form[name='add-template'] .rsp-assign-dialog__role-item",
        new Page.WaitForSelectorOptions().setTimeout(60000));
    return this;
  }

  public PermissionTemplatesPage waitForEditTemplateDialog() {
    log.info("Waiting for edit-template dialog to load");
    page.waitForSelector("form[name='edit-template'] .rsp-assign-dialog__role-item",
        new Page.WaitForSelectorOptions().setTimeout(60000));
    return this;
  }

  // --- Dialog interaction helpers ---

  public PermissionTemplatesPage dialogSetTemplateName(String name) {
    page.locator("form input[name='templateName']").fill(name);
    return this;
  }

  public PermissionTemplatesPage dialogTogglePermission(String permName) {
    page.locator("form .rsp-assign-dialog__role-item[data-role-name='" + permName + "'] label")
        .first().click();
    return this;
  }

  public PermissionTemplatesPage dialogPermissionIsCheckedAndDisabled(String permName) {
    Locator cb = page.locator(
        "form .rsp-assign-dialog__role-item[data-role-name='" + permName + "']")
        .first()
        .locator("input[type='checkbox']");
    assertThat(cb).isChecked();
    assertThat(cb).isDisabled();
    return this;
  }

  public PermissionTemplatesPage dialogSubmit() {
    page.locator("form button.jenkins-button--primary").click();
    page.waitForLoadState(LoadState.LOAD);
    page.waitForLoadState(LoadState.NETWORKIDLE);
    page.waitForTimeout(500);
    return this;
  }

  private Locator getTemplateCardLocator(String templateName) {
    return page.locator("#rsp-template-cards .rsp-card[data-template-name='" + templateName + "']");
  }
}
