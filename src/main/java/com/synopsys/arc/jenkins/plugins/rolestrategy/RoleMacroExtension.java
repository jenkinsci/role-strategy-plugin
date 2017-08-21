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

import com.synopsys.arc.jenkins.plugins.rolestrategy.macros.StubMacro;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;

/**
 * Extension for macro roles (automatic membership handling).
 *
 * @see UserMacroExtension
 * @author Oleg Nenashev
 * @since 2.1.0
 */
public abstract class RoleMacroExtension implements ExtensionPoint, IMacroExtension {

    private static final Map<String, RoleMacroExtension> NAME_CACHE =
            new ConcurrentHashMap<String, RoleMacroExtension>();

    private static void updateRegistry() {
        NAME_CACHE.clear();
        for (RoleMacroExtension ext : all()) {
            NAME_CACHE.put(ext.getName(), ext);
        }
    }

    @CheckForNull
    public static Macro getMacro(String unparsedMacroString) {
        //TODO: add macro cache
        try {
            return Macro.parse(unparsedMacroString);
        } catch (MacroException ex) {
            return null;
        }
    }

    public static RoleMacroExtension getMacroExtension(String macroName) {
        //TODO: the method is not thread-safe
        if (NAME_CACHE.isEmpty()) {
            updateRegistry();
        }
        RoleMacroExtension ext = NAME_CACHE.get(macroName);
        return ext != null ? ext : StubMacro.Instance;
    }

    /**
     * Get list of all registered {@link UserMacroExtension}s.
     *
     * @return List of {@link UserMacroExtension}s.
     */
    public static ExtensionList<RoleMacroExtension> all() {
        return ExtensionList.lookup(RoleMacroExtension.class);
    }
}
