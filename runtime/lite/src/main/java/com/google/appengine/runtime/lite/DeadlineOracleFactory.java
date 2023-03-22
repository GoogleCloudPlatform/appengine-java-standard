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

package com.google.appengine.runtime.lite;

import com.google.apphosting.runtime.ApiDeadlineOracle;
import java.util.HashMap;
import java.util.Map;

/** Makes a DeadlineOracle. */
final class DeadlineOracleFactory {
  private static final double YEAR_SECONDS = 31536000.0;

  interface DeadlineSetter {
    void set(String pkg, double defOnline, double defOffline);
  }

  static ApiDeadlineOracle create() {
    Map<String, Double> defaultOnlineDeadlineMap = new HashMap<>();
    Map<String, Double> maxOnlineDeadlineMap = new HashMap<>();
    Map<String, Double> defaultOfflineDeadlineMap = new HashMap<>();
    Map<String, Double> maxOfflineDeadlineMap = new HashMap<>();

    DeadlineSetter setter =
        (String pkg, double defOnline, double defOffline) -> {
          defaultOnlineDeadlineMap.put(pkg, defOnline);
          maxOnlineDeadlineMap.put(pkg, YEAR_SECONDS);
          defaultOfflineDeadlineMap.put(pkg, defOffline);
          maxOfflineDeadlineMap.put(pkg, YEAR_SECONDS);
        };

    setter.set("app_config_service", 60.0, 60.0);
    setter.set("blobstore", 15.0, 15.0);
    setter.set("datastore_v3", 60.0, 60.0);
    setter.set("datastore_v4", 60.0, 60.0);
    setter.set("file", 30.0, 30.0);
    setter.set("images", 30.0, 30.0);
    setter.set("logservice", 60.0, 60.0);
    setter.set("modules", 60.0, 60.0);
    setter.set("rdbms", 60.0, 60.0);
    setter.set("remote_socket", 60.0, 60.0);
    setter.set("search", 10.0, 10.0);
    setter.set("stubby", 10.0, 10.0);
    setter.set("taskqueue", 10.0, 5.0);
    setter.set("urlfetch", 10.0, 5.0);

    return new ApiDeadlineOracle.Builder()
        .initDeadlineMap(
            new ApiDeadlineOracle.DeadlineMap(
                /*defaultDeadline=*/ YEAR_SECONDS,
                defaultOnlineDeadlineMap,
                /*maxDeadline=*/ YEAR_SECONDS,
                maxOnlineDeadlineMap))
        .initOfflineDeadlineMap(
            new ApiDeadlineOracle.DeadlineMap(
                /*defaultDeadline=*/ YEAR_SECONDS,
                defaultOfflineDeadlineMap,
                /*maxDeadline=*/ YEAR_SECONDS,
                maxOfflineDeadlineMap))
        .build();
  }

  private DeadlineOracleFactory() {}
}
