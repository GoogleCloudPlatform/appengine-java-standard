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

package com.google.apphosting.utils.config;

import java.io.File;

/**
 * Utility class to establish the location of generated files, given an
 * application base directory.
 *
 *
 */
public class GenerationDirectory {

  // TODO: Move this to com.google.appengine.tools, post launch.

  /**
   * The directory in which files are generated. The directory is relative to the WEB-INF folder of
   * the current application.
   */
  public static final String GENERATED_DIR_PROPERTY = "appengine.generated.dir";

  /** The default value for {@code GENERATED_DIR_PROPERTY}. */
  private static final String GENERATED_DIR_DEFAULT = "appengine-generated";

  /**
   * Returns the generation directory for the current application.
   *
   * @param dir location of the application.  The default generation directory
   *    will be inside this, if not overridden via the system property
   *    {#link GENERATED_DIR_PROPERTY}.
   * @return pathname to generated file directory.  The directory may
   *    or may not have been created.
   */
  public static File getGenerationDirectory(File dir) {
    return new File(System.getProperty(GENERATED_DIR_PROPERTY,
            new File(new File(dir, "WEB-INF"), GENERATED_DIR_DEFAULT).getPath()));
  }

  private GenerationDirectory() {
    // packageonly constructor; use the statics.
  }

}
