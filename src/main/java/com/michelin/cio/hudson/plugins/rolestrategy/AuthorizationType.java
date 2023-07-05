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

/**
 * The type of object being granted authorization.
 */
public enum AuthorizationType {

  USER("User"),
  GROUP("Group"),
  /**
   * Either type is being granted permissions.
   * This is the legacy default.
   */
  EITHER("User/Group");

  private final String description;

  private AuthorizationType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  /**
   * The prefix used in the persistence of an permission entry.
   *
   * @return prefix
   */
  public String toPrefix() {
    if (this == AuthorizationType.EITHER) {
      return ""; // Same as legacy format
    }
    return this + ":";
  }
}
