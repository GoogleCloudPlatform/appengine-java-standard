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

package com.google.apphosting.runtime;

import com.google.auto.value.AutoValue;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The process environment for an application. Under typical circumstances, a JVM
 * normally has one process environment, but under Prometheus, we configure each
 * application with their own isolated environment.
 *
 */
public class ApplicationEnvironment {

  private final String appId;
  private final String versionId;
  private final Map<String, String> systemProperties;
  private final Map<String, String> environmentVariables;
  private final File rootDirectory;

  /**
   * This list of properties we copy directly into the user's local environment, without
   * modification.
   */
  private static final String[] VISIBLE_PROPERTIES = {
    "file.encoding",
    "file.separator",
    "path.separator",
    "line.separator",
    "os.name", // N.B. Java sets this to "Linux"
    "java.vendor",
    "java.vendor.url",
    "java.class.version",
    "java.specification.version",
    "java.specification.vendor",
    "java.specification.name",
    "java.vm.vendor",
    "java.vm.name",
    "java.vm.specification.version",
    "java.vm.specification.vendor",
    "java.vm.specification.name",
  };

  private RuntimeConfiguration runtimeConfiguration;

  private @Nullable Boolean useGoogleConnectorJ;

  /**
   * Contains configuration parameters for the Java runtime which are relative
   * to the specific application denoted by the ApplicationEnvironment.
   */
  @AutoValue
  public abstract static class RuntimeConfiguration {
    public static final RuntimeConfiguration DEFAULT_FOR_TEST = builder().build();

    public static Builder builder() {
      return new AutoValue_ApplicationEnvironment_RuntimeConfiguration.Builder();
    }

    public abstract Builder toBuilder();

    /** Builder for RuntimeConfiguration. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract RuntimeConfiguration build();
    }
  }

  /**
   * Creates a new ApplicationEnvironment for an application.
   *
   * @param appId the application id
   * @param versionId the version id of the application
   * @param extraSystemProperties system properties above and beyond the
   *     default allowed system properties.
   * @param environmentVariables System.getenv
   * @param rootDirectory root directory for the app
   * @param configuration the runtime configuration for the application
   */
  public ApplicationEnvironment(
      String appId,
      String versionId,
      Map<String, String> extraSystemProperties,
      Map<String, String> environmentVariables,
      File rootDirectory,
      RuntimeConfiguration configuration) {
    this.appId = appId;
    this.versionId = versionId;
    this.systemProperties = new HashMap<>(extraSystemProperties);
    this.rootDirectory = rootDirectory;

    for (String property : VISIBLE_PROPERTIES) {
      systemProperties.computeIfAbsent(property, System::getProperty);
    }

    // Tell users what they should expect their current directory to be.
    systemProperties.put("user.dir", rootDirectory.getPath());

    this.environmentVariables = new HashMap<>(environmentVariables);
    this.runtimeConfiguration = configuration;
  }

  public String getAppId() {
    return appId;
  }

  public String getVersionId() {
    return versionId;
  }

  public RuntimeConfiguration getRuntimeConfiguration() {
    return runtimeConfiguration;
  }

  public Map<String, String> getSystemProperties() {
    return systemProperties;
  }

  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  /**
   * Checks whether the application chose to disable Cloud Debugger.
   *
   * <p>The Cloud Debugger (once rolled out) will be enabled by default. Developer can disable
   * the debugger by setting "CDBG_DISABLE" environment variable to "1".
   */
  public boolean isCloudDebuggerDisabled() {
    return "1".equals(environmentVariables.get("CDBG_DISABLE"));
  }

  public File getRootDirectory() {
    return rootDirectory;
  }

  public void setUseGoogleConnectorJ(@Nullable Boolean useGoogleConnectorJ) {
    this.useGoogleConnectorJ = useGoogleConnectorJ;
  }

  public boolean getUseGoogleConnectorJ() {
    return Optional.ofNullable(useGoogleConnectorJ)
        .orElse(true);
  }
}
