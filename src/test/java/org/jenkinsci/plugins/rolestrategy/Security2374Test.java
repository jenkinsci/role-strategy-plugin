/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
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

import static com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType.EITHER;
import static com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType.GROUP;
import static com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType.USER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.SidACL;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;


@WithJenkins
class Security2374Test {

  @Test
  @WithJenkinsConfiguredWithCode
  @ConfiguredWithCode("Security2374Test/casc.yaml")
  void readFromCasc(JenkinsConfiguredWithCodeRule j) throws Exception {
    RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();

    // So we can log in
    JenkinsRule.DummySecurityRealm dsr = j.createDummySecurityRealm();
    dsr.addGroups("gerry", "groupname");

    dsr.addGroups("intruderA", "username");
    dsr.addGroups("intruderB", "eitherSID");
    dsr.addGroups("intruderC", "indifferentSID");

    j.jenkins.setSecurityRealm(dsr);

    ACL acl = j.jenkins.getACL();
    assertTrue(acl.hasPermission2(User.getById("indifferentSID", true).impersonate2(), Jenkins.ADMINISTER));
    assertTrue(acl.hasPermission2(User.getById("username", true).impersonate2(), Jenkins.ADMINISTER));
    assertTrue(acl.hasPermission2(User.getById("gerry", true).impersonate2(), Jenkins.ADMINISTER));
    assertFalse(acl.hasPermission2(User.getById("intruderA", true).impersonate2(), Jenkins.ADMINISTER));
    // Users with group named after one of the EITHER sids (explicit or implicit) are let in
    assertTrue(acl.hasPermission2(User.getById("intruderB", true).impersonate2(), Jenkins.ADMINISTER));
    assertTrue(acl.hasPermission2(User.getById("intruderC", true).impersonate2(), Jenkins.ADMINISTER));

    AmbiguousSidsAdminMonitor am = AmbiguousSidsAdminMonitor.get();
    assertTrue(am.isActivated());
    assertThat(am.getAmbiguousEntries(), Matchers.containsInAnyOrder("eitherSID", "indifferentSID"));

    HtmlPage manage;
    try (JenkinsRule.WebClient wc = j.createWebClient()) {
      wc.login("gerry", "gerry");
      manage = wc.goTo("manage");
      String source = manage.getWebResponse().getContentAsString();
      assertThat(source, Matchers.containsString("'USER:username' or 'GROUP:groupname'"));
      assertThat(source, Matchers.containsString("indifferentSID"));
      assertThat(source, Matchers.containsString("eitherSID"));
    }
  }

  @Test
  @WithoutJenkins
  void createPermissionEntry() {
    assertThat(PermissionEntry.user("foo"), equalTo(permissionEntry("USER:foo")));
    assertThat(PermissionEntry.group("foo"), equalTo(permissionEntry("GROUP:foo")));
    assertThat(permissionEntry(""), nullValue());
    assertThat(permissionEntry(":-)"), nullValue());
    assertThat(permissionEntry("Re:"), nullValue());
    assertThat(permissionEntry("GROUP:"), nullValue());
    assertThat(permissionEntry("USER:"), nullValue());
  }

  public PermissionEntry permissionEntry(String in) {
    return PermissionEntry.fromString(in);
  }

  @Test
  void adminMonitor(JenkinsRule j) throws Exception {
    AmbiguousSidsAdminMonitor am = AmbiguousSidsAdminMonitor.get();
    assertFalse(am.isActivated());
    assertThat(am.getAmbiguousEntries(), Matchers.emptyIterable());

    am.updateEntries(Collections.singletonList(new PermissionEntry(EITHER, "foo")));
    assertTrue(am.isActivated());
    assertThat(am.getAmbiguousEntries(), equalTo(Collections.singletonList("foo")));

    am.updateEntries(Collections.emptyList());
    assertFalse(am.isActivated());
    assertThat(am.getAmbiguousEntries(), Matchers.emptyIterable());

    am.updateEntries(Arrays.asList(new PermissionEntry(USER, "foo"), new PermissionEntry(GROUP, "bar")));
    assertFalse(am.isActivated());
    assertThat(am.getAmbiguousEntries(), Matchers.emptyIterable());

    am.updateEntries(Arrays.asList(new PermissionEntry(USER, "foo"), new PermissionEntry(GROUP, "bar"),
            new PermissionEntry(EITHER, "baz")));
    assertTrue(am.isActivated());
    assertThat(am.getAmbiguousEntries(), equalTo(Collections.singletonList("baz")));
  }

  @LocalData
  @Test
  void test3xDataMigration(JenkinsRule j) throws Exception {
    assertInstanceOf(RoleBasedAuthorizationStrategy.class, j.jenkins.getAuthorizationStrategy());
    final RoleBasedAuthorizationStrategy authorizationStrategy = (RoleBasedAuthorizationStrategy) j.jenkins.getAuthorizationStrategy();
    final SidACL acl = authorizationStrategy.getRootACL();
    final File configXml = new File(j.jenkins.getRootDir(), "config.xml");
    List<String> configLines;
    try (Reader reader = new FileReader(configXml)) {
      configLines = IOUtils.readLines(reader);
    }
    assertFalse(acl.hasPermission2(User.getById("markus", true).impersonate2(), Jenkins.ADMINISTER));
    assertTrue(acl.hasPermission2(User.getById("markus", true).impersonate2(), Jenkins.READ));
    assertTrue(acl.hasPermission2(User.getById("admin", true).impersonate2(), Jenkins.ADMINISTER));
    assertTrue(configLines.stream().anyMatch(line -> line.contains("<sid type=\"EITHER\">admin</sid>")));
  }
}
