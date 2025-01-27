package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;

import com.synopsys.arc.jenkins.plugins.ownership.OwnershipDescription;
import com.synopsys.arc.jenkins.plugins.ownership.nodes.OwnerNodeProperty;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.net.URL;
import java.util.Collections;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

@WithJenkinsConfiguredWithCode
class OwnershipTest {

  @Test
  @ConfiguredWithCode("OwnershipTest.yml")
  void currentUserIsPrimaryOwnerGrantsPermissions(JenkinsConfiguredWithCodeRule jcwcRule) throws Exception {
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
  void currentUserIsSecondaryOwnerGrantsPermissions(JenkinsConfiguredWithCodeRule jcwcRule) throws Exception {
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
