package org.jenkinsci.plugins.rolestrategy.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import org.jenkinsci.plugins.rolestrategy.playwright.config.PlaywrightConfig;
import org.jenkinsci.plugins.rolestrategy.playwright.helpers.UITestHelper;
import org.jenkinsci.plugins.rolestrategy.playwright.pages.PermissionTemplatesPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * End-to-end UI tests for the React-based Permission Templates page.
 */
@WithJenkins
@UsePlaywright(PlaywrightConfig.class)
@DisplayName("Permission Templates page")
class PermissionTemplatesUITest {

  private static final String ITEM_READ = "hudson.model.Item.Read";
  private static final String ITEM_BUILD = "hudson.model.Item.Build";
  private static final String ITEM_CONFIGURE = "hudson.model.Item.Configure";

  private PermissionTemplatesPage open(JenkinsRule j, Page page) throws Exception {
    UITestHelper.setupRbasWithTemplates(j);
    String baseUrl = j.jenkins.getRootUrl();
    UITestHelper.loginAsAdmin(page, baseUrl);
    return new PermissionTemplatesPage(page, baseUrl).goTo();
  }

  @Test
  @DisplayName("lists templates seeded in the configuration with their permission summaries")
  void listsSeededTemplates(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .hasTemplateCard("dev-template")
        .templateCardHasSummary("dev-template", "Build")
        .templateCardHasSummary("dev-template", "Read")
        .hasTemplateCard("empty-template");
  }

  @Test
  @DisplayName("shows an 'In use' badge and disables deletion for a template used by a role")
  void inUseTemplateHasBadgeAndDeleteDisabled(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .templateCardHasInUseBadge("dev-template")
        .deleteButtonDisabled("dev-template");
  }

  @Test
  @DisplayName("does not allow expanding a template that has no permissions")
  void emptyTemplateIsNotExpandable(JenkinsRule j, Page page) throws Exception {
    open(j, page).templateCardNotExpandable("empty-template");
  }

  @Test
  @DisplayName("adds a new template through the dialog")
  void addTemplate(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddTemplate()
        .waitForDialog()
        .dialogSetName("qa-template")
        .dialogTogglePermission(ITEM_CONFIGURE)
        .submitDialog()
        .hasTemplateCard("qa-template")
        .templateCardHasSummary("qa-template", "Configure");
  }

  @Test
  @DisplayName("submits the add dialog when Enter is pressed in the name field")
  void addTemplateWithEnterKey(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .clickAddTemplate()
        .waitForDialog()
        .dialogSetName("enter-template")
        .dialogTogglePermission(ITEM_READ)
        .submitDialogWithEnter()
        .hasTemplateCard("enter-template");
  }

  @Test
  @DisplayName("edits an existing template to grant an additional permission")
  void editTemplateAddsPermission(JenkinsRule j, Page page) throws Exception {
    PermissionTemplatesPage templates = open(j, page);

    // Seed a fresh, unused template so it can be edited and deleted freely.
    templates.clickAddTemplate()
        .waitForDialog()
        .dialogSetName("edit-me")
        .dialogTogglePermission(ITEM_READ)
        .submitDialog()
        .templateCardHasSummary("edit-me", "Read");

    templates.clickEditTemplate("edit-me")
        .waitForDialog()
        .dialogTogglePermission(ITEM_BUILD)
        .submitDialog()
        .templateCardHasSummary("edit-me", "Build");
  }

  @Test
  @DisplayName("keeps the template when deletion is cancelled and removes it when confirmed")
  void deleteTemplateCancelKeepsItThenConfirmRemovesIt(JenkinsRule j, Page page) throws Exception {
    PermissionTemplatesPage templates = open(j, page);

    templates.clickAddTemplate()
        .waitForDialog()
        .dialogSetName("disposable")
        .dialogTogglePermission(ITEM_READ)
        .submitDialog()
        .hasTemplateCard("disposable");

    templates.clickDeleteTemplate("disposable")
        .confirmDialogContainsText("disposable")
        .dismissConfirmDialog()
        .hasTemplateCard("disposable");

    templates.clickDeleteTemplate("disposable")
        .acceptConfirmDialog()
        .hasNoTemplateCard("disposable");
  }

  @Test
  @DisplayName("filters the visible templates as the user types in the search box")
  void searchFiltersTemplates(JenkinsRule j, Page page) throws Exception {
    open(j, page)
        .search("dev")
        .hasTemplateCard("dev-template")
        .hasNoTemplateCard("empty-template");
  }
}
