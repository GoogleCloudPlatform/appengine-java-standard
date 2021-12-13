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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a set of unordered and overlapping {@link Glob} objects
 * into an ordered list of {@link Glob} objects where each contains
 * all of the properties from all of the matching input {@link Glob}
 * objects.
 *
 * <p>For example, if two input globs are specified:
 * <ul>
 *  <li><code>/admin/*</code> (security=admin)
 *  <li><code>/*.png</code> (type=static)
 * <ul>
 * The output will be:
 * <ol>
 *  <li><code>/admin/*.png</code> (security=admin, type=static)
 *  <li><code>/admin/*</code> (security=admin)
 *  <li><code>/*.png</code> (type=static)
 * </ol>
 *
 * <p>This class is not responsible for resolving properties.
 * Instead, it just tracks the input {@link Glob} objects that
 * resulted in each output {@link Glob}.  A custom {@link
 * ConflictResolver} can be provided when looking up property values
 * to resolve conflicts between properties.
 *
 */
public class GlobIntersector {
  private final Collection<Glob> inputGlobs;

  public GlobIntersector() {
    inputGlobs = new ArrayList<Glob>();
  }

  public void addGlobs(Glob... globs) {
    for (Glob glob : globs) {
      inputGlobs.add(glob);
    }
  }

  public void addGlob(Glob glob) {
    inputGlobs.add(glob);
  }

  /**
   * Return an ordered list of {@link Glob} objects that matches the
   * same set of strings as the input globs, but in the appropriate
   * order and with the appropriate blending of properties from the
   * input globs to output globs.
   */
  public List<Glob> getIntersection() {
    // Use a LinkedHashSet so that the resulting order will be stable
    // across JDK releases.
    HashSet<Glob> results = new LinkedHashSet<Glob>();
    doIntersect(inputGlobs, results);
    List<Glob> resultList = new ArrayList<Glob>(results);
    reorderAndBlendChildren(resultList);
    removeExactDuplicates(resultList);
    return resultList;
  }

  private void removeExactDuplicates(Collection<Glob> results) {
    Set<String> newResults = new HashSet<String>();
    Iterator<Glob> i = results.iterator();
    while (i.hasNext()) {
      Glob glob = i.next();
      String pattern = glob.getPattern();
      if (newResults.contains(pattern)) {
        i.remove();
      } else {
        newResults.add(pattern);
      }
    }
  }

  private void reorderAndBlendChildren(List<Glob> globs) {
    int size = globs.size();
    for (int i = 0; i < size; i++) {
      for (int j = i + 1; j < size; j++) {
        // We're interested in a partial ordering of globs here.  The
        // only case we care about is if a glob (j) comes after a
        // glob (i) that matches all of the same strings that it does.
        // In this case we need to flip them around.
        if (globs.get(i).matchesAll(globs.get(j))) {
          Glob tmp = globs.get(i);
          globs.set(i, globs.get(j));
          globs.set(j, tmp);
        }

        // If a glob (i) comes before a glob (j) that matches all of
        // the same strings that it does, then we need to blend its
        // children up from j to i.  This ensures that a pattern like
        // a*b will include all of the properties of the a* and b*
        // patterns that come after it.
        if (globs.get(j).matchesAll(globs.get(i))) {
          if (globs.get(i) instanceof LeafGlob) {
            globs.set(i, GlobFactory.convertToBranch(globs.get(i)));
          }
          ((BranchGlob) globs.get(i)).addChild(globs.get(j));
        }
      }
    }
  }

  private void doIntersect(Collection<Glob> inputGlobs, HashSet<Glob> outputGlobs) {
    if (inputGlobs.size() <= 1) {
      outputGlobs.addAll(inputGlobs);
    } else if (inputGlobs.size() == 2) {
      Iterator<Glob> i = inputGlobs.iterator();
      doIntersectTwo(i.next(), i.next(), outputGlobs);
    } else {
      Set<Glob> globs = new LinkedHashSet<Glob>();
      Iterator<Glob> i = inputGlobs.iterator();
      globs.add(i.next());

      // It's too hard to do all of these at one time, so we divide
      // and conquer and merge the supplied patterns in one at a time.
      while (i.hasNext()) {
        Glob inputGlob = i.next();
        HashSet<Glob> newGlobs = new LinkedHashSet<Glob>();
        for (Glob glob : globs) {
          doIntersectTwo(inputGlob, glob, newGlobs);
        }
        globs = newGlobs;
      }
      outputGlobs.addAll(globs);
    }
  }

  private void doIntersectTwo(Glob inputGlob1, Glob inputGlob2, HashSet<Glob> outputGlobs) {
    String pattern1 = inputGlob1.getPattern();
    String pattern2 = inputGlob2.getPattern();

    // To simplify the work we do below, we first check to see if the
    // supplied globs share a common prefix excluding wildcards.
    //
    // For example:
    //   ab*d   becomes:  a | b*d
    //   a*cd             a | *cd
    String prefix = extractPrefix(pattern1, pattern2);
    if (prefix != null) {
      int prefixLength = prefix.length();
      // The prefix is guaranteed to exclude wildcards, so if they
      // don't match then we're done here.
      if (!prefix.equals(pattern2.substring(0, prefixLength))) {
        outputGlobs.add(inputGlob1);
        outputGlobs.add(inputGlob2);
      } else {
        // Strip off the prefix and recurse on the non-prefixed version.
        HashSet<Glob> newGlobs = new LinkedHashSet<Glob>();
        doIntersectTwo(
            GlobFactory.createChildGlob(pattern1.substring(prefixLength), inputGlob1),
            GlobFactory.createChildGlob(pattern2.substring(prefixLength), inputGlob2),
            newGlobs);
        // Now put the prefix back onto all of the returned globs.
        for (Glob newGlob : newGlobs) {
          outputGlobs.add(GlobFactory.createChildGlob(prefix + newGlob.getPattern(), newGlob));
        }
      }
      return;
    }

    // Now we do the same thing we did for prefixes with suffixes.
    //
    // For example:
    //   b*d   becomes:  b*d | d
    //   *cd             *cd | d
    String suffix = extractSuffix(pattern1, pattern2);
    if (suffix != null) {
      int suffixLength = suffix.length();
      // The suffix is guaranteed to exclude wildcards, so if they
      // don't match then we're done here.
      if (!suffix.equals(pattern2.substring(pattern2.length() - suffixLength))) {
        outputGlobs.add(inputGlob1);
        outputGlobs.add(inputGlob2);
      } else {
        // Strip off the suffix and recurse on the non-suffixed version.
        HashSet<Glob> newGlobs = new LinkedHashSet<Glob>();
        doIntersectTwo(GlobFactory.createChildGlob(
                           pattern1.substring(0, pattern1.length() - suffixLength),
                           inputGlob1),
                       GlobFactory.createChildGlob(
                           pattern2.substring(0, pattern2.length() - suffixLength),
                           inputGlob2),
                       newGlobs);
        // Now put the suffix back onto all of the returned globs.
        for (Glob newGlob : newGlobs) {
          outputGlobs.add(GlobFactory.createChildGlob(newGlob.getPattern() + suffix, newGlob));
        }
      }
      return;
    }

    // For example:
    //   abc*xyz   adds:  abc*ghi*xyz
    //   *ghi*
    if (pattern1.length() > 1 && pattern1.startsWith("*") && pattern1.endsWith("*")) {
      for (int star = pattern2.indexOf("*"); star != -1; star = pattern2.indexOf("*", star + 1)) {
        outputGlobs.add(GlobFactory.createChildGlob(
                            pattern2.substring(0, star) + pattern1 + pattern2.substring(star + 1),
                            inputGlob1, inputGlob2));
      }
    }
    // This is just the opposite of what we just did because we can't
    // be sure about the order.
    //
    // For example:
    //   *ghi*     adds:  abc*ghi*xyz
    //   abc*xyz
    if (pattern2.length() > 1 && pattern2.startsWith("*") && pattern2.endsWith("*")) {
      for (int star = pattern1.indexOf("*"); star != -1; star = pattern1.indexOf("*", star + 1)) {
        outputGlobs.add(GlobFactory.createChildGlob(
                            pattern1.substring(0, star) + pattern2 + pattern1.substring(star + 1),
                            inputGlob1, inputGlob2));
      }
    }

    // For example:
    //   abc*  adds:   abc*xyz
    //   *xyz
    if (pattern1.length() > 1 && pattern2.length() > 1 &&
        pattern1.endsWith("*") && pattern2.startsWith("*")) {
      String trimmedPattern1 = pattern1.substring(0, pattern1.length() - 1);
      outputGlobs.add(GlobFactory.createChildGlob(trimmedPattern1 + pattern2,
                                                  inputGlob1,
                                                  inputGlob2));

      // For example:
      //   abc*  adds:   abc
      //   *c
      if (inputGlob2.matchesAll(trimmedPattern1)) {
        outputGlobs.add(GlobFactory.createChildGlob(trimmedPattern1, inputGlob1, inputGlob2));
      }
    }
    // This is just the opposite of what we just did because we can't
    // be sure about the order.
    //
    // For example:
    //   *xyz
    //   abc*   adds:   abc*xyz
    if (pattern1.length() > 1 && pattern2.length() > 1 &&
        pattern1.startsWith("*") && pattern2.endsWith("*")) {
      String trimmedPattern2 = pattern2.substring(0, pattern2.length() - 1);
      outputGlobs.add(GlobFactory.createChildGlob(trimmedPattern2 + pattern1,
                                                  inputGlob1,
                                                  inputGlob2));

      // For example:
      //   *c    adds:   abc
      //   abc*
      if (inputGlob1.matchesAll(trimmedPattern2)) {
        outputGlobs.add(GlobFactory.createChildGlob(trimmedPattern2, inputGlob1, inputGlob2));
      }
    }

    // Now include the two original patterns.
    outputGlobs.add(inputGlob1);
    outputGlobs.add(inputGlob2);
  }

  private String extractPrefix(String pattern1, String pattern2) {
    int firstStar1 = pattern1.indexOf("*");
    int firstStar2 = pattern2.indexOf("*");
    if (firstStar1 != -1 || firstStar2 != -1) {
      if (firstStar1 == -1) {
        firstStar1 = pattern1.length();
      }
      if (firstStar2 == -1) {
        firstStar2 = pattern2.length();
      }
      int minStar = Math.min(firstStar1, firstStar2);
      if (minStar > 0) {
        return pattern1.substring(0, minStar);
      }
    }
    return null;
  }

  private String extractSuffix(String pattern1, String pattern2) {
    int lastStar1 = pattern1.lastIndexOf("*");
    int lastStar2 = pattern2.lastIndexOf("*");
    if (lastStar1 != -1 || lastStar2 != -1) {
      if (lastStar1 == -1) {
        lastStar1 = 0;
      }
      if (lastStar2 == -1) {
        lastStar2 = 0;
      }
      int fromEnd = Math.min(pattern1.length() - lastStar1 - 1, pattern2.length() - lastStar2 - 1);
      if (fromEnd > 0) {
        return pattern1.substring(pattern1.length() - fromEnd);
      }
    }
    return null;
  }
}
