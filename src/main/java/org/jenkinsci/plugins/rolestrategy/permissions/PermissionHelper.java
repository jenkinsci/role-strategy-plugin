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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.PluginManager;
import hudson.security.Permission;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Helper methods for dangerous permission handling.
 * @author Oleg Nenashev
 */
@Restricted(NoExternalUse.class)
public class PermissionHelper {

    /**
     * List of the dangerous permissions, which need to be suppressed by the plugin.
     */
    @Restricted(NoExternalUse.class)
    public static final Set<Permission> DANGEROUS_PERMISSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            Jenkins.RUN_SCRIPTS,
            PluginManager.CONFIGURE_UPDATECENTER,
            PluginManager.UPLOAD_PLUGINS)));

    private PermissionHelper() {
        // Cannot be constructed
    }

    /**
     * Convert a set of string to a collection of permissions.
     * Dangerous permissions will be checked.
     * @param permissionIds Permission IDs
     * @throws SecurityException Permission is rejected, because it is dangerous.
     * @return Created set of permissions
     */
    @NonNull
    public static Set<Permission> fromStrings(@CheckForNull Collection<String> permissionIds) throws SecurityException {
        if (permissionIds == null) {
            return Collections.emptySet();
        }

        HashSet<Permission> res = new HashSet<>(permissionIds.size());
        for (String permission : permissionIds) {
            final Permission p = Permission.fromId(permission);
            if (p == null) {
                // throw IllegalArgumentException?
                continue;
            }
            if (isDangerous(p)) {
                throw new SecurityException("Rejected dangerous permission: " + permission);
            }
            res.add(p);
        }
        return res;
    }

    /**
     * Check if the permissions is dangerous.
     * @param p Permission
     * @return {@code true} if the permission is considered as dangerous.
     */
    public static boolean isDangerous(@NonNull Permission p) {
        return DANGEROUS_PERMISSIONS.contains(p);
    }
}
