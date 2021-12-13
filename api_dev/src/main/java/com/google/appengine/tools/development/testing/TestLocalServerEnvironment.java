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

package com.google.appengine.tools.development.testing;

import com.google.appengine.tools.development.LocalServerEnvironment;
import java.io.File;

/**
 * {@link LocalServerEnvironment} implementation used for local service tests.
 *
 */
class TestLocalServerEnvironment implements LocalServerEnvironment {
  static final int DEFAULT_TEST_PORT = 8080;

  private final boolean enforceApiDeadlines;
  private final boolean simulateProdLatencies;

  TestLocalServerEnvironment(boolean enforceApiDeadlines, boolean simulateProdLatencies) {
    this.enforceApiDeadlines = enforceApiDeadlines;
    this.simulateProdLatencies = simulateProdLatencies;
  }

  @Override
  public File getAppDir() {
    return new File(".");
  }

  @Override
  public String getAddress() {
    return "localhost";
  }

  @Override
  public String getHostName() {
    return "localhost";
  }

  @Override
  public int getPort() {
    return DEFAULT_TEST_PORT;
  }

  @Override
  public void waitForServerToStart() {
    // not really starting the server so nothing to wait for
  }

  @Override
  public boolean enforceApiDeadlines() {
    return enforceApiDeadlines;
  }

  @Override
  public boolean simulateProductionLatencies() {
    return simulateProdLatencies;
  }
}
