package jmh;

import jenkins.model.Jenkins;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * Extension of {@link JenkinsRule} to allow it to be used from JMH benchmarks.
 * <p>
 * This class should be instantiated only when the Jenkins instance is confirmed to exist.
 */
public class JmhJenkinsRule extends JenkinsRule {
    private final Jenkins jenkins;

    public JmhJenkinsRule() {
        super();
        jenkins = Objects.requireNonNull(Jenkins.getInstanceOrNull());
        super.jenkins = null; // the jenkins is not started from JenkinsRule
    }

    @Override
    public URL getURL() throws MalformedURLException {
        // the rootURL should not be null as it should've been set by JmhBenchmarkState
        return new URL(Objects.requireNonNull(jenkins.getRootUrl()));
    }
}
