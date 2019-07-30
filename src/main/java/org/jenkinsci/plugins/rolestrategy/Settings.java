/*
 * The MIT License
 *
 * Copyright (c) 2016 Oleg Nenashev.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Class for managing the strategy.
 * This class will be converted to additional UI at some point.
 * Now it stores System properties only.
 * @author Oleg Nenashev
 * @since 2.3.1
 */
@Restricted(NoExternalUse.class)
public class Settings {

    /**
     * Defines maximum size of the User details cache.
     * This cache is being used when {@link #TREAT_USER_AUTHORITIES_AS_ROLES} is enabled.
     * Changing of this option requires Jenkins restart.
     * @since 2.3.1
     */
    public static final int USER_DETAILS_CACHE_MAX_SIZE =
            Integer.getInteger(Settings.class.getName() + ".userDetailsCacheMaxSize", 100);

    /**
     * Defines lifetime of entries in the User details cache.
     * This cache is being used when {@link #TREAT_USER_AUTHORITIES_AS_ROLES} is enabled.
     * Changing of this option requires Jenkins restart.
     * @since 2.3.1
     */
    public static final int USER_DETAILS_CACHE_EXPIRATION_TIME_SEC =
            Integer.getInteger(Settings.class.getName() + ".userDetailsCacheExpircationTimeSec", 60);

    /**
     * Enabling processing of User Authorities.
     * Alters the behavior of
     * {@link RoleMap#hasPermission(java.lang.String, hudson.security.Permission, com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType, hudson.security.AccessControlled)}.
     * Since 2.3.0 this value was {@code true}, but it has been switched due to the performance reasons.
     * The behavior can be reverted (even dynamically via System Groovy Script).
     * @since 2.3.1
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "We want to be it modifyable on the flight")
    public static boolean TREAT_USER_AUTHORITIES_AS_ROLES =
            Boolean.getBoolean(Settings.class.getName() + ".treatUserAuthoritiesAsRoles");

    private Settings() {}


}
