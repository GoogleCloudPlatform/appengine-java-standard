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

package com.google.apphosting.runtime.jetty9;

import static com.google.apphosting.runtime.RequestRunner.WAIT_FOR_USER_RUNNABLE_DEADLINE;
import static com.google.common.base.Verify.verify;

import com.google.appengine.api.ThreadManager;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.AppEngineConstants;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.BackgroundRequestCoordinator;
import com.google.apphosting.runtime.LocalRpcContext;
import com.google.apphosting.runtime.RequestManager;
import com.google.apphosting.runtime.RequestRunner;
import com.google.apphosting.runtime.RequestRunner.EagerRunner;
import com.google.apphosting.runtime.ResponseAPIData;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.common.flogger.GoogleLogger;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerWrapper;

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
public class JettyHttpHandler extends HandlerWrapper {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  public static final String FINISH_REQUEST_ATTRIBUTE =
      "com.google.apphosting.runtime.jetty9.finishRequestAttribute";

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
      AppInfoFactory appInfoFactory,
      ServerConnector connector) {
    this.passThroughPrivateHeaders = runtimeOptions.passThroughPrivateHeaders();
    this.appInfoFactory = appInfoFactory;
    this.appVersionKey = appVersionKey;
    this.appVersion = appVersion;

    ApiProxyImpl apiProxyImpl = (ApiProxyImpl) ApiProxy.getDelegate();
    coordinator = apiProxyImpl.getBackgroundRequestCoordinator();
    requestManager = (RequestManager) apiProxyImpl.getRequestThreadManager();
    connector.addBean(new CompletionListener());
  }

  @Override
  public void handle(
      String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    // This handler cannot be used with anything else which establishes an environment
    //  (e.g. RpcConnection).
    verify(request.getAttribute(AppEngineConstants.ENVIRONMENT_ATTR) == null);
    JettyRequestAPIData genericRequest =
        new JettyRequestAPIData(baseRequest, request, appInfoFactory, passThroughPrivateHeaders);
    JettyResponseAPIData genericResponse =
        new JettyResponseAPIData(baseRequest.getResponse(), response);

    // Read time remaining in request from headers and pass value to LocalRpcContext for use in
    // reporting remaining time until deadline for API calls (see b/154745969)
    Duration timeRemaining = genericRequest.getTimeRemaining();

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

    Runnable finishRequest =
        () -> finishRequest(currentEnvironment, requestToken, genericResponse, context);
    baseRequest.setAttribute(FINISH_REQUEST_ATTRIBUTE, finishRequest);

    try {
      dispatchRequest(target, requestToken, genericRequest, genericResponse);
      if (!baseRequest.isHandled()) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "request not handled");
      }
    } catch (
        @SuppressWarnings("InterruptedExceptionSwallowed")
        Throwable ex) {
      // Note we do intentionally swallow InterruptException.
      // We will report the exception via the rpc. We don't mark this thread as interrupted because
      // ThreadGroupPool would use that as a signal to remove the thread from the pool; we don't
      // need that.
      handleException(ex, requestToken, genericResponse);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
    } finally {
      // We don't want threads used for background requests to go back
      // in the thread pool, because users may have stashed references
      // to them or may be expecting them to exit.  Setting the
      // interrupt bit causes the pool to drop them.
      if (genericRequest.getRequestType() == RuntimePb.UPRequest.RequestType.BACKGROUND) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void finishRequest(
      ApiProxy.Environment env,
      RequestManager.RequestToken requestToken,
      JettyResponseAPIData response,
      AnyRpcServerContext context) {

    ApiProxy.Environment oldEnv = ApiProxy.getCurrentEnvironment();
    try {
      ApiProxy.setEnvironmentForCurrentThread(env);
      requestManager.finishRequest(requestToken);

      // Do not put this in a final block.  If we propagate an
      // exception the callback will be invoked automatically.
      response.finishWithResponse(context);
    }
    finally {
      ApiProxy.setEnvironmentForCurrentThread(oldEnv);
    }
  }

  private void dispatchRequest(
      String target,
      RequestManager.RequestToken requestToken,
      JettyRequestAPIData request,
      JettyResponseAPIData response)
      throws Throwable {
    switch (request.getRequestType()) {
      case SHUTDOWN:
        logger.atInfo().log("Shutting down requests");
        requestManager.shutdownRequests(requestToken);
        request.getBaseRequest().setHandled(true);
        break;
      case BACKGROUND:
        dispatchBackgroundRequest(request);
        request.getBaseRequest().setHandled(true);
        break;
      case OTHER:
        dispatchServletRequest(target, request, response);
        break;
    }
  }

  private void dispatchServletRequest(
      String target, JettyRequestAPIData request, JettyResponseAPIData response) throws Throwable {
    Request baseRequest = request.getBaseRequest();
    HttpServletRequest httpServletRequest = request.getHttpServletRequest();
    HttpServletResponse httpServletResponse = response.getHttpServletResponse();
    baseRequest.setAttribute(AppEngineConstants.APP_VERSION_KEY_REQUEST_ATTR, appVersionKey);

    // Environment is set in a request attribute which is set/unset for async threads by
    // a ContextScopeListener created inside the AppVersionHandlerFactory.
    super.handle(target, baseRequest, httpServletRequest, httpServletResponse);
  }

  private void dispatchBackgroundRequest(JettyRequestAPIData request)
      throws InterruptedException, TimeoutException {
    String requestId = getBackgroundRequestId(request);
    // The interface of coordinator.waitForUserRunnable() requires us to provide the app code with a
    // working thread *in the same exchange* where we get the runnable the user wants to run in the
    // thread. This prevents us from actually directly feeding that runnable to the thread. To work
    // around this conundrum, we create an EagerRunner, which lets us start running the thread
    // without knowing yet what we want to run.

    // Create an ordinary request thread as a child of this background thread.
    EagerRunner eagerRunner = new EagerRunner();
    Thread thread = ThreadManager.createThreadForCurrentRequest(eagerRunner);

    // Give this thread to the app code and get its desired runnable in response:
    Runnable runnable =
        coordinator.waitForUserRunnable(
            requestId, thread, WAIT_FOR_USER_RUNNABLE_DEADLINE.toMillis());

    // Finally, hand that runnable to the thread so it can actually start working.
    // This will block until Thread.start() is called by the app code. This is by design: we must
    // not exit this request handler until the thread has started *and* completed, otherwise the
    // serving infrastructure will cancel our ability to make API calls. We're effectively "holding
    // open the door" on the spawned thread's ability to make App Engine API calls.
    // Now set the context class loader to the UserClassLoader for the application
    // and pass control to the Runnable the user provided.
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(appVersion.getClassLoader());
    try {
      eagerRunner.supplyRunnable(runnable);
    } finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
    // Wait for the thread to end:
    thread.join();
  }

  private void handleException(
      Throwable ex, RequestManager.RequestToken requestToken, ResponseAPIData response) {
    // Unwrap ServletException, either from javax or from jakarta exception:
    try {
      java.lang.reflect.Method getRootCause = ex.getClass().getMethod("getRootCause");
      Object rootCause = getRootCause.invoke(ex);
      if (rootCause != null) {
        ex = (Throwable) rootCause;
      }
    } catch (Throwable ignore) {
      // We do want to ignore there, failure is propagated below in the protocol buffer.
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
    String backgroundRequestId = upRequest.getBackgroundRequestId();
    if (backgroundRequestId == null) {
      throw new IllegalArgumentException("Did not receive a background request identifier.");
    }
    return backgroundRequestId;
  }

  private static class CompletionListener implements HttpChannel.Listener {
    @Override
    public void onComplete(Request request) {
      System.err.println("CompletionListener " + request + "  thread " + Thread.currentThread().getName());
      Runnable finishRequest =
          (Runnable) request.getAttribute(JettyHttpHandler.FINISH_REQUEST_ATTRIBUTE);
      if (finishRequest != null) {
        finishRequest.run();
      }
    }
  }
}
