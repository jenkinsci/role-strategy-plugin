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
import org.jvnet.hudson.test.Issue;
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
  private User eitherGlobal;
  private User eitherJobCreate;
  private User eitherRead;
  private User userEitherGlobalGroup;
  private User userEitherJobCreateGroup;
  private User userEitherReadGroup;

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
    eitherGlobal = User.getById("eitherGlobal", true);
    eitherJobCreate = User.getById("eitherJobCreate", true);
    eitherRead = User.getById("eitherRead", true);
    userEitherGlobalGroup = User.getById("userEitherGlobalGroup", true);
    userEitherReadGroup = User.getById("userEitherReadGroup", true);
    userEitherJobCreateGroup = User.getById("userEitherJobCreateGroup", true);
    securityRealm.addGroups("userGlobalGroup", "groupGlobal");
    securityRealm.addGroups("userJobCreateGroup", "groupJobCreate");
    securityRealm.addGroups("userReadGroup", "groupRead");
    securityRealm.addGroups("userEitherGlobalGroup", "eitherGlobal");
    securityRealm.addGroups("userEitherJobCreateGroup", "eitherJobCreate");
    securityRealm.addGroups("userEitherReadGroup", "eitherRead");
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

    assertHasPermission(eitherGlobal, j.jenkins, Item.CREATE);
    assertHasPermission(eitherJobCreate, j.jenkins, Item.CREATE);
    assertHasPermission(userEitherGlobalGroup, j.jenkins, Item.CREATE);
    assertHasPermission(userEitherJobCreateGroup, j.jenkins, Item.CREATE);
    assertHasNoPermission(eitherRead, j.jenkins, Item.CREATE);
    assertHasNoPermission(userEitherReadGroup, j.jenkins, Item.CREATE);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void globalUserCanCreateAnyJob() {
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    checkName(userGlobal, "anyJobName", null);
    checkName(userGlobalGroup, "anyJobName", null);
    checkName(eitherGlobal, "anyJobName", null);
    checkName(userEitherGlobalGroup, "anyJobName", null);
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
    checkName(eitherJobCreate, "jobAllowed", null);
    checkName(userEitherJobCreateGroup, "jobAllowed", "folder");
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

    f = Assert.assertThrows(Failure.class, () -> checkName(eitherJobCreate, "notAllowed", null));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(eitherJobCreate, "notAllowed", "folder"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(eitherJobCreate, "jobAllowed", "folder2"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userEitherJobCreateGroup, "notAllowed", null));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userEitherJobCreateGroup, "notAllowed", "folder"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userEitherJobCreateGroup, "jobAllowed", "folder2"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Macro.yml")
  public void macroRolesDoNotGrantCreate() {
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

    f = Assert.assertThrows(Failure.class, () -> checkName(eitherRead, "jobAllowed", null));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(eitherRead, "jobAllowed", "folder"));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userEitherReadGroup, "jobAllowed", null));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userEitherReadGroup, "jobAllowed", "folder"));
    assertThat(f.getMessage(), is("No Create Permissions!"));
  }

  @Test
  @Issue("JENKINS-69625")
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void systemUserCanCreateAnyJob() {
    checkName("jobAllowed", null);
    checkName("jobAllowed", "folder");
    checkName("anyJob", null);
    checkName("anyJob", "folder");
    checkName("anyJob", "AnyFolder");
  }

  private void checkName(User user, final String jobName, final String parentName) {
    try (ACLContext c = ACL.as(user)) {
      checkName(jobName, parentName);
    }
  }

  private void checkName(final String jobName, final String parentName) {
    ProjectNamingStrategy pns = j.jenkins.getProjectNamingStrategy();
    assertThat(pns, instanceOf(RoleBasedProjectNamingStrategy.class));
    RoleBasedProjectNamingStrategy rbpns = (RoleBasedProjectNamingStrategy) pns;
    rbpns.checkName(parentName, jobName);
  }
}
