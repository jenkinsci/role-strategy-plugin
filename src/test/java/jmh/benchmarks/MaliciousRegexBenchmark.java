package jmh.benchmarks;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.model.User;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import jenkins.model.Jenkins;
import jmh.JmhJenkinsRule;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;

@JmhBenchmark
public class MaliciousRegexBenchmark {
    private static final String testUser = "user33";

    public static class JenkinsState extends JmhBenchmarkState {
        @Override
        public void setup() throws Exception {
            Jenkins jenkins = getJenkins();
            jenkins.setSecurityRealm(new JenkinsRule().createDummySecurityRealm());
            RoleBasedAuthorizationStrategy rbas = new RoleBasedAuthorizationStrategy();
            jenkins.setAuthorizationStrategy(rbas);
            for (int i = 0; i < 200; i++) {
                jenkins.createProject(Folder.class, "Foooooolder" + i);
            }

            Random rand = new Random(33);
            for (int i = 0; i < 300; i++) {
                if (rand.nextBoolean()) {
                    rbas.doAddRole(RoleBasedAuthorizationStrategy.PROJECT, "role" + i,
                            "hudson.model.Item.Discover,hudson.model.Item.Read,hudson.model.Item.Build",
                            "true", "F(o+)+lder[" + rand.nextInt(10) + rand.nextInt(10) + "]{1,2}");
                }
                rbas.doAssignRole(RoleBasedAuthorizationStrategy.PROJECT, "role" + i, "user" + i);
            }
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        @Setup(Level.Iteration)
        public void setup() {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(User.getById(testUser, true).impersonate());
        }
    }

    @State(Scope.Thread)
    public static class WebClientState {
        JenkinsRule.WebClient webClient = null;

        @Setup(Level.Iteration)
        public void setup() throws Exception {
            JmhJenkinsRule jenkinsRule = new JmhJenkinsRule();
            webClient = jenkinsRule.createWebClient();
            webClient.login(testUser);
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            webClient.close();
        }
    }

    @Benchmark
    public void benchmarkPermissionCheck(JenkinsState state, ThreadState threadState, Blackhole blackhole) {
        blackhole.consume(state.getJenkins().getAllItems()); // checks for READ permission
    }

    @Benchmark
    public void benchmarkPageLoad(JenkinsState state, WebClientState webClientState) throws Exception {
        webClientState.webClient.goTo("");
    }
}
