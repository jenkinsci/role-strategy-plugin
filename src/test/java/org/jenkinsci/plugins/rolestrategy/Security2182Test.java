package org.jenkinsci.plugins.rolestrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class Security2182Test {
  private static final String BUILD_CONTENT = "Started by user";
  private static final String JOB_CONTENT = "Full project name: folder/job";

  @Test
  @LocalData
  void testQueuePath(JenkinsRule jenkinsRule) throws Exception {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder");
    FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
    job.save();

    job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
    assertEquals(1, Jenkins.get().getQueue().getItems().length);

    final JenkinsRule.WebClient webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    final HtmlPage htmlPage = webClient.goTo("queue/items/0/task/");
    final String contentAsString = htmlPage.getWebResponse().getContentAsString();
    assertThat(contentAsString, not(containsString(JOB_CONTENT))); // Fails while unfixed
  }

  @Test
  @LocalData
  void testQueueContent(JenkinsRule jenkinsRule) throws Exception {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder");
    FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
    job.save();

    job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
    assertEquals(1, Jenkins.get().getQueue().getItems().length);

    final JenkinsRule.WebClient webClient = jenkinsRule.createWebClient();
    final Page page = webClient.goTo("queue/api/xml/", "application/xml");
    final String xml = page.getWebResponse().getContentAsString();
    assertThat(xml, not(containsString("job/folder/job/job"))); // Fails while unfixed
  }

  @Test
  @LocalData
  void testExecutorsPath(JenkinsRule jenkinsRule) throws Exception {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder");
    FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
    job.getBuildersList().add(new SleepBuilder(100000));
    job.save();

    final FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart();
    final int number = build.getExecutor().getNumber();

    final JenkinsRule.WebClient webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
    final HtmlPage htmlPage = webClient.goTo("computer/(master)/executors/" + number + "/currentExecutable/");
    final String contentAsString = htmlPage.getWebResponse().getContentAsString();
    assertThat(contentAsString, not(containsString(BUILD_CONTENT))); // Fails while unfixed
    build.doStop();
  }

  @Test
  @LocalData
  void testExecutorsContent(JenkinsRule jenkinsRule) throws Exception {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder");
    FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
    job.getBuildersList().add(new SleepBuilder(10000));
    job.save();

    FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart();

    final JenkinsRule.WebClient webClient = jenkinsRule.createWebClient();
    final Page page = webClient.goTo("computer/(master)/api/xml?depth=1", "application/xml");
    final String xml = page.getWebResponse().getContentAsString();
    assertThat(xml, not(containsString("job/folder/job/job"))); // Fails while unfixed
    build.doStop();
  }

  @Test
  @LocalData
  void testWidgets(JenkinsRule jenkinsRule) throws Exception {
    jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder");
    FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
    job.getBuildersList().add(new SleepBuilder(100000));
    job.save();

    FreeStyleBuild b1 = job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart(); // schedule one build now
    QueueTaskFuture<?> f2 = job.scheduleBuild2(0, new Cause.UserIdCause("admin")); // schedule an additional queue item
    assertEquals(1, Jenkins.get().getQueue().getItems().length); // expect there to be one queue item

    final JenkinsRule.WebClient webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);

    final HtmlPage htmlPage = webClient.goTo("");
    final String contentAsString = htmlPage.getWebResponse().getContentAsString();
    assertThat(contentAsString, not(containsString("job/folder/job/job"))); // Fails while unfixed
    f2.cancel(true);
    b1.doStop();
  }

  @Test
  @LocalData
  void testEscapeHatch(JenkinsRule jenkinsRule) throws Exception {
    final String propertyName = RoleMap.class.getName() + ".checkParentPermissions";
    try {
      System.setProperty(propertyName, "false");

      jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
      User.getOrCreateByIdOrFullName("admin");
      Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder");
      FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
      job.getBuildersList().add(new SleepBuilder(100000));
      job.save();

      job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
      assertEquals(1, Jenkins.get().getQueue().getItems().length);

      final JenkinsRule.WebClient webClient = jenkinsRule.createWebClient().withThrowExceptionOnFailingStatusCode(false);

        { // queue related assertions
          final HtmlPage htmlPage = webClient.goTo("queue/items/0/task/");
          final String contentAsString = htmlPage.getWebResponse().getContentAsString();
          assertThat(contentAsString, containsString(JOB_CONTENT)); // Fails while unfixed

          final Page page = webClient.goTo("queue/api/xml/", "application/xml");
          final String xml = page.getWebResponse().getContentAsString();
          assertThat(xml, containsString("job/folder/job/job")); // Fails while unfixed
        }

        final FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart();
        final int number = build.getExecutor().getNumber();
        assertEquals(0, Jenkins.get().getQueue().getItems().length); // collapsed queue items

        { // executor related assertions
          final HtmlPage htmlPage = webClient.goTo("computer/(master)/executors/" + number + "/currentExecutable/");
          final String contentAsString = htmlPage.getWebResponse().getContentAsString();
          assertThat(contentAsString, containsString(BUILD_CONTENT)); // Fails while unfixed

          final Page page = webClient.goTo("computer/(master)/api/xml?depth=1", "application/xml");
          final String xml = page.getWebResponse().getContentAsString();
          assertThat(xml, containsString("job/folder/job/job")); // Fails while unfixed
        }

        { // widget related assertions
          final HtmlPage htmlPage = webClient.goTo("");
          final String contentAsString = htmlPage.getWebResponse().getContentAsString();
          assertThat(contentAsString, containsString("job/folder/job/job")); // Fails while unfixed
        }
        build.doStop();
    } finally {
      System.clearProperty(propertyName);
    }
  }
}
