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

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Set;

/**
 * Executes ORM enhancement against jars and class files. 
 * 
 */
public class Enhancer {

  static final String DEFAULT_ENHANCER_VERSION = "v1";

  private static final String DATANUCLEUS_ENHANCER_CLASS
      = "org.datanucleus.enhancer.DataNucleusEnhancer";

  private Set<URL> enhanceTargets;
  private String[] args;
  private String datanucleusVersion = DEFAULT_ENHANCER_VERSION;

  /**
   * Sets the class files and jar files which will be enhanced. 
   * 
   * @param enhanceTargets must not be null
   */
  public void setTargets(Set<URL> enhanceTargets) {
    this.enhanceTargets = enhanceTargets;
  }

  /**
   * Sets arguments to be passed to the underlying enhancer.
   *  
   * @param args must not be null
   */
  public void setArgs(String[] args) {
    this.args = args;
  }

  /**
   * Sets the version of datanucleus to use for enhancement. Specifically,
   * this is the directory under <sdk_root>/lib/opt/tools/datanucleus
   * containing the enhancer and the jars it depends on.
   *
   * @param datanucleusVersion must not be null
   */
  public void setDatanucleusVersion(String datanucleusVersion) {
    if (datanucleusVersion == null) {
      throw new NullPointerException("datanucleusVersion cannot be null");
    }
    this.datanucleusVersion = datanucleusVersion;
  }

  /**
   * Runs enhancement.
   */
  public void execute() {
    if (enhanceTargets == null) {
      throw new IllegalArgumentException("Must set the targets for enhancement.");
    }
    
    if (args == null) {
      throw new IllegalArgumentException("Must set the enhancer arguments.");      
    }
    
    ClassLoader enhancerLoader = new EnhancerLoader(enhanceTargets, datanucleusVersion);
    Thread.currentThread().setContextClassLoader(enhancerLoader);
    try {
      Class<?> enhancerClass = enhancerLoader.loadClass(DATANUCLEUS_ENHANCER_CLASS);
      Method main = enhancerClass.getMethod("main", String[].class);
      main.invoke(null, new Object[] {args});
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception", e);
    }
  }

  String getDatanucleusVersion() {
    return datanucleusVersion;
  }
}
