package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.Macro;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;

/**
 * A Naming Strategy so that users with only item specific create permissions can create only items matching the role
 * pattern.
 *
 * @author Kanstantsin Shautsou
 * @since 2.2.0
 */
public class RoleBasedProjectNamingStrategy extends ProjectNamingStrategy implements Serializable {

  private static final long serialVersionUID = 1L;

  private final boolean forceExistingJobs;

  @DataBoundConstructor
  public RoleBasedProjectNamingStrategy(boolean forceExistingJobs) {
    this.forceExistingJobs = forceExistingJobs;
  }

  @Override
  public void checkName(String name) throws Failure {
    StaplerRequest request = Stapler.getCurrentRequest();
    // Workaround until JENKINS-68602 is implemented
    // This works only for requests via the UI. In case this method is called due to
    // job creation request via the CLI, we have no way to determine the
    // the parent so just check the name
    String parentName = "";
    if (request != null) {
      ItemGroup<?> i = Stapler.getCurrentRequest().findAncestorObject(ItemGroup.class);
      parentName = i.getFullName();
    }
    checkName(parentName, name);
  }

  /**
   * Checks if the given name and parentName match a role pattern.
   *
   * @param parentName Name of the parent item in which the new item should be created.
   * @param name       The name of the item that should be created.
   * @throws Failure When the name is not allowed or {@code Item.CREATE} permission is missing
   */
  // TODO: add Override once this method is implemented in Core and consumed here.
  public void checkName(String parentName, String name) throws Failure {

    if (StringUtils.isBlank(name)) {
      return;
    }

    String fullName = name;
    if (StringUtils.isNotBlank(parentName)) {
      fullName = parentName + "/" + name;
    }

    AuthorizationStrategy auth = Jenkins.get().getAuthorizationStrategy();
    if (auth instanceof RoleBasedAuthorizationStrategy) {
      Authentication a = Jenkins.getAuthentication2();
      if (a == ACL.SYSTEM2) {
        return;
      }
      PermissionEntry principal = new PermissionEntry(AuthorizationType.USER, new PrincipalSid(a).getPrincipal());

      RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) auth;
      RoleMap global = rbas.getRoleMap(RoleType.Global);
      List<String> authorities = a.getAuthorities().stream().map(x -> x.getAuthority()).collect(Collectors.toList());

      // first check global role
      if (hasCreatePermission(global, principal, authorities, RoleType.Global)) {
        return;
      }

      // check if user has anywhere else create permissions
      RoleMap item = rbas.getRoleMap(RoleType.Project);
      if (!hasCreatePermission(item, principal, authorities, RoleType.Project)) {
        throw new Failure(Messages.RoleBasedProjectNamingStrategy_NoPermissions());
      }

      // check project role with pattern
      SortedMap<Role, Set<PermissionEntry>> roles = rbas.getGrantedRolesEntries(RoleType.Project);
      ArrayList<String> badList = new ArrayList<>(roles.size());
      for (SortedMap.Entry<Role, Set<PermissionEntry>> entry : roles.entrySet()) {
        Role key = entry.getKey();
        if (!Macro.isMacro(key) && key.hasPermission(Item.CREATE)) {
          Set<PermissionEntry> sids = entry.getValue();
          Pattern namePattern = key.getPattern();
          if (StringUtils.isNotBlank(namePattern.toString())) {
            if (namePattern.matcher(fullName).matches()) {
              if (hasAnyPermission(principal, authorities, sids)) {
                return;
              }
            } else {
              badList.add(namePattern.toString());
            }
          }
        }
      }
      String error;
      if (badList != null && !badList.isEmpty()) {
        error = Messages.RoleBasedProjectNamingStrategy_JobNameConventionNotApplyed(fullName, badList.toString());
      } else {
        error = Messages.RoleBasedProjectNamingStrategy_NoPermissions();
      }
      throw new Failure(error);
    }
  }

  private boolean hasAnyPermission(PermissionEntry principal, List<String> authorities, Set<PermissionEntry> sids) {
    PermissionEntry eitherUser = new PermissionEntry(AuthorizationType.EITHER, principal.getSid());
    if (sids.contains(principal) || sids.contains(eitherUser)) {
      return true;
    } else {
      for (String authority : authorities) {
        if (sids.contains(new PermissionEntry(AuthorizationType.GROUP, authority))
            || sids.contains(new PermissionEntry(AuthorizationType.EITHER, authority))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasCreatePermission(RoleMap roleMap, PermissionEntry principal, List<String> authorities, RoleType roleType) {
    if (roleMap.hasPermission(principal, Item.CREATE, roleType, null)) {
      return true;
    }
    for (String group : authorities) {
      PermissionEntry groupEntry = new PermissionEntry(AuthorizationType.GROUP, group);
      if (roleMap.hasPermission(groupEntry, Item.CREATE, roleType, null)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isForceExistingJobs() {
    return forceExistingJobs;
  }

  /**
   * Descriptor.
   */
  @Extension
  public static final class DescriptorImpl extends ProjectNamingStrategyDescriptor {

    @NonNull
    @Override
    public String getDisplayName() {
      return Messages.RoleBasedAuthorizationStrategy_DisplayName();
    }

  }
}
