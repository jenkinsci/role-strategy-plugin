package org.jenkinsci.plugins.rolestrategy;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

public class Security2182Test {
    private static final String BUILD_CONTENT = "Started by user";
    private static final String JOB_CONTENT = "Full project name: folder/job";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void testQueuePath() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        Folder folder = j.jenkins.createProject(Folder.class, "folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        job.save();

        job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
        Assert.assertEquals(1, Jenkins.get().getQueue().getItems().length);

        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = webClient.goTo("queue/items/0/task/");
        final String contentAsString = htmlPage.getWebResponse().getContentAsString();
        assertThat(contentAsString, not(containsString(JOB_CONTENT))); // Fails while unfixed
    }

    @Test
    @LocalData
    public void testQueueContent() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        Folder folder = j.jenkins.createProject(Folder.class, "folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        job.save();

        job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
        Assert.assertEquals(1, Jenkins.get().getQueue().getItems().length);

        final JenkinsRule.WebClient webClient = j.createWebClient();
        final Page page = webClient.goTo("queue/api/xml/", "application/xml");
        final String xml = page.getWebResponse().getContentAsString();
        assertThat(xml, not(containsString("job/folder/job/job"))); // Fails while unfixed
    }

    @Test
    @LocalData
    public void testExecutorsPath() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        Folder folder = j.jenkins.createProject(Folder.class, "folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        job.getBuildersList().add(new SleepBuilder(100000));
        job.save();

        final FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart();
        final int number = build.getExecutor().getNumber();

        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = webClient.goTo("computer/(master)/executors/" + number + "/currentExecutable/");
        final String contentAsString = htmlPage.getWebResponse().getContentAsString();
        assertThat(contentAsString, not(containsString(BUILD_CONTENT))); // Fails while unfixed
    }

    @Test
    @LocalData
    public void testExecutorsContent() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        Folder folder = j.jenkins.createProject(Folder.class, "folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        job.getBuildersList().add(new SleepBuilder(10000));
        job.save();

        final FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart();
        final int number = build.getExecutor().getNumber();

        final JenkinsRule.WebClient webClient = j.createWebClient();
        final Page page = webClient.goTo("computer/(master)/api/xml?depth=1", "application/xml");
        final String xml = page.getWebResponse().getContentAsString();
        assertThat(xml, not(containsString("job/folder/job/job"))); // Fails while unfixed
    }

    @Test
    @LocalData
    public void testWidgets() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        Folder folder = j.jenkins.createProject(Folder.class, "folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        job.getBuildersList().add(new SleepBuilder(100000));
        job.save();

        job.scheduleBuild2(0, new Cause.UserIdCause("admin")).waitForStart(); // schedule one build now
        job.scheduleBuild2(0, new Cause.UserIdCause("admin")); // schedule an additional queue item
        Assert.assertEquals(1, Jenkins.get().getQueue().getItems().length); // expect there to be one queue item

        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);

        final HtmlPage htmlPage = webClient.goTo("");
        final String contentAsString = htmlPage.getWebResponse().getContentAsString();
        assertThat(contentAsString, not(containsString("job/folder/job/job"))); // Fails while unfixed
    }

    @Test
    @LocalData
    public void testEscapeHatch() throws Exception {
        final String propertyName = RoleMap.class.getName() + ".checkParentPermissions";
        try {
            System.setProperty(propertyName, "false");


            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            User root = User.getOrCreateByIdOrFullName("admin");
            Folder folder = j.jenkins.createProject(Folder.class, "folder");
            FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
            job.getBuildersList().add(new SleepBuilder(100000));
            job.save();

            job.scheduleBuild2(1000, new Cause.UserIdCause("admin"));
            Assert.assertEquals(1, Jenkins.get().getQueue().getItems().length);

            final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);

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
            Assert.assertEquals(0, Jenkins.get().getQueue().getItems().length); // collapsed queue items

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

        } finally {
            System.clearProperty(propertyName);
        }
    }
}
