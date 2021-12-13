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

import java.util.HashMap;
import java.util.Map;

/**
 * Creates {@link Glob} objects.
 *
 */
public class GlobFactory {
  public static Glob createGlob(String pattern) {
    return new LeafGlob(pattern);
  }

  public static Glob createGlob(String pattern, String propertyName, Object value) {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(propertyName, value);
    return createGlob(pattern, properties);
  }

  public static Glob createGlob(String pattern, Map<String, Object> properties) {
    return new LeafGlob(pattern, properties);
  }

  public static Glob createChildGlob(String pattern, Glob... children) {
    BranchGlob parent = new BranchGlob(pattern);
    for (Glob child : children) {
      parent.addChild(child);
    }
    return parent;
  }

  public static Glob createChildGlob(String pattern, Glob child) {
    BranchGlob parent = new BranchGlob(pattern);
    parent.addChild(child);
    return parent;
  }

  public static Glob convertToBranch(Glob leafGlob) {
    return createChildGlob(leafGlob.getPattern(), leafGlob);
  }
}
