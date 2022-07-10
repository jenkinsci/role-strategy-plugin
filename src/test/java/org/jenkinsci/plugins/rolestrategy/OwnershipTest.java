package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.synopsys.arc.jenkins.plugins.ownership.OwnershipDescription;
import com.synopsys.arc.jenkins.plugins.ownership.nodes.OwnerNodeProperty;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.net.URL;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

public class OwnershipTest {
  @Rule
  public JenkinsConfiguredWithCodeRule jcwcRule = new JenkinsConfiguredWithCodeRule();

  @Test
  @ConfiguredWithCode("OwnershipTest.yml")
  public void currentUserIsPrimaryOwnerGrantsPermissions() throws Exception {
    Node n = jcwcRule.createOnlineSlave();
    n.getNodeProperties().add(new OwnerNodeProperty(n, new OwnershipDescription(true, "nodePrimaryTester", null)));
    String nodeUrl = n.toComputer().getUrl();

    WebClient wc = jcwcRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("nodePrimaryTester", "nodePrimaryTester");
    HtmlPage managePage = wc.withThrowExceptionOnFailingStatusCode(false).goTo(String.format("%sconfigure", nodeUrl));
    assertThat(managePage.getWebResponse().getStatusCode(), is(200));
    URL testUrl = wc.createCrumbedUrl(String.format("%sdisconnect", nodeUrl));
    WebRequest request = new WebRequest(testUrl, HttpMethod.POST);
    NameValuePair param = new NameValuePair("offlineMessage", "Disconnect for Test");
    request.setRequestParameters(Collections.singletonList(param));
    WebResponse response = wc.loadWebResponse(request);
    assertThat(response.getStatusCode(), is(200));

    testUrl = wc.createCrumbedUrl(String.format("%slaunchSlaveAgent", nodeUrl));
    request = new WebRequest(testUrl, HttpMethod.POST);
    response = wc.loadWebResponse(request);
    assertThat(response.getStatusCode(), is(oneOf(200, 302)));

    testUrl = wc.createCrumbedUrl(String.format("%sdoDelete", nodeUrl));
    request = new WebRequest(testUrl, HttpMethod.POST);
    response = wc.loadWebResponse(request);
    assertThat(response.getStatusCode(), is(200));
  }

  @Test
  @ConfiguredWithCode("OwnershipTest.yml")
  public void currentUserIsSecondaryOwnerGrantsPermissions() throws Exception {
    Node n = jcwcRule.createOnlineSlave();
    n.getNodeProperties()
        .add(new OwnerNodeProperty(n, new OwnershipDescription(true, "nodePrimaryTester", Collections.singleton("nodeSecondaryTester"))));
    String nodeUrl = n.toComputer().getUrl();

    WebClient wc = jcwcRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("nodeSecondaryTester", "nodeSecondaryTester");
    HtmlPage managePage = wc.withThrowExceptionOnFailingStatusCode(false).goTo(String.format("%sconfigure", nodeUrl));
    assertThat(managePage.getWebResponse().getStatusCode(), is(200));
    URL testUrl = wc.createCrumbedUrl(String.format("%sdisconnect", nodeUrl));
    WebRequest request = new WebRequest(testUrl, HttpMethod.POST);
    NameValuePair param = new NameValuePair("offlineMessage", "Disconnect for Test");
    request.setRequestParameters(Collections.singletonList(param));
    WebResponse response = wc.loadWebResponse(request);
    assertThat(response.getStatusCode(), is(200));

    testUrl = wc.createCrumbedUrl(String.format("%slaunchSlaveAgent", nodeUrl));
    request = new WebRequest(testUrl, HttpMethod.POST);
    response = wc.loadWebResponse(request);
    assertThat(response.getStatusCode(), is(oneOf(200, 302)));

    testUrl = wc.createCrumbedUrl(String.format("%sdoDelete", nodeUrl));
    request = new WebRequest(testUrl, HttpMethod.POST);
    response = wc.loadWebResponse(request);
    assertThat(response.getStatusCode(), is(200));
  }

}
