package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page object for the Assign Roles page (the role-strategy index page). Role
 * types render as tabs; each user/group entry is an {@code .rsp-card}
 * identified by the {@code data-sid} attribute on its name, so lookups are
 * stable even when a resolved display name is rendered around the sid.
 * Shared modal handling lives in {@link RoleStrategyPage}.
 */
public class AssignRolesPage extends RoleStrategyPage<AssignRolesPage> {

  public AssignRolesPage(Page page, String baseUrl) {
    super(page, baseUrl + "manage/role-strategy/");
  }

  @Override
  protected void waitForLoaded() {
    assertThat(page.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Assign Roles"))).isVisible();
  }

  // --- Tabs ---

  public AssignRolesPage selectTab(String label) {
    page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label)).click();
    return this;
  }

  // --- Card assertions ---

  public AssignRolesPage hasSidCard(String sid) {
    assertThat(card(sid)).isVisible();
    return this;
  }

  public AssignRolesPage hasNoSidCard(String sid) {
    assertThat(card(sid)).hasCount(0);
    return this;
  }

  public AssignRolesPage sidCardHasSummary(String sid, String expected) {
    assertThat(card(sid).locator(".rsp-card__summary")).containsText(expected);
    return this;
  }

  public AssignRolesPage sidCardHasWarningBadge(String sid) {
    assertThat(card(sid).locator(".rsp-assign__badge--warning")).isVisible();
    return this;
  }

  public AssignRolesPage sidCardHasNoWarningBadge(String sid) {
    assertThat(card(sid).locator(".rsp-assign__badge--warning")).hasCount(0);
    return this;
  }

  public AssignRolesPage hasAmbiguousAlert() {
    assertThat(ambiguousAlert()).isVisible();
    return this;
  }

  public AssignRolesPage hasNoAmbiguousAlert() {
    assertThat(ambiguousAlert()).hasCount(0);
    return this;
  }

  // --- Expanded card (read-only role chips) ---

  public AssignRolesPage expandCard(String sid) {
    card(sid).locator(".rsp-card__header").click();
    return this;
  }

  public AssignRolesPage cardShowsRoleChip(String sid, String roleName) {
    assertThat(card(sid).locator(".rsp-assign__chip[data-role-name=\"" + roleName + "\"]"))
        .isVisible();
    return this;
  }

  // --- Actions ---

  public AssignRolesPage clickAddEntry() {
    page.locator("#rsp-add-sid-btn").click();
    return this;
  }

  /** Pick User or Group in the add dialog's type selector. */
  public AssignRolesPage dialogSelectType(String label) {
    // The native input is visually hidden by the jenkins-radio styling, so
    // interact through its label.
    dialog().locator(".jenkins-radio label", new Locator.LocatorOptions().setHasText(label))
        .click();
    return this;
  }

  public AssignRolesPage clickEditEntry(String sid) {
    card(sid).locator("button[aria-label='Edit roles']").click();
    return this;
  }

  public AssignRolesPage dialogSetSidName(String name) {
    dialog().locator("#rsp-sid-name").fill(name);
    return this;
  }

  /** Click a role's checkbox inside the add/edit dialog. */
  public AssignRolesPage dialogToggleRole(String roleName) {
    dialog().locator(".rsp-assign-dialog__role-item[data-role-name=\"" + roleName + "\"] label")
        .click();
    return this;
  }

  public AssignRolesPage dialogRoleChecked(String roleName) {
    assertThat(dialog()
        .locator(".rsp-assign-dialog__role-item[data-role-name=\"" + roleName + "\"] input"))
        .isChecked();
    return this;
  }

  /** Blur the name field to trigger the realm lookup feedback. */
  public AssignRolesPage dialogBlurSidName() {
    dialog().locator("#rsp-sid-name").blur();
    return this;
  }

  /** Assert on the server-rendered lookup snippet below the name field. */
  public AssignRolesPage dialogNameFeedbackContains(String text) {
    assertThat(dialog().locator(".rsp-table__cell")).containsText(text);
    return this;
  }

  public AssignRolesPage dialogNameFeedbackNotFound(String text) {
    assertThat(dialog().locator(".rsp-entry-not-found")).containsText(text);
    return this;
  }

  public AssignRolesPage dialogContainsText(String text) {
    assertThat(dialog()).containsText(text);
    return this;
  }

  public AssignRolesPage dialogHasError(String text) {
    assertThat(dialog()).containsText(text);
    return this;
  }

  public AssignRolesPage dialogSubmitDisabled() {
    assertThat(dialog().locator("button.jenkins-button--primary")).isDisabled();
    return this;
  }

  public AssignRolesPage cancelDialog() {
    dialog().locator("button[aria-label='Close']").click();
    assertThat(dialog()).hasCount(0);
    return this;
  }

  public AssignRolesPage clickDeleteEntry(String sid) {
    card(sid).locator("button[aria-label^='Remove']").click();
    return this;
  }

  public AssignRolesPage clickMigrateToUser(String sid) {
    card(sid).locator("button[aria-label='Migrate to user']").click();
    return this;
  }

  public AssignRolesPage clickMigrateToGroup(String sid) {
    card(sid).locator("button[aria-label='Migrate to group']").click();
    return this;
  }

  public AssignRolesPage search(String query) {
    page.locator("#rsp-assign-panel .jenkins-search__input").first().fill(query);
    return this;
  }

  public AssignRolesPage openFilterDropdown() {
    page.locator("#rsp-assign-panel .rsp-filter__button").click();
    return this;
  }

  public AssignRolesPage clickFilterOption(String name) {
    page.locator(".rsp-filter__dropdown")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name)).click();
    return this;
  }

  // --- Pagination ---

  public AssignRolesPage hasCardCount(int count) {
    assertThat(page.locator(".rsp-card")).hasCount(count);
    return this;
  }

  public AssignRolesPage paginationStatusContains(String text) {
    assertThat(page.locator(".rsp-pagination__status")).containsText(text);
    return this;
  }

  public AssignRolesPage hasNoPagination() {
    assertThat(page.locator(".rsp-pagination")).hasCount(0);
    return this;
  }

  public AssignRolesPage clickNextPage() {
    page.locator(".rsp-pagination")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Next")).click();
    return this;
  }

  public AssignRolesPage clickPreviousPage() {
    page.locator(".rsp-pagination")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Previous")).click();
    return this;
  }

  public AssignRolesPage clickManageRolesLink() {
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Manage Roles")).click();
    return this;
  }

  // --- Locators ---

  private Locator card(String sid) {
    return page.locator(".rsp-card:has([data-sid=\"" + sid + "\"])");
  }

  private Locator ambiguousAlert() {
    return page.locator("#rsp-assign-panel .jenkins-alert-warning");
  }
}
