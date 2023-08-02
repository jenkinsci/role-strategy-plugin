package org.jenkinsci.plugins.rolestrategy.pipeline;

import hudson.model.Cause;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.TimerTrigger;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.IOException;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.authorizeproject.GlobalQueueItemAuthenticator;
import org.jenkinsci.plugins.authorizeproject.strategy.AnonymousAuthorizationStrategy;
import org.jenkinsci.plugins.authorizeproject.strategy.SpecificUsersAuthorizationStrategy;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;

public class UserItemRolesTest {
  @Rule
  public JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

  private WorkflowJob pipeline;

  @Before
  public void setup() throws IOException {
    DummySecurityRealm securityRealm = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(securityRealm);
    securityRealm.addGroups("builder1", "readers");
    securityRealm.addGroups("builder2", "readers");
    User.getById("builder1", true);
    User.getById("builder2", true);
    pipeline = jenkinsRule.createProject(WorkflowJob.class, "pipeline");
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  public void systemUserHasAllItemRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserItemRoles showAllRoles: true\n"
            + "for (r in roles) {\n"
            + "    echo(\"Item Role: \" + r)\n"
            + "}", true));
    WorkflowRun run = jenkinsRule.buildAndAssertSuccess(pipeline);
    jenkinsRule.assertLogContains("builder1Role", run);
    jenkinsRule.assertLogContains("builder2Role", run);
    jenkinsRule.assertLogContains("reader1Role", run);
    jenkinsRule.assertLogContains("reader2Role", run);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  public void systemUserHasAllMatchingItemRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserItemRoles()\n"
            + "for (r in roles) {\n"
            + "    echo(\"Item Role: \" + r)\n"
            + "}", true));
    WorkflowRun run = jenkinsRule.buildAndAssertSuccess(pipeline);
    jenkinsRule.assertLogContains("builder1Role", run);
    jenkinsRule.assertLogNotContains("builder2Role", run);
    jenkinsRule.assertLogContains("reader1Role", run);
    jenkinsRule.assertLogNotContains("reader2Role", run);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  public void builderUserHasItemRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserItemRoles showAllRoles: true\n"
            + "for (r in roles) {\n"
            + "    echo(\"Item Role: \" + r)\n"
            + "}", true));
    try (ACLContext c = ACL.as(User.getById("builder1", true))) {
      pipeline.scheduleBuild(0, new Cause.UserIdCause());
    }
    jenkinsRule.waitUntilNoActivity();
    WorkflowRun run = pipeline.getLastBuild();
    jenkinsRule.assertLogContains("builder1Role", run);
    jenkinsRule.assertLogNotContains("builder2Role", run);
    jenkinsRule.assertLogContains("reader1Role", run);
    jenkinsRule.assertLogContains("reader2Role", run);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  public void builderUserHasMatchingItemRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserItemRoles()\n"
            + "for (r in roles) {\n"
            + "    echo(\"Item Role: \" + r)\n"
            + "}", true));
    try (ACLContext c = ACL.as(User.getById("builder1", true))) {
      pipeline.scheduleBuild(0, new Cause.UserIdCause());
    }
    jenkinsRule.waitUntilNoActivity();
    WorkflowRun run = pipeline.getLastBuild();
    jenkinsRule.assertLogContains("builder1Role", run);
    jenkinsRule.assertLogNotContains("builder2Role", run);
    jenkinsRule.assertLogContains("reader1Role", run);
    jenkinsRule.assertLogNotContains("reader2Role", run);
  }
}
