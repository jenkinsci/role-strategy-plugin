package jmh;

import jmh.benchmarks.CascBenchmark;
import jmh.benchmarks.PermissionBenchmark;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public final class BenchmarkRunner {
    @Test
    public void runJmhBenchmarks() throws Exception {
        Options options = new OptionsBuilder()
                .include(PermissionBenchmark.class.getName() + ".*")
                .include(CascBenchmark.class.getName() + ".*")
                .mode(Mode.AverageTime)
                .warmupIterations(2)
                .timeUnit(TimeUnit.MICROSECONDS)
                .threads(2)
                .forks(2)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .resultFormat(ResultFormatType.JSON)
                .result("jmh-report.json")
                .build();

        new Runner(options).run();
    }
}
