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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code RemoteVersionFactory} generates {@link Version} objects that
 * represents the release information for the latest release available
 * on a remote server.
 *
 * <p>Along with gathering release information from the remote server,
 * this class also uploads the local SDK release information to the
 * remote server.
 *
 */
public class RemoteVersionFactory {
  private static final Pattern RELEASE_RE =
      Pattern.compile("^release: *[\"'](.*)[\"']$");
  private static final Pattern TIMESTAMP_RE =
      Pattern.compile("^timestamp: *(\\d+)$");
  private static final Pattern API_VERSIONS_RE =
      Pattern.compile("^api_versions: \\[[\"'](.*)[\"']\\]$");
  private static final Pattern API_VERSION_SPLIT_RE =
      Pattern.compile("[\"'], *[\"']");

  private static final Logger logger =
      Logger.getLogger(RemoteVersionFactory.class.getName());

  private final Version localVersion;
  private final String server;
  private final boolean secure;

  /**
   * Creates a {@code RemoteVersionFactory} that will upload {@code
   * localVersion} to {@code server} and download the latest release
   * information.
   *
   * @param secure if {@code true}, connect to the {@code server}
   *     using https, otherwise connect with http.
   */
  public RemoteVersionFactory(Version localVersion, String server,
      boolean secure) {
    if (server == null) {
      throw new NullPointerException("No server specified.");
    }

    this.localVersion = localVersion;
    this.server = server;
    this.secure = secure;
  }

  public Version getVersion() {
    URL checkUrl;
    try {
      checkUrl = buildCheckURL();
    } catch (IOException ex) {
      logger.log(Level.INFO, "Unable to build remote version URL:", ex);
      return Version.UNKNOWN;
    }

    try {
      InputStream stream = checkUrl.openStream();
      try {
        return parseVersionResult(stream);
      } finally {
        stream.close();
      }
    } catch (IOException ex) {
      logger.log(Level.INFO, "Unable to access " + checkUrl, ex);
      return Version.UNKNOWN;
    }
  }

  // @VisibleForTesting
  URL buildCheckURL() throws IOException {
    StringBuilder buffer = new StringBuilder();
    buffer.append(secure ? "https://" : "http://");
    buffer.append(server);
    buffer.append("/api/updatecheck?runtime=java");
    if (localVersion.getRelease() != null) {
      buffer.append("&release=");
      buffer.append(localVersion.getRelease());
    }
    if (localVersion.getTimestamp() != null) {
      buffer.append("&timestamp=");
      buffer.append(localVersion.getTimestamp().getTime() / 1000);
    }
    if (localVersion.getApiVersions() != null) {
      buffer.append("&api_versions=[");

      boolean first = true;
      for (String apiVersion : localVersion.getApiVersions()) {
        if (first) {
          first = false;
        } else {
          buffer.append(", ");
        }
        buffer.append("'" + apiVersion + "'");
      }
      buffer.append("]");
    }
    return new URL(buffer.toString());
  }

  // @VisibleForTesting
  static Version parseVersionResult(InputStream stream) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

    String release = null;
    Date timestamp = null;
    Set<String> apiVersions = null;

    String line;
    while ((line = reader.readLine()) != null) {
      {
        Matcher match = RELEASE_RE.matcher(line);
        if (match.matches()) {
          release = match.group(1);
        }
      }
      {
        Matcher match = TIMESTAMP_RE.matcher(line);
        if (match.matches()) {
          timestamp = new Date(Long.parseLong(match.group(1)) * 1000);
        }
      }
      {
        Matcher match = API_VERSIONS_RE.matcher(line);
        if (match.matches()) {
          apiVersions = new HashSet<String>();
          Collections.addAll(apiVersions, API_VERSION_SPLIT_RE.split(match.group(1)));
        }
      }
    }
    return new Version(release, timestamp, apiVersions);
  }
}
