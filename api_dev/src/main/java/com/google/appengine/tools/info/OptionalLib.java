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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes an optional library in the SDK.
 *
 */
public final class OptionalLib {

  private final String name;
  private final String description;
  private final Map<String, List<File>> filesByVersion;
  private final Map<String, List<URL>> urlsByVersion;
  private final List<String> versions;

  /**
   * Constructor
   *
   * @param name The name of the library
   * @param description A description of the library
   * @param filesByVersion {@link Map} from version to all the jar files that
   * make up that version of the library. The Map must have a stable iteration
   * order and must return keys sorted by version in ascending order, so the
   * oldest version comes first and the newest version comes last. It is the
   * responsibility of the caller to determine the appropriate order of the
   * available versions.
   */
  public OptionalLib(String name, String description,
      Map<String, List<File>> filesByVersion) {
    this.name = checkNotNull(name, "name cannot be null");
    this.description = checkNotNull(description, "description cannot be null");
    checkNotNull(filesByVersion, "filesByVersion cannot be null");
    // defensive copy
    this.filesByVersion = new LinkedHashMap<String, List<File>>();
    for (Map.Entry<String, List<File>> entry : filesByVersion.entrySet()) {
      this.filesByVersion.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
    }
    this.versions = Collections.unmodifiableList(new ArrayList<String>(filesByVersion.keySet()));
    this.urlsByVersion = buildURLsByVersion(filesByVersion);
  }

  private Map<String, List<URL>> buildURLsByVersion(Map<String, List<File>> filesByVersion) {
    Map<String, List<URL>> urlsByVersion = new LinkedHashMap<String, List<URL>>();
    for (Map.Entry<String, List<File>> entry : filesByVersion.entrySet()) {
      urlsByVersion.put(entry.getKey(), Collections.unmodifiableList(toURLs(entry.getValue())));
    }
    return urlsByVersion;
  }

  private List<URL> toURLs(List<File> value) {
    List<URL> urls = new ArrayList<URL>();
    for (File f : value) {
      try {
        urls.add(f.toURI().toURL());
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    return urls;
  }

  /**
   * @return The name of the library
   */
  public String getName() {
    return name;
  }

  /**
   * @return A description of the library
   */
  public String getDescription() {
    return description;
  }

  /**
   * @return The available versions of this library, sorted in ascending order
   * (oldest version first, newest version last).
   */
  public List<String> getVersions() {
    return versions;
  }

  /**
   * @param version The version for which to retrieve files.
   * @return The files for this version of the library. Can be {@code null} if
   * there is no such version of the library.
   */
  public List<File> getFilesForVersion(String version) {
    return filesByVersion.get(version);
  }

  /**
   * @param version The version for which to retrieve URLs.
   * @return The URLs that reference the files for this version of the library.
   * Can be {@code null} if there is no such version of the library.
   */
  public List<URL> getURLsForVersion(String version) {
    return urlsByVersion.get(version);
  }
}
