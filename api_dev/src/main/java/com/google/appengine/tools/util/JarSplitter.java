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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Simple utility that splits a large jar file into one or more jar files that are each less than
 * the file size specified with --max_file_size.
 *
 *  This class strips out jar index files. It does not, however, ensure that resource or META-INF
 * files are directed to the appropriate jar file. It's unclear whether this will cause problems or
 * not.
 *
 *  UPDATE to the lack of clarity: It is now clear that this will cause problems. Some frameworks
 * (datanucleus in particular) make assumptions about the colocation of well-known files and the
 * manifest in the same jar. Splitting the jar violates these assumptions.
 *
 */
public class JarSplitter extends JarMaker {

  private static final String INDEX_FILE = "INDEX.LIST";

  private final File inputJar;
  private JarInputStream inputStream;
  private boolean replicateManifests;

  public JarSplitter(File inputJar,
      File outputDirectory,
      int maximumSize,
      boolean replicateManifests,
      int outputDigits,
      Set<String> excludes) {
    super(stripExtension(inputJar, EXT),
        outputDigits,
        outputDirectory,
        maximumSize,
        addIndexFile(excludes),
        false);
    this.inputJar = inputJar;
    this.replicateManifests = replicateManifests;
  }

  @Override
  public void run() throws IOException {
    inputStream = new JarInputStream(new BufferedInputStream(new FileInputStream(inputJar)));
    try {
      super.run();
    } finally {
      inputStream.close();
    }
  }

  private static String stripExtension(File file, String extension) {
    String name = file.getName();
    if (name.endsWith(extension)) {
      name = name.substring(0, name.length() - extension.length());
    }
    return name;
  }

  private static Set<String> addIndexFile(Set<String> excludes) {
    int numExcludes = (excludes == null ? 0 : excludes.size());
    Set<String> newSet = new HashSet<String>(numExcludes + 1);
    if (excludes != null) {
      newSet.addAll(excludes);
    }
    newSet.add(INDEX_FILE);
    return newSet;
  }


  @Override
  protected Manifest getManifest() {
    return replicateManifests ? inputStream.getManifest() : null;
  }

  @Override
  protected JarEntryData getNextJarEntry() throws IOException {
    JarEntry entry = inputStream.getNextJarEntry();
    if (entry == null) {
      return null;
    }
    JarEntry newEntry = new JarEntry(entry.getName());
    newEntry.setTime(entry.getTime());
    return new JarEntryData(newEntry, inputStream);
  }
}
