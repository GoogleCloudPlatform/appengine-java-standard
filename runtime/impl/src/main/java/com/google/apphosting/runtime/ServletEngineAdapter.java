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

import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.auto.value.AutoValue;
import com.google.common.net.HostAndPort;
import java.io.FileNotFoundException;
import javax.annotation.Nullable;

/**
 * This interface abstracts away the details of starting up and
 * shutting down a servlet engine, as well as adapting between the
 * concrete classes that implement the Java Servlet API and the
 * Prometheus Untrusted Process API.
 *
 */
public interface ServletEngineAdapter extends UPRequestHandler {
  /**
   * Performs whatever setup is necessary for this servlet container. This method waits for setup to
   * complete before returning.
   *
   * @param serverInfo The string that should be returned by {@code ServletContext.getServerInfo()}.
   * @param runtimeOptions Extra options, currently used for the Jetty HTTP adapter only.
   */
  void start(String serverInfo, ServletEngineAdapter.Config runtimeOptions);

  /**
   * Perform any shutdown procedures necessary for this servlet
   * container.  This method should return once the shutdown has
   * been completed.
   */
  void stop();

  /**
   * Register the specified application version for future calls to
   * {@code serviceRequest}.
   *
   * @throws FileNotFoundException If any of the specified files could
   * not be located.
   */
  void addAppVersion(AppVersion appVersion) throws FileNotFoundException;

  /**
   * Remove the specified application version and free up any
   * resources associated with it.
   */
  void deleteAppVersion(AppVersion appVersion);

  /**
   * Sets the {@link SessionStoreFactory} that will be used to create the list
   * of {@link SessionStore}s to which the HTTP Session will be
   * stored, if sessions are enabled. This method must be invoked after
   * {@link #start}.
   */
  void setSessionStoreFactory(SessionStoreFactory factory);

  /**
   * Options to configure a Jetty HTTP server, forwarding servlet requests to the GAE Java runtime.
   */
  @AutoValue
  abstract class Config {
    /** Boolean to turn on the Jetty HTTP server. False by default. */
    public abstract boolean useJettyHttpProxy();

    /**
     * Base root area for a given application. The exploded web app can be located under the
     * appId/appVersion directory, to be fully compatible with GAE, or given as a Java runtime flag.
     */
    public abstract String applicationRoot();

    /**
     * Fixed path to use for the application root directory, irrespective of the application id and
     * version. Ignored if not specified.
     */
    @Nullable
    public abstract String fixedApplicationPath();

    /** Host and port used by the Jetty HTTP server. Defaults to [::]:8080. */
    public abstract HostAndPort jettyHttpAddress();

    /** Whether to set SO_REUSEPORT on the Jetty HTTP port. */
    public abstract boolean jettyReusePort();

    /** Jetty server's max size for HTTP request headers. Defaults to 16384. */
    public abstract int jettyRequestHeaderSize();

    /** Jetty server's max size for HTTP response headers. Defaults to 16384. */
    public abstract int jettyResponseHeaderSize();

    /** A local server that can be called directly to satisfy EvaluationRuntime requests. */
    @Nullable
    public abstract EvaluationRuntimeServerInterface evaluationRuntimeServerInterface();

    /** Whether to pass through all headers to the web app, including X-AppEngine-*. */
    public abstract boolean passThroughPrivateHeaders();

    /** Returns an {@code Config.Builder}. */
    public static Builder builder() {
      return new AutoValue_ServletEngineAdapter_Config.Builder()
          .setUseJettyHttpProxy(false)
          .setJettyHttpAddress(HostAndPort.fromParts("::", 8080))
          .setJettyReusePort(false)
          .setJettyRequestHeaderSize(16384)
          .setJettyResponseHeaderSize(16384)
          .setApplicationRoot("/base/data/home/apps")
          .setPassThroughPrivateHeaders(false);
    }

    /** Builder for {@code Config} instances. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setUseJettyHttpProxy(boolean useJettyHttpProxy);

      public abstract Builder setApplicationRoot(String applicationRoot);

      public abstract Builder setFixedApplicationPath(@Nullable String applicationpath);

      public abstract Builder setJettyHttpAddress(HostAndPort jettyListenAddress);

      public abstract Builder setJettyReusePort(boolean jettyReusePort);

      public abstract Builder setJettyRequestHeaderSize(int jettyRequestHeaderSize);

      public abstract Builder setJettyResponseHeaderSize(int jettyResponseHeaderSize);

      public abstract Builder setEvaluationRuntimeServerInterface(
          EvaluationRuntimeServerInterface server);

      public abstract Builder setPassThroughPrivateHeaders(boolean passThroughPrivateHeaders);

      /** Returns a configured {@code Config} instance. */
      public abstract Config build();
    }
  }
}
