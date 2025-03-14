package jmh;

import java.util.concurrent.TimeUnit;
import jenkins.benchmark.jmh.BenchmarkFinder;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

class BenchmarkRunner {
  @Test
  void runJmhBenchmarks() throws Exception {
    ChainedOptionsBuilder options = new OptionsBuilder()
        .mode(Mode.AverageTime)
        .warmupIterations(2)
        .timeUnit(TimeUnit.MICROSECONDS)
        .threads(2)
        .forks(2)
        .measurementIterations(15)
        .shouldFailOnError(true)
        .shouldDoGC(true).resultFormat(ResultFormatType.JSON)
        .result("jmh-report.json");

    BenchmarkFinder bf = new BenchmarkFinder(getClass());
    bf.findBenchmarks(options);
    new Runner(options.build()).run();
  }
}
