package com.michelin.cio.hudson.plugins.rolestrategy;

import hudson.model.User;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link RoleStrategySecurityConfig}.
 * @author Thomas Nemer
 */
public class RoleStrategySecurityConfigTest {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        // Setting up jenkins configurations
        jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        jenkinsRule.jenkins.setAuthorizationStrategy(new RoleBasedAuthorizationStrategy());
        jenkinsRule.jenkins.setCrumbIssuer(null);
        // Adding admin role and assigning adminUser
        RoleBasedAuthorizationStrategy.getInstance().doAddRole("globalRoles", "adminRole",
                "hudson.model.Hudson.Read,hudson.model.Hudson.Administer,hudson.security.Permission.GenericRead" ,
                "false", "");
        RoleBasedAuthorizationStrategy.getInstance().doAssignRole("globalRoles", "adminRole", "adminUser");
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(User.getById("adminUser", true).impersonate());
    }

    @Test
    public void testAllSecurityConfigs() throws Exception {
        final RoleBasedAuthorizationStrategy plugin = RoleBasedAuthorizationStrategy.getInstance();
        
        // Test all possibilities in configuration creation
        testSecurityConfig(new RoleStrategySecurityConfig(true));
        testSecurityConfig(new RoleStrategySecurityConfig(false));
    }
    
    private void testSecurityConfig(RoleStrategySecurityConfig config) throws IOException {
        final RoleBasedAuthorizationStrategy plugin = RoleBasedAuthorizationStrategy.getInstance();
        RoleStrategySecurityConfig.configure(config.isLogDangerousPermissions());
        assertEquals("Value of logDangerousPermissions field differs after the configure() call",
                config.isLogDangerousPermissions(), plugin.getSecurityConfiguration().isLogDangerousPermissions());
        plugin.getSecurityConfiguration().save();
        
        // Reload from disk
        RoleStrategySecurityConfig reloaded = new RoleStrategySecurityConfig();
        assertEquals("Value of logDangerousPermissions field differs after the reload",
                config.isLogDangerousPermissions(), reloaded.isLogDangerousPermissions());
    }
}
