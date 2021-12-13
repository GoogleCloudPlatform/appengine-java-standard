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

import com.google.appengine.tools.util.ApiVersionFinder;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * {@code LocalVersionFactory} generates {@link Version} objects
 * that represents the release information for the SDK that is
 * currently running.
 *
 */
public class LocalVersionFactory {
  private static final Logger logger =
      Logger.getLogger(LocalVersionFactory.class.getName());

  /** Digits separated by dots, like 1 or 1.0 or 1.9.76 . */
  private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(\\.\\d+)*");

  // Where do we get version information from?
  //
  // First we look at this package's entry in the current jar's
  // manifest (which was parsed during class loading) and extract:
  //
  // Specification-Version is a dotted version number (e.g. 1.1.8)
  // which becomes the release name.
  //
  // Implementation-Version is a timestamp (represented as the number
  // of seconds since the epoch) which gets converted into a Date.
  //
  // Then we scan through each of the API jar files specified and read
  // each of their manifests via ApiVersionFinder.  If any of these
  // manifests contains an entry for the com.google.appengine.api
  // package, we extract its Specification-Version and use this as an
  // API version.  The Implementation-Version of the API jars (another
  // timestamp) is ignored.

  private final Collection<File> apiJars;
  private final ApiVersionFinder versionFinder;

  public LocalVersionFactory(Collection<File> apiJars) {
    this.apiJars = apiJars;
    this.versionFinder = new ApiVersionFinder();
  }

  public Version getVersion() {
    Package toolsPackage = LocalVersionFactory.class.getPackage();
    if (toolsPackage == null) {
      return Version.UNKNOWN;
    }

    String release = toolsPackage.getSpecificationVersion();
    Date timestamp = convertToDate(toolsPackage.getImplementationVersion());

    Set<String> apiVersions = new HashSet<String>();
    for (File apiJar : apiJars) {
      try {
        String apiVersion = versionFinder.findApiVersion(apiJar);
        if (apiVersion != null && VERSION_PATTERN.matcher(apiVersion).matches()) {
          // Matching numeric versions specifically means we exclude "user_defined" here.
          apiVersions.add(apiVersion);
        }
      } catch (IOException ex) {
        logger.log(Level.FINE, "Could not find API version from " + apiJar, ex);
      }
    }

    return new Version(release, timestamp, apiVersions);
  }

  private Date convertToDate(String timestamp) {
    if (timestamp == null) {
      return null;
    } else {
      return new Date(Long.parseLong(timestamp) * 1000);
    }
  }
}
