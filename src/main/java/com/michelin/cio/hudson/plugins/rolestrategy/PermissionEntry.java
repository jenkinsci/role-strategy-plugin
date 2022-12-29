/*
 * The MIT License
 *
 * Copyright (c) 2021 CloudBees, Inc.
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

package com.michelin.cio.hudson.plugins.rolestrategy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Combines sid with {@link AuthorizationType type}.
 */
public class PermissionEntry implements Comparable<PermissionEntry> {
  private final AuthorizationType type;
  private final String sid;

  @DataBoundConstructor
  public PermissionEntry(@NonNull AuthorizationType type, @NonNull String sid) {
    this.type = type;
    this.sid = sid;
  }

  public AuthorizationType getType() {
    return type;
  }

  public String getSid() {
    return sid;
  }

  /**
   * Utility method checking whether this entry applies based on whether we're looking for a principal.
   */
  protected boolean isApplicable(boolean principal) {
    if (getType() == AuthorizationType.EITHER) {
      return true;
    }
    return getType() == (principal ? AuthorizationType.USER : AuthorizationType.GROUP);
  }

  /**
   * Creates a {@code PermissionEntry} from a string.
   *
   * @param permissionEntryString String from which to create the entry
   * @return the PermissinoEntry
   */
  @Restricted(NoExternalUse.class)
  @CheckForNull
  public static PermissionEntry fromString(@NonNull String permissionEntryString) {
    Objects.requireNonNull(permissionEntryString);
    int idx = permissionEntryString.indexOf(':');
    if (idx < 0) {
      return null;
    }
    String typeString = permissionEntryString.substring(0, idx);
    AuthorizationType type;
    try {
      type = AuthorizationType.valueOf(typeString);
    } catch (RuntimeException ex) {
      return null;
    }
    String sid = permissionEntryString.substring(idx + 1);

    if (sid.isEmpty()) {
      return null;
    }

    return new PermissionEntry(type, sid);
  }

  public static PermissionEntry user(String sid) {
    return new PermissionEntry(AuthorizationType.USER, sid);
  }

  public static PermissionEntry group(String sid) {
    return new PermissionEntry(AuthorizationType.GROUP, sid);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PermissionEntry that = (PermissionEntry) o;
    return type == that.type && sid.equals(that.sid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, sid);
  }

  @Override
  public String toString() {
    return "PermissionEntry{"
        + "type=" + type
        + ", sid='" + sid + "'"
        + '}';
  }

  @Override
  public int compareTo(PermissionEntry o) {
    int sidCompare = this.sid.compareTo(o.sid);
    if (sidCompare == 0) {
      return this.type.compareTo(o.type);
    }
    return sidCompare;
  }
}
