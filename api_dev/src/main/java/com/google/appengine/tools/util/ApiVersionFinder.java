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
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * {@code ApiVersionFinder} extracts the {@code Specification-Version}
 * from the Jar manifest of the specified jar file.
 *
 */
public class ApiVersionFinder {
  private static final String DESIRED_VENDOR_ID = "com.google";
  private static final String API_PACKAGE_NAME = "com/google/appengine/api/";

  public static void main(String[] args) throws IOException {
    ApiVersionFinder finder = new ApiVersionFinder();
    String apiVersion = finder.findApiVersion(args[0]);
    if (apiVersion == null) {
      System.exit(1);
    }

    System.out.println(apiVersion);
    System.exit(0);
  }

  public String findApiVersion(String fileName) throws IOException {
    return findApiVersion(new File(fileName));
  }

  public String findApiVersion(File inputJar) throws IOException {
    JarFile jarFile = new JarFile(inputJar);
    try {
      Manifest manifest = jarFile.getManifest();

      if (manifest != null) {
        Attributes attr = manifest.getAttributes(API_PACKAGE_NAME);
        if (attr != null) {
          // Check to make sure it's our implementation.  If someone
          // wants to create an alternate implementation they can use a
          // different vendor ID.
          String vendorId = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR_ID);
          if (!DESIRED_VENDOR_ID.equals(vendorId)) {
            return null;
          }

          // Now return the specification version, if one is available.
          return attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
        }
      }
      return null;
    } finally {
      try {
        jarFile.close();
      } catch (IOException ex) {
        // ignore
      }
    }
  }
}
