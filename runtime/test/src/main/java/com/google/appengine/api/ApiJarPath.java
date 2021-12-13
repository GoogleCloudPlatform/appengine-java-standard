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

package com.google.appengine.api;


import com.google.appengine.api.users.UserService;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utilities for working with the appengine-api jar location on disk */
public final class ApiJarPath {

  private ApiJarPath() {}

  public static File getFile() {
    Path apiJarPath = getPath();
    return apiJarPath.toFile();
  }

  public static Path getPath() {
    URL apiJarUrl = UserService.class.getProtectionDomain().getCodeSource().getLocation();
    try {
      Path apiJarPath = Paths.get(apiJarUrl.toURI());
      // ensure that the UserService class remains in appengine-api.jar
      String pattern = ".*appengine-api.*\\.jar";
      if (!apiJarPath.getFileName().toString().matches(pattern)) {
        String msg =
            String.format(
                "Expected %s class to be located in a jar file matching pattern '%s'",
                UserService.class.getSimpleName(), pattern);
        throw new RuntimeException(msg);
      }
      return apiJarPath;
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
