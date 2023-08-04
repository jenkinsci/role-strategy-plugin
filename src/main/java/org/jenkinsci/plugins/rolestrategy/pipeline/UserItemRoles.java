package org.jenkinsci.plugins.rolestrategy.pipeline;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.security.AuthorizationStrategy;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step that returns the users item roles.
 */
public class UserItemRoles extends AbstractUserRolesStep {

  private boolean showAllRoles;

  @DataBoundConstructor
  public UserItemRoles() {
  }

  public boolean isShowAllRoles() {
    return showAllRoles;
  }

  @DataBoundSetter
  public void setShowAllRoles(boolean showAllRoles) {
    this.showAllRoles = showAllRoles;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new ItemRolesExecution(context, RoleType.Project, showAllRoles);
  }

  /**
   * Step Execution.
   */
  public static class ItemRolesExecution extends Execution {

    private final boolean showAllRoles;

    public ItemRolesExecution(@NonNull StepContext context, RoleType roleType, boolean showAllRoles) {
      super(context, roleType);
      this.showAllRoles = showAllRoles;
    }

    @Override
    protected RoleMap getRoleMap() throws IOException, InterruptedException {
      AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
      if (strategy instanceof RoleBasedAuthorizationStrategy) {
        RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) strategy;
        RoleMap roleMap = rbas.getRoleMap(roleType);
        if (showAllRoles) {
          return roleMap;
        } else {
          final Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
          Job<?, ?> job = run.getParent();
          return roleMap.newMatchingRoleMap(job.getFullName());
        }
      }
      return null;
    }
  }

  /**
   * The descriptor.
   */
  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.singleton(Run.class);
    }

    @NonNull
    @Override public String getDisplayName() {
      return "Current Users Item Roles";
    }

    @Override
    public String getFunctionName() {
      return "currentUserItemRoles";
    }
  }
}
