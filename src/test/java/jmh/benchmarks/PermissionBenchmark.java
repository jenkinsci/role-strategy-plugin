package jmh.benchmarks;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.model.User;
import hudson.security.Permission;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@JmhBenchmark
public class PermissionBenchmark {
  @State(Scope.Benchmark)
  public static class JenkinsState extends JmhBenchmarkState {
    @Override
    public void setup() {
      Jenkins jenkins = Objects.requireNonNull(Jenkins.getInstanceOrNull());
      Set<String> permissionSet = Collections.singleton("hudson.model.Hudson.Administer");
      Role role = new Role("USERS", ".*", permissionSet, "description");
      RoleMap roleMap = new RoleMap(new TreeMap<>(// expects a sorted map
          Collections.singletonMap(role, Collections.singleton(new PermissionEntry(AuthorizationType.USER,"alice")))));

      jenkins.setAuthorizationStrategy(
          new RoleBasedAuthorizationStrategy(Collections.singletonMap(RoleBasedAuthorizationStrategy.GLOBAL, roleMap)));

      jenkins.setSecurityRealm(new JenkinsRule().createDummySecurityRealm());
    }
  }

  @State(Scope.Thread)
  public static class AuthenticationState {
    private Authentication originalAuthentication = null;
    private SecurityContext securityContext = null;

    @Setup(Level.Iteration)
    public void setup() {
      securityContext = SecurityContextHolder.getContext();
      originalAuthentication = securityContext.getAuthentication();
      securityContext.setAuthentication(User.getById("alice", true).impersonate2());
    }

    @TearDown(Level.Iteration)
    public void tearDown() {
      securityContext.setAuthentication(originalAuthentication);
    }
  }

  @Benchmark
  public void roleAssignmentBenchmark(JenkinsState jenkinsState, AuthenticationState authState, Blackhole blackhole) {
    blackhole.consume(jenkinsState.getJenkins().hasPermission(Permission.READ));
  }
}
