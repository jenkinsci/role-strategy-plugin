package jmh;

import com.gargoylesoftware.htmlunit.Cache;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public WebClient createWebClient() {
        WebClient webClient = super.createWebClient();
        Cache cache = new Cache();
        cache.setMaxSize(0);
        webClient.setCache(cache); // benchmarks should not rely on cached content

        webClient.setJavaScriptEnabled(false); // TODO enable JavaScript when we can find jQuery
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setPrintContentOnFailingStatusCode(false); // reduce 404 noise

        return webClient;
    }
}
