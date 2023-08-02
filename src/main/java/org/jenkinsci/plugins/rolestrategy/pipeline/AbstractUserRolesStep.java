package org.jenkinsci.plugins.rolestrategy.pipeline;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.springframework.security.core.Authentication;

/**
 * Base class for the pipeline steps.
 */
public abstract class AbstractUserRolesStep extends Step {

  /**
   * Step Execution.
   */
  protected static class Execution extends SynchronousNonBlockingStepExecution<Set<String>> {
    protected final RoleType roleType;

    public Execution(@NonNull StepContext context, RoleType roleType) {
      super(context);
      this.roleType = roleType;
    }

    protected RoleMap getRoleMap() throws IOException, InterruptedException {
      AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
      if (strategy instanceof RoleBasedAuthorizationStrategy) {
        RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) strategy;
        return rbas.getRoleMap(roleType);
      }
      return null;
    }

    @Override
    protected Set<String> run() throws Exception {
      Set<String> roleSet = new HashSet<>();
      Authentication auth = getAuthentication();
      if (auth == null) {
        return roleSet;
      }
      RoleMap roleMap = getRoleMap();
      if (roleMap != null) {
        if (auth == ACL.SYSTEM2) {
          return roleMap.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        }
        return roleMap.getRolesForAuth(auth);
      }
      return roleSet;
    }


    private Authentication getAuthentication() throws IOException, InterruptedException {
      final Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class));
      Cause.UserIdCause cause = run.getCause(Cause.UserIdCause.class);
      if (cause != null) {
        User causeUser = User.getById(cause.getUserId(), false);
        if (causeUser != null) {
          return causeUser.impersonate2();
        }
      }
      Authentication auth = Jenkins.getAuthentication2();
      if (ACL.isAnonymous2(auth)) {
        return null;
      }
      return auth;
    }
  }
}
