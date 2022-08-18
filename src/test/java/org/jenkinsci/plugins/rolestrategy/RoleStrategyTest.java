package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.PluginManager;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class RoleStrategyTest {

  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();

  @Before
  public void initSecurityRealm() {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
  }

  @LocalData
  @Test
  public void testRoleAssignment() {
    try (ACLContext c = ACL.as(User.getById("alice", true))) {
      assertTrue(jenkinsRule.jenkins.hasPermission(Permission.READ));
    }
  }

  @LocalData
  @Test
  public void dangerousPermissionsAreIgnored() {
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) jenkinsRule.jenkins.getAuthorizationStrategy();
    assertThat(rbas.getRoleMap(RoleType.Global).getRole("POWERUSERS").hasPermission(PluginManager.CONFIGURE_UPDATECENTER), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole("POWERUSERS").hasPermission(PluginManager.UPLOAD_PLUGINS), is(false));
    assertThat(rbas.getRoleMap(RoleType.Global).getRole("POWERUSERS").hasPermission(Jenkins.RUN_SCRIPTS), is(false));
  }

}
