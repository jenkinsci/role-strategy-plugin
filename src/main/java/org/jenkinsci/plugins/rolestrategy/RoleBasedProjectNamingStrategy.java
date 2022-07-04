package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.AuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;

import org.acegisecurity.acls.sid.PrincipalSid;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
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
            String principal = new PrincipalSid(a).getPrincipal();
            RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) auth;
            RoleMap global = rbas.getRoleMap(RoleType.Global);
            List<String> authorities = a.getAuthorities().stream().map(x -> x.getAuthority()).collect(Collectors.toList());
            
            //first check global role
            if (hasCreatePermission(global, principal, authorities, RoleType.Global)) {
              return;
            }

            // check if user has anywhere else create permissions
            RoleMap item = rbas.getRoleMap(RoleType.Project);
            if (!hasCreatePermission(item, principal, authorities, RoleType.Project)) {
              throw new Failure(Messages.RoleBasedProjectNamingStrategy_NoPermissions());
            }

            // check project role with pattern
            SortedMap<Role, Set<String>> roles = rbas.getGrantedRoles(RoleType.Project);
            ArrayList<String> badList = new ArrayList<>(roles.size());
            for (SortedMap.Entry<Role, Set<String>> entry: roles.entrySet())  {
                Role key = entry.getKey();
                if (key.hasPermission(Item.CREATE)) {
                    Set<String> sids = entry.getValue();
                    Pattern namePattern = key.getPattern();
                    if (StringUtils.isNotBlank(namePattern.toString())) {
                        if (namePattern.matcher(fullName).matches()) {
                            if (sids.contains(principal)) {
                                return;
                            } else {
                              for (String authority: authorities) {
                                  if (sids.contains(authority)) {
                                      return;
                                  }
                              }
                            }
                        } else {
                          badList.add(namePattern.toString());
                        }
                    }
                }
            }
            String error;
            if (badList != null && !badList.isEmpty())
                //TODO beatify long outputs?
                error = Messages.RoleBasedProjectNamingStrategy_JobNameConventionNotApplyed(name, badList.toString());
            else
                error = Messages.RoleBasedProjectNamingStrategy_NoPermissions();
            throw new Failure(error);
        } 
    }

    private boolean hasCreatePermission(RoleMap roleMap, String principal, List<String> authorities, RoleType roleType) {
      if (roleMap.hasPermission(principal, Item.CREATE, roleType, null)) {
        return true;
      }
      for (String group: authorities) {
        if (roleMap.hasPermission(group, Item.CREATE, roleType, null)) {
          return true;
        }
      }
      return false;
    }
    
    @Override
    public boolean isForceExistingJobs() {
        return forceExistingJobs;
    }

    @Extension
    public static final class DescriptorImpl extends ProjectNamingStrategyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.RoleBasedAuthorizationStrategy_DisplayName();
        }

    }
}
