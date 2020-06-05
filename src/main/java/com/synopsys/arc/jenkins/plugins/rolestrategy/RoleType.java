/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev, Synopsys Inc..
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
package com.synopsys.arc.jenkins.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Enumeration wrapper for {@link RoleBasedAuthorizationStrategy}'s items.
 * @author Oleg Nenashev
 * @since 2.1.0
 */
public enum RoleType {

    Global,
    Project,
    Slave;

    /**
     * @deprecated Naming convention violation, use {@link #fromString(java.lang.String)}.
     */
    @Deprecated
    @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "deprecated, just for API compatibility")
    public static RoleType FromString(String roleName) {
        return fromString(roleName);
    }

    /**
     * Get Role Type for {@link RoleBasedAuthorizationStrategy}'s item
     *
     * @param roleName String representation of
     * {@link RoleBasedAuthorizationStrategy}'s item
     * @return Appropriate row type
     * @throws IllegalArgumentException Invalid roleName
     * @since 2.3.0
     */
    public static RoleType fromString(String roleName) {
        if (roleName.equals(RoleBasedAuthorizationStrategy.GLOBAL)) {
            return Global;
        }

        if (roleName.equals(RoleBasedAuthorizationStrategy.PROJECT)) {
            return Project;
        }

        if (roleName.equals(RoleBasedAuthorizationStrategy.SLAVE)) {
            return Slave;
        }

        throw new java.lang.IllegalArgumentException("Unexpected roleName=" + roleName);
    }

    /**
     * Converts role to the legacy {@link RoleBasedAuthorizationStrategy}'s
     * string.
     * @return {@link RoleBasedAuthorizationStrategy}'s string
     */
    public String getStringType() {
        switch (this) {
            case Global:
                return RoleBasedAuthorizationStrategy.GLOBAL;
            case Project:
                return RoleBasedAuthorizationStrategy.PROJECT;
            case Slave:
                return RoleBasedAuthorizationStrategy.SLAVE;
            default:
                throw new java.lang.IllegalArgumentException("Unsupported Role: " + this);
        }
    }
}
