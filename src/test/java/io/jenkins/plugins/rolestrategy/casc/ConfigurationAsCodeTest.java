package io.jenkins.plugins.rolestrategy.casc;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationAsCodeTest {
    private Folder folder;

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Before
    public void setUp() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        folder = j.jenkins.createProject(Folder.class, "root");
    }

    @Test
    @ConfiguredWithCode("config.yml")
    public void configurationImportTest() {
        try (ACLContext ignored = ACL.as(User.get("admin"))) {
            assertTrue(j.jenkins.hasPermission(Jenkins.ADMINISTER));
        }

        try (ACLContext ignored = ACL.as(User.get("user1"))) {
            assertTrue(folder.hasPermission(Item.READ));
            assertFalse(j.jenkins.hasPermission(Jenkins.ADMINISTER));
        }
    }
}
