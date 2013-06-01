/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
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

import com.michelin.cio.hudson.plugins.rolestrategy.Role;

/**
 * Macro for roles and users.
 * Implements following format.
 * 
 * @todo Macro parameters (ex, multiple usage of macro)
 * @since 2.1.0
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 */
public class Macro {
    public final static String MACRO_PREFIX = "@";
    
    private String name;
    private String dispName;
    
    private Macro(String name)
    {
        this.name = name;
        this.dispName = MACRO_PREFIX + name;
    }
    
    /**
     * Get name of the macro.
     * @return Name of the macro (without prefix)
     */
    public String getName()
    {
        return name;
    }
    
    public String getDisplayName()
    {
        return dispName;
    }

    @Override
    public String toString() {
        return dispName;
    }
   
    /**
     * Check if role is a macro
     * @param role Role to be checked
     * @return true if role meets macro criteria
     */
    public static boolean isMacro(Role role)
    {
        return isMacro(role.getName());
    }
    
    public static boolean isMacro(String macroString)
    {
        return macroString.startsWith(MACRO_PREFIX);
    }
    
    /**
     * Parse macro from string
     * @param macroString - macro string
     * @return Macro instance
     * @throws MacroException - Parse error 
     */
    public static Macro Parse(String macroString)
            throws MacroException
    {        
        if (!isMacro(macroString)) 
        {
            throw new MacroException(MacroExceptionCode.Not_Macro, 
               "Can't parse macro: Macro String should start from "+MACRO_PREFIX);
        }
        
        String macroName = macroString.substring(MACRO_PREFIX.length());
        return new Macro(macroName);
    }
}