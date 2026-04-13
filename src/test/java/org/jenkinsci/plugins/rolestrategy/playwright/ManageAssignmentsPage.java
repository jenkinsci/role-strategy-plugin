package org.jenkinsci.plugins.rolestrategy.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManageAssignmentsPage extends RoleStrategyPage<ManageAssignmentsPage> {

  private static final Logger log = LoggerFactory.getLogger(ManageAssignmentsPage.class);

  public ManageAssignmentsPage(Page page, String baseUrl) {
    super(page, baseUrl + "manage/role-strategy/");
  }

  @Override
  ManageAssignmentsPage waitForLoaded() {
    super.waitForLoaded();
    page.waitForSelector("#rsp-user-cards");
    return this;
  }

  public ManageAssignmentsPage hasUserCard(String userName) {
    log.info("Verifying user card exists: {}", userName);
    assertThat(getUserCardLocator(userName)).isVisible();
    return this;
  }

  public ManageAssignmentsPage hasNoUserCard(String userName) {
    log.info("Verifying user card does not exist: {}", userName);
    assertThat(getUserCardLocator(userName)).hasCount(0);
    return this;
  }

  public ManageAssignmentsPage userCardHasSummary(String userName, String expectedText) {
    log.info("Verifying user '{}' has summary containing '{}'", userName, expectedText);
    Locator summary = getUserCardLocator(userName).locator(".rsp-card__summary");
    assertThat(summary).containsText(expectedText);
    return this;
  }

  public ManageAssignmentsPage userCardNotExpandable(String userName) {
    Locator card = getUserCardLocator(userName);
    Locator toggle = card.locator(".rsp-card__toggle");
    // Hidden via CSS for empty cards
    Locator summary = card.locator(".rsp-card__summary--empty");
    assertThat(summary).hasCount(1);
    return this;
  }

  public ManageAssignmentsPage userCardHasEditButton(String userName) {
    assertThat(getUserCardLocator(userName).locator(".rsp-user-edit")).isVisible();
    return this;
  }

  public ManageAssignmentsPage userCardHasNoDeleteButton(String userName) {
    assertThat(getUserCardLocator(userName).locator(".rsp-user-delete")).hasCount(0);
    return this;
  }

  public ManageAssignmentsPage userCardHasDeleteButton(String userName) {
    assertThat(getUserCardLocator(userName).locator(".rsp-user-delete")).isVisible();
    return this;
  }

  public ManageAssignmentsPage expandUserCard(String userName) {
    getUserCardLocator(userName).locator(".rsp-card__header").click();
    return this;
  }

  public ManageAssignmentsPage expandedCardHasRole(String userName, String roleName) {
    Locator body = getUserCardLocator(userName).locator(".rsp-card__body");
    assertThat(body.getByText(roleName)).isVisible();
    return this;
  }

  public ManageAssignmentsPage search(String query) {
    log.info("Searching assignments for '{}'", query);
    page.locator(".rsp-assign-search input").fill(query);
    // Wait for debounced search + render
    page.waitForTimeout(500);
    return this;
  }

  public ManageAssignmentsPage clickAssignRole() {
    log.info("Clicking Assign role button");
    // Ensure page JS is fully initialized
    page.waitForLoadState();
    page.waitForTimeout(500);
    page.locator(".rsp-assign-role-btn").click();
    return this;
  }

  public ManageAssignmentsPage clickEditUser(String userName) {
    log.info("Clicking Edit on user '{}'", userName);
    getUserCardLocator(userName).locator(".rsp-user-edit").click();
    return this;
  }

  public ManageAssignmentsPage clickDeleteUser(String userName) {
    log.info("Clicking Delete on user '{}'", userName);
    getUserCardLocator(userName).locator(".rsp-user-delete").click();
    return this;
  }

  public ManageAssignmentsPage confirmDelete() {
    // Jenkins confirm dialog — use primary/destructive button
    page.locator("dialog.jenkins-dialog .jenkins-button--primary, dialog.jenkins-dialog .jenkins-button--destructive").first().click();
    page.waitForTimeout(500);
    return this;
  }

  public ManageAssignmentsPage waitForAssignDialog() {
    page.waitForSelector("form[name='assignRoles']",
        new Page.WaitForSelectorOptions().setTimeout(10000));
    // Wait for form content to fully load
    page.waitForTimeout(1000);
    return this;
  }

  public ManageAssignmentsPage waitForEditAssignDialog() {
    page.waitForSelector("form[name='editAssignRoles']");
    return this;
  }

  // --- Dialog helpers ---

  public ManageAssignmentsPage dialogSetName(String name) {
    page.locator("form input[name='name']").fill(name);
    return this;
  }

  public ManageAssignmentsPage dialogSetTypeUser() {
    page.locator("form input[name='type'][value='USER']").check(
        new com.microsoft.playwright.Locator.CheckOptions().setForce(true));
    return this;
  }

  public ManageAssignmentsPage dialogToggleRole(String roleName) {
    page.locator("form .rsp-assign-dialog__role-item[data-role-name='" + roleName + "'] label").first().click();
    return this;
  }

  public ManageAssignmentsPage dialogSubmit() {
    page.locator("form button.jenkins-button--primary").click();
    // Form submits and redirects — wait for the page to settle
    page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);
    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    page.waitForTimeout(500);
    return this;
  }

  private Locator getUserCardLocator(String userName) {
    return page.locator("#rsp-user-cards .rsp-card[data-user-name='" + userName + "']");
  }
}
