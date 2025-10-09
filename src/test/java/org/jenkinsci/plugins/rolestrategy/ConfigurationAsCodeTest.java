package org.jenkinsci.plugins.rolestrategy;

import static io.jenkins.plugins.casc.misc.Util.getJenkinsRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasNoPermission;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasPermission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.PluginManager;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.AuthorizationStrategy;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.rolestrategy.casc.RoleBasedAuthorizationStrategyConfigurator;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Configuration as code test.
 *
 * @author Oleg Nenashev
 * @since 2.11
 */
@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

  static {
    RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN.setEnabled(true);
    RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN.setEnabled(true);
  }

  @Test
  void shouldReturnCustomConfigurator(JenkinsConfiguredWithCodeRule jcwcRule) {
    ConfiguratorRegistry registry = ConfiguratorRegistry.get();
    Configurator<?> c = registry.lookup(RoleBasedAuthorizationStrategy.class);
    assertNotNull(c, "Failed to find configurator for RoleBasedAuthorizationStrategy");
    assertEquals(RoleBasedAuthorizationStrategyConfigurator.class, c.getClass(), "Retrieved wrong configurator");
  }

  @Test
  @Issue("Issue #48")
  @ConfiguredWithCode("Configuration-as-Code.yml")
  void shouldReadRolesCorrectly(JenkinsConfiguredWithCodeRule jcwcRule) throws Exception {
    jcwcRule.jenkins.setSecurityRealm(jcwcRule.createDummySecurityRealm());
    User admin = User.getById("admin", false);
    User user1 = User.getById("user1", false);
    User user2 = User.getById("user2", true);
    assertNotNull(admin);
    assertNotNull(user1);
    Computer agent1 = jcwcRule.jenkins.getComputer("agent1");
    Computer agent2 = jcwcRule.jenkins.getComputer("agent2");
    Folder folderA = jcwcRule.jenkins.createProject(Folder.class, "A");
    FreeStyleProject jobA1 = folderA.createProject(FreeStyleProject.class, "1");
    Folder folderB = jcwcRule.jenkins.createProject(Folder.class, "B");
    FreeStyleProject jobB2 = folderB.createProject(FreeStyleProject.class, "2");

    AuthorizationStrategy s = jcwcRule.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly", s, instanceOf(RoleBasedAuthorizationStrategy.class));
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) s;

    Map<Role, Set<PermissionEntry>> globalRoles = rbas.getGrantedRolesEntries(RoleType.Global);
    assertThat(globalRoles.size(), equalTo(2));

    // Admin has configuration access
    assertHasPermission(admin, jcwcRule.jenkins, Jenkins.ADMINISTER, Jenkins.READ);
    assertHasPermission(user1, jcwcRule.jenkins, Jenkins.READ);
    assertHasNoPermission(user1, jcwcRule.jenkins, Jenkins.ADMINISTER);
    assertHasNoPermission(user2, jcwcRule.jenkins, Jenkins.ADMINISTER);

    // Folder A is restricted to admin
    assertHasPermission(admin, folderA, Item.CONFIGURE);
    assertHasPermission(user1, folderA, Item.READ, Item.DISCOVER);
    assertHasNoPermission(user1, folderA, Item.CONFIGURE, Item.DELETE, Item.BUILD);

    // But they have access to jobs in Folder A
    assertHasPermission(admin, folderA, Item.CONFIGURE, Item.CANCEL);
    assertHasPermission(user1, jobA1, Item.READ, Item.DISCOVER, Item.CONFIGURE, Item.BUILD, Item.DELETE);
    assertHasPermission(user2, jobA1, Item.READ, Item.DISCOVER, Item.CONFIGURE, Item.BUILD, Item.DELETE);
    assertHasNoPermission(user1, folderA, Item.CANCEL);

    // FolderB is editable by user2, but he cannot delete it
    assertHasPermission(user2, folderB, Item.READ, Item.DISCOVER, Item.CONFIGURE, Item.BUILD);
    assertHasNoPermission(user2, folderB, Item.DELETE);
    assertHasNoPermission(user1, folderB, Item.CONFIGURE, Item.BUILD, Item.DELETE);

    // Only user1 can run on agent1, but he still cannot configure it
    assertHasPermission(admin, agent1, Computer.CONFIGURE, Computer.DELETE, Computer.BUILD);
    assertHasPermission(user1, agent1, Computer.BUILD);
    assertHasNoPermission(user1, agent1, Computer.CONFIGURE, Computer.DISCONNECT);

    // Same user still cannot build on agent2
    assertHasNoPermission(user1, agent2, Computer.BUILD);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code.yml")
  void shouldExportRolesCorrect(JenkinsConfiguredWithCodeRule jcwcRule) throws Exception {
    ConfiguratorRegistry registry = ConfiguratorRegistry.get();
    ConfigurationContext context = new ConfigurationContext(registry);
    CNode yourAttribute = getJenkinsRoot(context).get("authorizationStrategy");

    String exported = toYamlString(yourAttribute);
    String expected = toStringFromYamlFile(this, "Configuration-as-Code-Export.yml");

    assertThat(exported, is(expected));
  }

  @Test
  @Issue("Issue #214")
  @ConfiguredWithCode("Configuration-as-Code2.yml")
  void shouldHandleNullItemsAndAgentsCorrectly(JenkinsConfiguredWithCodeRule jcwcRule) {
    AuthorizationStrategy s = jcwcRule.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly", s, instanceOf(RoleBasedAuthorizationStrategy.class));
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) s;

    Map<Role, Set<PermissionEntry>> globalRoles = rbas.getGrantedRolesEntries(RoleType.Global);
    assertThat(globalRoles.size(), equalTo(2));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code3.yml")
  void dangerousPermissionsAreIgnored(JenkinsConfiguredWithCodeRule jcwcRule) {
    AuthorizationStrategy s = jcwcRule.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly", s, instanceOf(RoleBasedAuthorizationStrategy.class));
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) s;

    assertThat(rbas.getRoleMap(RoleType.Global).getRole("dangerous").hasPermission(PluginManager.CONFIGURE_UPDATECENTER), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole("dangerous").hasPermission(PluginManager.UPLOAD_PLUGINS), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole("dangerous").hasPermission(Jenkins.RUN_SCRIPTS), is(false));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-no-permissions.yml")
  void exportWithEmptyRole(JenkinsConfiguredWithCodeRule jcwcRule) throws Exception {
    ConfiguratorRegistry registry = ConfiguratorRegistry.get();
    ConfigurationContext context = new ConfigurationContext(registry);
    CNode yourAttribute = getJenkinsRoot(context).get("authorizationStrategy");

    String exported = toYamlString(yourAttribute);
    String expected = toStringFromYamlFile(this, "Configuration-as-Code-no-permissions-export.yml");

    assertThat(exported, is(expected));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-granular-permissions.yml")
  void shouldLoadGranularPermissions(JenkinsConfiguredWithCodeRule jcwcRule) {
    AuthorizationStrategy s = jcwcRule.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly", s, instanceOf(RoleBasedAuthorizationStrategy.class));
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) s;

    // Verify itemAdmin role has ITEM_ROLES_ADMIN permission
    Role itemAdminRole = rbas.getRoleMap(RoleType.Global).getRole("itemAdmin");
    assertNotNull(itemAdminRole, "itemAdmin role should exist");
    assertThat("itemAdmin role should have ITEM_ROLES_ADMIN permission",
        itemAdminRole.hasPermission(RoleBasedAuthorizationStrategy.ITEM_ROLES_ADMIN), is(true));
    assertThat("itemAdmin role should NOT have ADMINISTER permission",
        itemAdminRole.hasPermission(Jenkins.ADMINISTER), is(false));

    // Verify agentAdmin role has AGENT_ROLES_ADMIN permission
    Role agentAdminRole = rbas.getRoleMap(RoleType.Global).getRole("agentAdmin");
    assertNotNull(agentAdminRole, "agentAdmin role should exist");
    assertThat("agentAdmin role should have AGENT_ROLES_ADMIN permission",
        agentAdminRole.hasPermission(RoleBasedAuthorizationStrategy.AGENT_ROLES_ADMIN), is(true));
    assertThat("agentAdmin role should NOT have ADMINISTER permission",
        agentAdminRole.hasPermission(Jenkins.ADMINISTER), is(false));
  }
}
