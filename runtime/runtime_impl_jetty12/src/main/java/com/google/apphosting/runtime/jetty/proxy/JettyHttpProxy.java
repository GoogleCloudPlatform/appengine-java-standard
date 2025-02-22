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

package com.google.apphosting.runtime.jetty.proxy;

import com.google.apphosting.base.protos.AppLogsPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.AppEngineConstants;
import com.google.apphosting.runtime.LocalRpcContext;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.apphosting.runtime.jetty.AppInfoFactory;
import com.google.apphosting.runtime.jetty.AppVersionHandlerFactory;
import com.google.common.base.Ascii;
import com.google.common.base.Throwables;
import com.google.common.flogger.GoogleLogger;
import com.google.common.primitives.Ints;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.MultiPartCompliance;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SizeLimitHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.Callback;

/**
 * A Jetty web server handling HTTP requests on a given port and forwarding them via gRPC to the
 * Java8 App Engine runtime implementation. The deployed application is assumed to be located in a
 * location provided via a flag, or infered to "/base/data/home/apps/" + APP_ID + "/" + APP_VERSION
 * where APP_ID and APP_VERSION come from env variables (GAE_APPLICATION and GAE_VERSION), with some
 * default values. The logic relies on the presence of "WEB-INF/appengine-generated/app.yaml" so the
 * deployed app should have been staged by a GAE SDK before it can be served.
 *
 * <p>When used as a Docker Titanium image, you can create the image via a Dockerfile like:
 *
 * <pre>
 * FROM gcr.io/gae-gcp/java8-runtime-http-proxy
 * # for now s~ is needed for API calls.
 * ENV GAE_APPLICATION s~myapp
 * ENV GAE_VERSION myversion
 * ADD . /appdata/s~myapp/myversion
 * </pre>
 */
public class JettyHttpProxy {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final long MAX_REQUEST_SIZE = 32 * 1024 * 1024;
  private static final long MAX_RESPONSE_SIZE = 32 * 1024 * 1024;

  /**
   * Based on the adapter configuration, this will start a new Jetty server in charge of proxying
   * HTTP requests to the App Engine Java runtime.
   */
  public static void startServer(ServletEngineAdapter.Config runtimeOptions) {
    try {
      ForwardingHandler handler = new ForwardingHandler(runtimeOptions, System.getenv());
      Server server = newServer(runtimeOptions, handler);
      server.start();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static ServerConnector newConnector(
      Server server, ServletEngineAdapter.Config runtimeOptions) {
    ServerConnector connector =
        new JettyServerConnectorWithReusePort(server, runtimeOptions.jettyReusePort());
    connector.setHost(runtimeOptions.jettyHttpAddress().getHost());
    connector.setPort(runtimeOptions.jettyHttpAddress().getPort());

    HttpConfiguration config =
        connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();

    // If runtime is using EE8, then set URI compliance to LEGACY to behave like Jetty 9.4.
    if (Objects.equals(AppVersionHandlerFactory.getEEVersion(), AppVersionHandlerFactory.EEVersion.EE8)) {
      config.setUriCompliance(UriCompliance.LEGACY);
    }

    if (AppEngineConstants.LEGACY_MODE) {
      config.setUriCompliance(UriCompliance.LEGACY);
      config.setHttpCompliance(HttpCompliance.RFC7230_LEGACY);
      config.setRequestCookieCompliance(CookieCompliance.RFC2965);
      config.setResponseCookieCompliance(CookieCompliance.RFC2965);
      config.setMultiPartCompliance(MultiPartCompliance.LEGACY);
    }

    config.setRequestHeaderSize(runtimeOptions.jettyRequestHeaderSize());
    config.setResponseHeaderSize(runtimeOptions.jettyResponseHeaderSize());
    config.setSendDateHeader(false);
    config.setSendServerVersion(false);
    config.setSendXPoweredBy(false);

    return connector;
  }

  public static void insertHandlers(Server server, boolean ignoreResponseSizeLimit) {

    long responseLimit = -1;
    if (!ignoreResponseSizeLimit) {
      responseLimit = MAX_RESPONSE_SIZE;
    }
    SizeLimitHandler sizeLimitHandler = new SizeLimitHandler(MAX_REQUEST_SIZE, responseLimit);
    server.insertHandler(sizeLimitHandler);

    GzipHandler gzip = new GzipHandler();
    gzip.setInflateBufferSize(8 * 1024);
    gzip.setIncludedMethods(); // Include all methods for the GzipHandler.
    server.insertHandler(gzip);
  }

  public static Server newServer(
      ServletEngineAdapter.Config runtimeOptions, ForwardingHandler forwardingHandler) {
    Server server = new Server();
    server.setHandler(forwardingHandler);
    insertHandlers(server, true);

    ServerConnector connector = newConnector(server, runtimeOptions);
    server.addConnector(connector);

    logger.atInfo().log("Starting Jetty http server for Java runtime proxy.");
    return server;
  }

  /**
   * Handler to stub out the frontend server. This has to launch the runtime, configure the user's
   * app into it, and then forward HTTP requests over gRPC to the runtime and decode the responses.
   */
  // The class has to be public, as it is a Servlet that needs to be loaded by the Jetty server.
  public static class ForwardingHandler extends Handler.Abstract {

    private static final String X_APPENGINE_TIMEOUT_MS = "x-appengine-timeout-ms";

    private final EvaluationRuntimeServerInterface evaluationRuntimeServerInterface;
    private final UPRequestTranslator upRequestTranslator;

    public ForwardingHandler(ServletEngineAdapter.Config runtimeOptions, Map<String, String> env) {
      this.evaluationRuntimeServerInterface = runtimeOptions.evaluationRuntimeServerInterface();
      this.upRequestTranslator =
          new UPRequestTranslator(
              new AppInfoFactory(env),
              runtimeOptions.passThroughPrivateHeaders(),
              /* skipPostData= */ false);
    }

    /**
     * Forwards a request to the real runtime for handling. We translate the {@link Request} types
     * into protocol buffers and send the request, then translate the response proto back to a
     * {@link Response}.
     */
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
      // build the request object
      RuntimePb.UPRequest upRequest = upRequestTranslator.translateRequest(request);

      try {
        UPResponse upResponse = getUpResponse(upRequest);
        upRequestTranslator.translateResponse(response, upResponse, callback);
      } catch (Throwable t) {
        String errorMsg = "Can't make request of app: " + Throwables.getStackTraceAsString(t);
        UPRequestTranslator.populateErrorResponse(response, errorMsg, callback);
      }

      return true;
    }

    /**
     * Get the UP response
     *
     * @param upRequest The UP request to send
     * @return The UP response
     * @throws ExecutionException Error getting the response
     * @throws InterruptedException Interrupted while waiting for response
     */
    UPResponse getUpResponse(UPRequest upRequest) throws ExecutionException, InterruptedException {
      // Read time remaining in request from headers and pass value to LocalRpcContext for use in
      // reporting remaining time until deadline for API calls (see b/154745969)
      Duration timeRemaining =
          upRequest.getRuntimeHeadersList().stream()
              .filter(p -> Ascii.equalsIgnoreCase(p.getKey(), X_APPENGINE_TIMEOUT_MS))
              .map(p -> Duration.ofMillis(Long.parseLong(p.getValue())))
              .findFirst()
              .orElse(Duration.ofNanos(Long.MAX_VALUE));

      LocalRpcContext<UPResponse> context = new LocalRpcContext<>(UPResponse.class, timeRemaining);
      evaluationRuntimeServerInterface.handleRequest(context, upRequest);
      UPResponse upResponse = context.getResponse();
      for (AppLogsPb.AppLogLine line : upResponse.getAppLogList()) {
        logger.at(toJavaLevel(line.getLevel())).log("%s", line.getMessage());
      }
      return upResponse;
    }
  }

  private static Level toJavaLevel(long level) {
    switch (Ints.saturatedCast(level)) {
      case 0:
        return Level.FINE;
      case 1:
        return Level.INFO;
      case 3:
      case 4:
        return Level.SEVERE;
      default:
        return Level.WARNING;
    }
  }

  private JettyHttpProxy() {}
}
