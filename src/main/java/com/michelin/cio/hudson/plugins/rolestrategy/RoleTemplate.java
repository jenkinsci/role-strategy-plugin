package com.michelin.cio.hudson.plugins.rolestrategy;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Role Template.
 */
@Restricted(NoExternalUse.class)
public class RoleTemplate implements Comparable<RoleTemplate> {
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

  @Override
  public int compareTo(@NonNull RoleTemplate o) {
    return name.compareTo(o.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final RoleTemplate other = (RoleTemplate) obj;
    return Objects.equals(name, other.name);
  }
}
