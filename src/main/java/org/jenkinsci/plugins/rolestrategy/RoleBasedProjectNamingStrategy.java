package org.jenkinsci.plugins.rolestrategy;

import hudson.Extension;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.security.Permission;
import hudson.security.AuthorizationStrategy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;

/**
 * @author Kanstantsin Shautsou
 * @since 2.2.0
 */
/**
 * According to https://issues.jenkins-ci.org/browse/JENKINS-19934
 *
 * If a user has a global role with "Job Create" then they can create any
 * jobname. If the user does not have such a global role, they cannot create any
 * jobs. In fact, the "New View" link to create a job is not even displayed.
 * 
 * */
public final class RoleBasedProjectNamingStrategy extends ProjectNamingStrategy implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleBasedProjectNamingStrategy.class);

    private static final long serialVersionUID = 1L;

    private final boolean forceExistingJobs;

    @DataBoundConstructor
    public RoleBasedProjectNamingStrategy(boolean forceExistingJobs) {
        this.forceExistingJobs = forceExistingJobs;
    }

    @Override
    public void checkName(String name) throws Failure {
        boolean matches = false;
        ArrayList<String> badList = new ArrayList<String>();
        AuthorizationStrategy auth = Jenkins.getInstance().getAuthorizationStrategy();
        if (auth instanceof RoleBasedAuthorizationStrategy) {
            RoleBasedAuthorizationStrategy rbas = (RoleBasedAuthorizationStrategy) auth;
            // The current user
            String userName = Jenkins.getAuthentication().getName();
            LOGGER.debug("Current username " + userName);

            /*
             * In jenkins it seems there is a bug. If you only have the Job
             * Create Permission (and do not have the Global Create Permission),
             * you actually cannot create a job because the button 'New Item'
             * does not appear. So a user needs both to create a job.
             */

            // Here we check if he has both permissions (Global and Project
            // Creation)
            // If he has both, it means we want him to respect a pattern
            boolean hasGlobalPermissions = hasGlobalPermission(userName, Item.CREATE, rbas);
            boolean hasProjectPermissions = hasProjectPermission(userName, Item.CREATE, rbas);
            if (hasGlobalPermissions && hasProjectPermissions) {
                LOGGER.debug("The user: " + userName + " has global and project permissions");

                // Get all the project roles
                final SortedMap<Role, Set<String>> projectRoles = rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.PROJECT);
                Role userRole = null;
                for (SortedMap.Entry<Role, Set<String>> userPerRole : projectRoles.entrySet()) {
                    userRole = userPerRole.getKey();
                    // We only want to check the pattern from the role with Job
                    // Create Permission linked to the current user
                    if (userRole.hasPermission(Item.CREATE) && projectRoles.get(userRole).contains(userName)) {
                        String namePattern = userRole.getPattern().toString();
                        if (StringUtils.isNotBlank(namePattern) && StringUtils.isNotBlank(name)) {
                            if (Pattern.matches(namePattern, name)) {
                                LOGGER.info("The project name " + name + " respects the pattern " + namePattern + " of the role "
                                        + userRole.getName());
                                matches = true;
                            } else {
                                LOGGER.info("The project name " + name + " does not respect " + namePattern + " of the role " + userRole.getName());
                                badList.add(namePattern);
                            }
                        }
                    }

                }

                // The user has only Global Project Permission, he does not need
                // to respect a pattern
            } else if (hasGlobalPermissions) {
                LOGGER.debug("The user: " + userName + " has only global permissions ");
                matches = true;
            }

            if (!matches) {
                String error;
                if (badList != null && !badList.isEmpty()) {
                    // TODO beatify long outputs?
                    LOGGER.error("The name: " + name + " project does not match the pattern(s) " + toString(badList));
                    error = jenkins.model.Messages.Hudson_JobNameConventionNotApplyed(name, toString(badList));
                }
                else {
                    LOGGER.error("The user:" + userName + " does not have create permission.");
                    error = Messages.RoleBasedProjectNamingStrategy_NoPermissions();
                }
                throw new Failure(error);
            }
        }
    }

    private String toString(ArrayList<String> badList) {
        String toReturn = "";
        for (int i = 0; i < badList.size(); i++) {
            if (i == 0) {
                toReturn += badList.get(i);
            } else {
                toReturn += " or " + badList.get(i);

            }
        }
        return toReturn;
    }

    private boolean hasGlobalPermission(String userName, Permission permission, RoleBasedAuthorizationStrategy rbas) {
        LOGGER.debug("Check the global permission of the user " + userName + " " + rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.GLOBAL));

        return hasPermission(userName, permission, rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.GLOBAL));
    }

    private boolean hasProjectPermission(String userName, Permission permission, RoleBasedAuthorizationStrategy rbas) {
        LOGGER.debug("Check the project permission of the user " + userName + " " + rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.PROJECT));
        return hasPermission(userName, permission, rbas.getGrantedRoles(RoleBasedAuthorizationStrategy.PROJECT));
    }

    private boolean hasPermission(String userName, Permission permission, SortedMap<Role, Set<String>> roles) {
        Role role = null;
        for (SortedMap.Entry<Role, Set<String>> userPerRole : roles.entrySet()) {
            role = userPerRole.getKey();
            if (role.hasPermission(permission) && roles.get(role).contains(userName)) {
                LOGGER.debug("The user " + userName + " has the permission " + permission + " from the role " + role);
                return true;
            }
        }
        LOGGER.debug("The user " + userName + " doesn not have the permission " + permission);

        return false;
    }

    @Override
    public boolean isForceExistingJobs() {
        return forceExistingJobs;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(RoleBasedProjectNamingStrategy.class);

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
