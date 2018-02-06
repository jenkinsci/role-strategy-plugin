/*
 * The MIT License
 *
 * Copyright (c) 2017 Oleg Nenashev.
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

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Shows warnings about the Dangerous permissions.
 * It will be activated only in the {@link DangerousPermissionHandlingMode#UNDEFINED}.
 * @author Oleg Nenashev
 */
@Extension
@Restricted(NoExternalUse.class)
public class DangerousPermissionAdministrativeMonitor extends AdministrativeMonitor {

    @Override
    public boolean isEnabled() {
        return DangerousPermissionHandlingMode.getCurrent() == DangerousPermissionHandlingMode.UNDEFINED;
    }
 
    @Override
    public boolean isActivated() {
        
        // We care only about the undefined mode
        if (DangerousPermissionHandlingMode.getCurrent() != DangerousPermissionHandlingMode.UNDEFINED) {
            return false;
        }
        
        RoleBasedAuthorizationStrategy roleStrategy = RoleBasedAuthorizationStrategy.getInstance();
        if (roleStrategy == null) {
            // Disabled, nothing to do here
            return false;
        }
        
        final String report = PermissionHelper.reportDangerousPermissions(roleStrategy);
        return report != null;
    }

    @Override
    public String getDisplayName() {
        return "Role Strategy. Dangerous permissions";
    }
    
    @CheckForNull
    public String getReport() {
        RoleBasedAuthorizationStrategy roleStrategy = RoleBasedAuthorizationStrategy.getInstance();
        if (roleStrategy == null) {
            // Disabled, nothing to do here
            return null;
        }
        return PermissionHelper.reportDangerousPermissions(roleStrategy);
    }
    
    @CheckForNull
    public static DangerousPermissionAdministrativeMonitor getInstance() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        return j.getExtensionList(AdministrativeMonitor.class).get(DangerousPermissionAdministrativeMonitor.class);
    }
}
