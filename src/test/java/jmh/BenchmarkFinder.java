package jmh;

import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.reflections.Reflections;

import java.util.Objects;
import java.util.Set;

/**
 * Find classes annotated with {@link JmhBenchmark} to run their benchmark methods.
 */
@SuppressWarnings({"WeakerAccess"})
public final class BenchmarkFinder {
    final private String[] packageName;

    /**
     * Creates a {@link BenchmarkFinder}
     *
     * @param packageNames find benchmarks in these packages
     */
    public BenchmarkFinder(String... packageNames) {
        this.packageName = packageNames;
    }

    /**
     * Includes classes annotated with {@link JmhBenchmark} as candidates for JMH benchmarks.
     *
     * @param optionsBuilder the optionsBuilder used to build the benchmarks
     */
    public void findBenchmarks(ChainedOptionsBuilder optionsBuilder) {
        Reflections reflections = new Reflections((Object[]) packageName);
        Set<Class<?>> benchmarkClasses = reflections.getTypesAnnotatedWith(JmhBenchmark.class);
        benchmarkClasses.forEach(clazz -> {
            JmhBenchmark annotation = clazz.getAnnotation(JmhBenchmark.class);
            if (Objects.nonNull(annotation)) {
                optionsBuilder.include(clazz.getName() + annotation.value());
            }
        });
    }
}
