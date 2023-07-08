package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue.Item;
import hudson.model.Slave;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.authorizeproject.AuthorizeProjectProperty;
import org.jenkinsci.plugins.authorizeproject.ProjectQueueItemAuthenticator;
import org.jenkinsci.plugins.authorizeproject.strategy.TriggeringUsersAuthorizationStrategy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.recipes.LocalData;

public class AuthorizeProjectTest {

  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();

  private Slave node;
  private FreeStyleProject project;
  private AuthorizationCheckBuilder checker;

  @Before
  @LocalData
  public void setup() throws Exception {
    QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new ProjectQueueItemAuthenticator(Collections.emptyMap()));
    node = jenkinsRule.createSlave("TestAgent", null, null);
    jenkinsRule.waitOnline(node);
    project = jenkinsRule.createFreeStyleProject();
    project.setAssignedNode(node);
    project.addProperty(new AuthorizeProjectProperty(new TriggeringUsersAuthorizationStrategy()));
    checker = new AuthorizationCheckBuilder();
    project.getBuildersList().add(checker);
    DummySecurityRealm sr = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(sr);
  }

  @Test
  @LocalData
  public void agentBuildPermissionsAllowsToBuildOnAgent() throws Exception {
    try (ACLContext c = ACL.as(User.getById("tester", true))) {
      project.scheduleBuild2(0, new Cause.UserIdCause());
    }
    jenkinsRule.waitUntilNoActivity();
    FreeStyleBuild b = project.getLastBuild();
    assertThat(b, is(not(nullValue())));
    jenkinsRule.assertBuildStatusSuccess(b);
    assertThat(checker.userName, is("tester"));
  }

  @Test
  @LocalData
  public void missingAgentBuildPermissionsBlockBuild() throws Exception {
    try (ACLContext c = ACL.as(User.getById("reader", true))) {
      project.scheduleBuild2(0, new Cause.UserIdCause());
    }
    TimeUnit.SECONDS.sleep(15);
    Item qi = project.getQueueItem();
    assertThat(qi.getCauseOfBlockage().toString(), containsString("‘reader’ lacks permission to run on ‘TestAgent’"));
  }

  public static class AuthorizationCheckBuilder extends Builder {

    // "transient" is required for exclusion from serialization - see https://jenkins.io/redirect/class-filter/
    public transient String userName = null;

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
      userName = null;
      return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
      userName = Jenkins.getAuthentication2().getName();
      return true;
    }

    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
      @SuppressWarnings("rawtypes")
      @Override
      public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
      }

      @NonNull
      @Override
      public String getDisplayName() {
        return "AuthorizationCheckBuilder";
      }
    }
  }
}
