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

import java.io.File;

/**
 * Provides access to common attributes of a local server.
 *
 */
public interface LocalServerEnvironment {

  /**
   * @return the directory containing the application files
   */
  File getAppDir();

  /**
   * @return the address at which the server is running
   */
  String getAddress();

  /**
   * @return the port to which the server is bound
   */
  int getPort();

  /**
   * @return the host name at which the server is running.
   */
  String getHostName();
  /**
   * This call will block until the server is fully initialized
   * and ready to process incoming http requests.
   */
  void waitForServerToStart() throws InterruptedException;

  /**
   * @return Whether or not API deadlines should be emulated.
   */
  boolean enforceApiDeadlines();

  /**
   * @return Whether or not local services should simulate production
   * latencies.
   */
  boolean simulateProductionLatencies();
}
