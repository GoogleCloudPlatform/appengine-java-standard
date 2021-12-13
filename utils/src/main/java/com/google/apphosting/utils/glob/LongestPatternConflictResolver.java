/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.utils.glob;

import java.util.Set;

/**
 * Chooses the value with the largest pattern.  This is basically
 * equivalent to the resolution strategy supplied in SVN.11.1 of the
 * Java Servlet Specification.
 *
 */
public class LongestPatternConflictResolver implements ConflictResolver {
  @Override
  public Object chooseValue(String propertyName, Set<Glob> globs) {
    Glob largestGlob = null;
    Object largestObject = null;
    for (Glob glob : globs) {
      Object globObject = glob.getProperty(propertyName, this);
      if (globObject != null) {
        if (largestGlob == null || isMoreSpecificThan(glob, largestGlob)) {
          largestGlob = glob;
          largestObject = globObject;
        }
      }
    }
    return largestObject;
  }

  private boolean isMoreSpecificThan(Glob glob1, Glob glob2) {
    // If either is a literal, it's more specific than the other.
    if (glob1.isLiteral() && !glob2.isLiteral()) {
      return true;
    }
    if (!glob1.isLiteral() && glob2.isLiteral()) {
      return false;
    }

    // Otherwise use the pattern length.
    return glob1.getPattern().length() > glob2.getPattern().length();
  }
}
