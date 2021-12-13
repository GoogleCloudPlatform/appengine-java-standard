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

package com.google.appengine.tools.admin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;

/**
 * Our implementation of the AppAdmin interface.
 *
 */
public class AppAdminImpl implements AppAdmin {

  private final GenericApplication app;
  private final PrintWriter errorWriter;
  private final ApplicationProcessingOptions appOptions;

  protected AppAdminImpl(
      GenericApplication app, PrintWriter errorWriter, ApplicationProcessingOptions appOptions) {
    this.app = app;
    this.errorWriter = errorWriter;
    this.appOptions = appOptions;
  }

  @Override
  public void stageApplicationWithDefaultResourceLimits(File stagingDir) {
    stageApplication(stagingDir, false);
  }

  @Override
  public void stageApplicationWithRemoteResourceLimits(File stagingDir) {
    stageApplication(stagingDir, true);
  }

  /**
   * Convert a java appengine war directory into a format that can be deployed to
   * appengine.
   */
  void stageApplication(File stagingDir, boolean useRemoteResourceLimits) {
    if (stagingDir == null) {
      throw new AdminException("Staging dir is not a valid directory (it is null)");
    }
    if (stagingDir.exists()) {
      if (!stagingDir.isDirectory()) {
        throw new AdminException(stagingDir.getPath()
            + " is not a valid staging directory (it is a regular file)");
      }
      Path path = stagingDir.toPath();
      try (DirectoryStream<Path> dirStream = java.nio.file.Files.newDirectoryStream(path)) {
        if (dirStream.iterator().hasNext()) {
          throw new AdminException(
              stagingDir.getPath() + " is not a valid staging directory (it is not empty)");
        }
      } catch (IOException e) {
        throw new AdminException("Unable to stage application.", e);
      }
    }
    try {
      app.createStagingDirectory(appOptions, stagingDir);
      app.resetProgress();
      app.setDetailsWriter(new PrintWriter(System.out, true));
    } catch (Throwable t) {
      errorWriter.println("Unable to stage:");
      t.printStackTrace(errorWriter);
      throw new AdminException("Unable to stage app: " + t.getMessage(), t);
    }
  }


}
