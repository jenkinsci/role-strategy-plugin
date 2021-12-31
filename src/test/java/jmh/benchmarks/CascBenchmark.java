package jmh.benchmarks;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.AuthorizationStrategy;
import io.jenkins.plugins.casc.misc.jmh.CascJmhBenchmarkState;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasNoPermission;
import static org.jenkinsci.plugins.rolestrategy.PermissionAssert.assertHasPermission;

@JmhBenchmark
public class CascBenchmark {
    @State(Scope.Benchmark)
    public static class CascJenkinsState extends CascJmhBenchmarkState {
        RoleBasedAuthorizationStrategy rbas = null;
        Folder folder = null;

        @Override
        public void setup() throws Exception {
            super.setup();
            Jenkins jenkins = Objects.requireNonNull(Jenkins.getInstanceOrNull());
            jenkins.setSecurityRealm(new JenkinsRule().createDummySecurityRealm());
            User admin = User.getById("admin", true);
            User user1 = User.getById("user1", true);
            User user2 = User.getById("user2", true);
            Computer agent1 = jenkins.getComputer("agent1");
            Computer agent2 = jenkins.getComputer("agent2");
            Folder folderA = jenkins.createProject(Folder.class, "A");
            FreeStyleProject jobA1 = folderA.createProject(FreeStyleProject.class, "1");
            Folder folderB = jenkins.createProject(Folder.class, "B");
            folderB.createProject(FreeStyleProject.class, "2");

            AuthorizationStrategy s = jenkins.getAuthorizationStrategy();
            assertThat("Authorization Strategy has been read incorrectly",
                    s, instanceOf(RoleBasedAuthorizationStrategy.class));
            rbas = (RoleBasedAuthorizationStrategy) s;

            Map<Role, Set<String>> globalRoles = rbas.getGrantedRoles(RoleType.Global);
            assertThat(Objects.requireNonNull(globalRoles).size(), equalTo(2));

            // Admin has configuration access
            assertHasPermission(admin, jenkins, Jenkins.ADMINISTER, Jenkins.READ);
            assertHasPermission(user1, jenkins, Jenkins.READ);
            assertHasNoPermission(user1, jenkins, Jenkins.ADMINISTER, Jenkins.RUN_SCRIPTS);

            // Folder A is restricted to admin
            assertHasPermission(admin, folderA, Item.CONFIGURE);
            assertHasPermission(user1, folderA, Item.READ, Item.DISCOVER);
            assertHasNoPermission(user1, folderA, Item.CONFIGURE, Item.DELETE, Item.BUILD);

            // But they have access to jobs in Folder A
            assertHasPermission(admin, folderA, Item.CONFIGURE, Item.CANCEL);
            assertHasPermission(user1, jobA1, Item.READ, Item.DISCOVER, Item.CONFIGURE, Item.BUILD, Item.DELETE);
            assertHasPermission(user2, jobA1, Item.READ, Item.DISCOVER, Item.CONFIGURE, Item.BUILD, Item.DELETE);
            assertHasNoPermission(user1, folderA, Item.CANCEL);

            // FolderB is editable by user2, but he cannot delete it
            assertHasPermission(user2, folderB, Item.READ, Item.DISCOVER, Item.CONFIGURE, Item.BUILD);
            assertHasNoPermission(user2, folderB, Item.DELETE);
            assertHasNoPermission(user1, folderB, Item.CONFIGURE, Item.BUILD, Item.DELETE);

            // Only user1 can run on agent1, but he still cannot configure it
            assertHasPermission(admin, agent1, Computer.CONFIGURE, Computer.DELETE, Computer.BUILD);
            assertHasPermission(user1, agent1, Computer.BUILD);
            assertHasNoPermission(user1, agent1, Computer.CONFIGURE, Computer.DISCONNECT);

            // Same user still cannot build on agent2
            assertHasNoPermission(user1, agent2, Computer.BUILD);

            folder = folderB;
        }

        @NonNull
        @Override
        protected String getResourcePath() {
            return "sample-casc.yml";
        }

        @NonNull
        @Override
        protected Class<?> getEnclosingClass() {
            return CascBenchmark.class;
        }
    }

    @State(Scope.Thread)
    public static class AuthenticationThreadState {
        @Setup(Level.Iteration)
        public void setup() {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(User.getById("user2", true).impersonate());
        }
    }

    /**
     * Benchmark time to get the ACL object for a {@link Folder}
     */
    @Benchmark
    public void benchmark(CascJenkinsState state, Blackhole blackhole) {
        blackhole.consume(state.rbas.getACL(state.folder));
    }
}
