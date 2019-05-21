package jmh;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.model.User;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

public class PermissionBenchmark {
    @State(Scope.Benchmark)
    public static class JenkinsState extends JmhBenchmarkState {
        private Authentication originalAuthentication = null;
        private SecurityContext securityContext = null;

        @Override
        public void setup(Jenkins jenkins) {
            Set<String> permissionSet = Collections.singleton("hudson.model.Hudson.Administer");
            Role role = new Role("USERS", ".*", permissionSet, "description");
            RoleMap roleMap = new RoleMap(new TreeMap<>( // expects a sorted map
                    Collections.singletonMap(role, Collections.singleton("alice"))
            ));

            jenkins.setAuthorizationStrategy(new RoleBasedAuthorizationStrategy(
                    Collections.singletonMap(RoleBasedAuthorizationStrategy.GLOBAL, roleMap)));

            jenkins.setSecurityRealm(new JenkinsRule().createDummySecurityRealm());

            securityContext = SecurityContextHolder.getContext();
            originalAuthentication = securityContext.getAuthentication();
            securityContext.setAuthentication(User.get("alice").impersonate());
        }

        @Override
        public void tearDown(Jenkins jenkins) {
            securityContext.setAuthentication(originalAuthentication);
        }
    }

    @Benchmark
    public void roleAssignmentBenchmark(JenkinsState state, Blackhole blackhole) {
        blackhole.consume(state.getJenkins().hasPermission(Permission.READ));
    }
}
