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

package com.google.apphosting.runtime.jetty94;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.utils.config.AppYaml;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import javax.annotation.Nullable;

/** Builds AppinfoPb.AppInfo from the given ServletEngineAdapter.Config and environment. */
public class AppInfoFactory {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String DEFAULT_CLOUD_PROJECT = "testapp";
  private static final String DEFAULT_GAE_APPLICATION = "s~testapp";
  private static final String DEFAULT_GAE_SERVICE = "default";
  private static final String DEFAULT_GAE_VERSION = "1.0";
  /** Path in the WAR layout to app.yaml */
  private static final String APP_YAML_PATH = "WEB-INF/appengine-generated/app.yaml";

  private final String applicationRoot;
  private final String fixedApplicationPath;
  private final String gaeVersion;
  private final String googleCloudProject;
  private final String gaeApplication;
  private final String gaeService;
  private final String gaeServiceVersion;

  public AppInfoFactory(
      String applicationRoot, String fixedApplicationPath, Map<String, String> env) {
    this.applicationRoot = applicationRoot;
    this.fixedApplicationPath = fixedApplicationPath;
    String version = env.getOrDefault("GAE_VERSION", DEFAULT_GAE_VERSION);
    String deploymentId = env.getOrDefault("GAE_DEPLOYMENT_ID", null);
    gaeServiceVersion = (deploymentId != null) ? version + "." + deploymentId : version;
    gaeService = env.getOrDefault("GAE_SERVICE", DEFAULT_GAE_SERVICE);
    // Prepend service if it exists, otherwise do not prepend DEFAULT (go/app-engine-ids)
    gaeVersion =
        DEFAULT_GAE_SERVICE.equals(this.gaeService)
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
  public AppinfoPb.AppInfo getAppInfoFromFile() throws IOException {
    // App should be located under /base/data/home/apps/appId/versionID or in the optional
    // fixedApplicationPath parameter.
    String applicationPath =
        (fixedApplicationPath == null)
            ? applicationRoot + "/" + googleCloudProject + "/" + gaeServiceVersion
            : fixedApplicationPath;

    if (!new File(applicationPath).exists()) {
      throw new NoSuchFileException("Application does not exist under: " + applicationPath);
    }
    @Nullable String apiVersion = null;
    File appYamlFile = new File(applicationPath, APP_YAML_PATH);
    try {
      YamlReader reader = new YamlReader(Files.newBufferedReader(appYamlFile.toPath(), UTF_8));
      Object apiVersionObj = ((Map<?, ?>) reader.read()).get("api_version");
      if (apiVersionObj != null) {
        apiVersion = (String) apiVersionObj;
      }
    } catch (NoSuchFileException ex) {
      logger.atInfo().log(
          "Cannot configure App Engine APIs, because the generated app.yaml file "
              + "does not exist: %s",
          appYamlFile.getAbsolutePath());
    }
    return getAppInfoWithApiVersion(apiVersion);
  }

  public AppinfoPb.AppInfo getAppInfoFromAppYaml(AppYaml appYaml) throws IOException {
    return getAppInfoWithApiVersion(appYaml.getApi_version());
  }

  private AppinfoPb.AppInfo getAppInfoWithApiVersion(@Nullable String apiVersion) {
    final AppinfoPb.AppInfo.Builder appInfoBuilder =
        AppinfoPb.AppInfo.newBuilder()
            .setAppId(gaeApplication)
            .setVersionId(gaeVersion)
            .setRuntimeId("java8");

    if (apiVersion != null) {
      appInfoBuilder.setApiVersion(apiVersion);
    }

    return appInfoBuilder.build();
  }
}
