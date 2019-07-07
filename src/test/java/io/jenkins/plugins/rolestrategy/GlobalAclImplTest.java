package io.jenkins.plugins.rolestrategy;

import hudson.model.Item;
import hudson.model.User;
import io.jenkins.plugins.rolestrategy.acls.GlobalAclImpl;
import io.jenkins.plugins.rolestrategy.roles.GlobalRole;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.HashSet;
import java.util.Set;

import static io.jenkins.plugins.rolestrategy.misc.PermissionWrapper.wrapPermissions;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlobalAclImplTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void hasPermission() {
        jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        Set<GlobalRole> globalRoles = new HashSet<>();

        GlobalRole role1 = new GlobalRole("role1", wrapPermissions(Item.DISCOVER, Item.READ));
        GlobalRole role2 = new GlobalRole("role2", wrapPermissions(Item.READ, Item.CONFIGURE, Item.BUILD));
        GlobalRole adminRole = new GlobalRole("adminRole", wrapPermissions(Jenkins.ADMINISTER));

        role1.assignSids("foo", "bar", "baz");
        role2.assignSids("baz");
        adminRole.assignSids("admin");

        globalRoles.add(role1);
        globalRoles.add(role2);
        globalRoles.add(adminRole);

        GlobalAclImpl acl = new GlobalAclImpl(globalRoles);

        Authentication foo = User.get("foo").impersonate();
        Authentication bar = User.get("bar").impersonate();
        Authentication baz = User.get("baz").impersonate();
        Authentication admin = User.get("admin").impersonate();

        assertTrue(acl.hasPermission(foo, Item.READ));
        assertFalse(acl.hasPermission(foo, Item.CONFIGURE));
        assertFalse(acl.hasPermission(foo, Jenkins.ADMINISTER));

        assertTrue(acl.hasPermission(bar, Item.DISCOVER));
        assertTrue(acl.hasPermission(baz, Item.CONFIGURE));
        assertFalse(acl.hasPermission(baz, Jenkins.ADMINISTER));

        assertTrue(acl.hasPermission(admin, Jenkins.ADMINISTER));
    }
}
