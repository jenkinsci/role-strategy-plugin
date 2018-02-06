/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.PluginManager;
import hudson.model.User;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.rolestrategy.permissions.DangerousPermissionAdministrativeMonitor;
import org.jenkinsci.plugins.rolestrategy.permissions.DangerousPermissionHandlingMode;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests dangerous permissions in {@link RoleBasedAuthorizationStrategy}.
 * @author Oleg Nenashev
 */
public class DangerousPermissionsTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
      
    @Before
    public void initUserDB() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }
    
    @Test
    @Issue("SECURITY-410")
    @LocalData
    public void shouldNotShowDangerousWhenNotAssigned() throws Exception {
        assertThat(j.jenkins.getAuthorizationStrategy(), instanceOf(RoleBasedAuthorizationStrategy.class));
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)j.jenkins.getAuthorizationStrategy();
        
        Assert.assertFalse("There should be no dangerous permissions detected", PermissionHelper.hasDangerousPermissions(strategy));
        Assert.assertNull("Dangerous permission report should be empty", PermissionHelper.reportDangerousPermissions(strategy));
        
        assertHasPermission("admin", PluginManager.CONFIGURE_UPDATECENTER);
        assertHasPermission("admin", PluginManager.UPLOAD_PLUGINS);
        assertHasPermission("admin", Jenkins.RUN_SCRIPTS);
        assertHasPermission("admin", Jenkins.ADMINISTER);
        
        // Monitor is disabled
        DangerousPermissionAdministrativeMonitor m = DangerousPermissionAdministrativeMonitor.getInstance();
        Assert.assertTrue("DangerousPermissionAdministrativeMonitor should be enabled", m.isEnabled());
        Assert.assertFalse("DangerousPermissionAdministrativeMonitor should be deactivated", m.isActivated());
        
        /* TODO: Add UI tests, they are failing now (JavaScript error)
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(admin.getId());
        
        HtmlPage page = wc.goTo("role-strategy/manage-roles");
        assertThat(page.asText(), not(containsString(Jenkins.RUN_SCRIPTS.name)));
        */
    }
    
    @Test
    @Issue("SECURITY-410")
    @LocalData
    public void shouldShowDangerousWhenAssigned() throws Exception {
        assertThat(j.jenkins.getAuthorizationStrategy(), instanceOf(RoleBasedAuthorizationStrategy.class));
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)j.jenkins.getAuthorizationStrategy();
        
        String report = PermissionHelper.reportDangerousPermissions(strategy);
        Assert.assertTrue("There should be dangerous permissions detected", PermissionHelper.hasDangerousPermissions(strategy));
        Assert.assertNotNull("Dangerous permission report should be not empty", report);
        assertThat(report, containsString(hudson.model.Hudson.RUN_SCRIPTS.toString()));
        assertThat(report, containsString(PluginManager.CONFIGURE_UPDATECENTER.toString()));
        assertThat(report, containsString(PluginManager.UPLOAD_PLUGINS.toString()));
        assertThat(report, containsString("FakeAdmins"));
        
        // Ensure that fakeAdmin does not get the dangerous permission by default
        assertHasNoPermission("fakeAdmin", PluginManager.CONFIGURE_UPDATECENTER);
        assertHasNoPermission("fakeAdmin", PluginManager.UPLOAD_PLUGINS);
        assertHasNoPermission("fakeAdmin", Jenkins.RUN_SCRIPTS);
        assertHasNoPermission("fakeAdmin", Jenkins.ADMINISTER);
        
        // Monitor is enabled
        DangerousPermissionAdministrativeMonitor m = DangerousPermissionAdministrativeMonitor.getInstance();
        Assert.assertTrue("DangerousPermissionAdministrativeMonitor should be enabled", m.isEnabled());
        Assert.assertTrue("DangerousPermissionAdministrativeMonitor should be activated", m.isActivated());
        
    }
    
    @Test
    @Issue("SECURITY-410")
    @LocalData
    @SuppressWarnings("deprecation")
    public void shouldGrantDangerousPermissionsWhenEnabled() throws Exception {
        try {
            DangerousPermissionHandlingMode.CURRENT = DangerousPermissionHandlingMode.ENABLED;
            assertThat(j.jenkins.getAuthorizationStrategy(), instanceOf(RoleBasedAuthorizationStrategy.class));
            RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)j.jenkins.getAuthorizationStrategy();

            String report = PermissionHelper.reportDangerousPermissions(strategy);
            Assert.assertTrue("There should be dangerous permissions detected", PermissionHelper.hasDangerousPermissions(strategy));
            Assert.assertNotNull("Dangerous permission report should be not empty", report);
            assertThat(report, containsString(hudson.model.Hudson.RUN_SCRIPTS.toString()));
            assertThat(report, containsString(PluginManager.CONFIGURE_UPDATECENTER.toString()));
            assertThat(report, containsString(PluginManager.UPLOAD_PLUGINS.toString()));
            assertThat(report, containsString("FakeAdmins"));

            // Ensure that fakeAdmin has DangerousPermissions
            assertHasPermission("fakeAdmin", PluginManager.CONFIGURE_UPDATECENTER);
            assertHasNoPermission("fakeAdmin", PluginManager.UPLOAD_PLUGINS);
            assertHasPermission("fakeAdmin", Jenkins.RUN_SCRIPTS);
            assertHasNoPermission("fakeAdmin", Jenkins.ADMINISTER);
            
            // Ensure that the permissions will be still shown
            RoleBasedAuthorizationStrategy.DescriptorImpl d = (RoleBasedAuthorizationStrategy.DescriptorImpl) strategy.getDescriptor();
            Assert.assertTrue(d.showPermission(RoleBasedAuthorizationStrategy.GLOBAL, PluginManager.CONFIGURE_UPDATECENTER, true));
            Assert.assertTrue(d.showPermission(RoleBasedAuthorizationStrategy.GLOBAL, PluginManager.UPLOAD_PLUGINS, true));
            Assert.assertTrue(d.showPermission(RoleBasedAuthorizationStrategy.GLOBAL, Jenkins.RUN_SCRIPTS, true)); 
            
            // Monitor is disabled
            DangerousPermissionAdministrativeMonitor m = DangerousPermissionAdministrativeMonitor.getInstance();
            Assert.assertFalse("DangerousPermissionAdministrativeMonitor should be disabled", m.isEnabled());
            Assert.assertFalse("DangerousPermissionAdministrativeMonitor should be deactivated", m.isActivated());
        } finally {
            DangerousPermissionHandlingMode.CURRENT = DangerousPermissionHandlingMode.UNDEFINED;
        }
    }
    
    
    @Test
    @Issue("SECURITY-410")
    @LocalData
    public void shouldNotShowDangerousPermissionsWhenDisabled() throws Exception {
        try {
            DangerousPermissionHandlingMode.CURRENT = DangerousPermissionHandlingMode.DISABLED;
            assertThat(j.jenkins.getAuthorizationStrategy(), instanceOf(RoleBasedAuthorizationStrategy.class));
            RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)j.jenkins.getAuthorizationStrategy();

            String report = PermissionHelper.reportDangerousPermissions(strategy);
            Assert.assertTrue("There should be dangerous permissions detected", PermissionHelper.hasDangerousPermissions(strategy));
            Assert.assertNotNull("Dangerous permission report should be not empty", report);
            assertThat(report, containsString(hudson.model.Hudson.RUN_SCRIPTS.toString()));
            assertThat(report, containsString(PluginManager.CONFIGURE_UPDATECENTER.toString()));
            assertThat(report, containsString(PluginManager.UPLOAD_PLUGINS.toString()));
            assertThat(report, containsString("FakeAdmins"));

            // Ensure that fakeAdmin does not get the dangerous permission by default
            assertHasNoPermission("fakeAdmin", PluginManager.CONFIGURE_UPDATECENTER);
            assertHasNoPermission("fakeAdmin", PluginManager.UPLOAD_PLUGINS);
            assertHasNoPermission("fakeAdmin", Jenkins.RUN_SCRIPTS);
            assertHasNoPermission("fakeAdmin", Jenkins.ADMINISTER);
            
            // Ensure that the permissions are mnot shown even if we ask for that
            RoleBasedAuthorizationStrategy.DescriptorImpl d = (RoleBasedAuthorizationStrategy.DescriptorImpl) strategy.getDescriptor();
            Assert.assertFalse(d.showPermission(RoleBasedAuthorizationStrategy.GLOBAL, PluginManager.CONFIGURE_UPDATECENTER, true));
            Assert.assertFalse(d.showPermission(RoleBasedAuthorizationStrategy.GLOBAL, PluginManager.UPLOAD_PLUGINS, true));
            Assert.assertFalse(d.showPermission(RoleBasedAuthorizationStrategy.GLOBAL, Jenkins.RUN_SCRIPTS, true)); 
            
            // Monitor is disabled
            DangerousPermissionAdministrativeMonitor m = DangerousPermissionAdministrativeMonitor.getInstance();
            Assert.assertFalse("DangerousPermissionAdministrativeMonitor should be disabled", m.isEnabled());
            Assert.assertFalse("DangerousPermissionAdministrativeMonitor should be deactivated", m.isActivated());
        } finally {
            DangerousPermissionHandlingMode.CURRENT = DangerousPermissionHandlingMode.UNDEFINED;
        }
    }
            
            
    @Test
    @Issue("SECURITY-410")
    @LocalData
    public void shouldNotShowDangerousWhenAdmin() {
        assertThat(j.jenkins.getAuthorizationStrategy(), instanceOf(RoleBasedAuthorizationStrategy.class));
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)j.jenkins.getAuthorizationStrategy();
        
        Assert.assertFalse("There should be no dangerous permissions detected", PermissionHelper.hasDangerousPermissions(strategy));
        Assert.assertNull("Dangerous permission report should be empty", PermissionHelper.reportDangerousPermissions(strategy));
        
        // Ensure that admin gets the permissions
        assertHasPermission("admin", PluginManager.CONFIGURE_UPDATECENTER);
        assertHasPermission("admin", PluginManager.UPLOAD_PLUGINS);
        assertHasPermission("admin", Jenkins.RUN_SCRIPTS);
        assertHasPermission("admin", Jenkins.ADMINISTER);
        
        // Monitor is disabled
        DangerousPermissionAdministrativeMonitor m = DangerousPermissionAdministrativeMonitor.getInstance();
        Assert.assertTrue("DangerousPermissionAdministrativeMonitor should be enabled", m.isEnabled());
        Assert.assertFalse("DangerousPermissionAdministrativeMonitor should be deactivated", m.isActivated());
    }
    
    private void assertHasNoPermission(String user, Permission p) throws AssertionError {
        AuthorizationStrategy str = j.jenkins.getAuthorizationStrategy();
        assertThat(str, instanceOf(RoleBasedAuthorizationStrategy.class));
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)str;
        
        Assert.assertFalse(user + " should not have the " + p + " permission", 
                strategy.getACL(j.jenkins).hasPermission(User.get(user).impersonate(), p));
    }
    
    private void assertHasPermission(String user, Permission p) throws AssertionError {
        AuthorizationStrategy str = j.jenkins.getAuthorizationStrategy();
        assertThat(str, instanceOf(RoleBasedAuthorizationStrategy.class));
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)str;
        
        Assert.assertTrue(user + " should have the " + p + " permission", 
                strategy.getACL(j.jenkins).hasPermission(User.get(user).impersonate(), p));
    }
}
