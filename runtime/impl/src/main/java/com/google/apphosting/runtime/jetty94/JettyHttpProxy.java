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

import com.google.apphosting.base.protos.AppLogsPb;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.common.base.Ascii;
import com.google.common.base.Throwables;
import com.google.common.flogger.GoogleLogger;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;

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
  private static final String JETTY_LOG_CLASS = "org.eclipse.jetty.util.log.class";
  private static final String JETTY_STDERRLOG = "org.eclipse.jetty.util.log.StdErrLog";
  private static final long MAX_REQUEST_SIZE = 32 * 1024 * 1024;

  /**
   * Based on the adapter configuration, this will start a new Jetty server in charge of proxying
   * HTTP requests to the App Engine Java runtime.
   */
  public static void startServer(ServletEngineAdapter.Config runtimeOptions) {
    try {
      System.setProperty(JETTY_LOG_CLASS, JETTY_STDERRLOG);

      ForwardingHandler handler = new ForwardingHandler(runtimeOptions, System.getenv());
      handler.init();
      Server server = newServer(runtimeOptions, handler);
      server.start();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static Server newServer(
      ServletEngineAdapter.Config runtimeOptions, ForwardingHandler handler) {
    Server server = new Server();

    ServerConnector c =
        new JettyServerConnectorWithReusePort(server, runtimeOptions.jettyReusePort());
    c.setHost(runtimeOptions.jettyHttpAddress().getHost());
    c.setPort(runtimeOptions.jettyHttpAddress().getPort());
    server.setConnectors(new Connector[] {c});

    HttpConnectionFactory factory = c.getConnectionFactory(HttpConnectionFactory.class);
    factory.setHttpCompliance(
        RpcConnector.LEGACY_MODE ? HttpCompliance.RFC7230_LEGACY : HttpCompliance.RFC7230);

    HttpConfiguration config = factory.getHttpConfiguration();
    config.setRequestHeaderSize(runtimeOptions.jettyRequestHeaderSize());
    config.setResponseHeaderSize(runtimeOptions.jettyResponseHeaderSize());
    config.setSendDateHeader(false);
    config.setSendServerVersion(false);
    config.setSendXPoweredBy(false);

    SizeLimitHandler sizeLimitHandler = new SizeLimitHandler(MAX_REQUEST_SIZE, -1);
    sizeLimitHandler.setHandler(handler);

    GzipHandler gzip = new GzipHandler();
    gzip.setInflateBufferSize(8 * 1024);
    gzip.setHandler(sizeLimitHandler);

    // Include all methods for the GzipHandler.
    gzip.setIncludedMethods();

    server.setHandler(gzip);

    logger.atInfo().log("Starting Jetty http server for Java runtime proxy.");
    return server;
  }

  private static class LocalRpcContext<M extends MessageLite> implements AnyRpcServerContext {
    // We just dole out sequential ids here so we can tell requests apart in the logs.
    private static final AtomicLong globalIds = new AtomicLong();

    private final Class<M> responseMessageClass;
    private final long startTimeMillis;
    private final Duration timeRemaining;
    private final SettableFuture<M> futureResponse = SettableFuture.create();
    private final long globalId = globalIds.getAndIncrement();

    private LocalRpcContext(Class<M> responseMessageClass) {
      this(responseMessageClass, Duration.ofNanos((long) Double.MAX_VALUE));
    }

    private LocalRpcContext(Class<M> responseMessageClass, Duration timeRemaining) {
      this.responseMessageClass = responseMessageClass;
      this.startTimeMillis = System.currentTimeMillis();
      this.timeRemaining = timeRemaining;
    }

    @Override
    public void finishWithResponse(MessageLite response) {
      futureResponse.set(responseMessageClass.cast(response));
    }

    M getResponse() throws ExecutionException, InterruptedException {
      return futureResponse.get();
    }

    @Override
    public void finishWithAppError(int appErrorCode, String errorDetail) {
      String message = "AppError: code " + appErrorCode + "; errorDetail " + errorDetail;
      futureResponse.setException(new RuntimeException(message));
    }

    @Override
    public Duration getTimeRemaining() {
      return timeRemaining;
    }

    @Override
    public long getGlobalId() {
      return globalId;
    }

    @Override
    public long getStartTimeMillis() {
      return startTimeMillis;
    }
  }

  /**
   * Handler to stub out the frontend server. This has to launch the runtime, configure the user's
   * app into it, and then forward HTTP requests over gRPC to the runtime and decode the responses.
   */
  // The class has to be public, as it is a Servlet that needs to be loaded by the Jetty server.
  public static class ForwardingHandler extends AbstractHandler {

    private static final String X_APPENGINE_TIMEOUT_MS = "x-appengine-timeout-ms";

    private final String applicationRoot;
    private final String fixedApplicationPath;
    private final AppInfoFactory appInfoFactory;
    private final EvaluationRuntimeServerInterface evaluationRuntimeServerInterface;
    private final UPRequestTranslator upRequestTranslator;

    public ForwardingHandler(ServletEngineAdapter.Config runtimeOptions, Map<String, String> env)
        throws ExecutionException, InterruptedException, IOException {
      this.applicationRoot = runtimeOptions.applicationRoot();
      this.fixedApplicationPath = runtimeOptions.fixedApplicationPath();
      this.appInfoFactory = new AppInfoFactory(env);
      this.evaluationRuntimeServerInterface = runtimeOptions.evaluationRuntimeServerInterface();
      this.upRequestTranslator =
          new UPRequestTranslator(
              this.appInfoFactory,
              runtimeOptions.passThroughPrivateHeaders(),
              /*skipPostData=*/ false);
    }

    private void init() {
      /* The init actions are not done in the constructor as they are not used when testing */
      try {
        AppinfoPb.AppInfo appinfo =
            appInfoFactory.getAppInfoFromFile(applicationRoot, fixedApplicationPath);
        // TODO Should we also call ApplyCloneSettings()?
        LocalRpcContext<EmptyMessage> context = new LocalRpcContext<>(EmptyMessage.class);
        evaluationRuntimeServerInterface.addAppVersion(context, appinfo);
        context.getResponse();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    /**
     * Forwards a request to the real runtime for handling. We translate the javax.servlet types
     * into protocol buffers and send the request, then translate the response proto back to a
     * HttpServletResponse.
     */
    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response) {
      baseRequest.setHandled(true);

      // build the request object
      RuntimePb.UPRequest upRequest = upRequestTranslator.translateRequest(baseRequest);

      try {
        UPResponse upResponse = getUpResponse(upRequest);
        upRequestTranslator.translateResponse(baseRequest.getResponse(), upResponse);
      } catch (Exception ex) {
        UPRequestTranslator.populateErrorResponse(
            response, "Can't make request of app: " + Throwables.getStackTraceAsString(ex));
      }
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

  private JettyHttpProxy() {
  }
}
