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

import java.util.regex.Pattern;

/**
 * Abstract parent for {@link LeafGlob} and {@link BranchGlob}.
 *
 */
abstract class AbstractGlob implements Glob {
  /**
   * This is just an arbitrary string that is unlikely to overlap with
   * the user's URLs.  We replace any wildcards in a target regular
   * expression to see if another regular expression matches every
   * possible pattern.  Think of this as the most unlikely string that
   * could match a wildcard.
   */
  private static final String SENTINEL_VALUE = "_-= (S3Nt!N\\$L) =-_";

  protected final String pattern;

  public AbstractGlob(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public String getPattern() {
    return pattern;
  }

  @Override
  public boolean isLiteral() {
    return pattern.indexOf("*") == -1;
  }

  @Override
  public Pattern getRegularExpression() {
    String regex = pattern;
    // First we escape non-literals with a backslash (note that
    // Pattern.quote is sneaky and not useful for this).
    regex = regex.replaceAll("([^A-Za-z0-9\\-_/])", "\\\\$1");
    // Now we replace any literal star character with .* so it matches
    // zero or more characters.
    regex = regex.replaceAll("\\\\\\*", ".*");
    return Pattern.compile(regex);
  }

  @Override
  public boolean matches(String string) {
    return getRegularExpression().matcher(string).matches();
  }

  @Override
  public boolean matchesAll(Glob glob) {
    return matchesAll(glob.getPattern());
  }

  @Override
  public boolean matchesAll(String pattern) {
    return matches(pattern.replaceAll("\\*", SENTINEL_VALUE));
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof AbstractGlob) {
      AbstractGlob glob = (AbstractGlob) object;
      return pattern.equals(glob.pattern);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return pattern.hashCode();
  }

  @Override
  public String toString() {
    return pattern;
  }
}
