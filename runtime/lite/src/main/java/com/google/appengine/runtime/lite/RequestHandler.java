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

package com.google.appengine.runtime.lite;

import static com.google.apphosting.runtime.AppEngineConstants.ENVIRONMENT_ATTR;
import static com.google.apphosting.runtime.AppEngineConstants.SKIP_ADMIN_CHECK_ATTR;
import static com.google.apphosting.runtime.AppEngineConstants.X_GOOGLE_INTERNAL_SKIPADMINCHECK;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.AppInfoFactory;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.jetty.AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty.proxy.UPRequestTranslator;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.MessageLite;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 * Handles inbound request by passing them to the app after setting up App Engine request context
 * and doing some light request mutation.
 */
class RequestHandler extends Handler.Wrapper {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String X_FORWARDED_PROTO = "x-forwarded-proto";
  private static final String X_APPENGINE_HTTPS = "x-appengine-https";
  private static final String X_APPENGINE_USER_IP = "x-appengine-user-ip";
  private static final String X_APPENGINE_TIMEOUT_MS = "x-appengine-timeout-ms";
  private static final String X_APPENGINE_QUEUENAME = "x-appengine-queuename";

  private final AppVersion appVersion;

  private final AppVersionHandlerFactory handlerFactory;

  /** Handles request setup and tear-down. */
  private final RequestManager requestManager;

  private final UPRequestTranslator upRequestTranslator;

  private final Handler backgroundRequestHandler;

  private volatile Handler handler = null;

  RequestHandler(
      AppVersion appVersion,
      AppVersionHandlerFactory handlerFactory,
      RequestManager requestManager,
      AppInfoFactory appInfoFactory,
      Handler backgroundRequestHandler) {
    super(true);
    this.appVersion = appVersion;
    this.handlerFactory = handlerFactory;
    this.requestManager = requestManager;
    this.upRequestTranslator =
        new UPRequestTranslator(
            appInfoFactory, /* passThroughPrivateHeaders= */ true, /* skipPostData= */ true);
    this.backgroundRequestHandler = backgroundRequestHandler;
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    boolean isHttps = requestIsHttps(request);
    String userIp = request.getHeaders().get(X_APPENGINE_USER_IP);

    HttpURI httpUri;
    boolean isSecure;
    if (isHttps) {
      httpUri = HttpURI.build(request.getHttpURI()).scheme(HttpScheme.HTTPS);
      isSecure = true;
    } else {
      httpUri = request.getHttpURI();
      isSecure = request.isSecure();
    }

    Request wrappedRequest = new Request.Wrapper(request) {
      @Override
      public HttpURI getHttpURI() {
        return httpUri;
      }

      @Override
      public boolean isSecure() {
        return isSecure;
      }

      @Override
      public ConnectionMetaData getConnectionMetaData() {
        if (userIp == null) {
          return super.getConnectionMetaData();
        }
        return new ConnectionMetaData.Wrapper(super.getConnectionMetaData()) {
          @Override
          public SocketAddress getRemoteSocketAddress() {
            return InetSocketAddress.createUnresolved(userIp, 0);
          }
        };
      }
    };

    if (skipAdminCheck(request)) {
      wrappedRequest.setAttribute(SKIP_ADMIN_CHECK_ATTR, true);
    }

    // Read time remaining in request from headers and pass value to LiteRpcServerContext for
    // use in reporting remaining time until deadline for API calls:
    Duration timeRemaining =
        Optional.ofNullable(request.getHeaders().get(X_APPENGINE_TIMEOUT_MS))
            .map(x -> Duration.ofMillis(Long.parseLong(x)))
            .orElse(Duration.ofNanos(Long.MAX_VALUE));

    RequestManager.RequestToken requestToken =
        requestManager.startRequest(
            appVersion,
            new LiteRpcServerContext(timeRemaining),
            upRequestTranslator.translateRequest(wrappedRequest),
            // startRequest wants an upResponse and fills it in with things, but we throw it all
            // away:
            new MutableUpResponse(),
            Thread.currentThread().getThreadGroup());

    ApiProxy.Environment currentEnvironment = ApiProxy.getCurrentEnvironment();
    wrappedRequest.setAttribute(ENVIRONMENT_ATTR, currentEnvironment);

    try {
      return dispatchRequest(wrappedRequest, response, callback);
    } catch (Throwable ex) {
      logError(ex);
      throw ex;
    } finally {
      requestManager.finishRequest(requestToken);
    }
  }

  private boolean dispatchRequest(Request wrappedRequest, Response response, Callback callback)
      throws Exception {
    if (backgroundRequestHandler.handle(wrappedRequest, response, callback)) {
      return true;
    }

    Handler appHandler = getOrMaybeCreateHandler();
    boolean[] handled = new boolean[1];
    handled[0] = appHandler.handle(wrappedRequest, response, callback);
    Throwable ex = (Throwable) wrappedRequest.getAttribute("javax.servlet.error.exception");
    if (ex != null) {
      logError(ex);
    }
    return handled[0];
  }

  private static void logError(Throwable ex) {
    logger.atSevere().withCause(ex).log("Uncaught exception from servlet");
  }

  /** A mostly fake implementation of AnyRpcServerContext to satisfy RequestManager. */
  private static class LiteRpcServerContext implements AnyRpcServerContext {
    // We just dole out sequential ids here so we can tell requests apart in the logs.
    private static final AtomicLong globalIds = new AtomicLong();

    private final long startTimeMillis;
    private final Duration timeRemaining;
    private final long globalId = globalIds.getAndIncrement();

    LiteRpcServerContext(Duration timeRemaining) {
      this.startTimeMillis = System.currentTimeMillis();
      this.timeRemaining = timeRemaining;
    }

    @Override
    public void finishWithResponse(MessageLite response) {}

    @Override
    public void finishWithAppError(int appErrorCode, String errorDetail) {}

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
   * Determine if the request came from within App Engine via secure internal channels.
   *
   * <p>We round such cases up to "using https" to satisfy Jetty's transport-guarantee checks.
   */
  static boolean requestIsHttps(Request request) {
    if (Objects.equals(request.getHeaders().get(X_APPENGINE_HTTPS), "on")) {
      return true;
    }

    if (Objects.equals(request.getHeaders().get(X_FORWARDED_PROTO), "https")) {
      return true;
    }

    if (request.getHeaders().get(X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null) {
      return true;
    }

    return false;
  }

  static boolean skipAdminCheck(Request request) {
    if (request.getHeaders().get(X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null) {
      return true;
    }

    if (request.getHeaders().get(X_APPENGINE_QUEUENAME) != null) {
      return true;
    }

    return false;
  }

  synchronized Handler getOrMaybeCreateHandler() throws Exception {
    // We defer creation of the main request handler because because some apps call App Engine APIs
    // as soon as the WebAppContext is constructed, and that only works from within the context of
    // an incoming request. So we don't actually instantiate the app's WebAppContext until a request
    // is inbound.
    if (handler == null) {
      handler = handlerFactory.createHandler(appVersion);
      setHandler(handler);
    }
    return handler;
  }
}
