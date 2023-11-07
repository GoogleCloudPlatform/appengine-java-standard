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

import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.CronXml;
import com.google.apphosting.utils.config.DispatchXml;
import com.google.apphosting.utils.config.DosXml;
import com.google.apphosting.utils.config.IndexesXml;
import com.google.apphosting.utils.config.QueueXml;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * An App Engine application (may be Java/Python).
 *
 */
public interface GenericApplication {

  /**
   * Returns the application identifier
   * @return application identifier
   */
  String getAppId();

  /**
   * Returns the application version
   * @return application version
   */
  String getVersion();

  /**
   * Returns the runtime this application uses.
   */
  String getRuntime();

  /**
   * Returns the application module name or null if not specified.
   */
  String getModule();

  /**
   * Returns the application instance class name or null if not specified.
   */
  String getInstanceClass();

  /**
   * Returns whether precompilation is enabled for this application
   * @return precompilation setting
   */
  boolean isPrecompilationEnabled();

  /**
   * Returns the list of error handlers for this application
   * @return error handlers
   */
  List<ErrorHandler> getErrorHandlers();

  /**
   * Returns the mime-type if path corresponds to static content, {@code null} otherwise.
   * @return mime-type, possibly {@code null}
   */
  String getMimeTypeIfStatic(String path);

  /**
   * Returns the CronXml describing the applications' cron jobs.
   * @return a cron descriptor, possibly empty or {@code null}
   */
  CronXml getCronXml();

  /**
   * Returns the QueueXml describing the applications' task queues.
   * @return a queue descriptor, possibly empty or {@code null}
   */
  QueueXml getQueueXml();

  /**
   * Returns the possibly empty {@link DispatchXml} descriptor for this
   * application or null if none is configured.
   */
  DispatchXml getDispatchXml();

  /**
   * Returns the DosXml describing the applications' DoS entries.
   * @return a dos descriptor, possibly empty or {@code null}
   */
  DosXml getDosXml();

  /**
   * Returns the IndexesXml describing the applications' indexes.
   * @return an indexes descriptor, possibly empty or {@code null}
   */
  IndexesXml getIndexesXml();

  /**
   * Returns the BackendsXml describing the applications' backends.
   * @return a backends descriptor, possibly empty or {@code null}
   */
  BackendsXml getBackendsXml();

  /**
   * Returns the desired API version for the current application, or
   * {@code "none"} if no API version was used.
   *
   * @throws IllegalStateException if createStagingDirectory has not been called.
   */
  String getApiVersion();

  /**
   * Returns a path to an exploded WAR directory for the application.
   * This may be a temporary directory.
   *
   * @return a not {@code null} path pointing to a directory
   */
  String getPath();

  /**
   * Returns the staging directory, or {@code null} if none has been created.
   */
  File getStagingDir();

  void resetProgress();

  /**
   * Creates a new staging directory, if needed, or returns the existing one if already created.
   *
   * @param opts User-specified options for processing the application.
   * @return staging directory
   * @throws IOException
   */
  File createStagingDirectory(ApplicationProcessingOptions opts) throws IOException;

  /**
   * Creates (if necessary) and populates a user specified staging directory
   *
   * @param opts User-specified options for processing the application.
   * @param stagingDir User-specified staging directory (must be empty or not exist)
   * @return staging directory
   * @throws IOException if an error occurs trying to create or populate the staging directory
   */
  File createStagingDirectory(ApplicationProcessingOptions opts, File stagingDir)
      throws IOException;

  /** deletes the staging directory, if one was created. */
  void cleanStagingDirectory();

  void setListener(UpdateListener l);

  void setDetailsWriter(PrintWriter detailsWriter);

  void statusUpdate(String message, int amount);

  void statusUpdate(String message);

  /**
   * Returns the yaml string describing this application's configuration.
   * @return application configuration yaml string
   */
  String getAppYaml();

  /**
   * Interface describing the application's error handlers.
   */
  public interface ErrorHandler {
    /** Returns the not {@code null} error handler file name. */
    String getFile();
    /** Returns the error code, possibly {@code null}. */
    String getErrorCode();
    /** Returns the not {@code null} error handler mime-type. */
    String getMimeType();
  }
}
