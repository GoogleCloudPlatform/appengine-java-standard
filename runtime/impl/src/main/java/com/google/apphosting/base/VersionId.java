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

package com.google.apphosting.base;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A parsed Version Id.
 */
public record VersionId(
    @Nullable String versionId,
    String majorVersion,
    String engineId,
    String engineVersionId,
    @Nullable String minorVersion) {

  // These must be kept in sync with the corresponding constants in
  // apphosting/base/constants.h
  public static final String DEFAULT_ENGINE_ID = "default";

  private static final int ENGINE_ID_MAX_LENGTH = 63;
  private static final int ENGINE_VERSION_ID_MAX_LENGTH = 100;
  private static final int MINOR_VERSION_ID_LENGTH = 20;

  // These regexes must be kept in sync with the corresponding regexes
  // in apphosting/base/id_util.cc
  private static final String ENGINE_ID_RE =
      String.format("[a-z\\d][a-z\\d\\-]{0,%d}", ENGINE_ID_MAX_LENGTH - 1);

  private static final String ENGINE_VERSION_ID_RE =
      String.format("[a-z\\d][a-z\\d\\-]{0,%d}", ENGINE_VERSION_ID_MAX_LENGTH - 1);

  private static final String MAJOR_VERSION_RE =
      String.format("(?:(?:(%s):)?)(%s)", ENGINE_ID_RE, ENGINE_VERSION_ID_RE);

  // Updated to make minor version optional.
  private static final String FULL_VERSION_RE =
      String.format("(%s)(\\.(\\d{1,%d}))?",  MAJOR_VERSION_RE, MINOR_VERSION_ID_LENGTH);

  private static final Pattern FULL_VERSION_PATTERN = Pattern.compile(FULL_VERSION_RE);

  /**
   * Create a new VersionId based on an versionId formatted as follows:
   *
   * [(engine):](engine version)[.(minor version)]
   *
   * @param versionId The versionId to parse.
   */
  private static VersionId from(String versionId) {
    if (versionId == null) {
      throw new NullPointerException();
    }
    Matcher matcher = FULL_VERSION_PATTERN.matcher(versionId);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Malformed versionId: " + versionId);
    }
    String engineId = matcher.group(2) == null ? DEFAULT_ENGINE_ID : matcher.group(2);
    return new VersionId(
        versionId, matcher.group(1), engineId, matcher.group(3), matcher.group(5));
  }

  public static VersionId parse(String versionId) {
    return from(versionId);
  }

  /** Returns the versionId. */
  public @Nullable String getVersionId() {
    return versionId;
  }

  /**
   * Returns the majorVersion.
   */
  public String getMajorVersion() {
    return majorVersion;
  }

  /**
   * Returns the serverId.
   */
  public String getEngineId() {
    return engineId;
  }

  /**
   * Returns the server version id.
   */
  public String getEngineVersionId() {
    return engineVersionId;
  }

  /** Returns the minorVersion or {@code null} if no minor version was present. */
  public @Nullable String getMinorVersion() {
    return minorVersion;
  }
}
