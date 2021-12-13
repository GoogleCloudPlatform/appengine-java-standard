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

package com.google.appengine.tools.info;

import java.util.Arrays;
import java.util.List;

/**
 * Provides support information for the SDK.
 *
 */
public class SupportInfo {

  private static final List<String> systemProperties = Arrays.asList(
      "java.vm.vendor",
      "java.vm.version",
      "java.version",
      "os.name",
      "os.version"
  );

  private static final String newLine = System.getProperty("line.separator");

  private SupportInfo() {    
  }

  /**
   * Returns version information that is useful in terms of support.
   * This includes information about the JVM, operating system, and the SDK. 
   *
   * @return a non {@code null} value. 
   */
  public static String getVersionString() {        
    Version sdkVersion = AppengineSdk.getSdk().getLocalVersion();
    StringBuilder versionString = new StringBuilder();

    versionString.append(sdkVersion.toString());
    versionString.append(newLine);

    for (String property : systemProperties) {
      versionString.append(property);
      versionString.append(": ");
      versionString.append(System.getProperty(property));
      versionString.append(newLine);
    }
        
    return versionString.toString();
  }  
}
