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

package com.google.appengine.api.datastore;

import static java.util.Objects.requireNonNull;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.EnvironmentFactory;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.security.PrivateKey;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * User-configurable global properties of Cloud Datastore.
 *
 * <p>Code not running in App Engine Standard can use the Cloud Datastore API by making a single
 * call to {@link #setConfig} before accessing any other classes from {@link
 * com.google.appengine.api}. For example:
 *
 * <pre>{@code
 * public static void main(Strings[] args) {
 *   CloudDatastoreRemoteServiceConfig config = CloudDatastoreRemoteServiceConfig.builder()
 *       .appId(AppId.create(Location.US_CENTRAL, "my-project-id"))
 *       .build();
 *   CloudDatastoreRemoteServiceConfig.setConfig(config);
 *   DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
 *   ...
 * }
 * }</pre>
 *
 * Outside of tests, the config should not be cleared once it has been set. In tests, the config can
 * be cleared by calling {@link #clear}:
 *
 * <pre>{@code
 * {@literal @}Before
 * public void before() {
 *   CloudDatastoreRemoteServiceConfig config = CloudDatastoreRemoteServiceConfig.builder()
 *       .appId(AppId.create(Location.US_CENTRAL, "my-project-id"))
 *       .emulatorHost(...)
 *       .build();
 *   CloudDatastoreRemoteServiceConfig.setConfig(config);
 * }
 *
 * {@literal @}After
 * public void after() {
 *   CloudDatastoreRemoteServiceConfig.clear();
 * }
 * }</pre>
 *
 * By default, this configuration uses <a
 * href="https://developers.google.com/identity/protocols/application-default-credentials">application-default
 * credentials</a>.
 */
// As much implementation as possible is deferred to DatastoreServiceGlobalConfig.
//
// NOTE: If you add a field to this class, you must also updated the toInternalConfig()
// method.
@AutoValue
public abstract class CloudDatastoreRemoteServiceConfig {

  /**
   * Sets the {@link CloudDatastoreRemoteServiceConfig} instance.
   *
   * @throws IllegalStateException if the {@link CloudDatastoreRemoteServiceConfig} instance has
   *     already been set and {@link #clear} has not been called
   * @throws IllegalStateException if the provided {@link CloudDatastoreRemoteServiceConfig} is not
   *     supported in this environment
   */
  public static void setConfig(CloudDatastoreRemoteServiceConfig config) {
    DatastoreServiceGlobalConfig.setConfig(config.toInternalConfig());
  }

  /**
   * Clears the {@link CloudDatastoreRemoteServiceConfig} instance (if one has been set) as well as
   * the {@link ApiProxy}'s {@link EnvironmentFactory} and the {@link Environment} for the current
   * thread.
   *
   * <p>This method should only be called in tests.
   */
  public static void clear() {
    DatastoreServiceGlobalConfig.clear();
  }

  /** Converts this to a {@link DatastoreServiceGlobalConfig}. */
  private DatastoreServiceGlobalConfig toInternalConfig() {
    return DatastoreServiceGlobalConfig.builder()
        .appId(requireNonNull(appId()).appIdString())
        .emulatorHost(emulatorHost())
        .hostOverride(hostOverride())
        .additionalAppIds(additionalAppIdsAsStrings())
        .serviceAccount(serviceAccount())
        .accessToken(accessToken())
        .privateKey(privateKey())
        .useComputeEngineCredential(useComputeEngineCredential())
        .installApiProxyEnvironment(installApiProxyEnvironment())
        .maxRetries(maxRetries())
        .httpConnectTimeoutMillis(httpConnectTimeoutMillis())
        .asyncStackTraceCaptureEnabled(asyncStackTraceCaptureEnabled())
        .build();
  }

  /** An App Engine application ID. */
  @AutoValue
  public abstract static class AppId {
    /** Locations for App Engine applications. */
    public static enum Location {
      US_CENTRAL,
      EUROPE_WEST,
      US_EAST1,
      ASIA_NORTHEAST1,
      US_EAST4,
      AUSTRALIA_SOUTHEAST1,
      EUROPE_WEST1,
      EUROPE_WEST3;

      /**
       * Returns the {@link Location} for a location string. The location string is case-insensitive
       * and may use hyphens to separate components. For example, given the location string {@code
       * us-central}, this method returns {@link #US_CENTRAL}.
       *
       * @throws IllegalArgumentException if {@code locationString} does not correspond to a known
       *     {@link Location}
       * @throws NullPointerException if {@code locationString} is null
       */
      public static Location fromString(String locationString) {
        return valueOf(locationString.toUpperCase().replaceAll("-", "_"));
      }
    }

    abstract Location location();

    abstract String projectId();

    /**
     * Creates an {@link AppId}.
     *
     * @param location The location of the App Engine application. This can be found in the Google
     *     Cloud Console.
     * @param projectId The project ID of the App Engine application. This can be found in the
     *     Google Cloud Console.
     * @throws NullPointerException if {@code location} or {@code projectId} is null
     */
    public static AppId create(Location location, String projectId) {
      return new AutoValue_CloudDatastoreRemoteServiceConfig_AppId(location, projectId);
    }

    String appIdString() {
      return String.format("%s~%s", LocationMapper.getPartitionId(location()), projectId());
    }
  }

  abstract @Nullable AppId appId();

  abstract @Nullable String emulatorHost();

  abstract @Nullable String hostOverride();

  abstract @Nullable ImmutableSet<AppId> additionalAppIds();

  abstract boolean installApiProxyEnvironment();

  abstract @Nullable String serviceAccount();

  abstract @Nullable PrivateKey privateKey();

  abstract @Nullable String accessToken();

  abstract boolean useComputeEngineCredential();

  abstract int maxRetries();

  abstract int httpConnectTimeoutMillis();

  abstract boolean asyncStackTraceCaptureEnabled();

  @Nullable ImmutableSet<String> additionalAppIdsAsStrings() {
    if (additionalAppIds() == null) {
      return null;
    }
    ImmutableSet.Builder<String> appIds = ImmutableSet.builder();
    for (AppId appId : additionalAppIds()) {
      appIds.add(appId.toString());
    }
    return appIds.build();
  }

  /** Returns a {@link CloudDatastoreRemoteServiceConfig.Builder}. */
  public static CloudDatastoreRemoteServiceConfig.Builder builder() {
    return new AutoValue_CloudDatastoreRemoteServiceConfig.Builder()
        .installApiProxyEnvironment(true)
        .useComputeEngineCredential(false)
        .maxRetries(DatastoreServiceGlobalConfig.DEFAULT_MAX_RETRIES)
        .httpConnectTimeoutMillis(DatastoreServiceGlobalConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_MILLIS)
        .asyncStackTraceCaptureEnabled(
            true); // setting it to true by default as this was the original behavior
  }

  /** Builder for {@link CloudDatastoreRemoteServiceConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the {@link AppId} of the Cloud Datastore instance to call. Required. */
    public abstract CloudDatastoreRemoteServiceConfig.Builder appId(AppId value);

    /**
     * Instructs the client to connect to a locally-running Cloud Datastore Emulator and not to pass
     * credentials.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder emulatorHost(String value);

    /**
     * Overrides the host (e.g. {@code datastore.googleapis.com}) used to contact the Cloud
     * Datastore API. To connect to the Cloud Datastore Emulator, use {@link #emulatorHost} instead.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder hostOverride(String value);

    /**
     * Provides a set of additional app IDs that may appear in {@link Key} values in entities.
     *
     * <p>This is only required if the client will read entities containing {@link Key} values that
     * contain app IDs other than the one provided to {@link #appId}. Any such app IDs should be
     * provided to this method.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder additionalAppIds(Set<AppId> value);

    /**
     * If set to true, a minimal {@link Environment} will be installed (if none is already
     * installed).
     *
     * <p>If set to false, no attempt to install an environment will be made and the user must
     * install it instead. At a minimum, such an environment must provide implementations for {@link
     * Environment#getAppId()}, {@link Environment#getAttributes()}, and {@link
     * Environment#getRemainingMillis()}.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder installApiProxyEnvironment(
        boolean value);

    /**
     * If set to true, always use a Compute Engine credential instead of using the Application
     * Default Credentials library to construct the credential.
     *
     * <p>Cannot be combined with a call to {@link #useServiceAccountCredential(String, PrivateKey)}
     * or {@link #accessToken(String)}.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder useComputeEngineCredential(
        boolean value);

    /** Sets the maximum number of retries for underlying HTTP connect exceptions. */
    public abstract CloudDatastoreRemoteServiceConfig.Builder maxRetries(int value);

    /** Sets the HTTP connect timeout in milliseconds. */
    public abstract CloudDatastoreRemoteServiceConfig.Builder httpConnectTimeoutMillis(int value);

    /**
     * If set to true, stacktrace for async calls is captured and returned as part of error
     * messages. There is overhead in capturing this stack trace and it is recommended to enable it
     * primarily for debugging.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder asyncStackTraceCaptureEnabled(
        boolean value);

    /**
     * Sets the access token.
     *
     * <p>Cannot be combined with a call to {@link #useComputeEngineCredential(boolean)} or {@link
     * #useServiceAccountCredential(String, PrivateKey)}.
     */
    public abstract CloudDatastoreRemoteServiceConfig.Builder accessToken(String accessToken);
    /**
     * Instructs the client to use a service account credential instead of using the Application
     * Default Credentials library to construct the credential.
     *
     * <p>Cannot be combined with a call to {@link #useComputeEngineCredential(boolean)} or {@link
     * #accessToken(String)}.
     */
    public CloudDatastoreRemoteServiceConfig.Builder useServiceAccountCredential(
        String serviceAccountId, PrivateKey privateKey) {
      return serviceAccount(serviceAccountId).privateKey(privateKey);
    }

    abstract CloudDatastoreRemoteServiceConfig.Builder serviceAccount(String value);

    abstract CloudDatastoreRemoteServiceConfig.Builder privateKey(PrivateKey value);

    abstract CloudDatastoreRemoteServiceConfig autoBuild();

    public CloudDatastoreRemoteServiceConfig build() {
      CloudDatastoreRemoteServiceConfig config = autoBuild();
      // Trigger the validation logic in DatastoreServiceGlobalConfig.Builder.
      config.toInternalConfig();
      return config;
    }
  }
}
