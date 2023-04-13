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

import com.google.appengine.tools.development.TimedFuture;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiResultFuture;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.ApiStats;
import com.google.apphosting.api.CloudTrace;
import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb.APIRequest;
import com.google.apphosting.base.protos.RuntimePb.APIResponse;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.Status.StatusProto;
import com.google.apphosting.base.protos.TraceId;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.anyrpc.AnyRpcCallback;
import com.google.apphosting.runtime.anyrpc.AnyRpcClientContext;
import com.google.apphosting.runtime.timer.CpuRatioTimer;
import com.google.apphosting.utils.runtime.ApiProxyUtils;
import com.google.auto.value.AutoBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.flogger.GoogleLogger;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ForwardingFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/**
 * ApiProxyImpl is a concrete implementation of the ApiProxy.Delegate
 * interface.
 *
 * <p>It receives user-supplied API calls, translates them into the
 * APIRequest arguments that APIHost expects, calls the asynchronous
 * APIHost service, and translates any return value or error condition
 * back into something that the user would expect.
 *
 */
public class ApiProxyImpl implements ApiProxy.Delegate<ApiProxyImpl.EnvironmentImpl> {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  static final String USER_ID_KEY = "com.google.appengine.api.users.UserService.user_id_key";

  static final String USER_ORGANIZATION_KEY =
      "com.google.appengine.api.users.UserService.user_organization";

  static final String LOAS_PEER_USERNAME = "com.google.net.base.peer.loas_peer_username";

  static final String LOAS_SECURITY_LEVEL = "com.google.net.base.peer.loas_security_level";

  static final String IS_TRUSTED_IP = "com.google.appengine.runtime.is_trusted_ip";

  static final String API_DEADLINE_KEY = "com.google.apphosting.api.ApiProxy.api_deadline_key";

  static final String BACKGROUND_THREAD_REQUEST_DEADLINE_KEY =
      "com.google.apphosting.api.ApiProxy.background_thread_request_deadline_key";

  public static final String BACKEND_ID_KEY = "com.google.appengine.backend.id";

  public static final String INSTANCE_ID_KEY = "com.google.appengine.instance.id";

  static final String REQUEST_ID_HASH = "com.google.apphosting.api.ApiProxy.request_id_hash";

  static final String REQUEST_LOG_ID = "com.google.appengine.runtime.request_log_id";

  static final String DEFAULT_VERSION_HOSTNAME =
      "com.google.appengine.runtime.default_version_hostname";

  static final String GAIA_ID = "com.google.appengine.runtime.gaia_id";

  static final String GAIA_AUTHUSER = "com.google.appengine.runtime.gaia_authuser";

  static final String GAIA_SESSION = "com.google.appengine.runtime.gaia_session";

  static final String APPSERVER_DATACENTER = "com.google.appengine.runtime.appserver_datacenter";

  static final String APPSERVER_TASK_BNS = "com.google.appengine.runtime.appserver_task_bns";

  public static final String CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY =
      "com.google.appengine.runtime.new_database_connectivity";

  private static final long DEFAULT_BYTE_COUNT_BEFORE_FLUSHING = 100 * 1024L;
  private static final int DEFAULT_MAX_LOG_LINE_SIZE = 16 * 1024;
  /**
   * The number of milliseconds beyond the API call deadline to wait
   * to receive a Stubby callback before throwing a
   * {@link ApiProxy.ApiDeadlineExceededException} anyway.
   */
  private static final int API_DEADLINE_PADDING = 500;
  // Fudge factor to account for possible differences between the actual and expected timing of
  // the deadline notification. This should really be 0, but I'm not sure if we can count on the
  // timing of the cancellation always being strictly after the time that we expect, and it really
  // doesn't hurt to err on the side of attributing the cancellation to the deadline.
  private static final long ATTRIBUTE_TO_DEADLINE_MILLIS = 50L;
  /**
   * The number of milliseconds that a {@code ThreadFactory} will wait for a Thread to be created
   * before giving up.
   */
  private static final Number DEFAULT_BACKGROUND_THREAD_REQUEST_DEADLINE = 30000L;
  private static final String DEADLINE_REACHED_REASON =
      "the overall HTTP request deadline was reached";
  private static final String DEADLINE_REACHED_SLOT_REASON =
      "the overall HTTP request deadline was reached while waiting for concurrent API calls";
  private static final String INTERRUPTED_REASON = "the thread was interrupted";
  private static final String INTERRUPTED_SLOT_REASON =
      "the thread was interrupted while waiting for concurrent API calls";

  /** A logical, user-visible name for the current datacenter. */
  // TODO: We may want a nicer interface for exposing this
  // information.  Currently Environment attributes are either
  // internal-only or are wrapped by other, more public APIs.
  static final String DATACENTER = "com.google.apphosting.api.ApiProxy.datacenter";

  private final APIHostClientInterface apiHost;
  private final ApiDeadlineOracle deadlineOracle;
  private final String externalDatacenterName;
  private final long byteCountBeforeFlushing;
  private final int maxLogLineSize;
  private final Duration maxLogFlushTime;
  private final BackgroundRequestCoordinator coordinator;
  private RequestThreadManager requestThreadManager;
  private final boolean cloudSqlJdbcConnectivityEnabled;
  private final boolean disableApiCallLogging;
  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final boolean logToLogservice;

  public static Builder builder() {
    return new AutoBuilder_ApiProxyImpl_Builder()
        .setByteCountBeforeFlushing(DEFAULT_BYTE_COUNT_BEFORE_FLUSHING)
        .setMaxLogLineSize(DEFAULT_MAX_LOG_LINE_SIZE)
        .setMaxLogFlushTime(Duration.ZERO)
        .setCloudSqlJdbcConnectivityEnabled(false)
        .setDisableApiCallLogging(false)
        .setLogToLogservice(true);
  }


  /** Builder for ApiProxyImpl.Config. */
  @AutoBuilder
  public abstract static class Builder {

    public abstract Builder setApiHost(APIHostClientInterface x);

    public abstract Builder setDeadlineOracle(ApiDeadlineOracle x);

    public abstract ApiDeadlineOracle deadlineOracle();

    public abstract Builder setExternalDatacenterName(String x);

    public abstract String externalDatacenterName();

    public abstract Builder setByteCountBeforeFlushing(long x);

    public abstract long byteCountBeforeFlushing();

    public abstract Builder setMaxLogLineSize(int x);

    public abstract Builder setMaxLogFlushTime(Duration x);

    public abstract Duration maxLogFlushTime();

    public abstract Builder setCoordinator(BackgroundRequestCoordinator x);

    public abstract BackgroundRequestCoordinator coordinator();

    public abstract Builder setCloudSqlJdbcConnectivityEnabled(boolean x);

    public abstract boolean cloudSqlJdbcConnectivityEnabled();

    public abstract Builder setDisableApiCallLogging(boolean x);

    public abstract boolean disableApiCallLogging();

    public abstract Builder setLogToLogservice(boolean x);

    public abstract ApiProxyImpl build();
  }

  ApiProxyImpl(
      @Nullable APIHostClientInterface apiHost,
      @Nullable ApiDeadlineOracle deadlineOracle,
      @Nullable String externalDatacenterName,
      long byteCountBeforeFlushing,
      int maxLogLineSize,
      Duration maxLogFlushTime,
      @Nullable BackgroundRequestCoordinator coordinator,
      boolean cloudSqlJdbcConnectivityEnabled,
      boolean disableApiCallLogging,
      boolean logToLogservice) {
    this.apiHost = apiHost;
    this.deadlineOracle = deadlineOracle;
    this.externalDatacenterName = externalDatacenterName;
    this.byteCountBeforeFlushing = byteCountBeforeFlushing;
    this.maxLogLineSize = maxLogLineSize;
    this.maxLogFlushTime = maxLogFlushTime;
    this.coordinator = coordinator;
    this.cloudSqlJdbcConnectivityEnabled = cloudSqlJdbcConnectivityEnabled;
    this.disableApiCallLogging = disableApiCallLogging;
    this.logToLogservice = logToLogservice;
  }

  // TODO There's a circular dependency here:
  // RequestManager needs the EnvironmentFactory so it can create
  // environments, and ApiProxyImpl needs the RequestManager so it can
  // get the request threads. We should find a better way to
  // modularize this.
  public void setRequestManager(RequestThreadManager requestThreadManager) {
    this.requestThreadManager = requestThreadManager;
  }

  void disable() {
    // We're just using the AtomicBoolean as a boolean object we can synchronize on. We don't use
    // getAndSet or the like, because we want these properties:
    // (1) The first thread to call enable() is the one that calls apiHost.enable(), and every
    //     other thread that calls enable() will block waiting for that to finish.
    // (2) If apiHost.disable() or .enable() throws an exception, the enabled state does not change.
    synchronized (enabled) {
      if (enabled.get()) {
        apiHost.disable();
        enabled.set(false);
      }
    }
  }

  void enable() {
    synchronized (enabled) {
      if (!enabled.get()) {
        apiHost.enable();
        enabled.set(true);
      }
    }
  }

  @Override
  public byte[] makeSyncCall(
      final EnvironmentImpl environment,
      final String packageName,
      final String methodName,
      final byte[] request) {
    return AccessController.doPrivileged(
        (PrivilegedAction<byte[]>) () -> doSyncCall(environment, packageName, methodName, request));
  }

  @Override
  public Future<byte[]> makeAsyncCall(
      final EnvironmentImpl environment,
      final String packageName,
      final String methodName,
      final byte[] request,
      final ApiProxy.ApiConfig apiConfig) {
    return AccessController.doPrivileged(
        (PrivilegedAction<Future<byte[]>>) () -> doAsyncCall(
            environment, packageName, methodName, request, apiConfig.getDeadlineInSeconds()));
  }

  private byte[] doSyncCall(
      EnvironmentImpl environment, String packageName, String methodName, byte[] requestBytes) {
    double deadlineInSeconds = getApiDeadline(packageName, environment);
    Future<byte[]> future =
        doAsyncCall(environment, packageName, methodName, requestBytes, deadlineInSeconds);
    try {
      return future.get((long) (deadlineInSeconds * 1000), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      // Someone else called Thread.interrupt().  We probably
      // shouldn't swallow this, so propagate it as the closest
      // exception that we have.  Note that we specifically do not
      // re-set the interrupt bit because we don't want future API
      // calls to immediately throw this exception.
      long remainingMillis = environment.getRemainingMillis();
      String msg = String.format("Caught InterruptedException; %d millis %s soft deadline",
          Math.abs(remainingMillis), remainingMillis > 0 ? "until" : "since");
      logger.atWarning().withCause(ex).log("%s", msg);
      if (remainingMillis <= ATTRIBUTE_TO_DEADLINE_MILLIS) {
        // Usually this won't happen because the RequestManager first cancels all the Futures
        // before interrupting threads, so we would normally get the CancellationException instead.
        // Still, it's possible this could happen.
        throw new ApiProxy.CancelledException(packageName, methodName, DEADLINE_REACHED_REASON);
      } else {
        // Not sure why interrupted.
        throw new ApiProxy.CancelledException(packageName, methodName, INTERRUPTED_REASON);
      }
    } catch (CancellationException ex) {
      // This may happen when the overall HTTP request deadline expires.
      // See RequestManager.sendDeadline().
      long remainingMillis = environment.getRemainingMillis();
      // We attribute the reason for cancellation based on time remaining until the response
      // deadline because it's easier and safer than somehow passing in the reason for
      // cancellation, since the existing interfaces don't offer any mechanism for doing that.
      if (remainingMillis <= ATTRIBUTE_TO_DEADLINE_MILLIS) {
        String msg = String.format(
            "Caught CancellationException; %d millis %s soft deadline; attributing to deadline",
            Math.abs(remainingMillis), remainingMillis > 0 ? "until" : "since");
        logger.atWarning().withCause(ex).log("%s", msg);
        // Probably cancelled due to request deadline being exceeded.
        throw new ApiProxy.CancelledException(packageName, methodName, DEADLINE_REACHED_REASON);
      } else {
        // Not sure why cancelled early; this is unexpected on a synchronous call.
        String msg = String.format(
            "Caught CancellationException; %d millis %s soft deadline; this is unexpected",
            Math.abs(remainingMillis), remainingMillis > 0 ? "until" : "since");
        logger.atSevere().withCause(ex).log("%s", msg);
        throw new ApiProxy.CancelledException(packageName, methodName);
      }
    } catch (TimeoutException ex) {
      logger.atInfo().withCause(ex).log("API call exceeded deadline");
      throw new ApiProxy.ApiDeadlineExceededException(packageName, methodName);
    } catch (ExecutionException ex) {
      // This will almost always be an ApiProxyException.
      Throwable cause = ex.getCause();
      if (cause instanceof ApiProxy.ApiProxyException) {
        // The ApiProxyException was generated during a callback in a Stubby
        // thread, so the stack trace it contains is not very useful to the user.
        // It would be more useful to the user to replace the stack trace with
        // the current stack trace. But that might lose some information that
        // could be useful to an App Engine developer. So we throw a copy of the
        // original exception that contains the current stack trace and contains
        // the original exception as the cause.
        ApiProxy.ApiProxyException apiProxyException = (ApiProxy.ApiProxyException) cause;
        throw apiProxyException.copy(Thread.currentThread().getStackTrace());
      } else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof Error) {
        logger.atSevere().withCause(cause).log("Error thrown from API call.");
        throw (Error) cause;
      } else {
        // Shouldn't happen, but just in case.
        logger.atWarning().withCause(cause).log("Checked exception thrown from API call.");
        throw new RuntimeException(cause);
      }
    } finally {
      // We used to use CountDownLatch for this wait, which could end
      // up setting the interrupt bit for this thread even if no
      // InterruptedException was thrown.  This should no longer be
      // the case, but we've leaving this code here temporarily.
      if (Thread.interrupted()) {
        logger.atWarning().log(
            "Thread %s was interrupted but we "
                + "did not receive an InterruptedException.  Resetting interrupt bit.",
            Thread.currentThread());
        // Calling interrupted() already reset the bit.
      }
    }
  }

  private Future<byte[]> doAsyncCall(
      EnvironmentImpl environment,
      String packageName,
      String methodName,
      byte[] requestBytes,
      Double requestDeadlineInSeconds) {
    TraceWriter traceWriter = environment.getTraceWriter();
    CloudTraceContext currentContext = null;
    if (traceWriter != null) {
      CloudTraceContext parentContext = CloudTrace.getCurrentContext(environment);
      currentContext = traceWriter.startApiSpan(parentContext, packageName, methodName);

      // Collects stack trace if required.
      if (TraceContextHelper.isStackTraceEnabled(currentContext)
          && environment.getTraceExceptionGenerator() != null) {
        StackTraceElement[] stackTrace =
            environment
                .getTraceExceptionGenerator()
                .getExceptionWithRequestId(new Exception(), environment.getRequestId())
                .getStackTrace();
        traceWriter.addStackTrace(currentContext, stackTrace);
      }
    }

    // Browserchannel messages are actually sent via XMPP, so this cheap hack
    // translates the packageName in production.  If these two services are
    // ever separated, this should be removed.
    if (packageName.equals("channel")) {
      packageName = "xmpp";
    }

    double deadlineInSeconds =
        deadlineOracle.getDeadline(
            packageName, environment.isOfflineRequest(), requestDeadlineInSeconds);

    APIRequest.Builder apiRequest =
        APIRequest.newBuilder()
            .setApiPackage(packageName)
            .setCall(methodName)
            .setSecurityTicket(environment.getSecurityTicket())
            .setPb(ByteString.copyFrom(requestBytes));
    if (currentContext != null) {
      apiRequest.setTraceContext(TraceContextHelper.toProto2(currentContext));
    }

    AnyRpcClientContext rpc = apiHost.newClientContext();
    long apiSlotWaitTime;
    try {
      // Get an API slot, waiting if there are already too many threads doing API calls.
      // If we do wait for t milliseconds then our deadline is decreased by t.
      apiSlotWaitTime = environment.apiRpcStarting(deadlineInSeconds);
      deadlineInSeconds -= apiSlotWaitTime / 1000.0;
      if (deadlineInSeconds < 0) {
        throw new InterruptedException("Deadline was used up while waiting for API RPC slot");
      }
    } catch (InterruptedException ex) {
      long remainingMillis = environment.getRemainingMillis();
      String msg = String.format(
          "Interrupted waiting for an API RPC slot with %d millis %s soft deadline",
          Math.abs(remainingMillis), remainingMillis > 0 ? "until" : "since");
      logger.atWarning().withCause(ex).log("%s", msg);
      if (remainingMillis <= ATTRIBUTE_TO_DEADLINE_MILLIS) {
        return createCancelledFuture(packageName, methodName, DEADLINE_REACHED_SLOT_REASON);
      } else {
        return createCancelledFuture(packageName, methodName, INTERRUPTED_SLOT_REASON);
      }
    }
    // At this point we have counted the API call against the concurrent limit, so if we get an
    // exception starting the asynchronous RPC then we must uncount the API call.
    try {
      return finishAsyncApiCallSetup(
          rpc,
          apiRequest.build(),
          currentContext,
          environment,
          packageName,
          methodName,
          deadlineInSeconds,
          apiSlotWaitTime);
    } catch (RuntimeException | Error e) {
      environment.apiRpcFinished();
      logger.atWarning().withCause(e).log("Exception in API call setup");
      return Futures.immediateFailedFuture(e);
    }
  }

  private Future<byte[]> finishAsyncApiCallSetup(
      AnyRpcClientContext rpc,
      APIRequest apiRequest,
      CloudTraceContext currentContext,
      EnvironmentImpl environment,
      String packageName,
      String methodName,
      double deadlineInSeconds,
      long apiSlotWaitTime) {
    rpc.setDeadline(deadlineInSeconds);

    if (!disableApiCallLogging) {
      logger.atInfo().log(
          "Beginning API call to %s.%s with deadline %g",
          packageName, methodName, deadlineInSeconds);
      if (apiSlotWaitTime > 0) {
        logger.atInfo().log(
            "Had to wait %dms for previous API calls to complete", apiSlotWaitTime);
      }
    }

    SettableFuture<byte[]> settableFuture = SettableFuture.create();
    long deadlineMillis = (long) (deadlineInSeconds * 1000.0);
    Future<byte[]> timedFuture =
        new TimedFuture<byte[]>(settableFuture, deadlineMillis + API_DEADLINE_PADDING) {
          @Override
          protected RuntimeException createDeadlineException() {
            return new ApiProxy.ApiDeadlineExceededException(packageName, methodName);
          }
        };
    AsyncApiFuture rpcCallback =
        new AsyncApiFuture(
            deadlineMillis,
            timedFuture,
            settableFuture,
            rpc,
            environment,
            currentContext,
            packageName,
            methodName,
            disableApiCallLogging);
    apiHost.call(rpc, apiRequest, rpcCallback);

    settableFuture.addListener(
        environment::apiRpcFinished,
        MoreExecutors.directExecutor());

    environment.addAsyncFuture(rpcCallback);
    return rpcCallback;
  }

  @SuppressWarnings("ShouldNotSubclass")
  private Future<byte[]> createCancelledFuture(
      final String packageName, final String methodName, final String reason) {
    return new Future<byte[]>() {
      @Override
      public byte[] get() {
        throw new ApiProxy.CancelledException(packageName, methodName, reason);
      }

      @Override
      public byte[] get(long deadline, TimeUnit unit) {
        throw new ApiProxy.CancelledException(packageName, methodName, reason);
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public boolean isCancelled() {
        return true;
      }

      @Override
      public boolean cancel(boolean shouldInterrupt) {
        return false;
      }
    };
  }

  /** An exception that simply adds at the top a single frame with the request id. */
  private static class TraceExceptionGenerator {

    /** The name of the class we use to identify our magic frame. */
    private static final String FRAME_CLASS = "com.google.appengine.runtime.Request";

    /** The name for the frame's method. We record the request id within the name of the method. */
    private static final String FRAME_METHOD_PREFIX = "process-";

    /** The file for the frame. This can be anything, but we make it match {@link #FRAME_CLASS}. */
    private static final String FRAME_FILE = "Request.java";

    public <T extends Throwable> T getExceptionWithRequestId(T exception, String requestId) {
      StackTraceElement[] frames = exception.getStackTrace();
      StackTraceElement[] newFrames = new StackTraceElement[frames.length + 1];
      // NOTE: Cloud Trace relies on the negative line number to decide
      // whether a frame is generated/magic or not.
      newFrames[0] =
          new StackTraceElement(FRAME_CLASS, FRAME_METHOD_PREFIX + requestId, FRAME_FILE, -1);
      System.arraycopy(frames, 0, newFrames, 1, frames.length);
      exception.setStackTrace(newFrames);
      return exception;
    }
  }

  private static class AsyncApiFuture extends ForwardingFuture<byte[]>
      implements AnyRpcCallback<APIResponse>, ApiResultFuture<byte[]> {
    private final long deadlineMillis;
    private static final long NO_VALUE = -1;
    private final AnyRpcClientContext rpc;
    private final EnvironmentImpl environment;
    private final CloudTraceContext context;
    private final String packageName;
    private final String methodName;
    private final AtomicLong cpuTimeInMegacycles;
    private final AtomicLong wallclockTimeInMillis;
    private final SettableFuture<byte[]> settable;
    private final Future<byte[]> delegate;
    private final boolean disableApiCallLogging;

    AsyncApiFuture(
        long deadlineMillis,
        Future<byte[]> delegate,
        SettableFuture<byte[]> settable,
        AnyRpcClientContext rpc,
        EnvironmentImpl environment,
        @Nullable CloudTraceContext currentContext,
        String packageName,
        String methodName,
        boolean disableApiCallLogging) {
      this.deadlineMillis = deadlineMillis;
      // We would like to make sure that wallclockTimeInMillis
      // and cpuTimeInMegacycles are only calculated once. Hence, we use an initial value, and
      // compareAndSet method below for these variables.
      // If RPC callbacks set these values, then wall clock time will be calculated based on
      // RPC completion. If the Future is done (i.e. due to a deadline) before we get to set
      // the values inside the callbacks, then wall clock time will return deadlineMillis and
      // getCpuTimeInMegaCycles will return 0.
      this.wallclockTimeInMillis = new AtomicLong(NO_VALUE);
      this.cpuTimeInMegacycles = new AtomicLong(NO_VALUE);
      this.delegate = delegate;
      this.settable = settable;
      this.rpc = rpc;
      this.environment = environment;
      this.context = currentContext;
      this.packageName = packageName;
      this.methodName = methodName;
      this.disableApiCallLogging = disableApiCallLogging;
    }

    @Override
    protected final Future<byte[]> delegate() {
      return delegate;
    }

    @Override
    public long getCpuTimeInMegaCycles() {
      if (!isDone()) {
        throw new IllegalStateException("API call has not completed yet.");
      }
      cpuTimeInMegacycles.compareAndSet(NO_VALUE, 0);
      return cpuTimeInMegacycles.get();
    }

    @Override
    public long getWallclockTimeInMillis() {
      if (!isDone()) {
        throw new IllegalStateException("API call has not completed yet.");
      }
      wallclockTimeInMillis.compareAndSet(NO_VALUE, deadlineMillis);
      return wallclockTimeInMillis.get();
    }

    private void endApiSpan() {
      TraceWriter traceWriter = environment.getTraceWriter();
      if (traceWriter != null && context != null) {
        traceWriter.endApiSpan(context);
      }
    }

    @Override
    public void success(APIResponse response) {
      APIResponse apiResponse = response;
      wallclockTimeInMillis.compareAndSet(
          NO_VALUE, System.currentTimeMillis() - rpc.getStartTimeMillis());

      // Update the stats
      if (apiResponse.hasCpuUsage()) {
        cpuTimeInMegacycles.compareAndSet(NO_VALUE, apiResponse.getCpuUsage());
        ((ApiStatsImpl) ApiStats.get(environment))
            .increaseApiTimeInMegacycles(cpuTimeInMegacycles.get());
      }

      endApiSpan();

      // N.B.: Do not call settable.setException() with an
      // Error.  SettableFuture will immediately rethrow the Error "to
      // make sure it reaches the top of the call stack."  Throwing an
      // Error from within a Stubby RPC callback will invoke
      // GlobalEventRegistry's error hook, which will call
      // System.exit(), which will fail.  This is bad.
      if (apiResponse.getError() == APIResponse.ERROR.OK_VALUE) {
        if (!disableApiCallLogging) {
          logger.atInfo().log("API call completed normally with status: %s", rpc.getStatus());
        }
        settable.set(apiResponse.getPb().toByteArray());
      } else {
        settable.setException(
            ApiProxyUtils.getApiError(packageName, methodName, apiResponse, logger));
      }
      environment.removeAsyncFuture(this);
    }

    @Override
    public void failure() {
      wallclockTimeInMillis.set(System.currentTimeMillis() - rpc.getStartTimeMillis());
      endApiSpan();

      setRpcError(
          rpc.getStatus(), rpc.getApplicationError(), rpc.getErrorDetail(), rpc.getException());
      environment.removeAsyncFuture(this);
    }

    private void setRpcError(
        StatusProto status, int applicationError, String errorDetail, Throwable cause) {
      logger.atWarning().log("APIHost::Call RPC failed : %s : %s", status, errorDetail);
      // N.B.: Do not call settable.setException() with an
      // Error.  SettableFuture will immediately rethrow the Error "to
      // make sure it reaches the top of the call stack."  Throwing an
      // Error from within a Stubby RPC callback will invoke
      // GlobalEventRegistry's error hook, which will call
      // System.exit(), which will fail.  This is bad.
      settable.setException(
          ApiProxyUtils.getRpcError(
              packageName, methodName, status, applicationError, errorDetail, cause));
    }

    @Override
    public boolean cancel(boolean mayInterrupt) {
      if (mayInterrupt) {
        if (super.cancel(mayInterrupt)) {
          rpc.startCancel();
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public void log(EnvironmentImpl environment, LogRecord record) {
    if (logToLogservice && environment != null) {
        environment.addLogRecord(record);
      }
  }

  @Override
  public void flushLogs(EnvironmentImpl environment) {
    if (logToLogservice && environment != null) {
        environment.flushLogs();
      }
    }

  @Override
  public List<Thread> getRequestThreads(EnvironmentImpl environment) {
    if (environment == null) {
      return Collections.emptyList();
    }
    return requestThreadManager.getRequestThreads(environment.getAppVersion().getKey());
  }

  /** Creates an {@link Environment} instance that is suitable for use with this class. */
  public EnvironmentImpl createEnvironment(
      AppVersion appVersion,
      UPRequest upRequest,
      MutableUpResponse upResponse,
      @Nullable TraceWriter traceWriter,
      CpuRatioTimer requestTimer,
      String requestId,
      List<Future<?>> asyncFutures,
      Semaphore outstandingApiRpcSemaphore,
      ThreadGroup requestThreadGroup,
      RequestState requestState,
      @Nullable Long millisUntilSoftDeadline) {
    return new EnvironmentImpl(
        appVersion,
        upRequest,
        upResponse,
        traceWriter,
        requestTimer,
        requestId,
        externalDatacenterName,
        asyncFutures,
        outstandingApiRpcSemaphore,
        byteCountBeforeFlushing,
        maxLogLineSize,
        Ints.checkedCast(maxLogFlushTime.getSeconds()),
        requestThreadGroup,
        requestState,
        coordinator,
        cloudSqlJdbcConnectivityEnabled,
        millisUntilSoftDeadline);
  }

  /**
   * Determine the API deadline to use for the specified Environment.
   * The default deadline for that package is used, unless an entry is
   * present in {@link Environment#getAttributes} with a key of {@code
   * API_DEADLINE_KEY} and a value of a {@link Number} object.  In
   * either case, the deadline cannot be higher than maximum deadline
   * for that package.
   */
  private double getApiDeadline(String packageName, EnvironmentImpl env) {
    // This hack is only used for sync API calls -- async calls
    // specify their own deadline.
    // TODO: In the next API version, we should always get
    // this from an ApiConfig.
    Number userDeadline = (Number) env.getAttributes().get(API_DEADLINE_KEY);
    return deadlineOracle.getDeadline(packageName, env.isOfflineRequest(), userDeadline);
  }

  private static final class CloudTraceImpl extends CloudTrace {
    private final TraceWriter writer;

    @CanIgnoreReturnValue
    CloudTraceImpl(EnvironmentImpl env) {
      super(env);
      this.writer = env.getTraceWriter();
      // Initialize the context for the current thread to be the root context of the TraceWriter.
      // This ensures the context from any previous requests is cleared out.
      CloudTrace.setCurrentContext(env, this.writer.getTraceContext());
    }

    @Override
    @Nullable
    protected CloudTraceContext getDefaultContext() {
      return writer == null ? null : writer.getTraceContext();
    }

    @Override
    @Nullable
    protected CloudTraceContext startChildSpanImpl(CloudTraceContext parent, String name) {
      return writer == null ? null : writer.startChildSpan(parent, name);
    }

    @Override
    protected void setLabelImpl(CloudTraceContext context, String key, String value) {
      if (writer != null) {
        writer.setLabel(context, key, value);
      }
    }

    @Override
    protected void endSpanImpl(CloudTraceContext context) {
      if (writer != null) {
        writer.endSpan(context);
      }
    }
  }

  private static final class ApiStatsImpl extends ApiStats {

    /**
     * Time spent in api cycles. This is basically an aggregate of all calls to
     * apiResponse.getCpuUsage().
     */
    private long apiTime;

    private final EnvironmentImpl env;

    @CanIgnoreReturnValue
    ApiStatsImpl(EnvironmentImpl env) {
      super(env);
      this.env = env;
    }

    @Override
    public long getApiTimeInMegaCycles() {
      return apiTime;
    }

    @Override
    public long getCpuTimeInMegaCycles() {
      return env.getRequestTimer().getCycleCount() / 1000000;
    }

    /**
     * Set the overall time spent in API cycles, as returned by the system.
     * @param delta a delta to increase the value by (in megacycles of CPU time)
     */
    private void increaseApiTimeInMegacycles(long delta) {
      this.apiTime += delta;
    }
  }

  /**
   * To implement ApiProxy.Environment, we use a class that wraps
   * around an UPRequest and retrieves the necessary information from
   * it.
   */
  public static final class EnvironmentImpl implements ApiProxy.EnvironmentWithTrace {
    // Keep this in sync with X_APPENGINE_DEFAULT_NAMESPACE and
    // X_APPENGINE_CURRENT_NAMESPACE in google3/apphosting/base/http_proto.cc
    // as well as the following:
    // com.google.appengine.tools.development.LocalHttpRequestEnvironment.DEFAULT_NAMESPACE_HEADER
    // com.google.appengine.tools.development.LocalHttpRequestEnvironment.CURRENT_NAMESPACE_HEADER
    // com.google.appengine.api.NamespaceManager.CURRENT_NAMESPACE_KEY
    // This list is not exhaustive, do a gsearch to find other occurrences.
    /**
     * The name of the HTTP header specifying the default namespace
     * for API calls.
     */
    // (Not private so that tests can use it.)
    static final String DEFAULT_NAMESPACE_HEADER = "X-AppEngine-Default-Namespace";
    static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";
    private static final String CURRENT_NAMESPACE_KEY =
        "com.google.appengine.api.NamespaceManager.currentNamespace";
    private static final String APPS_NAMESPACE_KEY =
        "com.google.appengine.api.NamespaceManager.appsNamespace";
    private static final String REQUEST_THREAD_FACTORY_ATTR =
        "com.google.appengine.api.ThreadManager.REQUEST_THREAD_FACTORY";
    private static final String BACKGROUND_THREAD_FACTORY_ATTR =
        "com.google.appengine.api.ThreadManager.BACKGROUND_THREAD_FACTORY";

    private final AppVersion appVersion;
    private final UPRequest upRequest;
    private final CpuRatioTimer requestTimer;
    private final Map<String, Object> attributes;
    private final String requestId;
    private final List<Future<?>> asyncFutures;
    private final AppLogsWriter appLogsWriter;
    @Nullable private final TraceWriter traceWriter;
    @Nullable private final TraceExceptionGenerator traceExceptionGenerator;
    private final Semaphore outstandingApiRpcSemaphore;
    private final ThreadGroup requestThreadGroup;
    private final RequestState requestState;
    private final Optional<String> traceId;
    private final Optional<String> spanId;
    @Nullable private final Long millisUntilSoftDeadline;

    EnvironmentImpl(
        AppVersion appVersion,
        UPRequest upRequest,
        MutableUpResponse upResponse,
        @Nullable TraceWriter traceWriter,
        CpuRatioTimer requestTimer,
        String requestId,
        String externalDatacenterName,
        List<Future<?>> asyncFutures,
        Semaphore outstandingApiRpcSemaphore,
        long byteCountBeforeFlushing,
        int maxLogLineSize,
        int maxLogFlushSeconds,
        ThreadGroup requestThreadGroup,
        RequestState requestState,
        BackgroundRequestCoordinator coordinator,
        boolean cloudSqlJdbcConnectivityEnabled,
        @Nullable Long millisUntilSoftDeadline) {
      this.appVersion = appVersion;
      this.upRequest = upRequest;
      this.requestTimer = requestTimer;
      this.requestId = requestId;
      this.asyncFutures = asyncFutures;
      this.attributes =
          createInitialAttributes(
              upRequest, externalDatacenterName, coordinator, cloudSqlJdbcConnectivityEnabled);
      this.outstandingApiRpcSemaphore = outstandingApiRpcSemaphore;
      this.requestState = requestState;
      this.millisUntilSoftDeadline = millisUntilSoftDeadline;

      this.traceId = this.buildTraceId();
      this.spanId = this.buildSpanId();

      for (ParsedHttpHeader header : upRequest.getRequest().getHeadersList()) {
        if (header.getKey().equals(DEFAULT_NAMESPACE_HEADER)) {
          attributes.put(APPS_NAMESPACE_KEY, header.getValue());
        } else if (header.getKey().equals(CURRENT_NAMESPACE_HEADER)) {
          attributes.put(CURRENT_NAMESPACE_KEY, header.getValue());
        }
      }

      // Bind an ApiStats class to this environment.
      new ApiStatsImpl(this);

      boolean isLongRequest = attributes.containsKey(BACKEND_ID_KEY) || isOfflineRequest();
      this.appLogsWriter =
          new AppLogsWriter(
              upResponse,
              byteCountBeforeFlushing,
              maxLogLineSize,
              isLongRequest ? maxLogFlushSeconds : 0);

      this.traceWriter = traceWriter;
      if (TraceContextHelper.needsStackTrace(upRequest.getTraceContext())) {
        this.traceExceptionGenerator = new TraceExceptionGenerator();
      } else {
        this.traceExceptionGenerator = null;
      }

      // Bind a CloudTrace class to this environment.
      if (traceWriter != null && traceWriter.getTraceContext() != null) {
        new CloudTraceImpl(this);
      }

      this.requestThreadGroup = requestThreadGroup;
    }

    private Optional<String> buildTraceId() {
      if (this.upRequest.hasTraceContext()) {
        try {
          TraceId.TraceIdProto traceIdProto =
              TraceId.TraceIdProto.parseFrom(
                  this.upRequest.getTraceContext().getTraceId(),
                  ExtensionRegistry.getEmptyRegistry());
          String traceIdString =
              String.format("%016x%016x", traceIdProto.getHi(), traceIdProto.getLo());

          return Optional.of(traceIdString);
        } catch (InvalidProtocolBufferException e) {
          logger.atWarning().withCause(e).log("Unable to parse Trace ID:");
          return Optional.empty();
        }
      }
      return Optional.empty();
    }

    private Optional<String> buildSpanId() {
      if (this.upRequest.hasTraceContext() && this.upRequest.getTraceContext().hasSpanId()) {
        // Stackdriver expects the spanId to be a 16-character hexadecimal encoding of an 8-byte
        // array, such as "000000000000004a"
        String spanIdString = String.format("%016x", this.upRequest.getTraceContext().getSpanId());
        return Optional.of(spanIdString);
      }
      return Optional.empty();
    }

    /**
     * Ensure that we don't already have too many API calls in progress, and wait if we do.
     *
     * @return the length of time we had to wait for an API slot.
     */
    long apiRpcStarting(double deadlineInSeconds) throws InterruptedException {
      if (deadlineInSeconds >= Double.MAX_VALUE) {
        outstandingApiRpcSemaphore.acquire();
        return 0;
      }
      // System.nanoTime() is guaranteed monotonic, unlike System.currentTimeMillis(). Of course
      // there are 1,000,000 nanoseconds in a millisecond.
      long startTime = System.nanoTime();
      long deadlineInMillis = Math.round(deadlineInSeconds * 1000);
      boolean acquired =
          outstandingApiRpcSemaphore.tryAcquire(deadlineInMillis, TimeUnit.MILLISECONDS);
      long elapsed = (System.nanoTime() - startTime) / 1_000_000;
      if (!acquired || elapsed >= deadlineInMillis) {
        if (acquired) {
          outstandingApiRpcSemaphore.release();
        }
        throw new InterruptedException("Deadline passed while waiting for API slot");
      }
      return elapsed;
    }

    void apiRpcFinished() {
      outstandingApiRpcSemaphore.release();
    }

    void addAsyncFuture(Future<?> future) {
      asyncFutures.add(future);
    }

    boolean removeAsyncFuture(Future<?> future) {
      return asyncFutures.remove(future);
    }

    private static Map<String, Object> createInitialAttributes(
        UPRequest upRequest,
        String externalDatacenterName,
        BackgroundRequestCoordinator coordinator,
        boolean cloudSqlJdbcConnectivityEnabled) {
      Map<String, Object> attributes = new HashMap<String, Object>();
      attributes.put(USER_ID_KEY, upRequest.getObfuscatedGaiaId());
      attributes.put(USER_ORGANIZATION_KEY, upRequest.getUserOrganization());
      // Federated Login is no longer supported, but these fields were previously set,
      // so they must be maintained.
      attributes.put("com.google.appengine.api.users.UserService.federated_identity", "");
      attributes.put("com.google.appengine.api.users.UserService.federated_authority", "");
      attributes.put("com.google.appengine.api.users.UserService.is_federated_user", false);
      if (upRequest.getIsTrustedApp()) {
        attributes.put(LOAS_PEER_USERNAME, upRequest.getPeerUsername());
        attributes.put(LOAS_SECURITY_LEVEL, upRequest.getSecurityLevel());
        attributes.put(IS_TRUSTED_IP, upRequest.getRequest().getTrusted());
        // NOTE: Omitted if absent, so that this has the same format as
        // USER_ID_KEY.
        long gaiaId = upRequest.getGaiaId();
        attributes.put(GAIA_ID, gaiaId == 0 ? "" : Long.toString(gaiaId));
        attributes.put(GAIA_AUTHUSER, upRequest.getAuthuser());
        attributes.put(GAIA_SESSION, upRequest.getGaiaSession());
        attributes.put(APPSERVER_DATACENTER, upRequest.getAppserverDatacenter());
        attributes.put(APPSERVER_TASK_BNS, upRequest.getAppserverTaskBns());
      }

      if (externalDatacenterName != null) {
        attributes.put(DATACENTER, externalDatacenterName);
      }

      if (upRequest.hasEventIdHash()) {
        attributes.put(REQUEST_ID_HASH, upRequest.getEventIdHash());
      }
      if (upRequest.hasRequestLogId()) {
        attributes.put(REQUEST_LOG_ID, upRequest.getRequestLogId());
      }

      if (upRequest.hasDefaultVersionHostname()) {
        attributes.put(DEFAULT_VERSION_HOSTNAME, upRequest.getDefaultVersionHostname());
      }
      attributes.put(REQUEST_THREAD_FACTORY_ATTR, CurrentRequestThreadFactory.SINGLETON);
      attributes.put(BACKGROUND_THREAD_FACTORY_ATTR, new BackgroundThreadFactory(coordinator));

      attributes.put(CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY, cloudSqlJdbcConnectivityEnabled);

      // Environments are associated with requests, and can now be
      // shared across more than one thread.  We'll synchronize all
      // individual calls, which should be sufficient.
      return Collections.synchronizedMap(attributes);
    }

    public void addLogRecord(LogRecord record) {
      appLogsWriter.addLogRecordAndMaybeFlush(record);
    }

    public void flushLogs() {
      appLogsWriter.flushAndWait();
    }

    public TraceWriter getTraceWriter() {
      return traceWriter;
    }

    @Override
    public String getAppId() {
      return upRequest.getAppId();
    }

    @Override
    public String getModuleId() {
      return upRequest.getModuleId();
    }

    @Override
    public String getVersionId() {
      // We use the module_version_id field because the version_id field has the 'module:version'
      // form.
      return upRequest.getModuleVersionId();
    }

    /**
     * Get the trace id of the current request, which can be used to correlate log messages
     * belonging to that request.
     */
    public Optional<String> getTraceId() {
      return traceId;
    }

    /**
     * Get the span id of the current request, which can be used to identify a span within a trace.
     */
    public Optional<String> getSpanId() {
      return spanId;
    }

    public AppVersion getAppVersion() {
      return appVersion;
    }

    @Override
    public boolean isLoggedIn() {
      // TODO: It would be nice if UPRequest had a bool for this.
      return upRequest.getEmail().length() > 0;
    }

    @Override
    public boolean isAdmin() {
      return upRequest.getIsAdmin();
    }

    @Override
    public String getEmail() {
      return upRequest.getEmail();
    }

    @Override
    public String getAuthDomain() {
      return upRequest.getAuthDomain();
    }

    @Override
    @Deprecated
    public String getRequestNamespace() {
      // This logic is duplicated from NamespaceManager where it is a
      // static method so it can't be used here.
      String appsNamespace = (String) attributes.get(APPS_NAMESPACE_KEY);
      return appsNamespace == null ? "" : appsNamespace;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    /**
     * Returns the security ticket associated with this environment.
     *
     * <p>Note that this method is not available on the public
     * Environment interface, as it is used internally by ApiProxyImpl
     * and there is no reason to expose it to applications.
     */
    String getSecurityTicket() {
      return upRequest.getSecurityTicket();
    }

    boolean isOfflineRequest() {
      return upRequest.getRequest().getIsOffline();
    }

    /**
     * Returns the request id associated with this environment.
     */
    String getRequestId() {
      return requestId;
    }

    CpuRatioTimer getRequestTimer() {
      return requestTimer;
    }

    ThreadGroup getRequestThreadGroup() {
      return requestThreadGroup;
    }

    RequestState getRequestState() {
      return requestState;
    }

    @Override
    public long getRemainingMillis() {
      if (millisUntilSoftDeadline == null) {
        return Long.MAX_VALUE;
      }
      return millisUntilSoftDeadline
          - (requestTimer.getWallclockTimer().getNanoseconds() / 1000000L);
    }

    /**
     * Get the {@link AppLogsWriter} instance that is used by the
     * {@link #addLogRecord(LogRecord)} and {@link #flushLogs()} methods.
     *
     * <p>This method is not simply visible for testing, it only exists for testing.
     */
    @VisibleForTesting
    AppLogsWriter getAppLogsWriter() {
      return appLogsWriter;
    }

    public TraceExceptionGenerator getTraceExceptionGenerator() {
      return traceExceptionGenerator;
    }
  }

  /**
   * A thread created by {@code ThreadManager.currentRequestThreadFactory().
   */
  static class CurrentRequestThread extends Thread {
    private final Runnable userRunnable;
    private final RequestState requestState;
    private final ApiProxy.Environment environment;

    CurrentRequestThread(
        ThreadGroup requestThreadGroup,
        Runnable runnable,
        Runnable userRunnable,
        RequestState requestState,
        ApiProxy.Environment environment) {
      super(requestThreadGroup, runnable);
      this.userRunnable = userRunnable;
      this.requestState = requestState;
      this.environment = environment;
    }

    /**
     * Returns the original Runnable that was supplied to the thread factory, before any wrapping
     * we may have done.
     */
    Runnable userRunnable() {
      return userRunnable;
    }

    @Override
    public synchronized void start() {
      if (!requestState.getAllowNewRequestThreadCreation()) {
        throw new IllegalStateException(
            "Cannot create new threads after request thread stops.");
      }
      // We want to ensure that when start() returns, the thread has been recorded. That means
      // that if a request creates a thread and immediately returns, before the thread has started
      // to run, we will still wait for the new thread to complete. We need to be careful not to
      // hold on to the thread if the attempt to start it fails, because then we won't be executing
      // forgetRequestThread in the overridden run() method. It would be wrong to call
      // recordRequestThread *after* super.start(), because in that case the thread could run to
      // completion before we could record it, and forgetRequestThread would already have happened.
      requestState.recordRequestThread(this);
      boolean started = false;
      try {
        super.start();
        started = true;
      } finally {
        if (!started) {
          requestState.forgetRequestThread(this);
        }
      }
    }

    @Override
    public void run() {
      try {
        ApiProxy.setEnvironmentForCurrentThread(environment);
        super.run();
      } finally {
        requestState.forgetRequestThread(this);
      }
    }
  }

  private static PrivilegedAction<Void> runWithThreadContext(
      Runnable runnable, Environment environment, CloudTraceContext parentThreadContext) {
    return () -> {
      CloudTrace.setCurrentContext(environment, parentThreadContext);
      try {
        runnable.run();
      } finally {
        CloudTrace.setCurrentContext(environment, null);
      }
      return null;
    };
  }

  private static final class CurrentRequestThreadFactory implements ThreadFactory {
    private static final ThreadFactory SINGLETON = new CurrentRequestThreadFactory();

    @Override
    public Thread newThread(final Runnable runnable) {
      EnvironmentImpl environment = (EnvironmentImpl) ApiProxy.getCurrentEnvironment();
      if (environment == null) {
        throw new NullPointerException("Operation not allowed in a thread that is neither "
            + "the original request thread nor a thread created by ThreadManager");
      }
      ThreadGroup requestThreadGroup = environment.getRequestThreadGroup();
      RequestState requestState = environment.getRequestState();

      CloudTraceContext parentThreadContext =
          CloudTrace.getCurrentContext(environment);
      AccessControlContext context = AccessController.getContext();
      Runnable contextRunnable =
          () ->
              AccessController.doPrivileged(
                  runWithThreadContext(runnable, environment, parentThreadContext), context);
      return AccessController.doPrivileged(
          (PrivilegedAction<Thread>) () -> new CurrentRequestThread(
              requestThreadGroup, contextRunnable, runnable, requestState, environment));
    }
  }

  private static final class BackgroundThreadFactory implements ThreadFactory {
    private final BackgroundRequestCoordinator coordinator;
    private final SystemService systemService;

    public BackgroundThreadFactory(BackgroundRequestCoordinator coordinator) {
      this.coordinator = coordinator;
      this.systemService = new SystemService();
    }

    @Override
    public Thread newThread(final Runnable runnable) {
      EnvironmentImpl environment = (EnvironmentImpl) ApiProxy.getCurrentEnvironment();
      if (environment == null) {
        throw new NullPointerException("Operation not allowed in a thread that is neither "
            + "the original request thread nor a thread created by ThreadManager");
      }

      CloudTraceContext parentThreadContext =
          CloudTrace.getCurrentContext(environment);
      AccessControlContext context = AccessController.getContext();
      Runnable contextRunnable =
          () ->
              AccessController.doPrivileged(
                  runWithThreadContext(runnable, environment, parentThreadContext), context);

      String requestId = systemService.startBackgroundRequest();
      Number deadline = MoreObjects.firstNonNull(
          (Number) environment.getAttributes().get(BACKGROUND_THREAD_REQUEST_DEADLINE_KEY),
          DEFAULT_BACKGROUND_THREAD_REQUEST_DEADLINE);
      try {
        return coordinator.waitForThreadStart(requestId, contextRunnable, deadline.longValue());
      } catch (TimeoutException ex) {
        logger.atWarning().withCause(ex).log(
            "Timeout while waiting for background thread startup:");
        return null;
      } catch (InterruptedException ex) {
        logger.atWarning().withCause(ex).log(
            "Interrupted while waiting for background thread startup:");
        // Reset the interrupt bit because someone wants us to exit.
        Thread.currentThread().interrupt();
        // We can't throw InterruptedException from here though, so do
        // what an API call would do in this situation.
        throw new ApiProxy.CancelledException("system", "StartBackgroundRequest");
      }
    }
  }
}
