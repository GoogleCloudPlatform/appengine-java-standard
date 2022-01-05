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

package com.google.appengine.spi;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

// TODO Move all service provider code into some package
// that should really be shared between server and devappserver
// for example, tools.service.

/**
 * A helper class for reading and writing Services files.
 *
 * @see java.util.ServiceLoader
 */
public class ServicesFile {

  public static final String SERVICES_PATH = "META-INF/services";

  private ServicesFile() {}

  /**
   * Returns an absolute path to a service file given the class name of the service.
   *
   * @param serviceName not {@code null}
   * @return SERVICES_PATH + serviceName
   */
  public static String getPath(String serviceName) {
    return SERVICES_PATH + "/" + serviceName;
  }

  /**
   * Reads the set of service classes from a service file.
   *
   * @param input not {@code null}. Closed after use.
   * @return a not {@code null Set} of service class names.
   * @throws IOException
   */
  public static Set<String> readServiceFile(InputStream input) throws IOException {
    BufferedReader r = null;
    HashSet<String> serviceClasses = new HashSet<String>();
    try {
      r = new BufferedReader(new InputStreamReader(input, UTF_8));
      String line;
      while ((line = r.readLine()) != null) {
        int commentStart = line.indexOf('#');
        if (commentStart >= 0) {
          line = line.substring(0, commentStart);
        }
        line = line.trim();
        if (!line.equals("")) {
          serviceClasses.add(line);
        }
      }
    } finally {
      try {
        if (r != null) {
          r.close();
        }
      } catch (IOException e) {
        // Intentionally blank
      }
    }
    return serviceClasses;
  }

  /**
   * Writes the set of service class names to a service file.
   *
   * @param output not {@code null}. Not closed after use.
   * @param services a not {@code null Collection} of service class names.
   * @throws IOException
   */
  public static void writeServiceFile(Collection<String> services, OutputStream output)
      throws IOException {
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8));
    for (String service : services) {
      writer.write(service);
      writer.newLine();
    }
    writer.flush();
  }
}
