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

import com.google.appengine.tools.info.AppengineSdk;

/**
 * helper to load a {@link ContainerService} instance
 */
public class ContainerUtils {
  /**
   * Load a {@link ContainerService} instance based on the implementation: Jetty only for now.
   *
   * @return the deployed {@link ContainerService} instance.
   * @throws IllegalArgumentException if the container cannot be loaded.
   */
  public static ContainerService loadContainer() {
    ContainerService result;

    // Try to load jetty.
    String jettyService = "com.google.appengine.tools.development.jetty.JettyContainerService";

    try {
      result =
          (ContainerService)
              Class.forName(jettyService, true, DevAppServerImpl.class.getClassLoader())
                  .newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot load any servlet container.", e);
    }
    return result;
  }

  /**
   * @return the server info string with the dev-appserver version
   */
  public static String getServerInfo() {
    return "Google App Engine Development/" + AppengineSdk.getSdk().getLocalVersion().getRelease();
  }

  // There are no instances of this class.
  private ContainerUtils() {}
}
