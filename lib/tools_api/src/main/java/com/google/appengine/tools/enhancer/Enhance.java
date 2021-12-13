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

package com.google.appengine.tools.enhancer;

import com.google.appengine.tools.info.SdkImplInfo;
import com.google.appengine.tools.util.Logging;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The command-line interface for ORM enhancing. Usage: 
 * <pre>
 * java -cp [classpath]
 *   com.google.appengine.tools.enhancer.Enhance [options] [jdo-files] [class-files]
 * where options can be
 *   -persistenceUnit [persistence-unit-name] : Name of a "persistence-unit" containing the classes
 *                                              to enhance
 *   -d [target-dir-name] : Write the enhanced classes to the specified directory
 *   -api [api-name] : Name of the API we are enhancing for (JDO, JPA). Default is JDO
 *   -enhancerName [name] : Name of the ClassEnhancer to use. Options ASM
 *   -checkonly : Just check the classes for enhancement status
 *   -v : verbose output
 *   -enhancerVersion [version] : The version of the DataNucleus enhancer to use, where version
 *                                corresponds to a directory under lib/opt/user/datanucleus in the
 *                                sdk.
 *
 * where classpath must contain the following
 *   - your classes
 *   - your meta-data files 
 * <pre>
 *
 */
public class Enhance {

  static final String DATANUCLEUS_VERSION_ARG = "-enhancerVersion";

  public static void main(String[] args) {
    Logging.initializeLogging();
    new Enhance(args);
  }

  public Enhance(String[] args) {
    PrintWriter writer;
    File logFile;

    try {
      logFile = File.createTempFile("enhance", ".log");
      writer = new PrintWriter(new FileWriter(logFile), true);
    } catch (IOException e) {
      throw new RuntimeException("Unable to enable logging.", e);
    }

    Set<URL> targets = getEnhanceTargets(writer);
    Enhancer enhancer = new Enhancer();
    enhancer.setTargets(targets);
    args = processArgs(enhancer, args);
    enhancer.setArgs(args);
    enhancer.execute();
  }

  /**
   * Some of the {@code args} are destined for us (App Engine) and some of the
   * args are destined for DataNucleus. This method processes the args that
   * are for us and returns a version of the provided array with the processed
   * args removed.
   */
  static String[] processArgs(Enhancer enhancer, String[] args) {
    List<String> processed = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("-") || !args[i].equals(DATANUCLEUS_VERSION_ARG)) {
        processed.add(args[i]);
        continue;
      }
      // Current argument is the datanucleus id so the next argument is the
      // value.
      if (++i == args.length) {
        throw new IllegalArgumentException(
            String.format("Missing value for option %s", DATANUCLEUS_VERSION_ARG));
      }
      enhancer.setDatanucleusVersion(args[i]);
    }
    return processed.toArray(new String[processed.size()]);
  }

  /**
   * We assume that every URL on our classpath is an enhancer target. This is ugly, but it's how
   * DataNucleus does it and is currently the path of least resistance.
   */
  @VisibleForTesting
  @SuppressWarnings("URLEqualsHashCode")
  static Set<URL> getEnhanceTargets(PrintWriter writer) {
    Set<URL> urls = new LinkedHashSet<>();
    URL toolsJar = SdkImplInfo.getToolsApiJar();
    String paths = System.getProperty("java.class.path");
    for (String path : paths.split(File.pathSeparator)) {
      try {
        URL url = new File(path).toURI().toURL();
        if (!url.sameFile(toolsJar)) {
          urls.add(url);
        }
      } catch (MalformedURLException ex) {
        System.out.println("Encountered a problem: " + ex.getMessage());
        ex.printStackTrace(writer);
        System.exit(1);
      }
    }
    return urls;
  }
}
