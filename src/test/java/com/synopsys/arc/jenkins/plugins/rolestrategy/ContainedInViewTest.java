package com.synopsys.arc.jenkins.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.recipes.LocalData;

public class ContainedInViewTest {
  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();

  @Before
  @LocalData
  public void setup() throws Exception {
    DummySecurityRealm sr = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(sr);
  }

  @Test
  @LocalData
  public void userCanAccessJobInRootView() throws Exception {
    FreeStyleProject project = jenkinsRule.jenkins.getItemByFullName("testJob", FreeStyleProject.class);
    WebClient wc = jenkinsRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("tester", "tester");
    HtmlPage managePage = wc.goTo(project.getUrl());
    assertThat(managePage.getWebResponse().getStatusCode(), is(200));
  }

  @Test
  @LocalData
  public void userCantAccessJobNotInRootView() throws Exception {
    FreeStyleProject project = jenkinsRule.jenkins.getItemByFullName("hiddenJob", FreeStyleProject.class);
    WebClient wc = jenkinsRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("tester", "tester");
    HtmlPage managePage = wc.goTo(project.getUrl());
    assertThat(managePage.getWebResponse().getStatusCode(), is(404));
  }

  @Test
  @LocalData
  public void userCanAccessJobInFolderView() throws Exception {
    FreeStyleProject project = jenkinsRule.jenkins.getItemByFullName("folder/testjob2", FreeStyleProject.class);
    WebClient wc = jenkinsRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("tester", "tester");
    HtmlPage managePage = wc.goTo(project.getUrl());
    assertThat(managePage.getWebResponse().getStatusCode(), is(200));
  }
}
