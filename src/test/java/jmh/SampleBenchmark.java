package jmh;

import jenkins.model.Jenkins;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class SampleBenchmark {
    @State(Scope.Benchmark)
    public static class JenkinsState extends JmhBenchmarkState {
    }

    @Benchmark
    public void testMethod(JenkinsState state) {
        System.out.print(Jenkins.getInstanceOrNull());
    }
}
