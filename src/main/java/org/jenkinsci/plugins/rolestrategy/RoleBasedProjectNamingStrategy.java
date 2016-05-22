package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.Extension;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.security.AuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

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
        boolean matches = false;
        ArrayList<String> badList = null;
        AuthorizationStrategy auth = Jenkins.getInstance().getAuthorizationStrategy();
        
        
        if (auth instanceof RoleBasedAuthorizationStrategy){
            RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) auth;

            
            //firstly check global role
            boolean globalRolePermitted = false;
            SortedMap<Role, Set<String>> gRole = rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.GLOBAL);
            for (SortedMap.Entry<Role, Set<String>> entry: gRole.entrySet()){
                if (entry.getKey().hasPermission(Item.CREATE)) {
                	globalRolePermitted = true;
                	break;
                }
            }
            
            //no global role: no permission today
            if(!globalRolePermitted) {
                throw new Failure(Messages.RoleBasedProjectNamingStrategy_NoPermissions());
            }
            
            // check project role with pattern
            SortedMap<Role, Set<String>> roles = rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.PROJECT);
            badList = new ArrayList<String>(roles.size());
            for (SortedMap.Entry<Role, Set<String>> entry: roles.entrySet())  {
                Role key = entry.getKey();
                if (key.hasPermission(Item.CREATE) 
                		&& key.hasPermission(Item.READ) 
                		&& key.hasPermission(Item.CONFIGURE)
                		&& isUserIncluded(entry.getValue())) {
                	String namePattern = key.getPattern().toString();
                    if (StringUtils.isNotBlank(namePattern) && StringUtils.isNotBlank(name)) {
                        if (Pattern.matches(namePattern, name)){
                            matches = true;
                        } else {
                            badList.add(namePattern);
                        }
                	}
                }
            }
        }
        if (!matches) {
            String error;
            if (badList != null && !badList.isEmpty())
                //TODO beatify long outputs?
                error = jenkins.model.Messages.Hudson_JobNameConventionNotApplyed(name, badList.toString());
            else
                error = Messages.RoleBasedProjectNamingStrategy_NoPermissions();
            throw new Failure(error);
        }
    }

    private boolean isUserIncluded(Set<String> sids) {
        Authentication authentication = Jenkins.getAuthentication();

        //check user name
        PrincipalSid currentUser = new PrincipalSid(authentication);
        String userName = currentUser.getPrincipal();
		if(sids.contains(userName)) {
			return true;
		}

		//check groups - like with LDAP groups 
		for(GrantedAuthority a: authentication.getAuthorities()) {
			if(sids.contains(a.getAuthority())) {
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

        @Override
        public String getDisplayName() {
            String name = Messages.RoleBasedAuthorizationStrategy_DisplayName();
            if (!RoleBasedAuthorizationStrategy.isCreateAllowed())
                name += " (<font color=\"red\">(Require >1.565 core)</font>";
            return name;
        }

    }
}
