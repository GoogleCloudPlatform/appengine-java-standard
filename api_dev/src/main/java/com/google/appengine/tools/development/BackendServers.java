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

package com.google.appengine.tools.development;

import com.google.common.annotations.VisibleForTesting;

/**
 * Controls backend servers configured in appengine-web.xml. Each server is
 * started on a separate port. All servers run the same code as the main app.
 *
 *
 */
public class BackendServers extends AbstractBackendServers {


  // Singleton so BackendServers can to be accessed from the
  // {@ link DevAppServerModulesFilter} configured in the webdefaults.xml file.
  // The filter is configured in the xml file to ensure that it runs after the
  // StaticFileFilter but before any other filters.
  private static BackendServers instance = new BackendServers();

  public static BackendServers getInstance() {
    return instance;
  }

  @VisibleForTesting
  BackendServers() {
  }
}
