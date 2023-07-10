package org.jenkinsci.plugins.rolestrategy;

import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasNoPermission;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasPermission;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;


public class PermissionTemplatesTest {
  @Rule
  public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

  @Test
  @ConfiguredWithCode("PermissionTemplatesTest/casc.yaml")
  public void readFromCasc() throws Exception {
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();

    // So we can log in
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    User creator = User.getById("creator", true);
    User builder = User.getById("builder", true);

    Folder folderA = j.jenkins.createProject(Folder.class, "folder");
    FreeStyleProject jobA1 = folderA.createProject(FreeStyleProject.class, "project");

    assertHasPermission(creator, folderA, Item.READ);
    assertHasNoPermission(creator, folderA, Item.CONFIGURE);
    assertHasPermission(creator, jobA1, Item.READ);
    assertHasPermission(creator, jobA1, Item.CONFIGURE);
    assertHasPermission(builder, jobA1, Item.READ);
    assertHasNoPermission(builder, jobA1, Item.CONFIGURE);
  }
}
