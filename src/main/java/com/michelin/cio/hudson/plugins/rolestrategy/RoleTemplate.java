package com.michelin.cio.hudson.plugins.rolestrategy;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Role Template.
 */
@Restricted(NoExternalUse.class)
public class RoleTemplate {
  private final String name;
  private final String pattern;

  @DataBoundConstructor
  public RoleTemplate(String name, String pattern) {
    this.name = name;
    this.pattern = pattern;
  }

  public String getName() {
    return name;
  }

  public String getPattern() {
    return pattern;
  }
}
