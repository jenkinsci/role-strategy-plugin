package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
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
    
    User user1 = User.getById("user1", false);
    User user2 = User.getById("user2", false);

    assertHasPermission(user1, j.jenkins, Item.CREATE);
    assertHasNoPermission(user2, j.jenkins, Item.CREATE);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-Naming.yml")
  public void checkName() {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    AuthorizationStrategy s = j.jenkins.getAuthorizationStrategy();
    assertThat("Authorization Strategy has been read incorrectly",
        s, instanceOf(RoleBasedAuthorizationStrategy.class));
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) s;
    
    User user1 = User.getById("user1", false);
    User user2 = User.getById("user2", false);
    User user3 = User.getById("user2", false);
    
    checkName(user1, "notAllowed");
    Failure f = Assert.assertThrows(Failure.class, () -> checkName(user1, "notAllowed"));
    
  }

  private void checkName(User user, final String jobName) {
    try (ACLContext c = ACL.as(user)) {
        ProjectNamingStrategy pns = j.jenkins.getProjectNamingStrategy();
        assertThat(pns, instanceOf(RoleBasedProjectNamingStrategy.class));
        RoleBasedProjectNamingStrategy rbpns = (RoleBasedProjectNamingStrategy) pns;
        rbpns.checkName(null, jobName);
    }
  }

}
