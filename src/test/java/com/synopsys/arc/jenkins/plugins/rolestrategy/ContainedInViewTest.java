package com.synopsys.arc.jenkins.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.FreeStyleProject;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.DummySecurityRealm;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class ContainedInViewTest {

  private JenkinsRule jenkinsRule;

  @BeforeEach
  @LocalData
  void setup(JenkinsRule jenkinsRule) throws Exception {
    this.jenkinsRule = jenkinsRule;
    DummySecurityRealm sr = jenkinsRule.createDummySecurityRealm();
    jenkinsRule.jenkins.setSecurityRealm(sr);
  }

  @Test
  @LocalData
  void userCanAccessJobInRootView() throws Exception {
    FreeStyleProject project = jenkinsRule.jenkins.getItemByFullName("testJob", FreeStyleProject.class);
    WebClient wc = jenkinsRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("tester", "tester");
    HtmlPage managePage = wc.goTo(project.getUrl());
    assertThat(managePage.getWebResponse().getStatusCode(), is(200));
  }

  @Test
  @LocalData
  void userCantAccessJobNotInRootView() throws Exception {
    FreeStyleProject project = jenkinsRule.jenkins.getItemByFullName("hiddenJob", FreeStyleProject.class);
    WebClient wc = jenkinsRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("tester", "tester");
    HtmlPage managePage = wc.goTo(project.getUrl());
    assertThat(managePage.getWebResponse().getStatusCode(), is(404));
  }

  @Test
  @LocalData
  void userCanAccessJobInFolderView() throws Exception {
    FreeStyleProject project = jenkinsRule.jenkins.getItemByFullName("folder/testjob2", FreeStyleProject.class);
    WebClient wc = jenkinsRule.createWebClient();
    wc.withThrowExceptionOnFailingStatusCode(false);
    wc.login("tester", "tester");
    HtmlPage managePage = wc.goTo(project.getUrl());
    assertThat(managePage.getWebResponse().getStatusCode(), is(200));
  }
}
