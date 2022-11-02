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

import static com.google.common.base.Preconditions.checkState;
import static com.google.datastore.v1.client.DatastoreHelper.LOCAL_HOST_ENV_VAR;
import static com.google.datastore.v1.client.DatastoreHelper.PRIVATE_KEY_FILE_ENV_VAR;
import static com.google.datastore.v1.client.DatastoreHelper.PROJECT_ID_ENV_VAR;
import static com.google.datastore.v1.client.DatastoreHelper.SERVICE_ACCOUNT_ENV_VAR;
import static java.util.Objects.requireNonNull;

import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.api.utils.SystemProperty.Environment.Value;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.EnvironmentFactory;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** See {@link CloudDatastoreRemoteServiceConfig}. */
// TODO(b/64163395): consider merging with CloudDatastoreRemoteServiceConfig
@AutoValue
abstract class DatastoreServiceGlobalConfig {

  private static final Logger logger =
      Logger.getLogger(DatastoreServiceGlobalConfig.class.getName());

  static final String ADDITIONAL_APP_IDS_VAR = "DATASTORE_ADDITIONAL_APP_IDS";
  static final String USE_PROJECT_ID_AS_APP_ID_VAR = "DATASTORE_USE_PROJECT_ID_AS_APP_ID";
  static final String APP_ID_VAR = "DATASTORE_APP_ID";

  static final int DEFAULT_MAX_RETRIES = 3;
  // Matches the default in com.google.api.client.http.HttpRequest.
  static final int DEFAULT_HTTP_CONNECT_TIMEOUT_MILLIS = 20 * 1000;

  private static final Splitter COMMA_SPLITTER = Splitter.on(',');

  private static DatastoreServiceGlobalConfig config;

  static synchronized DatastoreServiceGlobalConfig getConfig() {
    if (config == null) {
      setConfig(DatastoreServiceGlobalConfig.fromEnv());
    }
    return config;
  }

  static synchronized void setConfig(DatastoreServiceGlobalConfig config) {
    Preconditions.checkState(
        DatastoreServiceGlobalConfig.config == null,
        "DatastoreServiceGlobalConfig was already set.");

    // Make sure we are using API proxy mode when running on App Engine Standard.
    if (!config.useApiProxy()) {
      if (SystemProperty.environment.value() == Value.Development) {
        logger.warning(
            "Using non-API proxy mode in the development server. "
                + "This mode will not work on production App Engine Standard.");
      } else if (SystemProperty.environment.value() == Value.Production) {
        if (isRunningOnAppEngineStandard()) {
          throw new IllegalStateException(
              "Cannot use non-API proxy mode on production App Engine Standard.");
        } else {
          logger.info("Allowing non-API proxy mode on production App Engine Flex.");
        }
      }
      // SystemProperty.environment.value() is null for non-managed GCE instances
      // and non-API proxy mode is allowed.
    }

    DatastoreServiceGlobalConfig.config = config;
    maybeSetUpApiProxyEnvironment();
  }

  private static boolean isRunningOnAppEngineStandard() {
    // The first condition holds true for Java 8 Standard and *will* hold true for Java 7 Standard
    // in the near future, but in the interim we fall back to the second condition.
    // TODO: remove the second conditional when GAE_ENV makes it to prod Java 7
    String gaeEnv = EnvProxy.getenv("GAE_ENV");
    return "standard".equals(gaeEnv)
        || (gaeEnv == null
            && EnvProxy.getenv("GAE_VM") == null
            && EnvProxy.getenv("GCLOUD_PROJECT") == null);
  }

  static synchronized void clearConfig() {
    config = null;
  }

  static synchronized void clear() {
    clearConfig();
    try {
      maybeClearApiProxyEnvironment();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  static void maybeClearApiProxyEnvironment() throws ReflectiveOperationException {
    // TODO and call it here.
    Field field = ApiProxy.class.getDeclaredField("environmentFactory");
    field.setAccessible(true);
    Object environmentFactory = field.get(null);
    if (!(environmentFactory instanceof StubApiProxyEnvironmentFactory)) {
      // We did not install this.
      return;
    }
    field.set(null, null);
    // We don't/can't clear environments that may have been set on other threads.
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  // Nothing prevents another thread from trying to set up the API proxy at the same time, but
  // at least this class won't do it from multiple threads at the same time.
  private static synchronized void maybeSetUpApiProxyEnvironment() {
    if (getConfig().useApiProxy()
        || ApiProxy.getCurrentEnvironment() != null
        || !getConfig().installApiProxyEnvironment()) {
      // Don't touch anything.
      // If you've already installed an Environment, we assume you've set an appropriate app ID.
      // We also won't bother setting additional app IDs.
      return;
    }

    // We're going to install it for you.
    ApiProxy.setEnvironmentFactory(
        new StubApiProxyEnvironmentFactory(getConfig().configuredAppId()));

    // Add additional app IDs.
    ImmutableSet<String> additionalAppIds =
        DatastoreServiceGlobalConfig.getConfig().additionalAppIds();
    if (additionalAppIds != null) {
      ImmutableMap.Builder<String, String> projectIdToAppId = ImmutableMap.builder();
      for (String appId : additionalAppIds) {
        projectIdToAppId.put(DatastoreApiHelper.toProjectId(appId), appId.toString());
      }
      ApiProxy.Environment env = requireNonNull(ApiProxy.getCurrentEnvironment());
      env.getAttributes()
          .put(
              DataTypeTranslator.ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY,
              projectIdToAppId.buildOrThrow());
    }
  }

  /**
   * Returns the current {@link Environment}.
   *
   * <p>If no {@link Environment} has been configured, installs a stubbed-out version and returns
   * it.
   */
  static Environment getCurrentApiProxyEnvironment() {
    // TODO This call should _only_ happen in the setConfig() call.
    // For now, support lazy initialization for existing users that are using environment variables
    // to use Cloud Datastore v1.
    maybeSetUpApiProxyEnvironment();
    return ApiProxy.getCurrentEnvironment();
  }

  abstract boolean useApiProxy();

  @Nullable
  abstract String hostOverride();

  @Nullable
  abstract ImmutableSet<String> additionalAppIds();

  abstract boolean installApiProxyEnvironment();

  @Nullable
  abstract String appId();

  @Nullable
  abstract String projectId();

  abstract boolean useProjectIdAsAppId();

  @Nullable
  abstract String emulatorHost();

  @Nullable
  abstract String accessToken();

  @Nullable
  abstract String serviceAccount();

  @Nullable
  abstract PrivateKey privateKey();

  @Nullable
  abstract String privateKeyFile();

  abstract boolean useComputeEngineCredential();

  abstract int maxRetries();

  abstract int httpConnectTimeoutMillis();

  abstract boolean asyncStackTraceCaptureEnabled();

  /**
   * Returns the app ID that should be used in actual API objects. Could be an app ID or a project
   * ID depending on how the user has configured things.
   */
  String configuredAppId() {
    if (appId() != null) {
      return appId().toString();
    }
    checkState(useProjectIdAsAppId());
    return projectId();
  }

  /** Returns a {@link DatastoreServiceGlobalConfig.Builder}. */
  static DatastoreServiceGlobalConfig.Builder builder() {
    return new AutoValue_DatastoreServiceGlobalConfig.Builder()
        .useApiProxy(false)
        .useProjectIdAsAppId(false)
        .installApiProxyEnvironment(true)
        .useComputeEngineCredential(false)
        .maxRetries(DEFAULT_MAX_RETRIES)
        .httpConnectTimeoutMillis(DEFAULT_HTTP_CONNECT_TIMEOUT_MILLIS)
        .asyncStackTraceCaptureEnabled(true);
  }

  /** Builder for {@link DatastoreServiceGlobalConfig}. */
  @AutoValue.Builder
  abstract static class Builder {

    abstract DatastoreServiceGlobalConfig.Builder appId(String value);

    abstract DatastoreServiceGlobalConfig.Builder emulatorHost(String value);

    abstract DatastoreServiceGlobalConfig.Builder hostOverride(String value);

    abstract DatastoreServiceGlobalConfig.Builder additionalAppIds(Set<String> value);

    abstract DatastoreServiceGlobalConfig.Builder installApiProxyEnvironment(boolean value);

    abstract DatastoreServiceGlobalConfig.Builder useApiProxy(boolean value);

    abstract DatastoreServiceGlobalConfig.Builder accessToken(String value);

    abstract DatastoreServiceGlobalConfig.Builder serviceAccount(String value);

    abstract DatastoreServiceGlobalConfig.Builder privateKey(PrivateKey value);

    // TODO: Remove once no customers are configuring the SDK using environment
    // variables.
    abstract DatastoreServiceGlobalConfig.Builder privateKeyFile(String value);

    abstract DatastoreServiceGlobalConfig.Builder useComputeEngineCredential(boolean value);

    abstract DatastoreServiceGlobalConfig.Builder maxRetries(int value);

    abstract DatastoreServiceGlobalConfig.Builder httpConnectTimeoutMillis(int value);

    // TODO: Remove once no customers are configuring the SDK using environment
    // variables.
    abstract DatastoreServiceGlobalConfig.Builder projectId(String value);

    // TODO: Remove once no customers are configuring the SDK using environment
    // variables.
    abstract DatastoreServiceGlobalConfig.Builder useProjectIdAsAppId(boolean value);

    abstract DatastoreServiceGlobalConfig.Builder asyncStackTraceCaptureEnabled(boolean value);

    abstract DatastoreServiceGlobalConfig autoBuild();

    /**
     * Build a {@link DatastoreServiceGlobalConfig} instance.
     *
     * @throws IllegalStateException if the {@link DatastoreServiceGlobalConfig} instance is
     *     invalid.
     */
    DatastoreServiceGlobalConfig build() {
      DatastoreServiceGlobalConfig config = autoBuild();

      // Most options don't apply if we're using the API Proxy.
      if (config.useApiProxy()) {
        checkState(config.appId() == null, "Cannot specify app ID when using API proxy.");
        checkState(
            config.hostOverride() == null, "Cannot specify host override when using API proxy.");
        checkState(
            config.additionalAppIds() == null,
            "Cannot specify additional app IDs when using API proxy.");
        checkState(
            config.accessToken() == null, "Cannot specify access token when using API proxy.");
        checkState(
            config.serviceAccount() == null,
            "Cannot specify service account when using API proxy.");
        checkState(config.privateKey() == null, "Cannot specify private key when using API proxy.");
        checkState(
            config.privateKeyFile() == null,
            "Cannot specify private key file when using API proxy.");
        checkState(config.projectId() == null, "Cannot specify project ID when using API proxy.");
        checkState(
            !config.useProjectIdAsAppId(), "Cannot use project ID as app ID when using API proxy.");
        return config;
      }

      // Make sure app ID is provided unambiguously.
      checkState(
          config.appId() == null || config.projectId() == null,
          "Cannot provide both app ID and project ID.");
      if (config.appId() != null) {
        checkState(
            !config.useProjectIdAsAppId(),
            "Cannot use project ID as app ID if app ID was provided.");
      } else if (config.projectId() != null) {
        checkState(
            config.useProjectIdAsAppId(),
            "Must use project ID as app ID if project ID is provided.");
      }

      if (config.emulatorHost() != null) {
        checkState(
            config.hostOverride() == null, "Cannot provide both host override and emulator host.");
      }

      checkState(
          config.privateKey() == null || config.privateKeyFile() == null,
          "Must not provide both a private key and a private key file.");
      boolean providedPrivateKey = config.privateKey() != null || config.privateKeyFile() != null;
      checkState(
          (config.serviceAccount() != null) == providedPrivateKey,
          "Service account must be provided if and only if private key or private key"
              + " file is provided.");
      checkState(
          !(config.serviceAccount() != null && config.useComputeEngineCredential()),
          "Must not provide a service account and at the same time require the use of Compute "
              + "Engine credentials.");
      checkState(
          config.accessToken() == null || config.serviceAccount() == null,
          "Must not provide both an access token and a service account.");

      return config;
    }
  }

  /** Creates an {@link DatastoreServiceGlobalConfig} objects from environment variables. */
  private static DatastoreServiceGlobalConfig fromEnv() {
    DatastoreServiceGlobalConfig.Builder builder = DatastoreServiceGlobalConfig.builder();

    // TODO: Once DatastoreServiceGlobalConfig is made public, log warnings when the
    // SDK is configured via environment variables.
    boolean useCloudDatastoreApi =
        Boolean.parseBoolean(EnvProxy.getenv("DATASTORE_USE_CLOUD_DATASTORE"))
            || EnvProxy.getenv(APP_ID_VAR) != null
            || EnvProxy.getenv(PROJECT_ID_ENV_VAR) != null;
    builder.useApiProxy(!useCloudDatastoreApi);

    if (EnvProxy.getenv(ADDITIONAL_APP_IDS_VAR) != null) {
      Set<String> additionalAppIds = new HashSet<>();
      for (String appId : COMMA_SPLITTER.split(EnvProxy.getenv(ADDITIONAL_APP_IDS_VAR))) {
        appId = appId.trim();
        if (!appId.isEmpty()) {
          additionalAppIds.add(appId);
        }
      }
      builder.additionalAppIds(additionalAppIds);
    }
    if (EnvProxy.getenv(APP_ID_VAR) != null) {
      builder.appId(EnvProxy.getenv(APP_ID_VAR));
    }
    if (EnvProxy.getenv(PROJECT_ID_ENV_VAR) != null) {
      builder.projectId(EnvProxy.getenv(PROJECT_ID_ENV_VAR));
    }
    builder.useProjectIdAsAppId(
        Boolean.parseBoolean(EnvProxy.getenv(USE_PROJECT_ID_AS_APP_ID_VAR)));
    if (EnvProxy.getenv(LOCAL_HOST_ENV_VAR) != null) {
      builder.emulatorHost(EnvProxy.getenv(LOCAL_HOST_ENV_VAR));
    }
    if (EnvProxy.getenv(SERVICE_ACCOUNT_ENV_VAR) != null) {
      builder.serviceAccount(EnvProxy.getenv(SERVICE_ACCOUNT_ENV_VAR));
    }
    if (EnvProxy.getenv(PRIVATE_KEY_FILE_ENV_VAR) != null) {
      builder.privateKeyFile(EnvProxy.getenv(PRIVATE_KEY_FILE_ENV_VAR));
    }

    return builder.build();
  }

  /** An {@link EnvironmentFactory} that builds {@link StubApiProxyEnvironment}s. */
  static class StubApiProxyEnvironmentFactory implements EnvironmentFactory {
    private final String appId;

    public StubApiProxyEnvironmentFactory(String appId) {
      this.appId = appId;
    }

    @Override
    public Environment newEnvironment() {
      return new StubApiProxyEnvironment(appId);
    }
  }

  /**
   * An {@link Environment} that supports the minimal subset of features needed to run code from the
   * datastore package outside of App Engine. All other methods throw {@link
   * UnsupportedOperationException}.
   */
  /* @VisibleForTesting */
  static class StubApiProxyEnvironment implements Environment {
    private final Map<String, Object> attributes;
    private final String appId;

    public StubApiProxyEnvironment(String appId) {
      this.attributes = new HashMap<>();
      this.appId = appId;
    }

    @Override
    public boolean isLoggedIn() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAdmin() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getVersionId() {
      throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public String getRequestNamespace() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getRemainingMillis() {
      // FutureHelper.getInternal() uses this.
      return 1L;
    }

    @Override
    public String getModuleId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getEmail() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getAuthDomain() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    @Override
    public String getAppId() {
      return appId;
    }
  }
}
