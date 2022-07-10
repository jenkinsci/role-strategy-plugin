package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasNoPermission;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasPermission;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.model.ProjectNamingStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;

public class RoleBasedProjectNamingStrategyTest {

  @Rule
  public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

  private DummySecurityRealm securityRealm;
  private User userGlobal;
  private User userJobCreate;
  private User userRead;
  private User userGlobalGroup;
  private User userJobCreateGroup;
  private User userReadGroup;

  @Before
  public void setup() {
    securityRealm = j.createDummySecurityRealm();
    j.jenkins.setSecurityRealm(securityRealm);
    userGlobal = User.getById("userGlobal", true);
    userJobCreate = User.getById("userJobCreate", true);
    userRead = User.getById("userRead", true);
    userGlobalGroup = User.getById("userGlobalGroup", true);
    userJobCreateGroup = User.getById("userJobCreateGroup", true);
    userReadGroup = User.getById("userReadGroup", true);
    securityRealm.addGroups("userGlobalGroup", "groupGlobal");
    securityRealm.addGroups("userJobCreateGroup", "groupJobCreate");
    securityRealm.addGroups("userReadGroup", "groupRead");
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void createPermission() {
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    assertHasPermission(userGlobal, j.jenkins, Item.CREATE);
    assertHasPermission(userJobCreate, j.jenkins, Item.CREATE);
    assertHasNoPermission(userRead, j.jenkins, Item.CREATE);
    assertHasPermission(userGlobalGroup, j.jenkins, Item.CREATE);
    assertHasPermission(userJobCreateGroup, j.jenkins, Item.CREATE);
    assertHasNoPermission(userReadGroup, j.jenkins, Item.CREATE);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void globalUserCanCreateAnyJob() {
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    checkName(userGlobal, "anyJobName", null);
    checkName(userGlobalGroup, "anyJobName", null);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void itemUserCanCreateOnlyAllowedJobs() {
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    checkName(userJobCreate, "jobAllowed", null);
    checkName(userJobCreate, "jobAllowed", "folder");
    checkName(userJobCreateGroup, "jobAllowed", null);
    checkName(userJobCreateGroup, "jobAllowed", "folder");
    Failure f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreate, "notAllowed", null));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreate, "notAllowed", "folder"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreate, "jobAllowed", "folder2"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreateGroup, "notAllowed", null));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreateGroup, "notAllowed", "folder"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreateGroup, "jobAllowed", "folder2"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void readUserCantCreateAllowedJobs() {
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    Failure f = Assert.assertThrows(Failure.class, () -> checkName(userRead, "jobAllowed", null));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userRead, "jobAllowed", "folder"));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userReadGroup, "jobAllowed", null));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userReadGroup, "jobAllowed", "folder"));
    assertThat(f.getMessage(), is("No Create Permissions!"));
  }

  private void checkName(User user, final String jobName, final String parentName) {
    try (ACLContext c = ACL.as(user)) {
      ProjectNamingStrategy pns = j.jenkins.getProjectNamingStrategy();
      assertThat(pns, instanceOf(RoleBasedProjectNamingStrategy.class));
      RoleBasedProjectNamingStrategy rbpns = (RoleBasedProjectNamingStrategy) pns;
      rbpns.checkName(parentName, jobName);
    }
  }

}
