/*
 * The MIT License
 *
 * Copyright 2022 Markus Winter
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

package com.synopsys.arc.jenkins.plugins.rolestrategy.macros;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.synopsys.arc.jenkins.plugins.rolestrategy.Macro;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleMacroExtension;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.security.AccessControlled;
import hudson.security.Permission;

/**
 * Applies permissions to folders only.
 *
 */
@Extension(optional = true)
public class FolderMacro extends RoleMacroExtension {

  @Override
  public String getName() {
    return "Folder";
  }

  // TODO: fix naming conventions
  @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "Old code, should be fixed later")
  @Override
  public boolean IsApplicable(RoleType roleType) {
    return roleType == RoleType.Project;
  }

  @Override
  public boolean hasPermission(PermissionEntry sid, Permission p, RoleType type, AccessControlled item, Macro macro) {
    if (AbstractFolder.class.isAssignableFrom(item.getClass())) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public String getDescription() {
    return "Filters out everything that is not a folder.";
  }
}
