package org.jenkinsci.plugins.rolestrategy.playwright.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Base page object for the React role-strategy pages, holding the bits that are
 * common across them: navigation and the two kinds of modal — the React add/edit
 * {@code dialog.rsp-dialog} and Jenkins core's confirm dialog (a plain
 * {@code dialog.jenkins-dialog} opened by {@code dialog.confirm}).
 *
 * <p>Uses the self-type generic so the shared fluent methods return the concrete
 * page type.
 *
 * @param <T> the concrete page object type
 */
abstract class RoleStrategyPage<T extends RoleStrategyPage<T>> {

  protected final Page page;
  private final String pageUrl;

  protected RoleStrategyPage(Page page, String pageUrl) {
    this.page = page;
    this.pageUrl = pageUrl;
  }

  @SuppressWarnings("unchecked")
  protected final T self() {
    return (T) this;
  }

  public T goTo() {
    page.navigate(pageUrl);
    waitForLoaded();
    return self();
  }

  /** Assert a stable landmark so callers know the page has rendered. */
  protected abstract void waitForLoaded();

  // --- React add/edit dialog (dialog.rsp-dialog) ---

  /** The React add/edit dialog. */
  protected Locator dialog() {
    return page.locator("dialog.rsp-dialog");
  }

  /** Click the dialog's primary button and wait for it to close. */
  public T submitDialog() {
    dialog().locator("button.jenkins-button--primary").click();
    assertThat(dialog()).hasCount(0);
    return self();
  }

  // --- Jenkins core confirm dialog (dialog.confirm) ---

  /** Jenkins core's confirm dialog — a plain jenkins-dialog, not the React one. */
  protected Locator confirmDialog() {
    return page.locator("dialog.jenkins-dialog:not(.rsp-dialog)");
  }

  public T confirmDialogContainsText(String text) {
    assertThat(confirmDialog()).containsText(text);
    return self();
  }

  /** Click the confirm dialog's OK button (data-id="ok") and wait for it to close. */
  public T acceptConfirmDialog() {
    confirmDialog().locator("[data-id='ok']").click();
    assertThat(confirmDialog()).hasCount(0);
    return self();
  }

  /** Click the confirm dialog's Cancel button (data-id="cancel") and wait for it to close. */
  public T dismissConfirmDialog() {
    confirmDialog().locator("[data-id='cancel']").click();
    assertThat(confirmDialog()).hasCount(0);
    return self();
  }
}
