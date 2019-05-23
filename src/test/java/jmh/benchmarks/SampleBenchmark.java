package jmh.benchmarks;

import jenkins.model.Jenkins;
import jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import static org.junit.Assert.assertNotNull;

public class SampleBenchmark {
    @State(Scope.Benchmark)
    public static class JenkinsState extends JmhBenchmarkState {
    }

    @Benchmark
    @SuppressWarnings("unused")
    public void testMethod(JenkinsState state, Blackhole blackhole) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        assertNotNull("Unable to find Jenkins Instance", jenkins);
        blackhole.consume(jenkins);
    }
}
