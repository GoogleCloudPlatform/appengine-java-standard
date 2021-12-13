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

package com.google.appengine.tools;

import com.google.appengine.tools.util.JarSplitter;
import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Simple utility that splits a large jar file into one or more jar
 * files that are each less than the file size specified with
 * --max_file_size.
 *
 * This class strips out jar index files.  It does not, however,
 * ensure that resource or META-INF files are directed to the
 * appropriate jar file.  It's unclear whether this will cause
 * problems or not.
 *
 * UPDATE to the lack of clarity:  It is now clear that this will
 * cause problems.  Some frameworks (datanucleus in particular)
 * make assumptions about the colocation of well-known files
 * and the manifest in the same jar.  Splitting the jar
 * violates these assumptions.
 *
 * Usage:
 * <pre>
 *   JarSplitter --input_jar=MyProject_deploy.jar \
 *               --output_directory=./lib \
 *               --max_file_size=10000000 \
 *               --replicate_manifests=false \
 *               --exclude_suffixes=.so,.dll
 *
 */
public class JarSplitterMain {
  @FlagSpec(
      help = "The maximum file for each output jar file.",
      name = "max_file_size",
      altName = "MAX_FILE_SIZE")
  public static final Flag<Integer> MAX_FILE_SIZE = Flag.value(10 * 1024 * 1024);

  @FlagSpec(help = "The input jar file.", name = "input_jar", altName = "INPUT_JAR")
  public static final Flag<String> INPUT_JAR = Flag.value("input.jar");

  @FlagSpec(
      help = "The directory where output jars will be written.",
      name = "output_directory",
      altName = "OUTPUT_DIRECTORY")
  public static final Flag<String> OUTPUT_DIRECTORY = Flag.value(".");

  @FlagSpec(
      help = "The number of digits used for the output files.",
      name = "output_digits",
      altName = "OUTPUT_DIGITS")
  public static final Flag<Integer> OUTPUT_DIGITS = Flag.value(4);

  @FlagSpec(
      help =
          "Whether or not manifests are replicated across all split jars.  If false, manifests are"
              + " ignored.",
      name = "replicate_manifests",
      altName = "REPLICATE_MANIFESTS")
  public static final Flag<Boolean> REPLICATE_MANIFESTS = Flag.value(Boolean.FALSE);

  @FlagSpec(
      help = "A set of filename suffixes that will be excluded from all jars",
      name = "exclude_suffixes",
      altName = "EXCLUDE_SUFFIXES")
  public static final Flag<Set<String>> EXCLUDE_SUFFIXES = Flag.stringSet();

  public static void main(String[] args) throws IOException {
    args = Flags.parseAndReturnLeftovers(args);

    JarSplitter splitter = new JarSplitter(new File(INPUT_JAR.get()),
                                           new File(OUTPUT_DIRECTORY.get()),
                                           MAX_FILE_SIZE.get(),
                                           REPLICATE_MANIFESTS.get(),
                                           OUTPUT_DIGITS.get(),
                                           EXCLUDE_SUFFIXES.get());
    splitter.run();
  }
}
