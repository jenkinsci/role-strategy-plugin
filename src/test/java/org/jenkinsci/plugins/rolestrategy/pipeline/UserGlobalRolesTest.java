package org.jenkinsci.plugins.rolestrategy.pipeline;

import hudson.model.Cause;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.TimerTrigger;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.io.IOException;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.authorizeproject.GlobalQueueItemAuthenticator;
import org.jenkinsci.plugins.authorizeproject.strategy.AnonymousAuthorizationStrategy;
import org.jenkinsci.plugins.authorizeproject.strategy.SpecificUsersAuthorizationStrategy;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;

@WithJenkinsConfiguredWithCode
class UserGlobalRolesTest {

  private JenkinsConfiguredWithCodeRule jenkinsRule;

  private WorkflowJob pipeline;

  @BeforeEach
  void setup(JenkinsConfiguredWithCodeRule jenkinsRule) throws IOException {
    this.jenkinsRule = jenkinsRule;
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
  void systemUserHasAllGlobalRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserGlobalRoles()\n"
            + "for (r in roles) {\n"
            + "    echo(\"Global Role: \" + r)\n"
            + "}", true));
    WorkflowRun run = jenkinsRule.buildAndAssertSuccess(pipeline);
    jenkinsRule.assertLogContains("adminRole", run);
    jenkinsRule.assertLogContains("readonlyRole", run);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  void builderUserHasGlobalRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserGlobalRoles()\n"
            + "for (r in roles) {\n"
            + "    echo(\"Global Role: \" + r)\n"
            + "}", true));
    try (ACLContext c = ACL.as(User.getById("builder1", true))) {
      pipeline.scheduleBuild(0, new Cause.UserIdCause());
    }
    jenkinsRule.waitUntilNoActivity();
    WorkflowRun run = pipeline.getLastBuild();
    jenkinsRule.assertLogContains("readonlyRole", run);
    jenkinsRule.assertLogNotContains("adminRole", run);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  void anonymousUserHasNoRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserGlobalRoles()\n"
            + "for (r in roles) {\n"
            + "    echo(\"Global Role: \" + r)\n"
            + "}\n"
            + "roles = currentUserItemRoles showAllRoles: true\n"
            + "for (r in roles) {\n"
            + "    echo(\"Item Role: \" + r)\n"
            + "}", true));
    QueueItemAuthenticatorConfiguration.get().getAuthenticators()
            .add(new GlobalQueueItemAuthenticator(new AnonymousAuthorizationStrategy()));
    pipeline.scheduleBuild(0, new Cause.UserIdCause());
    jenkinsRule.waitUntilNoActivity();
    WorkflowRun run = pipeline.getLastBuild();
    jenkinsRule.assertLogNotContains("readonlyRole", run);
    jenkinsRule.assertLogNotContains("adminRole", run);
    jenkinsRule.assertLogNotContains("builder1Role", run);
    jenkinsRule.assertLogNotContains("builder2Role", run);
    jenkinsRule.assertLogNotContains("reader1Role", run);
    jenkinsRule.assertLogNotContains("reader2Role", run);
  }

  @Test
  @ConfiguredWithCode("Configuration-as-Code-pipeline.yml")
  void builderUserHasRoles() throws Exception {
    pipeline.setDefinition(new CpsFlowDefinition("roles = currentUserGlobalRoles()\n"
            + "for (r in roles) {\n"
            + "    echo(\"Global Role: \" + r)\n"
            + "}\n"
            + "roles = currentUserItemRoles showAllRoles: true\n"
            + "for (r in roles) {\n"
            + "    echo(\"Item Role: \" + r)\n"
            + "}", true));
    QueueItemAuthenticatorConfiguration.get().getAuthenticators()
            .add(new GlobalQueueItemAuthenticator(new SpecificUsersAuthorizationStrategy("builder1")));
    pipeline.scheduleBuild(0, new TimerTrigger.TimerTriggerCause());
    jenkinsRule.waitUntilNoActivity();
    WorkflowRun run = pipeline.getLastBuild();
    jenkinsRule.assertLogContains("readonlyRole", run);
    jenkinsRule.assertLogNotContains("adminRole", run);
    jenkinsRule.assertLogContains("builder1Role", run);
    jenkinsRule.assertLogNotContains("builder2Role", run);
    jenkinsRule.assertLogContains("reader1Role", run);
    jenkinsRule.assertLogContains("reader2Role", run);
  }
}
