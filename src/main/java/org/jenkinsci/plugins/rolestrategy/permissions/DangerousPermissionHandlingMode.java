/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
package org.jenkinsci.plugins.rolestrategy.permissions;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nonnull;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Defines the dangerous permission management logic.
 * @author Oleg Nenashev
 * @since TODO
 */
public enum DangerousPermissionHandlingMode {
    
    /**
     * Dangerous permissions are explicitly disabled.
     * They will be hidden from UI, and the settings will be ignored.
     * {@link DangerousPermissionAdministrativeMonitor} will be disabled as well.
     */
    DISABLED,
    /**
     * Dangerous permissions are explicitly enabled.
     * In such case they will be shown and enabled, and the {@link DangerousPermissionAdministrativeMonitor} will be suppressed.
     * @deprecated Use on your own risk, compatibility mode
     */
    @Deprecated
    ENABLED,
    /**
     * The behavior is up to the global settings and the migration logic.
     * By default the permissions will be blocked, but there will be an administrative warning if any permission is set.
     * 
     */
    UNDEFINED;
    
    @Restricted(NoExternalUse.class)
    public static final String PROPERTY_NAME = DangerousPermissionHandlingMode.class.getName() + ".enableDangerousPermissions";
    
    @Nonnull
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_REFACTORED_TO_BE_FINAL", justification = "Groovy script console access")
    public static /* allow script access */ DangerousPermissionHandlingMode CURRENT;

    static {
        String str = System.getProperty(PROPERTY_NAME);
        if (StringUtils.isBlank(str)) {
            CURRENT = UNDEFINED;
        } else {
            CURRENT = Boolean.parseBoolean(str) ? ENABLED : DISABLED;
        }
    }

    /**
     * Retrieves the current mode.
     * @return Current dangerous permission handling mode.
     */
    @Nonnull
    public static DangerousPermissionHandlingMode getCurrent() {
        return CURRENT;
    }
}
