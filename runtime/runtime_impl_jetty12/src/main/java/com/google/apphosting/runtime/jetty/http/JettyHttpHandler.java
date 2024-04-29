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

package com.google.apphosting.runtime.jetty.http;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.BackgroundRequestCoordinator;
import com.google.apphosting.runtime.JettyConstants;
import com.google.apphosting.runtime.RequestManager;
import com.google.apphosting.runtime.RequestRunner;
import com.google.apphosting.runtime.ResponseAPIData;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.ThreadGroupPool;
import com.google.apphosting.runtime.jetty.AppEngineConstants;
import com.google.apphosting.runtime.jetty.AppInfoFactory;
import com.google.common.base.Ascii;
import com.google.common.flogger.GoogleLogger;
import com.google.common.util.concurrent.Uninterruptibles;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Blocker;
import org.eclipse.jetty.util.Callback;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import static com.google.apphosting.runtime.RequestRunner.WAIT_FOR_USER_RUNNABLE_DEADLINE;

/**
 * This class replicates the behaviour of the {@link RequestRunner} for Requests which do not come
 * through RPC. It should be added as a {@link Handler} to the Jetty {@link Server} wrapping the
 * {@code AppEngineWebAppContext}.
 *
 * <p>This uses the {@link RequestManager} to start any AppEngine state associated with this request
 * including the {@link ApiProxy.Environment} which it sets as a request attribute at {@link
 * AppEngineConstants#ENVIRONMENT_ATTR}. This request attribute is pulled out by {@code
 * ContextScopeListener}s installed by the {@code AppVersionHandlerFactory} implementations so that
 * the {@link ApiProxy.Environment} is available all threads which are used to handle the request.
 */
public class JettyHttpHandler extends Handler.Wrapper {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final boolean passThroughPrivateHeaders;
  private final AppInfoFactory appInfoFactory;
  private final AppVersionKey appVersionKey;
  private final AppVersion appVersion;
  private final RequestManager requestManager;
  private final BackgroundRequestCoordinator coordinator;

  public JettyHttpHandler(
      ServletEngineAdapter.Config runtimeOptions,
      AppVersion appVersion,
      AppVersionKey appVersionKey,
      AppInfoFactory appInfoFactory) {
    this.passThroughPrivateHeaders = runtimeOptions.passThroughPrivateHeaders();
    this.appInfoFactory = appInfoFactory;
    this.appVersionKey = appVersionKey;
    this.appVersion = appVersion;

    ApiProxyImpl apiProxyImpl = (ApiProxyImpl) ApiProxy.getDelegate();
    coordinator = apiProxyImpl.getBackgroundRequestCoordinator();
    requestManager = (RequestManager) apiProxyImpl.getRequestThreadManager();
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    JettyRequestAPIData genericRequest =
        new JettyRequestAPIData(request, appInfoFactory, passThroughPrivateHeaders);
    JettyResponseAPIData genericResponse = new JettyResponseAPIData(response);

    // Read time remaining in request from headers and pass value to LocalRpcContext for use in
    // reporting remaining time until deadline for API calls (see b/154745969)
    Duration timeRemaining = genericRequest.getTimeRemaining();

    boolean handled;
    ThreadGroup currentThreadGroup = Thread.currentThread().getThreadGroup();
    LocalRpcContext<EmptyMessage> context =
        new LocalRpcContext<>(EmptyMessage.class, timeRemaining);
    RequestManager.RequestToken requestToken =
        requestManager.startRequest(
            appVersion, context, genericRequest, genericResponse, currentThreadGroup);

    // Set the environment as a request attribute, so it can be pulled out and set for async
    // threads.
    ApiProxy.Environment currentEnvironment = ApiProxy.getCurrentEnvironment();
    request.setAttribute(AppEngineConstants.ENVIRONMENT_ATTR, currentEnvironment);

    try {
      handled = dispatchRequest(requestToken, genericRequest, genericResponse, callback);
      if (handled) {
        callback.succeeded();
      }
    } catch (
        @SuppressWarnings("InterruptedExceptionSwallowed")
        Throwable ex) {
      // Note we do intentionally swallow InterruptException.
      // We will report the exception via the rpc. We don't mark this thread as interrupted because
      // ThreadGroupPool would use that as a signal to remove the thread from the pool; we don't
      // need that.
      handled = handleException(ex, requestToken, genericResponse);
      Response.writeError(request, response, callback, ex);
    } finally {
      requestManager.finishRequest(requestToken);
    }
    // Do not put this in a final block.  If we propagate an
    // exception the callback will be invoked automatically.
    genericResponse.finishWithResponse(context);
    // We don't want threads used for background requests to go back
    // in the thread pool, because users may have stashed references
    // to them or may be expecting them to exit.  Setting the
    // interrupt bit causes the pool to drop them.
    if (genericRequest.getRequestType() == RuntimePb.UPRequest.RequestType.BACKGROUND) {
      Thread.currentThread().interrupt();
    }

    return handled;
  }

  private boolean dispatchRequest(
      RequestManager.RequestToken requestToken,
      JettyRequestAPIData request,
      JettyResponseAPIData response,
      Callback callback)
      throws Throwable {
    switch (request.getRequestType()) {
      case SHUTDOWN:
        logger.atInfo().log("Shutting down requests");
        requestManager.shutdownRequests(requestToken);
        return true;
      case BACKGROUND:
        dispatchBackgroundRequest(request, response);
        return true;
      case OTHER:
        return dispatchServletRequest(request, response, callback);
      default:
        throw new IllegalStateException(request.getRequestType().toString());
    }
  }

  private boolean dispatchServletRequest(
      JettyRequestAPIData request, JettyResponseAPIData response, Callback callback)
      throws Throwable {
    Request jettyRequest = request.getWrappedRequest();
    Response jettyResponse = response.getWrappedResponse();
    jettyRequest.setAttribute(JettyConstants.APP_VERSION_KEY_REQUEST_ATTR, appVersionKey);

    // Environment is set in a request attribute which is set/unset for async threads by
    // a ContextScopeListener created inside the AppVersionHandlerFactory.
    try (Blocker.Callback cb = Blocker.callback()) {
      boolean handle = super.handle(jettyRequest, jettyResponse, cb);
      cb.block();
      return handle;
    }
  }

  private void dispatchBackgroundRequest(JettyRequestAPIData request, JettyResponseAPIData response)
      throws InterruptedException, TimeoutException {
    String requestId = getBackgroundRequestId(request);
    // Wait here for synchronization with the ThreadFactory.
    CountDownLatch latch = ThreadGroupPool.resetCurrentThread();
    Thread thread = new ThreadProxy();
    Runnable runnable =
        coordinator.waitForUserRunnable(requestId, thread, WAIT_FOR_USER_RUNNABLE_DEADLINE);
    // Wait here until someone calls start() on the thread again.
    latch.await();
    // Now set the context class loader to the UserClassLoader for the application
    // and pass control to the Runnable the user provided.
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(appVersion.getClassLoader());
    try {
      runnable.run();
    } finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  private boolean handleException(
      Throwable ex, RequestManager.RequestToken requestToken, ResponseAPIData response) {
    // Unwrap ServletException, either from javax or from jakarta exception:
    try {
      java.lang.reflect.Method getRootCause = ex.getClass().getMethod("getRootCause");
      Object rootCause = getRootCause.invoke(ex);
      if (rootCause != null) {
        ex = (Throwable) rootCause;
      }
    } catch (Throwable ignore) {
    }
    String msg = "Uncaught exception from servlet";
    logger.atWarning().withCause(ex).log("%s", msg);
    // Don't use ApiProxy here, because we don't know what state the
    // environment/delegate are in.
    requestToken.addAppLogMessage(ApiProxy.LogRecord.Level.fatal, formatLogLine(msg, ex));

    if (shouldKillCloneAfterException(ex)) {
      logger.atSevere().log("Detected a dangerous exception, shutting down clone nicely.");
      response.setTerminateClone(true);
    }
    RuntimePb.UPResponse.ERROR error = RuntimePb.UPResponse.ERROR.APP_FAILURE;
    setFailure(response, error, "Unexpected exception from servlet: " + ex);
    return true;
  }

  /** Create a failure response from the given code and message. */
  public static void setFailure(
      ResponseAPIData response, RuntimePb.UPResponse.ERROR error, String message) {
    logger.atWarning().log("Runtime failed: %s, %s", error, message);
    // If the response is already set, use that -- it's probably more
    // specific (e.g. THREADS_STILL_RUNNING).
    if (response.getError() == RuntimePb.UPResponse.ERROR.OK_VALUE) {
      response.error(error.getNumber(), message);
    }
  }

  private String formatLogLine(String message, Throwable ex) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    printWriter.println(message);
    ex.printStackTrace(printWriter);
    return stringWriter.toString();
  }

  public static boolean shouldKillCloneAfterException(Throwable th) {
    while (th != null) {
      if (th instanceof OutOfMemoryError) {
        return true;
      }
      try {
        Throwable[] suppressed = th.getSuppressed();
        if (suppressed != null) {
          for (Throwable s : suppressed) {
            if (shouldKillCloneAfterException(s)) {
              return true;
            }
          }
        }
      } catch (OutOfMemoryError ex) {
        return true;
      }
      // TODO: Consider checking for other subclasses of
      // VirtualMachineError, but probably not StackOverflowError.
      th = th.getCause();
    }
    return false;
  }

  private String getBackgroundRequestId(JettyRequestAPIData upRequest) {
    Optional<HttpField> match =
        upRequest.getOriginalRequest().getHeaders().stream()
            .filter(h -> Ascii.equalsIgnoreCase(h.getName(), "X-AppEngine-BackgroundRequest"))
            .findFirst();
    if (match.isPresent()) {
      return match.get().getValue();
    }
    throw new IllegalArgumentException("Did not receive a background request identifier.");
  }

  /** Creates a thread which does nothing except wait on the thread that spawned it. */
  private static class ThreadProxy extends Thread {

    private final Thread proxy;

    private ThreadProxy() {
      super(
          Thread.currentThread().getThreadGroup().getParent(),
          Thread.currentThread().getName() + "-proxy");
      proxy = Thread.currentThread();
    }

    @Override
    public synchronized void start() {
      proxy.start();
      super.start();
    }

    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
      proxy.setUncaughtExceptionHandler(eh);
    }

    @Override
    public void run() {
      Uninterruptibles.joinUninterruptibly(proxy);
    }
  }
}
