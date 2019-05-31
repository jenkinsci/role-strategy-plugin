package jmh.benchmarks;

import jmh.JmhBenchmarkState;
import jmh.JmhJenkinsRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

public class WebClientBenchmark {
    public static class JenkinsState extends JmhBenchmarkState {
        JenkinsRule.WebClient webClient = null;

        @Override
        public void tearDown() {
            webClient.close();
        }

        @Override
        public void setup() {
            JmhJenkinsRule jenkinsRule = new JmhJenkinsRule();
            webClient = jenkinsRule.createWebClient();
        }

        @Setup(Level.Iteration)
        public void login() throws Exception {
            webClient.login("user33", "user33");
        }
    }

    @Benchmark
    public void viewRenderBenchmark(JenkinsState state, Blackhole blackhole)
            throws Exception {
        blackhole.consume(state.webClient.goTo("jenkins"));
    }
}
