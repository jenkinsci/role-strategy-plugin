package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasNoPermission;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasPermission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.Failure;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Item roles scoped to folders rather than a pattern: a role applies to the listed folders and everything inside them,
 * for setups that grant access per folder (the model of the Folder-based Authorization Strategy plugin).
 */
@WithJenkins
class FolderScopedRolesTest {

  private JenkinsRule j;
  private RoleBasedAuthorizationStrategy rbas;
  private FreeStyleProject special;
  private FreeStyleProject nested;
  private FreeStyleProject other;
  private FreeStyleProject lookalike;

  @BeforeEach
  void setUp(JenkinsRule j) throws Exception {
    this.j = j;
    JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
    realm.addGroups("alice", "special-team");
    j.jenkins.setSecurityRealm(realm);
    rbas = new RoleBasedAuthorizationStrategy();
    j.jenkins.setAuthorizationStrategy(rbas);
    j.jenkins.setCrumbIssuer(null);

    Folder team = j.jenkins.createProject(Folder.class, "Team");
    Folder specialFolder = team.createProject(Folder.class, "Special");
    special = specialFolder.createProject(FreeStyleProject.class, "build");
    nested = specialFolder.createProject(Folder.class, "Nested").createProject(FreeStyleProject.class, "build");
    other = team.createProject(Folder.class, "Other").createProject(FreeStyleProject.class, "build");
    // Shares the name prefix with Team/Special; a role on Team/Special must not reach it.
    lookalike = team.createProject(Folder.class, "Special-old").createProject(FreeStyleProject.class, "build");

    rbas.doAddRole("globalRoles", "admin", Jenkins.ADMINISTER.getId(), "false", "", "");
    rbas.doAssignUserRole("globalRoles", "admin", "admin");
    rbas.doAddRole("globalRoles", "read",
        String.join(",", Jenkins.READ.getId(), Item.READ.getId(), Item.DISCOVER.getId()), "false", "", "");
    rbas.doAssignGroupRole("globalRoles", "read", "authenticated");
  }

  private void addSpecialBuildersRole() throws Exception {
    rbas.doAddFolderRole("special-builders", Item.BUILD.getId(), "false", "Team/Special", "");
    rbas.doAssignGroupRole("projectRoles", "special-builders", "special-team");
  }

  @Test
  void grantsPermissionsOnTheFolderAndEverythingInsideIt() throws Exception {
    addSpecialBuildersRole();
    User alice = User.getById("alice", true);
    User bob = User.getById("bob", true);

    assertHasPermission(alice, Item.BUILD, special, nested, (Folder) special.getParent());
    assertHasNoPermission(alice, Item.BUILD, other, lookalike);
    assertHasNoPermission(bob, Item.BUILD, special, nested, other, lookalike);
  }

  @Test
  void survivesSaveAndReloadAndKeepsAnEquivalentPatternForOlderVersions() throws Exception {
    addSpecialBuildersRole();
    j.jenkins.save();

    String xml = Files.readString(j.jenkins.getRootDir().toPath().resolve("config.xml"), StandardCharsets.UTF_8);
    assertThat(xml, containsString("<folder>Team/Special</folder>"));
    // Versions that do not know about folders fall back to this equivalent pattern.
    String derived = Pattern.quote("Team/Special");
    assertThat(xml, containsString("pattern=\"^(?:" + derived + ")(?s:/.*)?$\""));
    // Older readers expect permissions then sids, and skip any child after them.
    int roleStart = xml.indexOf("<role name=\"special-builders\"");
    int sids = xml.indexOf("<assignedSIDs>", roleStart);
    int folders = xml.indexOf("<folders>", roleStart);
    int roleEnd = xml.indexOf("</role>", roleStart);
    assertTrue(roleStart >= 0 && sids > roleStart && folders > sids && folders < roleEnd,
        "folders must be the last child of the role element");

    j.jenkins.reload();
    RoleBasedAuthorizationStrategy reloaded = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
    Role role = reloaded.getRoleMap(RoleType.Project).getRole("special-builders");
    assertNotNull(role);
    assertThat(role.getFolders(), contains("Team/Special"));
    assertThat(reloaded.getRoleMap(RoleType.Project).getSidEntriesForRole("special-builders"),
        contains(new PermissionEntry(AuthorizationType.GROUP, "special-team")));

    FreeStyleProject reloadedLookalike = j.jenkins.getItemByFullName("Team/Special-old/build", FreeStyleProject.class);
    FreeStyleProject reloadedNested = j.jenkins.getItemByFullName("Team/Special/Nested/build", FreeStyleProject.class);
    assertHasPermission(User.getById("alice", true), reloadedNested, Item.BUILD);
    assertHasNoPermission(User.getById("alice", true), reloadedLookalike, Item.BUILD);
  }

  @Test
  void patternRolesLoadUnchanged() throws Exception {
    rbas.doAddRole("projectRoles", "by-pattern", Item.BUILD.getId(), "false", "Team/Other/.*", "");
    j.jenkins.save();
    String xml = Files.readString(j.jenkins.getRootDir().toPath().resolve("config.xml"), StandardCharsets.UTF_8);
    assertThat(xml, not(containsString("<folders>")));

    j.jenkins.reload();
    Role role = ((RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy()).getRoleMap(RoleType.Project)
        .getRole("by-pattern");
    assertNotNull(role);
    assertFalse(role.isFolderScoped());
    assertEquals("Team/Other/.*", role.getPattern().pattern());
  }

  private Page post(String user, String endpoint, NameValuePair... params) throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    wc.login(user, user);
    WebRequest request = new WebRequest(new URL(j.getURL(), "role-strategy/strategy/" + endpoint), HttpMethod.POST);
    request.setRequestParameters(List.of(params));
    return wc.getPage(request);
  }

  private Page get(String user, String endpointAndQuery) throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    wc.login(user, user);
    return wc.goTo("role-strategy/strategy/" + endpointAndQuery, "application/json");
  }

  @Test
  void addFolderRoleOverRest() throws Exception {
    Page page = post("admin", "addFolderRole",
        new NameValuePair("roleName", "special-builders"),
        new NameValuePair("permissionIds", Item.BUILD.getId()),
        new NameValuePair("overwrite", "false"),
        new NameValuePair("folders", "Team/Special\n/Team/Ops/\n"));
    assertEquals(200, page.getWebResponse().getStatusCode());

    Role role = rbas.getRoleMap(RoleType.Project).getRole("special-builders");
    assertNotNull(role);
    assertThat(role.getFolders(), contains("Team/Ops", "Team/Special"));
    assertThat(role.getPermissions(), contains(Item.BUILD));

    JSONObject json = JSONObject.fromObject(
        get("admin", "getRole?type=projectRoles&roleName=special-builders").getWebResponse().getContentAsString());
    assertThat(json.getJSONArray("folders"), contains("Team/Ops", "Team/Special"));
  }

  @Test
  void addFolderRoleSplitsFoldersOnlyOnLineBreaks() throws Exception {
    rbas.doAddFolderRole("r", Item.BUILD.getId(), "false", "Team/Special\u2028Archive\r\nTeam/Ops\rTeam/Other", "");
    assertThat(rbas.getRoleMap(RoleType.Project).getRole("r").getFolders(),
        contains("Team/Ops", "Team/Other", "Team/Special\u2028Archive"));
  }

  @Test
  void addFolderRoleKeepsAssignmentsWhenOverwriting() throws Exception {
    addSpecialBuildersRole();
    Page page = post("admin", "addFolderRole",
        new NameValuePair("roleName", "special-builders"),
        new NameValuePair("permissionIds", Item.BUILD.getId() + "," + Item.CANCEL.getId()),
        new NameValuePair("overwrite", "true"),
        new NameValuePair("folders", "Team/Other"));
    assertEquals(200, page.getWebResponse().getStatusCode());

    assertThat(rbas.getRoleMap(RoleType.Project).getRole("special-builders").getFolders(), contains("Team/Other"));
    assertHasPermission(User.getById("alice", true), other, Item.BUILD);
    assertHasNoPermission(User.getById("alice", true), special, Item.BUILD);
  }

  @Test
  void addFolderRoleRejectsMissingFoldersAndNonAdmins() throws Exception {
    Page blank = post("admin", "addFolderRole",
        new NameValuePair("roleName", "r"),
        new NameValuePair("permissionIds", Item.BUILD.getId()),
        new NameValuePair("overwrite", "false"),
        new NameValuePair("folders", " \n/\n"));
    assertEquals(400, blank.getWebResponse().getStatusCode());

    Page forbidden = post("alice", "addFolderRole",
        new NameValuePair("roleName", "r"),
        new NameValuePair("permissionIds", Item.BUILD.getId()),
        new NameValuePair("overwrite", "false"),
        new NameValuePair("folders", "Team"));
    assertEquals(403, forbidden.getWebResponse().getStatusCode());
    assertNull(rbas.getRoleMap(RoleType.Project).getRole("r"));
  }

  @Test
  void listsFoldersForThePicker() throws Exception {
    JSONObject all = JSONObject.fromObject(get("admin", "getFolders?query=&max=100").getWebResponse().getContentAsString());
    assertThat(all.getJSONArray("folders"),
        contains("Team", "Team/Other", "Team/Special", "Team/Special-old", "Team/Special/Nested"));
    assertEquals(5, all.getInt("count"));

    JSONObject filtered = JSONObject.fromObject(get("admin", "getFolders?query=SPECIAL&max=2").getWebResponse().getContentAsString());
    assertThat(filtered.getJSONArray("folders"), contains("Team/Special", "Team/Special-old"));
    assertEquals(3, filtered.getInt("count"));

    JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    wc.login("alice", "alice");
    assertEquals(403, wc.goTo("role-strategy/strategy/getFolders?query=&max=10", null).getWebResponse().getStatusCode());
  }

  @Test
  void namingStrategyAllowsCreatingInsideTheFolder() throws Exception {
    rbas.doAddFolderRole("special-creators", Item.CREATE.getId(), "false", "Team/Special", "");
    rbas.doAssignUserRole("projectRoles", "special-creators", "alice");
    RoleBasedProjectNamingStrategy naming = new RoleBasedProjectNamingStrategy(true);

    try (ACLContext ignored = ACL.as(User.getById("alice", true))) {
      naming.checkName("Team/Special", "new-job");
      naming.checkName("Team/Special/Nested", "new-job");
      Failure failure = assertThrows(Failure.class, () -> naming.checkName("Team/Special-old", "new-job"));
      // The message lists the role's folders rather than its derived pattern.
      assertThat(failure.getMessage(), containsString("[Team/Special]"));
      assertThat(failure.getMessage(), not(containsString("\\Q")));
    }
  }

  @Test
  void folderRoleCanTakeItsPermissionsFromATemplate() throws Exception {
    rbas.doAddTemplate("builder", Item.BUILD.getId() + "," + Item.CANCEL.getId(), false);
    Page page = post("admin", "addFolderRole",
        new NameValuePair("roleName", "special-builders"),
        new NameValuePair("permissionIds", Item.READ.getId()),
        new NameValuePair("overwrite", "false"),
        new NameValuePair("folders", "Team/Special"),
        new NameValuePair("template", "builder"));
    assertEquals(200, page.getWebResponse().getStatusCode());

    Role role = rbas.getRoleMap(RoleType.Project).getRole("special-builders");
    assertEquals("builder", role.getTemplateName());
    assertThat(role.getFolders(), contains("Team/Special"));
    assertThat(role.getPermissions(), containsInAnyOrder(Item.BUILD, Item.CANCEL));

    // Changing the template updates the role in place and keeps its folders.
    rbas.doAddTemplate("builder", Item.BUILD.getId(), true);
    role = rbas.getRoleMap(RoleType.Project).getRole("special-builders");
    assertThat(role.getFolders(), contains("Team/Special"));
    assertThat(role.getPermissions(), contains(Item.BUILD));
  }

  @Test
  void addFolderRoleRejectsAnUnknownTemplate() throws Exception {
    Page page = post("admin", "addFolderRole",
        new NameValuePair("roleName", "special-builders"),
        new NameValuePair("permissionIds", Item.READ.getId()),
        new NameValuePair("overwrite", "false"),
        new NameValuePair("folders", "Team/Special"),
        new NameValuePair("template", "missing"));
    assertEquals(400, page.getWebResponse().getStatusCode());
    assertNull(rbas.getRoleMap(RoleType.Project).getRole("special-builders"));
  }

  @Test
  void getFoldersUsesADefaultLimitWhenMaxIsMissing() throws Exception {
    JSONObject json = JSONObject.fromObject(get("admin", "getFolders?query=team").getWebResponse().getContentAsString());
    assertThat(json.getJSONArray("folders"),
        contains("Team", "Team/Other", "Team/Special", "Team/Special-old", "Team/Special/Nested"));
    assertEquals(5, json.getInt("count"));
  }

  /** Form data as the legacy role management form posts it; reading it consumes the {@code folders} key. */
  private static JSONObject legacyForm(String folders) {
    JSONObject role = new JSONObject();
    role.put(Item.BUILD.getId(), true);
    role.put("folders", folders);
    JSONObject data = new JSONObject();
    data.put("special-builders", role);
    JSONObject items = new JSONObject();
    items.put("data", data);
    JSONObject form = new JSONObject();
    form.put(RoleBasedAuthorizationStrategy.PROJECT, items);
    return form;
  }

  @Test
  void legacyFormSubmitKeepsFolderRolesAndRejectsAnEmptyFolderList() {
    RoleBasedAuthorizationStrategy submitted = (RoleBasedAuthorizationStrategy)
        RoleBasedAuthorizationStrategy.DESCRIPTOR.newInstance((StaplerRequest2) null, legacyForm("Team/Special\nTeam/Ops\u2029Archive"));
    assertThat(submitted.getRoleMap(RoleType.Project).getRole("special-builders").getFolders(),
        contains("Team/Ops\u2029Archive", "Team/Special"));

    Failure failure = assertThrows(Failure.class,
        () -> RoleBasedAuthorizationStrategy.DESCRIPTOR.newInstance((StaplerRequest2) null, legacyForm(" / ")));
    assertThat(failure.getMessage(), containsString("at least one folder"));
  }

}
