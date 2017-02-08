/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev, Synopsys Inc.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.AccessControlled;
import hudson.security.Permission;

/**
 * Interface for Role-based plug-in Macro extensions.
 * @see RoleMacroExtension
 * @see UserMacroExtension
 * @author Oleg Nenashev
 * @since 2.1.0
 */
public interface IMacroExtension {
    
    /**
     * Get name of the appropriate macro.
     * @return Name of the macro
     */
    String getName();      
    
    /**
     * Check if the macro extension is applicable to specified role type
     * @param roleType Type to be checked
     * @return {@code true} if the macro is applicable to the specified role type
     */
    //TODO: fix naming conventions
    @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "Old code, should be fixed later")
    boolean IsApplicable(RoleType roleType);
    
    /**
     * Returns description of the macro (including parameters).
     * @return Description of the macro
     */
    String getDescription();
    
    /**
     * Check if user belongs to specified Macro
     * @param sid SID to be checked
     * @param p Permission
     * @param type Type of the role to be checked
     * @param item Item
     * @param macro Macro with parameters
     * @return True if user satisfies macro's requirements 
     */
    boolean hasPermission(String sid, Permission p, RoleType type, AccessControlled item, Macro macro);
}
