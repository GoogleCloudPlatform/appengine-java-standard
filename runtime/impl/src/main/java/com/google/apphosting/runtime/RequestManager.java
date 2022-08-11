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

import static com.google.apphosting.base.protos.RuntimePb.UPRequest.Deadline.RPC_DEADLINE_PADDING_SECONDS_VALUE;

import com.google.appengine.api.LifecycleManager;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord.Level;
import com.google.apphosting.api.DeadlineExceededException;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppLogsPb.AppLogLine;
import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.timer.CpuRatioTimer;
import com.google.apphosting.runtime.timer.JmxGcTimerSet;
import com.google.apphosting.runtime.timer.JmxHotspotTimerSet;
import com.google.apphosting.runtime.timer.TimerFactory;
import com.google.auto.value.AutoBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.GoogleLogger;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * {@code RequestManager} is responsible for setting up and tearing
 * down any state associated with each request.
 *
 * At the moment, this includes:
 * <ul>
 *  <li>Injecting an {@code Environment} implementation for the
 *  request's thread into {@code ApiProxy}.
 *  <li>Scheduling any future actions that must occur while the
 *  request is executing (e.g. deadline exceptions), and cleaning up
 *  any scheduled actions that do not occur.
 * </ul>
 *
 * It is expected that clients will use it like this:
 * <pre>
 * RequestManager.RequestToken token =
 *     requestManager.startRequest(...);
 * try {
 *   ...
 * } finally {
 *   requestManager.finishRequest(token);
 * }
 * </pre>
 *
 */
public class RequestManager {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * The number of threads to use to execute scheduled {@code Future}
   * actions.
   */
  private static final int SCHEDULER_THREADS = 1;

  // SimpleDateFormat is not threadsafe, so we'll just share the format string and let
  // clients instantiate the format instances as-needed.  At the moment the usage of the format
  // objects shouldn't be too high volume, but if the construction of the format instance ever has
  // a noticeable impact on performance (unlikely) we can switch to one format instance per thread
  // using a ThreadLocal.
  private static final String SIMPLE_DATE_FORMAT_STRING = "yyyy/MM/dd HH:mm:ss.SSS z";

  /**
   * The maximum number of stack frames to log for each thread when
   * logging a deadlock.
   */
  private static final int MAXIMUM_DEADLOCK_STACK_LENGTH = 20;

  private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

  private static final String INSTANCE_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.id";

  /** The amount of time to wait for pending async futures to cancel. */
  private static final Duration CANCEL_ASYNC_FUTURES_WAIT_TIME = Duration.ofMillis(150);

  /**
   * The amount of time to wait for Thread.Interrupt to complete on all threads servicing a request.
   */
  private static final Duration THREAD_INTERRUPT_WAIT_TIME = Duration.ofSeconds(1);

  private final long softDeadlineDelay;
  private final long hardDeadlineDelay;
  private final boolean disableDeadlineTimers;
  private final ScheduledThreadPoolExecutor executor;
  private final TimerFactory timerFactory;
  private final Optional<RuntimeLogSink> runtimeLogSink;
  private final ApiProxyImpl apiProxyImpl;
  private final boolean threadStopTerminatesClone;
  private final Map<String, RequestToken> requests;
  private final boolean interruptFirstOnSoftDeadline;
  private int maxOutstandingApiRpcs;
  @Nullable private final CloudDebuggerAgentWrapper cloudDebuggerAgent;
  private final AtomicBoolean enableCloudDebugger;
  private final boolean waitForDaemonRequestThreads;
  private final AtomicBoolean debugletStartNotified = new AtomicBoolean(false);
  private final Map<String, String> environmentVariables;

  /** Make a partly-initialized builder for a RequestManager. */
  public static Builder builder() {
    return new AutoBuilder_RequestManager_Builder()
        .setEnvironment(System.getenv());
  }

  /** Builder for of a RequestManager instance. */
  @AutoBuilder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder setSoftDeadlineDelay(long x);

    public abstract long softDeadlineDelay();

    public abstract Builder setHardDeadlineDelay(long x);

    public abstract long hardDeadlineDelay();

    public abstract Builder setDisableDeadlineTimers(boolean x);

    public abstract boolean disableDeadlineTimers();

    public abstract Builder setRuntimeLogSink(Optional<RuntimeLogSink> x);


    public abstract Builder setApiProxyImpl(ApiProxyImpl x);

    public abstract Builder setMaxOutstandingApiRpcs(int x);

    public abstract int maxOutstandingApiRpcs();

    public abstract Builder setThreadStopTerminatesClone(boolean x);

    public abstract boolean threadStopTerminatesClone();

    public abstract Builder setInterruptFirstOnSoftDeadline(boolean x);

    public abstract boolean interruptFirstOnSoftDeadline();

    public abstract Builder setCloudDebuggerAgent(@Nullable CloudDebuggerAgentWrapper x);

    public abstract Builder setEnableCloudDebugger(boolean x);

    public abstract boolean enableCloudDebugger();

    public abstract Builder setCyclesPerSecond(long x);

    public abstract long cyclesPerSecond();

    public abstract Builder setWaitForDaemonRequestThreads(boolean x);

    public abstract boolean waitForDaemonRequestThreads();

    public abstract Builder setEnvironment(Map<String, String> x);

    public abstract RequestManager build();
  }

  RequestManager(
      long softDeadlineDelay,
      long hardDeadlineDelay,
      boolean disableDeadlineTimers,
      Optional<RuntimeLogSink> runtimeLogSink,
      ApiProxyImpl apiProxyImpl,
      int maxOutstandingApiRpcs,
      boolean threadStopTerminatesClone,
      boolean interruptFirstOnSoftDeadline,
      @Nullable CloudDebuggerAgentWrapper cloudDebuggerAgent,
      boolean enableCloudDebugger,
      long cyclesPerSecond,
      boolean waitForDaemonRequestThreads,
      ImmutableMap<String, String> environment) {
    this.softDeadlineDelay = softDeadlineDelay;
    this.hardDeadlineDelay = hardDeadlineDelay;
    this.disableDeadlineTimers = disableDeadlineTimers;
    this.executor = new ScheduledThreadPoolExecutor(SCHEDULER_THREADS);
    this.timerFactory =
        new TimerFactory(cyclesPerSecond, new JmxHotspotTimerSet(), new JmxGcTimerSet());
    this.runtimeLogSink = runtimeLogSink;
    this.apiProxyImpl = apiProxyImpl;
    this.maxOutstandingApiRpcs = maxOutstandingApiRpcs;
    this.threadStopTerminatesClone = threadStopTerminatesClone;
    this.interruptFirstOnSoftDeadline = interruptFirstOnSoftDeadline;
    this.cloudDebuggerAgent = cloudDebuggerAgent;
    this.enableCloudDebugger = new AtomicBoolean(enableCloudDebugger);
    this.waitForDaemonRequestThreads = waitForDaemonRequestThreads;
    this.requests = Collections.synchronizedMap(new HashMap<String, RequestToken>());
    this.environmentVariables = environment;
  }

  public void setMaxOutstandingApiRpcs(int maxOutstandingApiRpcs) {
    this.maxOutstandingApiRpcs = maxOutstandingApiRpcs;
  }

  /**
   * Disables Cloud Debugger.
   *
   * <p>If called before the first request has been processed, the Cloud Debugger will not be even
   * activated.
   */
  public void disableCloudDebugger() {
    enableCloudDebugger.set(false);
  }

  /**
   * Set up any state necessary to execute a new request using the
   * specified parameters.  The current thread should be the one that
   * will execute the new request.
   *
   * @return a {@code RequestToken} that should be passed into {@code
   * finishRequest} after the request completes.
   */
  public RequestToken startRequest(
      AppVersion appVersion,
      AnyRpcServerContext rpc,
      UPRequest upRequest,
      MutableUpResponse upResponse,
      ThreadGroup requestThreadGroup) {
    long remainingTime = getAdjustedRpcDeadline(rpc, 60000);
    long softDeadlineMillis = Math.max(getAdjustedRpcDeadline(rpc, -1) - softDeadlineDelay, -1);
    long millisUntilSoftDeadline = remainingTime - softDeadlineDelay;
    Thread thread = Thread.currentThread();

    // Hex-encode the request-id, formatted to 16 digits, in lower-case,
    // with leading 0s, and no leading 0x to match the way stubby
    // request ids are formatted in Google logs.
    String requestId = String.format("%1$016x", rpc.getGlobalId());
    logger.atInfo().log("Beginning request %s remaining millis : %d", requestId, remainingTime);

    Runnable endAction;
    if (isSnapshotRequest(upRequest)) {
      logger.atInfo().log("Received snapshot request");
      endAction = new DisableApiHostAction();
    } else {
      apiProxyImpl.enable();
      endAction = new NullAction();
    }

    TraceWriter traceWriter = TraceWriter.getTraceWriterForRequest(upRequest, upResponse);
    if (traceWriter != null) {
      URL requestURL = null;
      try {
        requestURL = new URL(upRequest.getRequest().getUrl());
      } catch (MalformedURLException e) {
        logger.atWarning().withCause(e).log(
            "Failed to extract path for trace due to malformed request URL: %s",
            upRequest.getRequest().getUrl());
      }
      if (requestURL != null) {
        traceWriter.startRequestSpan(requestURL.getPath());
      } else {
        traceWriter.startRequestSpan("Unparsable URL");
      }
    }

    CpuRatioTimer timer = timerFactory.getCpuRatioTimer(thread);

    // This list is used to block the end of a request until all API
    // calls have completed or timed out.
    List<Future<?>> asyncFutures = Collections.synchronizedList(new ArrayList<Future<?>>());
    // This semaphore maintains the count of currently running API
    // calls so we can block future calls if too many calls are
    // outstanding.
    Semaphore outstandingApiRpcSemaphore = new Semaphore(maxOutstandingApiRpcs);

    RequestState state = new RequestState();
    state.recordRequestThread(Thread.currentThread());

    ApiProxy.Environment environment =
        apiProxyImpl.createEnvironment(
            appVersion,
            upRequest,
            upResponse,
            traceWriter,
            timer,
            requestId,
            asyncFutures,
            outstandingApiRpcSemaphore,
            requestThreadGroup,
            state,
            millisUntilSoftDeadline);

    // If the instance id was not set (e.g. for some Titanium runtimes), set the instance id
    // retrieved from the environment variable.
    String instanceId = environmentVariables.get("GAE_INSTANCE");
    if (!Strings.isNullOrEmpty(instanceId)) {
      environment.getAttributes().putIfAbsent(INSTANCE_ID_ENV_ATTRIBUTE, instanceId);
    }

    // Create a RequestToken where we will store any state we will
    // need to restore in finishRequest().
    RequestToken token =
        new RequestToken(
            thread,
            upResponse,
            requestId,
            upRequest.getSecurityTicket(),
            timer,
            asyncFutures,
            appVersion,
            softDeadlineMillis,
            rpc,
            rpc.getStartTimeMillis(),
            traceWriter,
            state,
            endAction);

    requests.put(upRequest.getSecurityTicket(), token);

    // Tell the ApiProxy about our current request environment so that
    // it can make callbacks and pass along information about the
    // logged-in user.
    ApiProxy.setEnvironmentForCurrentThread(environment);

    // Let the appserver know that we're up and running.
    setPendingStartCloudDebugger(upResponse);

    // Start counting CPU cycles used by this thread.
    timer.start();

    if (!disableDeadlineTimers) {
      // The timing conventions here are a bit wonky, but this is what
      // the Python runtime does.
      logger.atInfo().log(
          "Scheduling soft deadline in %d ms for %s", millisUntilSoftDeadline, requestId);
      token.addScheduledFuture(
          schedule(new DeadlineRunnable(this, token, false), millisUntilSoftDeadline));
    }

    return token;
  }

  /**
   * Tear down any state associated with the specified request, and
   * restore the current thread's state as it was before {@code
   * startRequest} was called.
   *
   * @throws IllegalStateException if called from the wrong thread.
   */
  public void finishRequest(RequestToken requestToken) {
    verifyRequestAndThread(requestToken);

    // Don't let user code create any more threads.  This is
    // especially important for ThreadPoolExecutors, which will try to
    // backfill the threads that we're about to interrupt without user
    // intervention.
    requestToken.getState().setAllowNewRequestThreadCreation(false);

    // Interrupt any other request threads.
    for (Thread thread : getActiveThreads(requestToken)) {
      logger.atWarning().log("Interrupting %s", thread);
      thread.interrupt();
    }

    // Send any pending breakpoint updates from Cloud Debugger.
    if (enableCloudDebugger.get() && cloudDebuggerAgent.hasBreakpointUpdates()) {
      setPendingCloudDebuggerBreakpointUpdates(requestToken.getUpResponse());
    }

    // Now wait for any async API calls and all request threads to complete.
    waitForUserCodeToComplete(requestToken);

    // There is no more user code left, stop the timers and tear down the state.
    requests.remove(requestToken.getSecurityTicket());
    requestToken.setFinished();

    // Stop the timer first so the user does get charged for our clean-up.
    CpuRatioTimer timer = requestToken.getRequestTimer();
    timer.stop();

    // Cancel any scheduled future actions associated with this
    // request.
    //
    // N.B.: Copy the list to avoid a
    // ConcurrentModificationException due to a race condition where
    // the soft deadline runnable runs and adds the hard deadline
    // runnable while we are waiting for it to finish.  We don't
    // actually care about this race because we set
    // RequestToken.finished above and both runnables check that
    // first.
    for (Future<?> future : new ArrayList<Future<?>>(requestToken.getScheduledFutures())) {
      // Unit tests will fail if a future fails to execute correctly, but
      // we won't get a good error message if it was due to some exception.
      // Log a future failure due to exception here.
      if (future.isDone()) {
        try {
          future.get();
        } catch (Exception e) {
          logger.atSevere().withCause(e).log("Future failed execution: %s", future);
        }
      } else if (future.cancel(false)) {
        logger.atFine().log("Removed scheduled future: %s", future);
      } else {
        logger.atFine().log("Unable to remove scheduled future: %s", future);
      }
    }

    // Store the CPU usage for this request in the UPResponse.
    logger.atInfo().log("Stopped timer for request %s %s", requestToken.getRequestId(), timer);
    requestToken.getUpResponse().setUserMcycles(timer.getCycleCount() / 1000000L);

    if (requestToken.getTraceWriter() != null) {
      requestToken.getTraceWriter().endRequestSpan();
      requestToken.getTraceWriter().flushTrace();
    }

    requestToken.runEndAction();

    // Remove our environment information to remove any potential
    // for leakage.
    ApiProxy.clearEnvironmentForCurrentThread();

    runtimeLogSink.ifPresent(x -> x.flushLogs(requestToken.getUpResponse()));
  }

  private static boolean isSnapshotRequest(UPRequest request) {
    try {
      URI uri = new URI(request.getRequest().getUrl());
      if (!"/_ah/snapshot".equals(uri.getPath())) {
        return false;
      }
    } catch (URISyntaxException e) {
      return false;
    }
    for (HttpPb.ParsedHttpHeader header : request.getRequest().getHeadersList()) {
      if ("X-AppEngine-Snapshot".equalsIgnoreCase(header.getKey())) {
        return true;
      }
    }
    return false;
  }

  private class DisableApiHostAction implements Runnable {
    @Override
    public void run() {
      apiProxyImpl.disable();
    }
  }

  private static class NullAction implements Runnable {
    @Override
    public void run() {}
  }

  public void sendDeadline(String securityTicket, boolean isUncatchable) {
    logger.atInfo().log("Looking up token for security ticket %s", securityTicket);
    sendDeadline(requests.get(securityTicket), isUncatchable);
  }

  // In Java 8, the method Thread.stop(Throwable), which has been deprecated for about 15 years,
  // has finally been disabled. It now throws UnsupportedOperationException. However, Thread.stop()
  // still works, and calls the JNI Method Thread.stop0(Object) with a Throwable argument.
  // So at least for the time being we can still achieve the effect of Thread.stop(Throwable) by
  // calling the JNI method. That means we don't get the permission checks and so on that come
  // with Thread.stop, but the code that's calling it is privileged anyway.
  private static final Method threadStop0;

  static {
    try {
      threadStop0 = Thread.class.getDeclaredMethod("stop0", Object.class);
      threadStop0.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  // Although Thread.stop(Throwable) is deprecated due to being
  // "inherently unsafe", it does exactly what we want.  Locks will
  // still be released (unlike Thread.destroy), so the only real
  // risk is that users are not expecting a particular piece of code
  // to throw an exception, and therefore when an exception is
  // thrown it leaves their objects in a bad state.  Since objects
  // should not be shared across requests, this should not be a very
  // big problem.
  public void sendDeadline(RequestToken token, boolean isUncatchable) {
    if (token == null) {
      logger.atInfo().log("No token, can't send deadline");
      return;
    }
    checkForDeadlocks(token);

    final Thread targetThread = token.getRequestThread();
    logger.atInfo().log(
        "Sending deadline: %s, %s, %b", targetThread, token.getRequestId(), isUncatchable);

    if (interruptFirstOnSoftDeadline && !isUncatchable) {
      // Disable thread creation and cancel all pending futures, then interrupt all threads,
      // all while giving the application some time to return a response after each step.
      token.getState().setAllowNewRequestThreadCreation(false);
      cancelPendingAsyncFutures(token.getAsyncFutures());
      waitForResponseDuringSoftDeadline(CANCEL_ASYNC_FUTURES_WAIT_TIME);
      if (!token.isFinished()) {
        logger.atInfo().log("Interrupting all request threads.");
        for (Thread thread : getActiveThreads(token)) {
          thread.interrupt();
        }
        // Runtime will kill the clone if all threads servicing the request
        // are not interrupted by the end of this wait. This is set to 2s as
        // a reasonable amount of time to interrupt the maximum number of threads (50).
        waitForResponseDuringSoftDeadline(THREAD_INTERRUPT_WAIT_TIME);
      }
    }

    if (isUncatchable) {
      token.getState().setHardDeadlinePassed(true);
    } else {
      token.getState().setSoftDeadlinePassed(true);
    }

    // Only resort to Thread.stop on a soft deadline if all the prior nudging
    // failed to elicit a response. On hard deadlines, there is no nudging.
    if (!token.isFinished()) {
      // SimpleDateFormat isn't threadsafe so just instantiate as-needed
      final DateFormat dateFormat = new SimpleDateFormat(SIMPLE_DATE_FORMAT_STRING);
      // Give the user as much information as we can.
      final Throwable throwable =
          createDeadlineThrowable(
              "This request ("
                  + token.getRequestId()
                  + ") "
                  + "started at "
                  + dateFormat.format(token.getStartTimeMillis())
                  + " and was still executing at "
                  + dateFormat.format(System.currentTimeMillis())
                  + ".",
              isUncatchable);
      // There is a weird race condition here.  We're throwing an
      // exception during the execution of an arbitrary method, but
      // that exception will contain the stack trace of what the
      // thread was doing a very small amount of time *before* the
      // exception was thrown (i.e. potentially in a different method).
      //
      // TODO: Add a try-catch block to every instrumented
      // method, which catches this throwable (or an internal version
      // of it) and checks to see if the stack trace has the proper
      // class and method at the top.  If so, rethrow it (or a public
      // version of it).  If not, create a new exception with the
      // correct class and method, but with an unknown line number.
      //
      // N.B.: Also, we're now using this stack trace to
      // determine when to terminate the clone.  The above issue may
      // cause us to terminate either too many or two few clones.  Too
      // many is merely wasteful, and too few is no worse than it was
      // without this change.
      boolean terminateClone = false;
      StackTraceElement[] stackTrace = targetThread.getStackTrace();
      if (threadStopTerminatesClone || isUncatchable || inClassInitialization(stackTrace)) {
        // If we bypassed catch blocks or interrupted class
        // initialization, don't reuse this clone.
        terminateClone = true;
      }

      throwable.setStackTrace(stackTrace);

      // Check again, since calling Thread.stop is so harmful.
      if (!token.isFinished()) {
        // Only set this if we're absolutely determined to call Thread.stop.
        token.getUpResponse().setTerminateClone(terminateClone);
        if (terminateClone) {
            token.getUpResponse().setCloneIsInUncleanState(true);
        }
        logger.atInfo().log("Stopping request thread.");
        // Throw the exception in targetThread.
        AccessController.doPrivileged(
            (PrivilegedAction<Void>) () -> {
              try {
                threadStop0.invoke(targetThread, throwable);
              } catch (Exception e) {
                logger.atWarning().withCause(e).log("Failed to stop thread");
              }
              return null;
            });
      }
    }

  }

  private void setPendingStartCloudDebugger(MutableUpResponse upResponse) {
    if (!enableCloudDebugger.get()) {
      return;
    }

    // First time ever we need to set "DebugletStarted" flag. This will trigger
    // debuggee initialization sequence on AppServer.
    if (debugletStartNotified.compareAndSet(false, true)) {
      upResponse.setPendingCloudDebuggerActionDebuggeeRegistration(true);
    }
  }

  private void setPendingCloudDebuggerBreakpointUpdates(MutableUpResponse upResponse) {
    if (!enableCloudDebugger.get()) {
      return;
    }

    upResponse.setPendingCloudDebuggerActionBreakpointUpdates(true);
  }

  private String threadDump(Collection<Thread> threads, String prefix) {
    StringBuilder message = new StringBuilder(prefix);
    for (Thread thread : threads) {
      message.append(thread).append(" in state ").append(thread.getState()).append("\n");
      StackTraceElement[] stack = thread.getStackTrace();
      if (stack.length == 0) {
        message.append("... empty stack\n");
      } else {
        for (StackTraceElement element : thread.getStackTrace()) {
          message.append("... ").append(element).append("\n");
        }
      }
      message.append("\n");
    }
    return message.toString();
  }

  private void waitForUserCodeToComplete(RequestToken requestToken) {
    RequestState state = requestToken.getState();
    if (Thread.interrupted()) {
      logger.atInfo().log("Interrupt bit set in waitForUserCodeToComplete, resetting.");
      // interrupted() already reset the bit.
    }

    try {
      if (state.hasHardDeadlinePassed()) {
        logger.atInfo().log("Hard deadline has already passed; skipping wait for async futures.");
      } else {
        // Wait for async API calls to complete.  Don't bother doing
        // this if the hard deadline has already passed, we're not going to
        // reuse this JVM anyway.
        waitForPendingAsyncFutures(requestToken.getAsyncFutures());
      }

      // Now wait for any request-scoped threads to complete.
      Collection<Thread> threads;
      while (!(threads = getActiveThreads(requestToken)).isEmpty()) {
        if (state.hasHardDeadlinePassed()) {
          requestToken.getUpResponse().setError(UPResponse.ERROR.THREADS_STILL_RUNNING_VALUE);
          requestToken.getUpResponse().clearHttpResponse();
          String messageString = threadDump(threads, "Thread(s) still running after request:\n");
          logger.atWarning().log("%s", messageString);
          requestToken.addAppLogMessage(ApiProxy.LogRecord.Level.fatal, messageString);
          return;
        } else {
          try {
            // Interrupt the threads one more time before joining.
            // This helps with ThreadPoolExecutors, where the first
            // interrupt may cancel the current runnable but another
            // interrupt is needed to kill the (now-idle) worker
            // thread.
            for (Thread thread : threads) {
              thread.interrupt();
            }
            if (Boolean.getBoolean("com.google.appengine.force.thread.pool.shutdown")) {
              attemptThreadPoolShutdown(threads);
            }
            for (Thread thread : threads) {
              logger.atInfo().log("Waiting for completion of thread: %s", thread);
              // Initially wait up to 10 seconds. If the interrupted thread takes longer than that
              // to stop then it's probably not going to. We will wait for it anyway, in case it
              // does stop, but we'll also log what the threads we are waiting for are doing.
              thread.join(10_000);
              if (thread.isAlive()) {
                // We're probably going to block forever.
                String message = threadDump(threads, "Threads still running after 10 seconds:\n");
                logger.atWarning().log("%s", message);
                requestToken.addAppLogMessage(ApiProxy.LogRecord.Level.warn, message);
                thread.join();
              }
            }
            logger.atInfo().log("All request threads have completed.");
          } catch (DeadlineExceededException ex) {
            // expected, try again.
          } catch (HardDeadlineExceededError ex) {
            // expected, loop around and we'll do something else this time.
          }
        }
      }
    } catch (Throwable ex) {
      logger.atWarning().withCause(ex).log(
          "Exception thrown while waiting for background work to complete:");
    }
  }

  /**
   * Scans the given threads to see if any of them looks like a ThreadPoolExecutor thread that was
   * created using {@link com.google.appengine.api.ThreadManager#currentRequestThreadFactory()},
   * and if so attempts to shut down the owning ThreadPoolExecutor.
   */
  private void attemptThreadPoolShutdown(Collection<Thread> threads) {
    for (Thread t : threads) {
      if (t instanceof ApiProxyImpl.CurrentRequestThread) {
        // This thread was made by ThreadManager.currentRequestThreadFactory. Check what Runnable
        // it was given.
        Runnable runnable = ((ApiProxyImpl.CurrentRequestThread) t).userRunnable();
        if (runnable.getClass().getName()
            .equals("java.util.concurrent.ThreadPoolExecutor$Worker")) {
          // This is the class that ThreadPoolExecutor threads use as their Runnable.
          // This check depends on implementation details of the JDK, and could break in the future.
          // In that case we have tests that should fail.
          // Assuming it is indeed a ThreadPoolExecutor.Worker, and given that that is an inner
          // class, we should be able to access the enclosing ThreadPoolExecutor instance by
          // accessing the synthetic this$0 field. That is again dependent on the JDK
          // implementation.
          try {
            Field outerField = runnable.getClass().getDeclaredField("this$0");
            outerField.setAccessible(true);
            Object outer = outerField.get(runnable);
            if (outer instanceof ThreadPoolExecutor) {
              ThreadPoolExecutor executor = (ThreadPoolExecutor) outer;
              executor.shutdown();
              // We might already have seen this executor via another thread in the loop, but
              // there's no harm in telling it more than once to shut down.
            }
          } catch (ReflectiveOperationException e) {
            logger.atInfo().withCause(e).log("ThreadPoolExecutor reflection failed");
          }
        }
      }
    }
  }

  private void waitForPendingAsyncFutures(Collection<Future<?>> asyncFutures)
      throws InterruptedException {
    int size = asyncFutures.size();
    if (size > 0) {
      logger.atWarning().log("Waiting for %d pending async futures.", size);
      List<Future<?>> snapshot;
      synchronized (asyncFutures) {
        snapshot = new ArrayList<>(asyncFutures);
      }
      for (Future<?> future : snapshot) {
        // Unlike scheduled futures, we do *not* want to cancel these
        // futures if they aren't done yet.  They represent asynchronous
        // actions that the user began which we still want to succeed.
        // We simply need to block until they do.
        try {
          // Don't bother specifying a deadline --
          // DeadlineExceededException's will break us out of here if
          // necessary.
          future.get();
        } catch (ExecutionException ex) {
          logger.atInfo().withCause(ex.getCause()).log("Async future failed:");
        }
      }
      // Note that it's possible additional futures have been added to asyncFutures while
      // we were waiting, and they will not get waited for. It's also possible additional
      // futures could be added after this method returns. There's nothing to prevent that.
      // For now we are keeping this loophole in order to avoid the risk of incompatibility
      // with existing apps.
      logger.atWarning().log("Done waiting for pending async futures.");
    }
  }

  private void cancelPendingAsyncFutures(Collection<Future<?>> asyncFutures) {
    int size = asyncFutures.size();
    if (size > 0) {
      logger.atInfo().log("Canceling %d pending async futures.", size);
      List<Future<?>> snapshot;
      synchronized (asyncFutures) {
        snapshot = new ArrayList<>(asyncFutures);
      }
      for (Future<?> future : snapshot) {
        future.cancel(true);
      }
      logger.atInfo().log("Done canceling pending async futures.");
    }
  }

  private void waitForResponseDuringSoftDeadline(Duration responseWaitTimeMs) {
    try {
      Thread.sleep(responseWaitTimeMs.toMillis());
    } catch (InterruptedException e) {
      logger.atInfo().withCause(e).log(
          "Interrupted while waiting for response during soft deadline");
    }
  }

  /**
   * Returns all the threads belonging to the current request except the current thread. For
   * compatibility, on Java 7 this returns all threads in the same thread group as the original
   * request thread. On later Java versions this returns the original request thread plus all
   * threads that were created with {@code ThreadManager.currentRequestThreadFactory()} and that
   * have not yet terminated.
   */
  private Set<Thread> getActiveThreads(RequestToken token) {
    Collection<Thread> threads;
    if (waitForDaemonRequestThreads) {
      // Join all request threads created using the current request ThreadFactory, including
      // daemon ones.
      threads = token.getState().requestThreads();
    } else {
      // Join all live non-daemon request threads created using the current request ThreadFactory.
      Set<Thread> nonDaemonThreads = new LinkedHashSet<>();
      for (Thread thread : token.getState().requestThreads()) {
        if (thread.isDaemon()) {
          logger.atInfo().log("Ignoring daemon thread: %s", thread);
        } else if (!thread.isAlive()) {
          logger.atInfo().log("Ignoring dead thread: %s", thread);
        } else {
          nonDaemonThreads.add(thread);
        }
      }
      threads = nonDaemonThreads;
    }
    Set<Thread> activeThreads = new LinkedHashSet<>(threads);
    activeThreads.remove(Thread.currentThread());
    return activeThreads;
  }

  /**
   * Check that the current thread matches the one that called startRequest.
   * @throws IllegalStateException If called from the wrong thread.
   */
  private void verifyRequestAndThread(RequestToken requestToken) {
    if (requestToken.getRequestThread() != Thread.currentThread()) {
      throw new IllegalStateException(
          "Called from "
              + Thread.currentThread()
              + ", should be "
              + requestToken.getRequestThread());
    }
  }

  /**
   * Arrange for the specified {@code Runnable} to be executed in
   * {@code time} milliseconds.
   */
  private Future<?> schedule(Runnable runnable, long time) {
    logger.atFine().log("Scheduling %s to run in %d ms.", runnable, time);
    return executor.schedule(runnable, time, TimeUnit.MILLISECONDS);
  }

  /**
   * Adjusts the deadline for this RPC by the padding constant along with the
   * elapsed time.  Will return the defaultValue if the rpc is not valid.
   */
  private long getAdjustedRpcDeadline(AnyRpcServerContext rpc, long defaultValue) {
    if (rpc.getTimeRemaining().compareTo(Duration.ofNanos(Long.MAX_VALUE)) >= 0
        || rpc.getStartTimeMillis() == 0) {
      logger.atWarning().log(
          "Did not receive enough RPC information to calculate adjusted deadline: %s", rpc);
      return defaultValue;
    }

    long elapsedMillis = System.currentTimeMillis() - rpc.getStartTimeMillis();

    if (rpc.getTimeRemaining().compareTo(Duration.ofSeconds(RPC_DEADLINE_PADDING_SECONDS_VALUE)) < 0) {
      logger.atWarning().log("RPC deadline is less than padding.  Not adjusting deadline");
      return rpc.getTimeRemaining().minusMillis(elapsedMillis).toMillis();
    } else {
      return rpc.getTimeRemaining()
          .minusSeconds(RPC_DEADLINE_PADDING_SECONDS_VALUE)
          .minusMillis(elapsedMillis)
          .toMillis();
    }
  }

  /**
   * Notify requests that the server is shutting down.
   */
  public void shutdownRequests(RequestToken token) {
    checkForDeadlocks(token);
    logger.atInfo().log("Calling shutdown hooks for %s", token.getAppVersionKey());
    // TODO what if there's other app/versions in this VM?
    MutableUpResponse response = token.getUpResponse();

    // Set the context classloader to the UserClassLoader while invoking the
    // shutdown hooks.
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(token.getAppVersion().getClassLoader());
    try {
      LifecycleManager.getInstance().beginShutdown(token.getDeadline());
    } finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }

    logMemoryStats();

    logAllStackTraces();

    response.setError(UPResponse.ERROR.OK_VALUE);
    response.setHttpResponseCodeAndResponse(200, "OK");
  }

  List<Thread> getRequestThreads(AppVersionKey appVersionKey) {
    List<Thread> threads = new ArrayList<Thread>();
    synchronized (requests) {
      for (RequestToken token : requests.values()) {
        if (appVersionKey.equals(token.getAppVersionKey())) {
          threads.add(token.getRequestThread());
        }
      }
    }
    return threads;
  }

  /**
   * Consults {@link ThreadMXBean#findDeadlockedThreads()} to see if
   * any deadlocks are currently present.  If so, it will
   * immediately respond to the runtime and simulate a LOG(FATAL)
   * containing the stack trace of the offending threads.
   */
  private void checkForDeadlocks(final RequestToken token) {
    AccessController.doPrivileged(
        (PrivilegedAction<Object>) () -> {
          long[] deadlockedThreadsIds = THREAD_MX.findDeadlockedThreads();
          if (deadlockedThreadsIds != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(
                "Detected a deadlock across " + deadlockedThreadsIds.length + " threads:");
            for (ThreadInfo info :
                THREAD_MX.getThreadInfo(deadlockedThreadsIds, MAXIMUM_DEADLOCK_STACK_LENGTH)) {
              builder.append(info);
              builder.append("\n");
            }
            String message = builder.toString();
            token.addAppLogMessage(Level.fatal, message);
            token.logAndKillRuntime(message);
          }
          return null;
        });
  }

  private void logMemoryStats() {
    Runtime runtime = Runtime.getRuntime();
    logger.atInfo().log(
        "maxMemory=%d totalMemory=%d freeMemory=%d",
        runtime.maxMemory(), runtime.totalMemory(), runtime.freeMemory());
  }

  private void logAllStackTraces() {
    AccessController.doPrivileged(
        (PrivilegedAction<Object>)
            () -> {
              long[] allthreadIds = THREAD_MX.getAllThreadIds();
              StringBuilder builder = new StringBuilder();
              builder.append(
                  "Dumping thread info for all " + allthreadIds.length + " runtime threads:");
              for (ThreadInfo info :
                  THREAD_MX.getThreadInfo(allthreadIds, MAXIMUM_DEADLOCK_STACK_LENGTH)) {
                builder.append(info);
                builder.append("\n");
              }
              String message = builder.toString();
              logger.atInfo().log("%s", message);
              return null;
            });
  }

  private Throwable createDeadlineThrowable(String message, boolean isUncatchable) {
    if (isUncatchable) {
      return new HardDeadlineExceededError(message);
    } else {
      return new DeadlineExceededException(message);
    }
  }

  private boolean inClassInitialization(StackTraceElement[] stackTrace) {
    for (StackTraceElement element : stackTrace) {
      if ("<clinit>".equals(element.getMethodName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code RequestToken} acts as a Memento object that passes state
   * between a call to {@code startRequest} and {@code finishRequest}.
   * It should be treated as opaque by clients.
   */
  public static class RequestToken {
    /**
     * The thread of the request.  This is used to verify that {@code
     * finishRequest} was called from the right thread.
     */
    private final Thread requestThread;

    private final MutableUpResponse upResponse;

    /**
     * A collection of {@code Future} objects that have been scheduled
     * on behalf of this request.  These futures will each be
     * cancelled when the request completes.
     */
    private final Collection<Future<?>> scheduledFutures;

    private final Collection<Future<?>> asyncFutures;

    private final String requestId;

    private final String securityTicket;

    /**
     * A {@code Timer} that runs during the course of the request and
     * measures both wallclock and CPU time.
     */
    private final CpuRatioTimer requestTimer;

    @Nullable private final TraceWriter traceWriter;

    private volatile boolean finished;

    private final AppVersion appVersion;

    private final long deadline;

    private final AnyRpcServerContext rpc;
    private final long startTimeMillis;

    private final RequestState state;

    private final Runnable endAction;

    RequestToken(
        Thread requestThread,
        MutableUpResponse upResponse,
        String requestId,
        String securityTicket,
        CpuRatioTimer requestTimer,
        Collection<Future<?>> asyncFutures,
        AppVersion appVersion,
        long deadline,
        AnyRpcServerContext rpc,
        long startTimeMillis,
        @Nullable TraceWriter traceWriter,
        RequestState state,
        Runnable endAction) {
      this.requestThread = requestThread;
      this.upResponse = upResponse;
      this.requestId = requestId;
      this.securityTicket = securityTicket;
      this.requestTimer = requestTimer;
      this.asyncFutures = asyncFutures;
      this.scheduledFutures = new ArrayList<Future<?>>();
      this.finished = false;
      this.appVersion = appVersion;
      this.deadline = deadline;
      this.rpc = rpc;
      this.startTimeMillis = startTimeMillis;
      this.traceWriter = traceWriter;
      this.state = state;
      this.endAction = endAction;
    }

    public RequestState getState() {
      return state;
    }

    Thread getRequestThread() {
      return requestThread;
    }

    MutableUpResponse getUpResponse() {
      return upResponse;
    }

    CpuRatioTimer getRequestTimer() {
      return requestTimer;
    }

    public String getRequestId() {
      return requestId;
    }

    public String getSecurityTicket() {
      return securityTicket;
    }

    public AppVersion getAppVersion() {
      return appVersion;
    }

    public AppVersionKey getAppVersionKey() {
      return appVersion.getKey();
    }

    public long getDeadline() {
      return deadline;
    }

    public long getStartTimeMillis() {
      return startTimeMillis;
    }

    Collection<Future<?>> getScheduledFutures() {
      return scheduledFutures;
    }

    void addScheduledFuture(Future<?> future) {
      scheduledFutures.add(future);
    }

    Collection<Future<?>> getAsyncFutures() {
      return asyncFutures;
    }

    @Nullable
    TraceWriter getTraceWriter() {
      return traceWriter;
    }

    boolean isFinished() {
      return finished;
    }

    void setFinished() {
      finished = true;
    }

    public void addAppLogMessage(ApiProxy.LogRecord.Level level, String message) {
      upResponse.addAppLog(AppLogLine.newBuilder()
          .setLevel(level.ordinal())
          .setTimestampUsec(System.currentTimeMillis() * 1000)
          .setMessage(message));
    }

    void logAndKillRuntime(String errorMessage) {
      logger.atSevere().log("LOG(FATAL): %s", errorMessage);
      upResponse.clearHttpResponse();
      upResponse.setError(UPResponse.ERROR.LOG_FATAL_DEATH_VALUE);
      upResponse.setErrorMessage(errorMessage);
      rpc.finishWithResponse(upResponse.build());
    }

    void runEndAction() {
      endAction.run();
    }
  }

  /**
   * {@code DeadlineRunnable} causes the specified {@code Throwable}
   * to be thrown within the specified thread.  The stack trace of the
   * Throwable is ignored, and is replaced with the stack trace of the
   * thread at the time the exception is thrown.
   */
  public class DeadlineRunnable implements Runnable {
    private final RequestManager requestManager;
    private final RequestToken token;
    private final boolean isUncatchable;

    public DeadlineRunnable(
        RequestManager requestManager, RequestToken token, boolean isUncatchable) {
      this.requestManager = requestManager;
      this.token = token;
      this.isUncatchable = isUncatchable;
    }

    @Override
    public void run() {
      requestManager.sendDeadline(token, isUncatchable);

      if (!token.isFinished()) {
        if (!isUncatchable) {
          token.addScheduledFuture(
              schedule(
                  new DeadlineRunnable(requestManager, token, true),
                  softDeadlineDelay - hardDeadlineDelay));
        }

        logger.atInfo().log("Finished execution of %s", this);
      }
    }

    @Override
    public String toString() {
      return "DeadlineRunnable("
          + token.getRequestThread()
          + ", "
          + token.getRequestId()
          + ", "
          + isUncatchable
          + ")";
    }
  }
}
