package jmh.benchmarks;

import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import jmh.JmhJenkinsRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@JmhBenchmark
public class WebClientBenchmark {
    public static class JenkinsState extends JmhBenchmarkState {
    }

    // WebClient is not thread-safe, so use a different WebClient for each thread
    @State(Scope.Thread)
    public static class ThreadState {
        JenkinsRule.WebClient webClient = null;

        @Setup(Level.Iteration)
        public void setup() throws Exception {
            // JQuery UI is 404 from these tests so disable stopping benchmark when it is used.
            JmhJenkinsRule j = new JmhJenkinsRule();
            webClient = j.createWebClient();

            webClient.login("mockUser", "mockUser");
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            webClient.close();
        }
    }

    @Benchmark
    public void viewRenderBenchmark(JenkinsState state, ThreadState threadState, Blackhole blackhole)
            throws Exception {
        blackhole.consume(threadState.webClient.goTo(""));
    }
}
