package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasPermission;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasNoPermission;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

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

public class RoleBasedProjectNamingStrategyTest
{
  
  @Rule
  public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();
  
  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void createPermission() {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));
    
    User userGlobal = User.getById("userGlobal", true);
    User user1 = User.getById("userJobCreate", true);
    User user2 = User.getById("userRead", true);

    assertHasPermission(userGlobal, j.jenkins, Item.CREATE);
    assertHasPermission(user1, j.jenkins, Item.CREATE);
    assertHasNoPermission(user2, j.jenkins, Item.CREATE);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void globalUserCanCreateAnyJob() {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    User userGlobal = User.getById("userGlobal", true);

    checkName(userGlobal, "anyJobName", null);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void itemUserCanCreateOnlyAllowedJobs() {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    User userJobCreate = User.getById("userJobCreate", true);

    checkName(userJobCreate, "jobAllowed", null);
    checkName(userJobCreate, "jobAllowed", "folder");
    Failure f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreate, "notAllowed", null));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreate, "notAllowed", "folder"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userJobCreate, "jobAllowed", "folder2"));
    assertThat(f.getMessage(), containsString("does not match the job name convention"));
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void readUserCantCreateAllowedJobs() {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));

    User userRead = User.getById("userRead", true);

    Failure f = Assert.assertThrows(Failure.class, () -> checkName(userRead, "jobAllowed", null));
    assertThat(f.getMessage(), is("No Create Permissions!"));
    f = Assert.assertThrows(Failure.class, () -> checkName(userRead, "jobAllowed", "folder"));
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
