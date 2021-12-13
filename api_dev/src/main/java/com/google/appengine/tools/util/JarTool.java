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

package com.google.appengine.tools.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

/**
 * A utility for building multiple jar files, each of size less than a specified maximum, from a
 * directory.
 * <p>
 * Usage: Construct an instance and then invoke the method {@link #run()}.
 * <p>
 * A note on ordering: The files from the input directory will be split across multiple jar files.
 * In order to facilitate being able to find a file from the input directory in the set of output
 * jar files, the input files are added to the jar files in package order. For example:
 * <ol>
 * <li>{@code A.class}
 * <li>{@code B.class}
 * <li>{@code ant/A.class}
 * <li>{@code ant/B.class}
 * <li>{@code ant/axe/A.class}
 * <li>{@code ant/axe/B.class}
 * <li>{@code ball/A.class}
 * </ol>
 *
 */
public class JarTool extends JarMaker {

  private Deque<File> stack = new ArrayDeque<File>(1000);
  private int prefixLength;

  /**
   * Constructor
   *
   * @param baseName The base name of the emitted jar files. The file name will also include a
   *        suffix containing four numerical digits.
   * @param inputDirectory The directory whose contents should be included in the emitted jar files.
   * @param outputDirectory The directory into which to emit the jar files. This directory will be
   *        created if it does not exist.
   * @param maximumSize The maximum size of a jar file that should be emitted, in bytes. Note that
   *        the actual size of the jar files may be less than this value due to compression. The
   *        compression ratio is not specified. (In practice it is frequently 50%).
   * @param excludes A set file-name suffixes. If this is not {@code null} then {@link JarEntry
   *        JarEntries} whose path includes one of these suffixes will be excluded from the
   *        generated jar files.
   */
  public JarTool(String baseName, File inputDirectory, File outputDirectory, int maximumSize,
      Set<String> excludes) {
    super(baseName, 4, outputDirectory, maximumSize, excludes, true);
    if (!inputDirectory.isDirectory()) {
      throw new IllegalArgumentException(inputDirectory.getPath() + " is not a directory.");
    }
    stack.push(inputDirectory);
    prefixLength = getZipCompatiblePath(inputDirectory).length();
  }

  @Override
  protected JarEntryData getNextJarEntry() throws IOException {
    while (true) {
      File nextFile = stack.pollFirst();
      if (nextFile == null) {
        return null;
      }
      if (nextFile.isDirectory()) {
        for (File f : sortedListing(nextFile)) {
          if (f.isDirectory() || shouldIncludeFile(f.getName())) {
            stack.push(f);
          }
        }
      } else {
        JarEntry jarEntry = new JarEntry(getZipCompatiblePath(nextFile).substring(prefixLength));
        // Control entry timestamp, so that jar content would not change if the entry is the same.
        jarEntry.setTime(0L);
        InputStream inputStream = new FileInputStream(nextFile);
        return new JarEntryData(jarEntry, inputStream);
      }
    }
  }

  private static String getZipCompatiblePath(File f) {
    String path = f.getPath();
    path = path.replace("\\", "/");
    if (f.isDirectory() && !path.endsWith("/")) {
      path += "/";
    }
    return path;
  }

  @Override
  protected Manifest getManifest() {
    return null;
  }

  /**
   * Returns the listing of a directory sorted so that directories precede files, and files and
   * directories are sorted <b>decreasing</b> by name. The output from this method will be pushed
   * onto a FIFO stack and visited in the reverse order. The result is that we will be visiting a
   * tree of Java classes in package order.
   */
  private static File[] sortedListing(File dir) {
    File[] contents = dir.listFiles();
    Comparator<File> comparator = new Comparator<File>() {

      @Override
      public int compare(File f1, File f2) {
        if (f1.isDirectory() && f2.isFile()) {
          return -1;
        }
        if (f1.isFile() && f2.isDirectory()) {
          return 1;
        }
        // Two files and two directories are compared
        // based on their names. We are sorting in
        // decreasing order.
        return f2.getName().compareTo(f1.getName());
      }

    };
    Arrays.sort(contents, comparator);
    return contents;
  }
}
