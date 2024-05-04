package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Alert administrators in case ambiguous SID are declared.
 *
 * @see <a href="https://issues.jenkins.io/browse/SECURITY-2374">SECURITY-2374</a>
 */
@Extension
@Restricted(NoExternalUse.class)
public class AmbiguousSidsAdminMonitor extends AdministrativeMonitor {

  private @NonNull List<String> ambiguousEntries = Collections.emptyList();

  public static @NonNull AmbiguousSidsAdminMonitor get() {
    return ExtensionList.lookupSingleton(AmbiguousSidsAdminMonitor.class);
  }

  /**
   * To be called everytime Permission Entries are updated.
   *
   * @param entries All entries in the system.
   */
  public void updateEntries(@NonNull Collection<PermissionEntry> entries) {
    List<String> ambiguous = new ArrayList<>();
    for (PermissionEntry entry : entries) {
      try {
        if (entry.getType() == AuthorizationType.EITHER) {
          ambiguous.add(entry.getSid());
        }
      } catch (IllegalArgumentException ex) {
        // Invalid, but not the problem we are looking for
      }
    }
    ambiguousEntries = ambiguous;
  }

  public @NonNull List<String> getAmbiguousEntries() {
    return ambiguousEntries;
  }

  @Override
  public boolean isActivated() {
    return !ambiguousEntries.isEmpty();
  }

  @Override
  public String getDisplayName() {
    return Messages.RoleBasedProjectNamingStrategy_Ambiguous();
  }

}
