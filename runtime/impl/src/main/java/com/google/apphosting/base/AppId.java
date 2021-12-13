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

/**
 * Class used for parsing the various components of the AppId.
 *
 */
// The format is specified here:
// http://g3doc/apphosting/g3doc/wiki-carryover/app_id.md
public class AppId {
  // Keep these in sync with {@code apphosting/base/id_util.cc}.
  private static final int APP_ID_MAX_LEN = 100;
  private static final Pattern DISPLAY_APP_ID_RE =
      Pattern.compile("[a-z\\d\\-]{1," + APP_ID_MAX_LEN + "}", Pattern.CASE_INSENSITIVE);
  private static final Pattern DOMAIN_RE =
      Pattern.compile("([a-z\\d\\-\\.]{1," + APP_ID_MAX_LEN + "})?\\:", Pattern.CASE_INSENSITIVE);
  private static final Pattern PARTITION_RE =
      Pattern.compile("([a-z\\d\\-]{1," + APP_ID_MAX_LEN + "})?\\~", Pattern.CASE_INSENSITIVE);
  private static final Pattern APP_ID_RE =
      Pattern.compile(
          "(?:" + PARTITION_RE + ")?((?:" + DOMAIN_RE + ")?(" + DISPLAY_APP_ID_RE + "))",
          Pattern.CASE_INSENSITIVE);

  private String appId;
  private String domain;
  private String longAppId;
  private String displayAppId;
  private String partition;

  /**
   * Create a new AppId based on an appId formatted as follows:
   *
   * [(partition)~][(domain):](display-app-id)
   *
   * @param appId The appId to parse.
   */
  private AppId(String appId) {
    this.appId = appId;
    if (appId == null || appId.length() == 0) {
      return;
    }
    Matcher matcher = APP_ID_RE.matcher(appId);
    if (!matcher.matches()) {
      return;
    }
    this.partition = matcher.group(1);
    this.longAppId = matcher.group(2);
    this.domain = matcher.group(3);
    this.displayAppId = matcher.group(4);
  }

  /**
   * @return The full appId.
   */
  public String getAppId() {
    return appId;
  }

  /**
   * @return The domain component of this appId.
   */
  public String getDomain() {
    return domain;
  }

  /**
   * @return The display-app-id component of this appId.
   */
  public String getDisplayAppId() {
    return displayAppId;
  }

  /**
   * @return The partition component of the appId.
   */
  public String getPartition() {
    return partition;
  }

  /**
   * @return The appId without the partition component.
   */
  public String getLongAppId() {
    return longAppId;
  }

  /**
   * Returns a new AppId object based on an appId formatted as follows:
   *
   * [(partition)~][(domain):](display-app-id)
   *
   * @param appId The appId to parse.
   *
   * @return AppId object with the parsed appid components.
   */
  public static AppId parse(String appId) {
    return new AppId(appId);
  }
}
