package org.jenkinsci.plugins.rolestrategy;

import static io.jenkins.plugins.casc.misc.Util.getJenkinsRoot;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.model.Item;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class FolderScopedRolesCascTest {

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Folders.yml")
  void importsFolderScopedItemRoles(JenkinsConfiguredWithCodeRule j) {
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
    Role folders = rbas.getRoleMap(RoleType.Project).getRole("special-builders");
    assertThat(folders.getFolders(), contains("Team/Ops", "Team/Special"));
    assertFalse(rbas.getRoleMap(RoleType.Project).getRole("by-pattern").isFolderScoped());
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Folders.yml")
  void exportsFoldersInsteadOfTheDerivedPattern(JenkinsConfiguredWithCodeRule j) throws Exception {
    ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
    CNode strategy = getJenkinsRoot(context).get("authorizationStrategy");
    String exported = toYamlString(strategy);

    assertThat(exported, containsString("folders:\n      - \"Team/Ops\"\n      - \"Team/Special\"\n      name: \"special-builders\""));
    assertThat(exported, containsString("pattern: \"Team/Other/.*\""));
    assertThat(exported, not(containsString("\\\\Q")));
  }

  private ConfiguratorException configureFails(String resource) {
    String yaml = getClass().getResource(resource).toExternalForm();
    return assertThrows(ConfiguratorException.class, () -> ConfigurationAsCode.get().configure(yaml));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Folders.yml")
  void rejectsFoldersOnAgentAndGlobalRoles(JenkinsConfiguredWithCodeRule j) {
    assertThat(configureFails("Configuration-as-Code-Folders-agent.yml").getMessage(),
        containsString("folders are only supported for item roles, not agent roles"));
    assertThat(configureFails("Configuration-as-Code-Folders-global.yml").getMessage(),
        containsString("folders are only supported for item roles, not global roles"));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Folders.yml")
  void rejectsAnEmptyFolderListInsteadOfMatchingEverything(JenkinsConfiguredWithCodeRule j) {
    assertThat(configureFails("Configuration-as-Code-Folders-empty.yml").getMessage(),
        containsString("folders must contain at least one folder"));
    // The earlier configuration stays in place.
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
    assertThat(rbas.getRoleMap(RoleType.Project).getRole("special-builders").getFolders(),
        contains("Team/Ops", "Team/Special"));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Folders.yml")
  void rejectsPatternAndFoldersTogether(JenkinsConfiguredWithCodeRule j) {
    assertThat(configureFails("Configuration-as-Code-Folders-and-pattern.yml").getMessage(),
        containsString("set either pattern or folders, not both"));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Folders-template.yml")
  void folderRoleCanUseAPermissionTemplate(JenkinsConfiguredWithCodeRule j) {
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
    Role role = rbas.getRoleMap(RoleType.Project).getRole("special-builders");
    assertThat(role.getFolders(), contains("Team/Special"));
    assertThat(role.getTemplateName(), is("builder"));
    assertThat(role.getPermissions(), containsInAnyOrder(Item.BUILD, Item.CANCEL));
  }
}
