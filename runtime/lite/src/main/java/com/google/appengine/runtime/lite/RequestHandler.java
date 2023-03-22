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

import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.RequestManager;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.jetty94.AppInfoFactory;
import com.google.apphosting.runtime.jetty94.AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty94.UPRequestTranslator;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.session.Session;

/**
 * Handles inbound request by passing them to the app after setting up App Engine request context
 * and doing some light request mutation.
 */
class RequestHandler extends AbstractHandler {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String X_FORWARDED_PROTO = "x-forwarded-proto";
  private static final String X_APPENGINE_HTTPS = "x-appengine-https";
  private static final String X_APPENGINE_USER_IP = "x-appengine-user-ip";
  private static final String X_APPENGINE_TIMEOUT_MS = "x-appengine-timeout-ms";
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "x-google-internal-skipadmincheck";
  private static final String X_APPENGINE_QUEUENAME = "x-appengine-queuename";
  private static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  private final AppVersion appVersion;

  private final AppVersionHandlerFactory handlerFactory;

  /** Handles request setup and tear-down. */
  private final RequestManager requestManager;

  private final UPRequestTranslator upRequestTranslator;

  private final Handler backgroundRequestHandler;

  private Handler handler = null;

  RequestHandler(
      AppVersion appVersion,
      AppVersionHandlerFactory handlerFactory,
      RequestManager requestManager,
      AppInfoFactory appInfoFactory,
      Handler backgroundRequestHandler) {
    this.appVersion = appVersion;
    this.handlerFactory = handlerFactory;
    this.requestManager = requestManager;
    this.upRequestTranslator =
        new UPRequestTranslator(
            appInfoFactory, /* passThroughPrivateHeaders= */ true, /* skipPostData= */ true);
    this.backgroundRequestHandler = backgroundRequestHandler;
  }

  /**
   * A utility add an async servlet listener which cleans up the request state on exit.
   *
   * <p>Async servlets are untested in the Lite runtime, and as of this writing we aren't aware of
   * any apps using it. This class is, at present, a best effort attempt at supporting async, for
   * cleanup only.
   */
  static class ResponseFinisherInstaller implements AutoCloseable {
    Request request;
    ResponseFinisher responseFinisher;

    ResponseFinisherInstaller(Request request, ResponseFinisher responseFinisher) {
      this.request = request;
      this.responseFinisher = responseFinisher;
    }

    @Override
    public void close() throws Exception {
      if (request.getHttpChannelState().isAsyncStarted()) {
        request.getAsyncContext().addListener(responseFinisher);
      } else {
        // The app did not attempt to enable async mode before returning; do the cleanup in this
        // thread.
        responseFinisher.doFinish();
      }
    }
  }

  class ResponseFinisher implements AsyncListener {
    ApiProxyEnvironmentManager environmentManager;
    RequestManager.RequestToken requestToken;
    Request request;

    ResponseFinisher(
        ApiProxyEnvironmentManager environmentManager,
        RequestManager.RequestToken requestToken,
        Request request) {
      this.environmentManager = environmentManager;
      this.requestToken = requestToken;
      this.request = request;
    }

    void doFinish() throws IOException {
      try {
        environmentManager.installEnvironmentAndCall(this::doFinishWithEnvironment);
      } catch (Exception e) {
        throw new IOException("Failed to finish processing request", e);
      }
    }

    Void doFinishWithEnvironment() throws IOException {
      // The App Engine session storage system needs the request token to be active in order to save
      // sessions. Normally, Jetty will save sessions when it tears down the HTTP channel, but that
      // is too late for us, because we're going to tear down the request token first. Thus, we save
      // the HTTP session (if any) proactively *before* tearing down the request token.

      Optional<HttpSession> httpSession =
          Optional.ofNullable(request.getSession(/* create= */ false));
      if (!httpSession.isPresent()) {
        return null;
      }

      Session session = (Session) httpSession.get();
      try {
        session.getSessionHandler().getSessionCache().release(session.getId(), session);
      } catch (Exception e) {
        throw new IOException("Failed to save session", e);
      }
      // Make sure Jetty doesn't try to save this session again:
      request.setSession(null);

      requestManager.finishRequest(requestToken);

      return null;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
      doFinish();
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
      doFinish();
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
      doFinish();
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {}
  }

  @Override
  public void handle(
      String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    // Mutate the incoming request based special App Engine headers:
    if (requestIsHttps(request)) {
      baseRequest.setScheme("https");
      baseRequest.setSecure(true);
    }

    if (skipAdminCheck(request)) {
      request.setAttribute(SKIP_ADMIN_CHECK_ATTR, true);
    }

    Optional.ofNullable(request.getHeader(X_APPENGINE_USER_IP))
        .ifPresent(ip -> baseRequest.setRemoteAddr(InetSocketAddress.createUnresolved(ip, 0)));

    // Read time remaining in request from headers and pass value to LiteRpcServerContext for
    // use in reporting remaining time until deadline for API calls:
    Duration timeRemaining =
        Optional.ofNullable(request.getHeader(X_APPENGINE_TIMEOUT_MS))
            .map(x -> Duration.ofMillis(Long.parseLong(x)))
            .orElse(Duration.ofNanos(Long.MAX_VALUE));

    RequestManager.RequestToken requestToken =
        requestManager.startRequest(
            appVersion,
            new LiteRpcServerContext(timeRemaining),
            upRequestTranslator.translateRequest(request),
            // startRequest wants an upResponse and fills it in with things, but we throw it all
            // away:
            new MutableUpResponse(),
            Thread.currentThread().getThreadGroup());

    backgroundRequestHandler.handle(target, baseRequest, request, response);
    if (baseRequest.isHandled()) {
      return;
    }

    try (ResponseFinisherInstaller responseFinisherInstaller =
        new ResponseFinisherInstaller(
            baseRequest,
            new ResponseFinisher(
                ApiProxyEnvironmentManager.ofCurrentEnvironment(), requestToken, baseRequest))) {
      try {
        getHandler().handle(target, baseRequest, request, response);
        // We log any exceptions in this "inner try" so they can use the request context before we
        // tear it down:
      } catch (ServletException ex) {
        // Unwrap ServletException for nicer logging:
        logError(Optional.ofNullable(ex.getRootCause()).orElse(ex));
        throw ex;
      } catch (Throwable ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt(); // Restore the interrupted status
        }

        // Log anything else as-is:
        logError(ex);
        throw ex;
      }

    } catch (IOException | ServletException ex) {
      // These exceptions can be rethrown by this method directly.
      throw ex;
    } catch (Throwable ex) {
      // All other exceptions must be wrapped in ServletException to be thrown.

      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
      }

      throw new ServletException(ex);
    }
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
  static boolean requestIsHttps(HttpServletRequest request) {
    if ("on".equals(request.getHeader(X_APPENGINE_HTTPS))) {
      return true;
    }

    if ("https".equals(request.getHeader(X_FORWARDED_PROTO))) {
      return true;
    }

    if (request.getHeader(X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null) {
      return true;
    }

    return false;
  }

  static boolean skipAdminCheck(HttpServletRequest request) {
    if (request.getHeader(X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null) {
      return true;
    }

    if (request.getHeader(X_APPENGINE_QUEUENAME) != null) {
      return true;
    }

    return false;
  }

  synchronized Handler getHandler() throws ServletException {
    // We defer creation of the main request handler because because some apps call App Engine APIs
    // as soon as the WebAppContext is constructed, and that only works from within the context of
    // an incoming request. So we don't actually instantiate the app's WebAppContext until a request
    // is inbound.
    if (handler == null) {
      handler = handlerFactory.createHandler(appVersion);
    }
    return handler;
  }
}
