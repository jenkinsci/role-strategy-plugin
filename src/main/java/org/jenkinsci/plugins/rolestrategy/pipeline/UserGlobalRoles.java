package org.jenkinsci.plugins.rolestrategy.pipeline;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.util.Collections;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Pipeline step that returns the users global roles.
 */
public class UserGlobalRoles extends AbstractUserRolesStep {

  @DataBoundConstructor
  public UserGlobalRoles() {
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new Execution(context, RoleType.Global);
  }

  /**
   * The descriptor of the step.
   */
  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.singleton(Run.class);
    }

    @NonNull
    @Override public String getDisplayName() {
      return "Current Users Global Roles";
    }

    @Override
    public String getFunctionName() {
      return "currentUserGlobalRoles";
    }
  }
}
