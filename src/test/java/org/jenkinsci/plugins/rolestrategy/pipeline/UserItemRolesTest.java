package org.jenkinsci.plugins.rolestrategy.pipeline;

import hudson.model.Cause;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;

@WithJenkinsConfiguredWithCode
class UserItemRolesTest {

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
  void systemUserHasAllItemRoles() throws Exception {
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
  void systemUserHasAllMatchingItemRoles() throws Exception {
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
  void builderUserHasItemRoles() throws Exception {
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
  void builderUserHasMatchingItemRoles() throws Exception {
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
