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

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link Glob} with children.
 *
 */
class BranchGlob extends AbstractGlob {
  private final Set<Glob> children;

  BranchGlob(String pattern) {
    super(pattern);
    this.children = new HashSet<Glob>();
  }

  public void addChild(Glob child) {
    if (child instanceof LeafGlob) {
      children.add(child);
    } else {
      BranchGlob branch = (BranchGlob) child;
      for (Glob realChild : branch.getChildren()) {
        children.add(realChild);
      }
    }
  }

  public Set<Glob> getChildren() {
    return children;
  }

  @Override
  public Object getProperty(String propertyName, ConflictResolver resolver) {
    return resolver.chooseValue(propertyName, children);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof BranchGlob) {
      BranchGlob glob = (BranchGlob) object;
      return super.equals(glob) && children.equals(glob.children);
    }
    return false;
  }

  @Override
  public String toString() {
    return pattern + children;
  }
}
