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

import com.google.common.base.Preconditions;
import java.io.File;

/**
 * The application administration interface to App Engine. Use this
 * API to update, configure, and otherwise manage an App Engine
 * application. Use {@link AppAdminFactory} to retrieve an {@code AppAdmin}
 * instance configured for a specific application.
 * <p>
 * <b>Synchronous versus Asynchronous requests:</b>
 * Some requests, such as {@link #update}, occur asynchronously
 * and must be monitored with a {@code listener}. Other requests, such as
 * {@link #updateIndexes}, are made synchronously. In either case,
 * work often continues to occur asynchronously on the remote server after the
 * request has been completed.
 * <p>
 * <b>Error handling:</b> Most configuration operations that communicate
 * with App Engine's remote administration server use a
 * network connection. In cases where unrecoverable failures occur (such as a
 * network failure), this API throws an
 * {@link com.google.appengine.tools.admin.AdminException}.
 * <p>
 * Application updates occur transactionally. If a failure occurs during
 * update, you must {@link #rollback} the incomplete transaction before
 * beginning another.
 *
 */
public interface AppAdmin {



  /**
   * Stage an application directory with default resource limits
   */
  void stageApplicationWithDefaultResourceLimits(File stagingDir);

  /**
   * Stage an application directory with remote resource limits
   */
  void stageApplicationWithRemoteResourceLimits(File stagingDir);



  /**
   * Settable options for configuring the behavior of update operations.
   */
  public static class UpdateOptions {
    /**
     * SDK version for logging in cases the SDK version is unavailable.
     */
    static final String UNKNOWN_SDK_VERSION = "Java/unknown";

    private boolean updateGlobalConfigurations = true;
    private String sdkVersion = UNKNOWN_SDK_VERSION;
    private boolean updateUsageReporting = true;





    /**
     * Set the SDK version.
     * <p>
     * This is currently used for logging purposes.
     *
     * @param sdkVersion
     */
    void setSdkVersion(String sdkVersion) {
      this.sdkVersion = Preconditions.checkNotNull(sdkVersion);
    }

    /**
     * Returns the SDK version if it has been set and a string
     * indicating an unknown version if it has not been set.
     */
    String getSdkVersion() {
      return sdkVersion;
    }
  }
}
