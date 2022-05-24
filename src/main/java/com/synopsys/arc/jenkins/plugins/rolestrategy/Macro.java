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

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;

/**
 * Macro representation for roles and users.
 * Implements following format:
 *  {@code @macroId[:index][(parameter1, parameter2, ...)]},
 * <ul>
 *    <li>macroId - name of the macro. Supports alphanumeric symbols</li>
 *    <li>index   - optional integer, which allow to duplicate macro calls</li>
 *    <li>parameters - optional set of strings. each parameter should be string without quotes</li>
 * </ul>
 *
 * TODO: Macro parameters (ex, multiple usage of macro)
 * @since 2.1.0
 * @author Oleg Nenashev, Synopsys Inc.
 */
public class Macro {
    public final static String MACRO_PREFIX = "@";
    private final static String PARAMS_LEFT_BORDER = "(";
    private final static String PARAMS_RIGHT_BORDER = ")";
    private final static String PARAMS_DELIMITER = "\\\"*,\\\"*";
    private final static String INDEX_DELIMITER = ":";
    private final static int DEFAULT_MACRO_ID = Integer.MIN_VALUE;

    private final String name;
    private final String dispName;
    private final int index;

    //TODO: rework to list/set?
    @NonNull
    private final String[] parameters;

    public Macro(String name, Integer index, String[] parameters) {
        this.name = name;
        this.dispName = MACRO_PREFIX + name;
        this.index = (index == null) ? DEFAULT_MACRO_ID : index;
        this.parameters = parameters != null ? Arrays.copyOf(parameters, parameters.length) : new String[0];
    }

    /**
     * Get name of the macro.
     * @return Name of the macro (without prefix)
     */
    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return dispName;
    }

    public int getIndex() {
        return index;
    }

    public boolean hasIndex() {
        return index != DEFAULT_MACRO_ID;
    }

    public String[] getParameters() {
        return Arrays.copyOf(parameters, parameters.length);
    }

    public boolean hasParameters() {
        return parameters.length != 0;
    }

    @Override
    public String toString() {
        StringBuilder bldr = new StringBuilder(dispName);
        if (hasIndex()) {
            bldr.append(":");
            bldr.append(index);
        }

        if (hasParameters()) {
            bldr.append("(");
            bldr.append(parameters[0]);
            for (int i=1;i<parameters.length;i++) {
                bldr.append(",");
                bldr.append(parameters[i]);
            }
            bldr.append(")");
        }

        return bldr.toString();
    }

    /**
     * Check if role is a macro.
     * @param role Role to be checked
     * @return true if role meets macro criteria
     */
    public static boolean isMacro(Role role) {
        return isMacro(role.getName());
    }

    public static boolean isMacro(String macroString) {
        return macroString.startsWith(MACRO_PREFIX);
    }

    /**
     * @deprecated Use {@link #parse(java.lang.String)}
     */
    @Deprecated
    @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "deprecated, just for API compatibility")
    public static Macro Parse(String macroString) throws MacroException {
        return parse(macroString);
    }

    /**
     * Parse macro from string
     * @param macroString - macro string
     * @return Macro instance
     * @throws MacroException - Parse error
     * @since 2.3.0
     */
    public static Macro parse(String macroString) throws MacroException {
        if (!isMacro(macroString)) {
            throw new MacroException(MacroExceptionCode.Not_Macro,
                    "Can't parse macro: Macro String should start from " + MACRO_PREFIX);
        }

        int leftBorder = macroString.indexOf(PARAMS_LEFT_BORDER);
        int rightBorder = macroString.lastIndexOf(PARAMS_RIGHT_BORDER);
        boolean hasParams = checkBorders(macroString, leftBorder, rightBorder);

        // Get macroName and id
        String macroIdStr = hasParams ? macroString.substring(0, leftBorder) : macroString;
        String[] macroIdItems = macroIdStr.split(INDEX_DELIMITER);
        if (macroIdItems.length > 2) {
            throw new MacroException(MacroExceptionCode.WrongFormat,
                    "Macro string should contain only one '"+INDEX_DELIMITER+"' delimiter");
        }

        // Macro name
        String macroName = macroIdItems[0].substring(MACRO_PREFIX.length());
        if (macroName.isEmpty()) {
            throw new MacroException(MacroExceptionCode.WrongFormat, "Macro name is empty");
        }

        // Macro id
        int macroId = DEFAULT_MACRO_ID;
        if (macroIdItems.length == 2) {
            try {
                macroId = Integer.parseInt(macroIdItems[1]);
            }
            catch(NumberFormatException ex) {
                throw new MacroException(MacroExceptionCode.WrongFormat,
                        "Can't parse int from "+macroIdItems[1]+": "+ex.getMessage());
            }
        }

        // Parse parameters
        String[] params = null;
        if (hasParams) {
            String paramsStr = macroString.substring(leftBorder+1, rightBorder);
            params = paramsStr.split(PARAMS_DELIMITER);
        }

        return new Macro(macroName, macroId, params);
    }

    private static boolean checkBorders(String macroStr, int leftBorder, int rightBorder)
            throws MacroException
    {
        if (leftBorder==-1 || rightBorder==-1)
        {
            if (leftBorder == rightBorder) {
                return false; // no borders
            }
            String missingBorder = (leftBorder == -1) ? "left" : "right";
            throw new MacroException(MacroExceptionCode.WrongFormat, "Missing border: "+missingBorder);
        }

        // Check ending
        if (rightBorder != -1 && !macroStr.endsWith(PARAMS_RIGHT_BORDER))
        {
            throw new MacroException(MacroExceptionCode.WrongFormat,
                    "Parametrized Macro should end with '"+PARAMS_RIGHT_BORDER+"'");
        }

        // Check duplicated borders
        if (leftBorder != macroStr.lastIndexOf(PARAMS_LEFT_BORDER)) {
            throw new MacroException(MacroExceptionCode.WrongFormat,
                    "Duplicated left border ('"+PARAMS_LEFT_BORDER+"' symbol)");
        }
        if (rightBorder != macroStr.indexOf(PARAMS_RIGHT_BORDER)) {
            throw new MacroException(MacroExceptionCode.WrongFormat,
                    "Duplicated right border ('"+PARAMS_RIGHT_BORDER+"' symbol)");
        }

        // Check quatas
        if (macroStr.contains("\"")) {
            throw new MacroException(MacroExceptionCode.WrongFormat,
                    "Double quotes aren't supported");
        }
        if (macroStr.contains("'")) {
            throw new MacroException(MacroExceptionCode.WrongFormat,
                    "Single quotes aren't supported");
        }

        return true;
    }
}
