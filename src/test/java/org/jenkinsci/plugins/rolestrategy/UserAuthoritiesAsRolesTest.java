package org.jenkinsci.plugins.rolestrategy;

import hudson.model.User;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.Permission;
import junit.framework.Assert;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.springframework.dao.DataAccessException;

public class UserAuthoritiesAsRolesTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Before
    public void enableUserAuthorities() {
        Settings.TREAT_USER_AUTHORITIES_AS_ROLES = true;
    }
    
    @After
    public void disableUserAuthorities() {
        Settings.TREAT_USER_AUTHORITIES_AS_ROLES = false;
    }
    
    @LocalData
    @Test public void testRoleAuthority() throws Exception {
        j.jenkins.setSecurityRealm(new AbstractPasswordBasedSecurityRealm() {

            @Override
            protected UserDetails authenticate(String username, String password) throws AuthenticationException {
                throw new UnsupportedOperationException();
            }

            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                return new org.acegisecurity.userdetails.User(username, "", true, true, true, true, new GrantedAuthority[] {new GrantedAuthorityImpl("USERS")});
            }

            @Override
            public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
                throw new UnsupportedOperationException();
            }

        });

        SecurityContext seccon = SecurityContextHolder.getContext();
        Authentication orig = seccon.getAuthentication();
        seccon.setAuthentication(User.get("alice").impersonate());
        try {
            Assert.assertTrue(j.jenkins.hasPermission(Permission.READ));
        } finally {
            seccon.setAuthentication(orig);
        }
    }

}
