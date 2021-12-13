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
import java.net.URL;
import java.util.Collection;

/**
 * Describes an object that can answer classpath-related questions about an app
 * running in the dev appserver.
 *
 */
public interface UserCodeClasspathManager {

  /**
   * Returns the classpath for the app.
   */
  Collection<URL> getUserCodeClasspath(File root);

  /**
   * {@code true} if a WEB-INF directory must exist, {@code false} otherwise. 
   */
  boolean requiresWebInf();
}
