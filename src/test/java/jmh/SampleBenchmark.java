package jmh;

import jenkins.model.Jenkins;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

public class SampleBenchmark {
    @State(Scope.Benchmark)
    public static class JenkinsState extends JmhBenchmarkState {
    }

    @Benchmark
    public void testMethod(JenkinsState state, Blackhole blackhole) throws Exception {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new Exception("Unable to start Jenkins");
        } else {
            blackhole.consume(jenkins);
        }
    }
}
