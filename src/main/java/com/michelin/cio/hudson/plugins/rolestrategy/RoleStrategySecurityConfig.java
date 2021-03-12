package com.michelin.cio.hudson.plugins.rolestrategy;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Configuration of security options for {@link RoleBasedAuthorizationStrategy}.
 *
 * @author Thomas Nemer
 * @since 3.2
 */
@Extension
@Symbol("roleStrategyConfig")
public class RoleStrategySecurityConfig extends GlobalConfiguration {

    private static final RoleStrategySecurityConfig DEFAULT =
            new RoleStrategySecurityConfig(false);

    /**
     * If true, using a PermissionHelper to get a set of Permissions containing
     * dangerous permissions will log a warning instead of throwing a SecurityException.
     * In any case, the permission is dismissed from the set.
     */
    private boolean logDangerousPermissions;

    @DataBoundConstructor
    public RoleStrategySecurityConfig() {
        load();
    }

    /**
     * Constructor.
     *
     * @param logDangerousPermissions allows logging dangerous permissions
     *                                instead of throwing exceptions.
     */
    RoleStrategySecurityConfig(boolean logDangerousPermissions) {
        this.logDangerousPermissions = logDangerousPermissions;
    }

    /**
     * Gets the default configuration of {@link RoleBasedAuthorizationStrategy}
     *
     * @return Default configuration
     */
    @Nonnull
    public static final RoleStrategySecurityConfig getDefault() {
        return DEFAULT;
    }

    /**
     * Configuration method for testing purposes.
     *
     * @param logDangerousPermissions allows logging dangerous permissions instead of throwing exceptions.
     * @return true if the configuration successful
     * @throws IllegalStateException Cannot retrieve the plugin config instance
     */
    @VisibleForTesting
    static boolean configure(boolean logDangerousPermissions) {
        RoleStrategySecurityConfig instance = getInstance();
        if (null != instance) {
            instance.logDangerousPermissions = logDangerousPermissions;
            instance.save();
            return true;
        } else {
            throw new IllegalStateException("Cannot retrieve the plugin config instance");
        }
    }

    @CheckForNull
    public static RoleStrategySecurityConfig getInstance() {
        return RoleStrategySecurityConfig.all().get(RoleStrategySecurityConfig.class);
    }

    /**
     * Retrieves the Role Based Strategy security configuration.
     *
     * @return Settings
     * @throws NullPointerException The configuration cannot be retrieved
     */
    @Nonnull
    public static RoleStrategySecurityConfig getOrFail() throws NullPointerException {
        RoleStrategySecurityConfig c = RoleStrategySecurityConfig.all().get(RoleStrategySecurityConfig.class);
        if (null != c) {
            return c;
        } else {
            throw new NullPointerException("Cannot retrieve the Role Based Strategy plugin configuration");
        }
    }

    public boolean isLogDangerousPermissions() {
        return logDangerousPermissions;
    }

    @DataBoundSetter
    public void setLogDangerousPermissions(boolean logDangerousPermissions) {
        this.logDangerousPermissions = logDangerousPermissions;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        final boolean newPermissive = json.getBoolean("logDangerousPermissions");
        return configure(newPermissive);
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }
}

