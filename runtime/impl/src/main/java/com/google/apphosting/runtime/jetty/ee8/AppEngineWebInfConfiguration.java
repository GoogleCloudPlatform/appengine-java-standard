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

package com.google.apphosting.runtime.jetty.ee8;

import org.eclipse.jetty.ee8.webapp.WebInfConfiguration;

/**
 * Replace the default handling of temp directories in WebInfConfiguration.
 **/
public class AppEngineWebInfConfiguration extends WebInfConfiguration {
  /* TODO: Review, this has been changed by https://github.com/eclipse/jetty.project/pull/9153
  @Override
  public void configureTempDirectory(File dir, WebAppContext context) {
    if (dir == null) {
      throw new IllegalArgumentException("Null temp dir");
    }

    // If dir exists and we don't want it persisted, delete it.
    if (dir.exists() && !context.isPersistTempDirectory()) {
      if (!IO.delete(dir)) {
        throw new IllegalStateException("Failed to delete temp dir " + dir);
      }
    }

    // If it doesn't exist make it.
    if (!dir.exists()) {
      dir.mkdirs();
    }

    if (!context.isPersistTempDirectory()) {
      dir.deleteOnExit();
    }

    if (!dir.isDirectory()) {
      throw new IllegalStateException("Temp dir " + dir + " not directory");
    }
  }
   */
}
