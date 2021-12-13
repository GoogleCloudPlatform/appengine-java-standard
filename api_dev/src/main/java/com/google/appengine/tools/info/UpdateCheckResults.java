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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Makes information about the local SDK version and the latest remote
 * version available.
 *
 */
public class UpdateCheckResults {
  private static final VersionComparator COMPARATOR = new VersionComparator();

  private final Version localVersion;
  private final Version remoteVersion;

  UpdateCheckResults(Version localVersion, Version remoteVersion) {
    this.localVersion = localVersion;
    this.remoteVersion = remoteVersion;
  }

  /**
   * Returns a {@link Version} for the current local SDK.
   */
  public Version getLocalVersion() {
    return localVersion;
  }

  /**
   * Returns a {@link Version} for the remote servers.
   */
  public Version getRemoteVersion() {
    return remoteVersion;
  }

  /**
   * Returns true if there is a newer SDK release available on the
   * remote server.
   */
  public boolean isNewerReleaseAvailable() {
    String localRelease = localVersion.getRelease();
    String remoteRelease = remoteVersion.getRelease();

    if (localRelease != null && remoteRelease != null) {
      return COMPARATOR.compare(remoteRelease, localRelease) > 0;
    } else {
      return false;
    }
  }

  /**
   * Returns true if the server supports a new API version that the
   * local SDK does not.
   */
  public boolean isNewerApiVersionAvailable() {
    Set<String> localApiVersions = localVersion.getApiVersions();
    Set<String> remoteApiVersions = remoteVersion.getApiVersions();

    if (localApiVersions != null && remoteApiVersions != null) {
      if (localApiVersions.isEmpty()) {
        // If they're not using any API version, then this check is not useful.
        return false;
      }

      List<String> sortedApiVersions = new ArrayList<String>(remoteApiVersions);
      Collections.sort(sortedApiVersions, COMPARATOR);
      String largestVersion = sortedApiVersions.get(sortedApiVersions.size() - 1);
      return !localApiVersions.contains(largestVersion);
    } else {
      return false;
    }
  }

  /**
   * Returns true if the server does not support any of the API
   * versions supported by the local SDK.
   */
  public boolean isLocalApiVersionNoLongerSupported() {
    Set<String> localApiVersions = localVersion.getApiVersions();
    Set<String> remoteApiVersions = remoteVersion.getApiVersions();

    if (localApiVersions != null && remoteApiVersions != null) {
      if (localApiVersions.isEmpty()) {
        // If they're not using any API version, then this check is not useful.
        return false;
      }

      return Collections.disjoint(localApiVersions, remoteApiVersions);
    } else {
      return false;
    }
  }

  /**
   * Returns true if {@code apiVersion} is supported on the server.
   */
  public boolean isApiVersionSupportedRemotely(String apiVersion) {
    Set<String> remoteApiVersions = remoteVersion.getApiVersions();
    if (remoteApiVersions != null) {
      return remoteApiVersions.contains(apiVersion);
    } else {
      return true;
    }
  }

  /**
   * Returns true if {@code apiVersion} is supported by the local SDK.
   */
  public boolean isApiVersionSupportedLocally(String apiVersion) {
    Set<String> localApiVersions = localVersion.getApiVersions();
    if (localApiVersions != null) {
      return localApiVersions.contains(apiVersion);
    } else {
      return true;
    }
  }

  /**
   * {@code VersionComparator} compares strings that represent dotted
   * version numbers (e.g. "1.1.2").  These string are compared using
   * the ordering laid out in the <a
   * href="http://java.sun.com/jse/1.5.0/docs/guide/versioning/spec/versioning2.html#wp90779">Specification
   * Versioning section of the Java Production Versioning Guide</a>.
   */
  public static class VersionComparator implements Comparator<String> {
    @Override
    public int compare(String string1, String string2) throws NumberFormatException {
      // N.B.(schwardo): This code is loosely equivalent to
      // java.lang.Package.isCompatibleWith(String).
      String[] array1 = string1.split("\\.");
      String[] array2 = string2.split("\\.");

      for (int i = 0; i < Math.max(array1.length, array2.length); i++) {
        int i1 = (i < array1.length) ? Integer.parseInt(array1[i]) : 0;
        int i2 = (i < array2.length) ? Integer.parseInt(array2[i]) : 0;
        if (i1 != i2) {
          return i1 - i2;
        }
      }
      return 0;
    }
  }
}
