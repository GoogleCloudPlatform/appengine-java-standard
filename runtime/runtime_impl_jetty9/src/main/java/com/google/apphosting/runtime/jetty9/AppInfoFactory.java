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

package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.utils.config.AppYaml;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Objects;

/** Builds AppinfoPb.AppInfo from the given ServletEngineAdapter.Config and environment. */
public class AppInfoFactory {

  private static final String DEFAULT_CLOUD_PROJECT = "testapp";
  private static final String DEFAULT_GAE_APPLICATION = "s~testapp";
  private static final String DEFAULT_GAE_SERVICE = "default";
  private static final String DEFAULT_GAE_VERSION = "1.0";

  private final String gaeVersion;
  private final String googleCloudProject;
  private final String gaeApplication;
  private final String gaeService;
  private final String gaeServiceVersion;

  public AppInfoFactory(Map<String, String> env) {
    String version = env.getOrDefault("GAE_VERSION", DEFAULT_GAE_VERSION);
    String deploymentId = env.getOrDefault("GAE_DEPLOYMENT_ID", null);
    gaeServiceVersion = (deploymentId != null) ? version + "." + deploymentId : version;
    gaeService = env.getOrDefault("GAE_SERVICE", DEFAULT_GAE_SERVICE);
    // Prepend service if it exists, otherwise do not prepend DEFAULT (go/app-engine-ids)
    gaeVersion =
        Objects.equals(this.gaeService, DEFAULT_GAE_SERVICE)
            ? this.gaeServiceVersion
            : this.gaeService + ":" + this.gaeServiceVersion;
    googleCloudProject = env.getOrDefault("GOOGLE_CLOUD_PROJECT", DEFAULT_CLOUD_PROJECT);
    gaeApplication = env.getOrDefault("GAE_APPLICATION", DEFAULT_GAE_APPLICATION);
  }

  public String getGaeService() {
    return gaeService;
  }

  public String getGaeVersion() {
    return gaeVersion;
  }

  public String getGaeServiceVersion() {
    return gaeServiceVersion;
  }

  public String getGaeApplication() {
    return gaeApplication;
  }

  /** Creates a AppinfoPb.AppInfo object. */
  public AppinfoPb.AppInfo getAppInfoFromFile(String applicationRoot, String fixedApplicationPath)
      throws IOException {
    // App should be located under /base/data/home/apps/appId/versionID or in the optional
    // fixedApplicationPath parameter.
    String applicationPath =
        (fixedApplicationPath == null)
            ? applicationRoot + "/" + googleCloudProject + "/" + gaeServiceVersion
            : fixedApplicationPath;

    if (!new File(applicationPath).exists()) {
      throw new NoSuchFileException("Application does not exist under: " + applicationPath);
    }
    return getAppInfo();
  }

  public AppinfoPb.AppInfo getAppInfoFromAppYaml(AppYaml unused) throws IOException {
    return getAppInfo();
  }

  public AppinfoPb.AppInfo getAppInfo() {
    final AppinfoPb.AppInfo.Builder appInfoBuilder =
        AppinfoPb.AppInfo.newBuilder()
            .setAppId(gaeApplication)
            .setVersionId(gaeVersion)
            .setRuntimeId("java8");

    return appInfoBuilder.build();
  }
}
