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

import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Class, which stores global configuration of Role-Strategy security.
 * @author Oleg Nenashev <nenashev@synopsys.com>
 */
public class RoleStrategyProperties implements Describable<RoleStrategyProperties>, Serializable {
    /**Default value, which preserves legacy behavior*/
    public static final RoleStrategyProperties DEFAULT = new RoleStrategyProperties(false);
    
    boolean convertSidsToLowerCase;    

    @DataBoundConstructor
    public RoleStrategyProperties(boolean convertSidsToLowerCase) {
        this.convertSidsToLowerCase = convertSidsToLowerCase;
    }

    public boolean isConvertSidsToLowerCase() {
        return convertSidsToLowerCase;
    }

    @Override
    public Descriptor<RoleStrategyProperties> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    @Extension
    public static final class DescriptorImpl extends Descriptor<RoleStrategyProperties> {

        @Override
        public String getDisplayName() {
            return Messages.RoleStrategyProperties_DisplayName();
        }    
    }
}
