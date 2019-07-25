package org.jenkinsci.plugins.rolestrategy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.Extension;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.security.AuthorizationStrategy;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.userdetails.UserDetails;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author Kanstantsin Shautsou
 * @since 2.2.0
 */
public class RoleBasedProjectNamingStrategy extends ProjectNamingStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean forceExistingJobs;

    private static final Cache<String, UserDetails> cache = CacheBuilder.newBuilder()
            .softValues()
            .maximumSize(Settings.USER_DETAILS_CACHE_MAX_SIZE)
            .expireAfterWrite(Settings.USER_DETAILS_CACHE_EXPIRATION_TIME_SEC, TimeUnit.SECONDS)
            .build();

    @DataBoundConstructor
    public RoleBasedProjectNamingStrategy(boolean forceExistingJobs) {
        this.forceExistingJobs = forceExistingJobs;
    }

    @Override
    public void checkName(String name) throws Failure {
        boolean matches = false;
        ArrayList<String> badList = null;
        final AuthorizationStrategy auth = Jenkins.get().getAuthorizationStrategy();
        if (auth instanceof RoleBasedAuthorizationStrategy) {
            final RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) auth;

            // Check whether the user has a global role allowing them to carry out this action
            boolean globalRolePermitted = false;
            final SortedMap<Role, Set<String>> gRole = rbas.getGrantedRoles(RoleType.Global);
            for (final SortedMap.Entry<Role, Set<String>> entry : gRole.entrySet()) {
                if (entry.getKey().hasPermission(Item.CREATE)) {
                    globalRolePermitted = true;
                    break;
                }
            }

            if (!globalRolePermitted) {
                throw new Failure(Messages.RoleBasedProjectNamingStrategy_NoPermissions());
            }

            // Check whether there's a project-specific role that matches
            final SortedMap<Role, Set<String>> roles = rbas.getGrantedRoles(RoleType.Project);
            badList = new ArrayList<>(roles.size());
            if (StringUtils.isNotBlank(name)) {
                for (SortedMap.Entry<Role, Set<String>> entry: roles.entrySet())  {
                    Role key = entry.getKey();
                    if (key.hasPermission(Item.CREATE)) {
                        String namePattern = key.getPattern().toString();
                        if (StringUtils.isNotBlank(namePattern)) {
                            if (Pattern.matches(namePattern, name)){
                                matches = true;
                            } else {
                                badList.add(namePattern);
                            }
                        }
                    }
                }
            }
        }

        if (!matches) {
            String error;
            if (badList != null && !badList.isEmpty())
                //TODO beatify long outputs?
                error = Messages.RoleBasedProjectNamingStrategy_JobNameConventionNotApplyed(name, badList.toString());
            else
                error = Messages.RoleBasedProjectNamingStrategy_NoPermissions();
            throw new Failure(error);
        }
    }

    private boolean isUserIncluded(final Set<String> sids) {
        // Get current user information
        final Authentication authentication = Jenkins.getAuthentication();
        final PrincipalSid currentUser = new PrincipalSid(authentication);
        final String currentSid = currentUser.getPrincipal();

        // Check if the current user is in the list of allowed SIDs
        if (sids.contains(currentSid))
        {
            return true;
        }

        if(Settings.TREAT_USER_AUTHORITIES_AS_ROLES)
        {
            // Get details from cache to avoid performance hit
            UserDetails userDetails = cache.getIfPresent(currentSid);
            if (userDetails == null) {
                userDetails = Jenkins.getInstance().getSecurityRealm().loadUserByUsername(currentSid);
                cache.put(currentSid, userDetails);
            }

            // Intersect the list of allowed roles with the user's groups
            // If at least one remains, then the user has a group that has
            // been allocated this role
            sids.retainAll(Arrays.asList(userDetails.getAuthorities()));
            return sids.size() > 0;
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
            return Messages.RoleBasedAuthorizationStrategy_DisplayName();
        }

    }
}
