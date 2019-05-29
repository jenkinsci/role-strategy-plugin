package jmh;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate your benchmark classes with this annotation to allow them to be discovered by {@link BenchmarkFinder}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JmhBenchmark {
    /**
     * Methods which annotated by {@link org.openjdk.jmh.annotations.Benchmark}
     * in classes annotated by {@link JmhBenchmark} are to be run as benchmarks if they
     * match this regex pattern.
     * <p>
     * Matches all functions by default, i.e. default pattern is {@code .*}.
     *
     * @return the regular expression used to match function names.
     */
    String value() default ".*";
}
