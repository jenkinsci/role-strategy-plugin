package org.jenkinsci.plugins.rolestrategy.casc;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleTemplate;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Casc Wrapper for RoleTemplates.
 */
@Restricted(NoExternalUse.class)
public class RoleTemplates {
  private final List<RoleTemplate> roleTemplates;

  @DataBoundConstructor
  public RoleTemplates(List<RoleTemplate> roleTemplates) {
    this.roleTemplates = roleTemplates;
  }

  public List<RoleTemplate> getRoleTemplates() {
    return roleTemplates;
  }
}
