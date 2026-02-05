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

package com.google.apphosting.api;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * ApiProxy is a static class that serves as the collection point for
 * all API calls from user code into the application server.
 *
 * It is responsible for proxying makeSyncCall() calls to a delegate,
 * which actually implements the API calls.  It also stores an
 * Environment for each thread, which contains additional user-visible
 * information about the request.
 *
 */
public class ApiProxy {
  static final int MAX_SAVED_LOG_RECORDS = 1_000;

  private static final String API_DEADLINE_KEY =
      "com.google.apphosting.api.ApiProxy.api_deadline_key";

  private static final String HTTP_CONNECTOR_ENABLED =
      System.getenv("EXPERIMENT_ENABLE_HTTP_CONNECTOR_FOR_JAVA") != null ? " httpc=on " : "";

  /** Store an environment object for each thread. */
  private static final ThreadLocal<Environment> environmentThreadLocal = new ThreadLocal<>();

  /**
   * Used to create an Environment object to use if no thread local Environment is set.
   *
   * When the ThreadManager is used to create a thread, an appropriate environment instance will be
   * created and associated with the thread. This class is used if the thread is created another
   * way, most likely directly using the Thread constructor.
   */
  private static EnvironmentFactory environmentFactory = null;

  /** Store a single delegate, to which we proxy all makeSyncCall requests. */
  private static Delegate<?> delegate;

  /**
   * Logging records outside the scope of a request are lazily logged.
   */
  private static final List<LogRecord> outOfBandLogs = new DeferredLogRecords();

  /**
   * All methods are static.  Do not instantiate.
   */
  private ApiProxy() {
  }

  // Giving Delegate a type parameter was surely a mistake, given that the `delegate` field is
  // static. But we're kind of stuck with it since it's part of the public API. This method attempts
  // to limit the amount of @SuppressWarnings we need to deal with the damage.
  @SuppressWarnings("unchecked")
  private static Delegate<Environment> delegate() {
    return (Delegate<Environment>) delegate;
  }

  /** @see #makeSyncCall(String,String,byte[],ApiConfig) */
  public static byte[] makeSyncCall(String packageName, String methodName, byte[] request) {
    return makeSyncCall(packageName, methodName, request, null);
  }

  /**
   * Make a synchronous call to the specified method in the specified API package.
   *
   * <p>Note: if you have not installed a {@code Delegate} and called {@code
   * setEnvironmentForCurrentThread} in this thread before calling this method, it will act like no
   * API calls are available (i.e. always throw {@code CallNotFoundException}).
   *
   * @param packageName the name of the API package.
   * @param methodName the name of the method within the API package.
   * @param request a byte array containing the serialized form of the request protocol buffer.
   * @param apiConfig that specifies API-specific configuration parameters.
   * @return a byte array containing the serialized form of the response protocol buffer.
   * @throws ApplicationException For any error that is the application's fault.
   * @throws RPCFailedException If we could not connect to a backend service.
   * @throws CallNotFoundException If the specified method does not exist, or if the thread making
   *     the call is neither a request thread nor a thread created by {@link
   *     com.google.appengine.api.ThreadManager ThreadManager}.
   * @throws ArgumentException If the request could not be parsed.
   * @throws ApiDeadlineExceededException If the request took too long.
   * @throws CancelledException If the request was explicitly cancelled.
   * @throws CapabilityDisabledException If the API call is currently unavailable.
   * @throws OverQuotaException If the API call required more quota than is available.
   * @throws RequestTooLargeException If the request to the API was too large.
   * @throws ResponseTooLargeException If the response to the API was too large.
   * @throws UnknownException If any other error occurred.
   */
  public static byte[] makeSyncCall(
      String packageName, String methodName, byte[] request, ApiConfig apiConfig) {
    Environment env = getCurrentEnvironment();
    if (delegate == null || env == null) {
      // If no delegate was installed or no environment was registered
      // for this thread, just act like we do not understand any
      // methods.
      throw CallNotFoundException.foreignThread(packageName, methodName);
    }
    if (apiConfig == null || apiConfig.getDeadlineInSeconds() == null) {
      return delegate().makeSyncCall(env, packageName, methodName, request);
    } else {
      Object oldValue = env.getAttributes().put(API_DEADLINE_KEY, apiConfig.getDeadlineInSeconds());
      try {
        return delegate().makeSyncCall(env, packageName, methodName, request);
      } finally {
        // ConcurrentHashMap might be used here, and it doesn't allow null values.
        if (oldValue == null) {
          env.getAttributes().remove(API_DEADLINE_KEY);
        } else {
          env.getAttributes().put(API_DEADLINE_KEY, oldValue);
        }
      }
    }
  }

  /**
   * @see #makeAsyncCall(String,String,byte[],ApiConfig)
   */
  public static Future<byte[]> makeAsyncCall(String packageName,
                                             String methodName,
                                             byte[] request) {
    return makeAsyncCall(packageName, methodName, request, new ApiConfig());
  }

  /**
   * Make an asynchronous call to the specified method in the
   * specified API package.
   *
   * <p>Note: if you have not installed a {@code Delegate} and called
   * {@code setEnvironmentForCurrentThread} in this thread before
   * calling this method, it will act like no API calls are available
   * (i.e. the returned {@link Future} will throw {@code
   * CallNotFoundException}).
   *
   * <p>There is a limit to the number of simultaneous asynchronous
   * API calls (currently 100).  Invoking this method while this number
   * of API calls are outstanding will block.
   *
   * @param packageName the name of the API package.
   * @param methodName the name of the method within the API package.
   * @param request a byte array containing the serialized form of
   *     the request protocol buffer.
   * @param apiConfig that specifies API-specific configuration
   *     parameters.
   *
   * @return a {@link Future} that will resolve to a byte array
   *     containing the serialized form of the response protocol buffer
   *     on success, or throw one of the exceptions documented for
   *     {@link #makeSyncCall(String, String, byte[], ApiConfig)}  on failure.
   */
  public static Future<byte[]> makeAsyncCall(final String packageName,
                                             final String methodName,
                                             byte[] request,
                                             ApiConfig apiConfig) {
    Environment env = getCurrentEnvironment();
    if (delegate == null || env == null) {
      // If no delegate was installed or no environment was registered
      // for this thread, just act like we do not understand any
      // methods.
      return new Future<byte[]>() {

        @Override public byte[] get() {
          throw CallNotFoundException.foreignThread(packageName, methodName);
        }

        @Override public byte[] get(long deadline, TimeUnit unit) {
          throw CallNotFoundException.foreignThread(packageName, methodName);
        }

        @Override public boolean isDone() {
          return true;
        }

        @Override public boolean isCancelled() {
          return false;
        }

        @Override public boolean cancel(boolean shouldInterrupt) {
          return false;
        }
      };
    }
    return delegate().makeAsyncCall(env, packageName, methodName, request, apiConfig);
  }

  public static void log(LogRecord record) {
    Environment env = getCurrentEnvironment();
    if (delegate != null && env != null) {
      delegate().log(env, record);
      return;
    }

    synchronized (outOfBandLogs) {
      outOfBandLogs.add(record);
    }
  }

  // Flush any out of band logs if the delegate and environment are
  // set for this thread.
  private static void possiblyFlushOutOfBandLogs() {
    Environment env = getCurrentEnvironment();
    if (delegate != null && env != null) {
      List<LogRecord> logsToWrite;
      synchronized (outOfBandLogs) {
        logsToWrite = new ArrayList<>(outOfBandLogs);
        outOfBandLogs.clear();
      }
      // Write the logs without holding the lock for outOfBandLogs.
      for (LogRecord record : logsToWrite) {
        delegate().log(env, record);
      }
    }
  }

  /**
   * Synchronously flush all pending application logs.
   */
  public static void flushLogs() {
    if (delegate != null) {
      delegate().flushLogs(getCurrentEnvironment());
    }
  }

  /**
   * Gets the environment associated with this thread. This can be used to discover additional
   * information about the current request.
   *
   * The value returned is the {@code Environment} that this thread most recently set with
   * {@link #setEnvironmentForCurrentThread}. If that is null and {@link #setEnvironmentFactory} has
   * set an {@link EnvironmentFactory}, that {@code EnvironmentFactory} is used to create an
   * {@code Environment} instance which is returned by this call and future calls. If there is no
   * {@code EnvironmentFactory} either, then null is returned.
   */
  public static Environment getCurrentEnvironment() {
    Environment threadLocalEnvironment = environmentThreadLocal.get();
    if (threadLocalEnvironment != null) {
      return threadLocalEnvironment;
    }
    // Use the EnvironmentFactory getter, so access is synchronized.
    EnvironmentFactory envFactory = getEnvironmentFactory();
    if (envFactory != null) {
      Environment environment = envFactory.newEnvironment();
      environmentThreadLocal.set(environment);
      return environment;
    }
    return null;
  }

  /** Sets a delegate to which we will proxy requests. This should not be used from user-code. */
  public static void setDelegate(@Nullable Delegate<?> aDelegate) {
    delegate = aDelegate;
    possiblyFlushOutOfBandLogs();
  }

  /**
   * Gets the delegate to which we will proxy requests. This should really only be called from
   * test-code where, for example, you might want to downcast and invoke methods on a specific
   * implementation that you happen to know has been installed.
   */
  @SuppressWarnings("rawtypes") // Can't easily change this since it's part of the public API.
  public static Delegate getDelegate() {
    return delegate;
  }

  // TODO: The rest of the methods should neither be visible
  // to, nor callable from, user-supplied code.  They are public
  // because the runtime package needs to access them, as well as unit
  // tests.  How do we secure this?

  /** Sets an environment for the current thread. This should not be used from user-code. */
  public static void setEnvironmentForCurrentThread(Environment environment) {
    environmentThreadLocal.set(environment);
    possiblyFlushOutOfBandLogs();
  }

  /**
   * Removes any environment associated with the current thread.  This
   * should not be used from user-code.
   */
  public static void clearEnvironmentForCurrentThread() {
    environmentThreadLocal.set(null);
  }

  public static synchronized EnvironmentFactory getEnvironmentFactory() {
    return environmentFactory;
  }

  /**
   * Set the EnvironmentFactory instance to use, which will be used to create Environment instances
   * when a thread local one is not set. This should not be used from user-code, and it should only
   * be called once, with a value that must not be null.
   */
  public static synchronized void setEnvironmentFactory(EnvironmentFactory factory) {
    if (factory == null) {
      throw new NullPointerException("factory cannot be null.");
    }
    if (environmentFactory != null) {
      throw new IllegalStateException("EnvironmentFactory has already been set.");
    }
    environmentFactory = factory;
  }

  /** Removes the environment factory. This should not be used from user-code. */
  // TODO: Make this method public.
  static synchronized void clearEnvironmentFactory() {
    environmentFactory = null;
  }

  /**
   * Returns a list of all threads which are currently running requests.
   */
  public static List<Thread> getRequestThreads() {
    Environment env = getCurrentEnvironment();
    if (delegate == null) {
      return Collections.emptyList();
    } else {
      return delegate().getRequestThreads(env);
    }
  }

  /**
   * Environment is a simple data container that provides additional
   * information about the current request (e.g. who is logged in, are
   * they an administrator, etc.).
   */
  public interface Environment {
    /**
     * Gets the application identifier for the current application.
     */
    String getAppId();

    /**
     * Gets the module identifier for the current application instance.
     */
    String getModuleId();

    /**
     * Gets the version identifier for the current application version.
     * Result is of the form {@literal <major>.<minor>} where
     * {@literal <major>} is the version name supplied at deploy time and
     * {@literal <minor>} is a timestamp value maintained by App Engine.
     */
    String getVersionId();

    /**
     * Gets the email address of the currently logged-in user.
     */
    String getEmail();

    /**
     * Returns true if the user is logged in.
     */
    boolean isLoggedIn();

    /**
     * Returns true if the currently logged-in user is an administrator.
     */
    boolean isAdmin();

    /**
     * Returns the domain used for authentication.
     */
    String getAuthDomain();

    /**
     * @deprecated Use {@link
     *     com.google.appengine.api.NamespaceManager NamespaceManager}.getGoogleAppsNamespace()
     */
    @Deprecated
    String getRequestNamespace();

    /**
     * Get a {@code Map} containing any attributes that have been set in this
     * {@code Environment}.  The returned {@code Map} is mutable and is a
     * useful place to store transient, per-request information.
     */
    Map<String, Object> getAttributes();

    /**
     * Gets the remaining number of milliseconds left before this request receives a
     * DeadlineExceededException from App Engine. This API can be used for planning how much work
     * you can reasonably accomplish before the soft deadline kicks in.
     *
     * <p>If there is no deadline for the request, then this will reply with Long.MAX_VALUE.
     */
    long getRemainingMillis();
  }

  /**
   * Used to create an Environment object to use if no thread local Environment is set.
   */
  public interface EnvironmentFactory {
    /**
     * Creates a new Environment object to use if no thread local Environment is set.
     */
    Environment newEnvironment();
  }

  /** A specialization of Environment with call-tracing metadata. */
  public interface EnvironmentWithTrace extends Environment {
    /**
     * Get the trace id of the current request, which can be used to correlate log messages
     * belonging to that request.
     */
    public Optional<String> getTraceId();

    /**
     * Get the span id of the current request, which can be used to identify a span within a trace.
     */
    public Optional<String> getSpanId();
  }

  /**
   * This interface can be used to provide a class that actually
   * implements API calls.
   *
   * @param <E> The concrete class implementing Environment that this
   *          Delegate expects to receive.
   */
  public interface Delegate<E extends Environment> {
    // TODO: In the next API version, remove this method and
    // implement ApiProxy.makeSyncCall in terms of makeAsyncCall.
    /**
     * Make a synchronous call to the specified method in the specified API package.
     *
     * <p>Note: if you have not installed a {@code Delegate} and called {@code
     * setEnvironmentForCurrentThread} in this thread before calling this method, it will act like
     * no API calls are available (i.e. always throw {@code CallNotFoundException}).
     *
     * @param environment the current request environment.
     * @param packageName the name of the API package.
     * @param methodName the name of the method within the API package.
     * @param request a byte array containing the serialized form of the request protocol buffer.
     * @return a byte array containing the serialized form of the response protocol buffer.
     * @throws ApplicationException For any error that is the application's fault.
     * @throws RPCFailedException If we could not connect to a backend service.
     * @throws CallNotFoundException If the specified method does not exist.
     * @throws ArgumentException If the request could not be parsed.
     * @throws DeadlineExceededException If the request took too long.
     * @throws CancelledException If the request was explicitly cancelled.
     * @throws UnknownException If any other error occurred.
     */
    byte[] makeSyncCall(E environment, String packageName, String methodName, byte[] request);

    /**
     * Make an asynchronous call to the specified method in the specified API package.
     *
     * <p>Note: if you have not installed a {@code Delegate} and called
     * {@code setEnvironmentForCurrentThread} in this thread before
     * calling this method, it will act like no API calls are available
     * (i.e. always throw {@code CallNotFoundException}).
     *
     * @param environment the current request environment.
     * @param packageName the name of the API package.
     * @param methodName the name of the method within the API package.
     * @param request a byte array containing the serialized form of
     * the request protocol buffer.
     * @param apiConfig that specifies API-specific configuration
     * parameters.
     *
     * @return a {@link Future} that will resolve to a byte array
     * containing the serialized form of the response protocol buffer
     * on success, or throw one of the exceptions documented for
     * {@link #makeSyncCall(Environment, String, String, byte[])} on failure.
     */
    Future<byte[]> makeAsyncCall(E environment,
                                 String packageName,
                                 String methodName,
                                 byte[] request,
                                 ApiConfig apiConfig);

    void log(E environment, LogRecord record);

    void flushLogs(E environment);

    /**
     * Returns a list of all threads which are currently running requests.
     */
    List<Thread> getRequestThreads(E environment);
  }

  /**
   * {@code LogRecord} represents a single apphosting log entry,
   * including a Java-specific logging level, a timestamp in
   * microseconds, and a message, which is a formatted string containing the
   * rest of the logging information (e.g. class and line number
   * information, the message itself, the stack trace for any
   * exception associated with the log record, etc.).
   *
   * <p>A StackTraceElement may be attached to track the origin of the original
   * log message so it can be recorded in the log.
   */
  public static final class LogRecord {
    private final Level level;
    private final long timestamp;
    private final String message;

    // A throwable created inside the handler, for identifying the caller.
    @Nullable
    private final Throwable sourceLocation;

    @Nullable
    private final StackTraceElement stackFrame;

    // This should be kept in sync with kJavaLevelNames in
    // app_logs_util.cc.  We intentionally use lower case enumeration
    // values so the strings will be identical.
    public enum Level {
      debug,
      info,
      warn,
      error,
      fatal,
    }

    // Constructor for logs not directly generated by user code.
    public LogRecord(Level level, long timestamp, String message) {
      this(level, timestamp, message, null, null);
    }

    // Constructor for logs generated in user code.
    //
    // N.B.(jmacd): when sourceLocation is non-null, it should be used
    // to calculate the source code location where the log statement
    // was called.  Currently there exists a legacy convention:
    //
    // (1) When source location logging is disabled, a "<classname>
    // <method>: " prefix is inserted by DefaultHandler.java using the
    // class and method names provided by the
    // java.util.logging.LogRecord, which are incorrect in some cases
    // according to the logic in AppLogsWriter.java.
    //
    // (2) When source location logging is enabled, the message prefix
    // is not inserted by DefaultHandler.java. Code in
    // AppLogsWriter.java is expected to add the prefix after
    // calculating the source location in this case.

    /**
     * Constructor for when the source location will be extracted from a Throwable.
     *
     * @deprecated Prefer the {@linkplain #LogRecord(Level, long, String, StackTraceElement)
     *     constructor} that takes a StackTraceElement to identify the source location.
     */
    @Deprecated
    public LogRecord(Level level, long timestamp, String message, Throwable sourceLocation) {
      this(level, timestamp, message, sourceLocation, null);
    }

    /**
     * Constructor for when the source location will be extracted from a StackTraceElement.
     *
     * @param level the log level.
     * @param timestamp the log timestamp, in microseconds since midnight UTC on 1 January 1970.
     * @param message the log message.
     * @param stackFrame indicates the class name, method name, file name, and line number to be
     *     used in the log record. The source location is extracted from this object provided that
     *     the file name is not null and the line number is at least 1. Otherwise, the logging
     *     infrastructure may attempt to deduce the source location by finding a stack frame in the
     *     call stack matching the class and method from {@code stackFrame}.
     */
    public LogRecord(
        Level level,
        long timestamp,
        String message,
        StackTraceElement stackFrame) {
      this(level, timestamp, message, null, checkNotNull("stackFrame", stackFrame));
    }

    private LogRecord(
        Level level,
        long timestamp,
        String message,
        @Nullable Throwable sourceLocation,
        @Nullable StackTraceElement stackFrame) {
      this.level = level;
      this.timestamp = timestamp;
      this.message = message;
      this.stackFrame = stackFrame;
      this.sourceLocation = sourceLocation;
    }

    private static <T> T checkNotNull(String what, T x) {
      if (x == null) {
        throw new NullPointerException(what);
      }
      return x;
    }

    /**
     * A partial copy constructor.
     *
     * @param other A {@code LogRecord} from which to copy the {@link #level} and {@link #timestamp}
     *     but not the {@link #message}
     * @param message
     */
    public LogRecord(LogRecord other, String message) {
      this(other.level, other.timestamp, message);
    }

    public Level getLevel() {
      return level;
    }

    /**
     * Returns the timestamp of the log message, in microseconds since midnight UTC on
     * 1 January 1970.
     */
    public long getTimestamp() {
      return timestamp;
    }

    public String getMessage() {
      return message;
    }

    @Nullable
    public Throwable getSourceLocation() {
      return sourceLocation;
    }

    @Nullable
    public StackTraceElement getStackFrame() {
      return stackFrame;
    }
  }

  /**
   * {@code ApiConfig} encapsulates one or more configuration
   * parameters scoped to an individual API call.
   */
  public static final class ApiConfig {
    private Double deadlineInSeconds;

    /**
     * Returns the number of seconds that the API call will be allowed
     * to run, or {@code null} for the default deadline.
     */
    public Double getDeadlineInSeconds() {
      return deadlineInSeconds;
    }

    /**
     * Set the number of seconds that the API call will be allowed to
     * run, or {@code null} for the default deadline.
     */
    public void setDeadlineInSeconds(Double deadlineInSeconds) {
      this.deadlineInSeconds = deadlineInSeconds;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ApiConfig apiConfig = (ApiConfig) o;

      if (deadlineInSeconds != null
          ? !deadlineInSeconds.equals(apiConfig.deadlineInSeconds)
          : apiConfig.deadlineInSeconds != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return deadlineInSeconds != null ? deadlineInSeconds.hashCode() : 0;
    }
  }

  /**
   * A subtype of {@link Future} that provides more detailed
   * information about the timing and resource consumption of
   * particular API calls.
   *
   * <p>Objects returned from {@link
   * #makeAsyncCall(String,String,byte[],ApiConfig)} may implement
   * this interface.  However, callers should not currently assume
   * that all RPCs will.
   */
  @SuppressWarnings("ShouldNotSubclass")
  public interface ApiResultFuture<T> extends Future<T> {
    /**
     * Returns the amount of CPU time consumed across any backend
     * servers responsible for serving this API call.  This quantity
     * is measured in millions of CPU cycles to avoid suming times
     * across a hetergeneous machine set with varied CPU clock speeds.
     *
     * @throws IllegalStateException If the RPC has not yet completed.
     */
    long getCpuTimeInMegaCycles();

    /**
     * Returns the amount of wallclock time, measured in milliseconds,
     * that this API call took to complete, as measured from the
     * client side.
     *
     * @throws IllegalStateException If the RPC has not yet completed.
     */
    long getWallclockTimeInMillis();
  }

  /** Returns a debug string indicating if the http connector experiment is enabled. */
  private static String debugInfo() {
    return HTTP_CONNECTOR_ENABLED;
  }

  // There isn't much that the client can do about most of these.
  // Making these checked exceptions would just annoy people.
  /** An exception produced when trying to perform an API call. */
  public static class ApiProxyException extends RuntimeException {
    private static final long serialVersionUID = -8047817766181831916L;

    /**
     * Returns a new {@link ApiProxyException} where the exception message is the result of calling
     * {@code String.format(message, packageName, methodName)}.
     */
    // This leads to ErrorProne warnings on calls to this constructor, which can be suppressed with
    // @SuppressWarnings("OrphanedFormatString").
    public ApiProxyException(String message, String packageName, String methodName) {
      this(String.format(message, packageName, methodName));
    }

    private ApiProxyException(
        String message, String packageName, String methodName, Throwable nestedException) {
      super(String.format(message + debugInfo(), packageName, methodName), nestedException);
    }

    public ApiProxyException(String message) {
      super(message + debugInfo());
    }

    public ApiProxyException(String message, Throwable cause) {
      super(message + debugInfo(), cause);
    }

    /**
     * Clones this exception and then sets this Exception as the cause
     * of the clone and sets the given stack trace in the clone.
     *
     * @param stackTrace The stack trace to set in the returned clone
     * @return a clone of this Exception with this Exception as the cause and with the given stack
     *     trace.
     */
    public ApiProxyException copy(StackTraceElement[] stackTrace) {
      ApiProxyException theCopy = cloneWithoutStackTrace();
      theCopy.setStackTrace(stackTrace);
      theCopy.initCause(this);
      return theCopy;
    }

    /**
     * Produces a copy of this exception where the stack trace is replaced by one from the place
     * where this method was called.
     */
    protected ApiProxyException cloneWithoutStackTrace() {
      return new ApiProxyException(this.getMessage());
    }

  }

  public static class ApplicationException extends ApiProxyException {
    private static final long serialVersionUID = 6842926107675100571L;

    private final int applicationError;
    private final String errorDetail;

    public ApplicationException(int applicationError) {
      this(applicationError, "");
    }

    public ApplicationException(int applicationError, String errorDetail) {
      super("ApplicationError: " + applicationError + ": " + errorDetail);
      this.applicationError = applicationError;
      this.errorDetail = errorDetail;
    }

    public int getApplicationError() {
      return applicationError;
    }

    public String getErrorDetail() {
      return errorDetail;
    }

    @Override
    protected ApplicationException cloneWithoutStackTrace() {
      return new ApplicationException(applicationError, errorDetail);
    }
  }

  public static class RPCFailedException extends ApiProxyException {
    private static final long serialVersionUID = -2986651420214055269L;

    @SuppressWarnings("OrphanedFormatString")
    public RPCFailedException(String packageName, String methodName) {
      super(
          "The remote RPC to the application server failed for the call %s.%s().",
          packageName, methodName);
    }

    public RPCFailedException(String message, Throwable cause) {
      super(message, cause);
    }

    private RPCFailedException(String message) {
      super(message);
    }

    @Override
    protected RPCFailedException cloneWithoutStackTrace() {
      return new RPCFailedException(this.getMessage());
    }
  }

  public static class CallNotFoundException extends ApiProxyException {
    private static final long serialVersionUID = 7509604548069974905L;

    @SuppressWarnings("OrphanedFormatString")
    public CallNotFoundException(String packageName, String methodName) {
      super("The API package '%s' or call '%s()' was not found.",
            packageName, methodName);
    }

    private CallNotFoundException(String messageFormat, String packageName, String methodName) {
      super(messageFormat, packageName, methodName);
    }

    @SuppressWarnings("OrphanedFormatString")
    static CallNotFoundException foreignThread(String packageName, String methodName) {
      return new CallNotFoundException(
          "Can't make API call %s.%s in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager",
          packageName, methodName);
    }

    private CallNotFoundException(String message) {
      super(message);
    }

    @Override
    public CallNotFoundException cloneWithoutStackTrace() {
      return new CallNotFoundException(this.getMessage());
    }
  }

  public static class ArgumentException extends ApiProxyException {
    private static final long serialVersionUID = -5659754301141352543L;

    @SuppressWarnings("OrphanedFormatString")
    public ArgumentException(String packageName, String methodName) {
      super(
          "An error occurred parsing (locally or remotely) the arguments to %s.%s().",
          packageName, methodName);
    }

    private ArgumentException(String message) {
      super(message);
    }

    @Override
    public ArgumentException cloneWithoutStackTrace() {
      return new ArgumentException(this.getMessage());
    }
  }

  public static class ApiDeadlineExceededException extends ApiProxyException {
    private static final long serialVersionUID = -4609858606653988949L;

    @SuppressWarnings("OrphanedFormatString")
    public ApiDeadlineExceededException(String packageName, String methodName) {
      super(
          "The API call %s.%s() took too long to respond and was cancelled.",
          packageName, methodName);
    }

    private ApiDeadlineExceededException(String message) {
      super(message);
    }

    @Override
    public ApiDeadlineExceededException cloneWithoutStackTrace() {
      return new ApiDeadlineExceededException(this.getMessage());
    }
  }

  public static class CancelledException extends ApiProxyException {
    private static final long serialVersionUID = -6001978533238308631L;

    @SuppressWarnings("OrphanedFormatString")
    public CancelledException(String packageName, String methodName) {
      super("The API call %s.%s() was explicitly cancelled.", packageName, methodName);
    }

    public CancelledException(String packageName, String methodName, String reason) {
      super(
          String.format(
              "The API call %s.%s() was cancelled because %s.", packageName, methodName, reason));
    }

    private CancelledException(String message) {
      super(message);
    }

    @Override
    public CancelledException cloneWithoutStackTrace() {
      return new CancelledException(this.getMessage());
    }

  }

  public static class CapabilityDisabledException extends ApiProxyException {
    private static final long serialVersionUID = -3302799372322803580L;

    @SuppressWarnings("OrphanedFormatString")
    public CapabilityDisabledException(String message, String packageName, String methodName) {
      super("The API call %s.%s() is temporarily unavailable: " + message, packageName, methodName);
    }

    private CapabilityDisabledException(String message) {
      super(message);
    }

    @Override
    public CapabilityDisabledException cloneWithoutStackTrace() {
      return new CapabilityDisabledException(this.getMessage());
    }
  }

  public static class FeatureNotEnabledException extends ApiProxyException {
    private static final long serialVersionUID = -8612326236209075001L;

    public FeatureNotEnabledException(String message,
                                      String packageName,
                                      String methodName) {
      super(message, packageName, methodName);
    }

    public FeatureNotEnabledException(String message) {
      super(message);
    }

    @Override
    public FeatureNotEnabledException cloneWithoutStackTrace() {
      return new FeatureNotEnabledException(this.getMessage());
    }
  }

  public static class OverQuotaException extends ApiProxyException {
    private static final long serialVersionUID = -1041380497236424921L;

    public OverQuotaException(String packageName, String methodName) {
      this(null, packageName, methodName);
    }

    public OverQuotaException(String message, String packageName, String methodName) {
      this(formatMessage(message, packageName, methodName));
    }

    /**
     * Constructs an error message indicating insufficient quota for the operation described by the
     * given package and method names, optionally followed by a supplementary explanation.
     */
    private static String formatMessage(String coda, String packageName, String methodName) {
      String basicMessage =
          String.format(
              "The API call %s.%s() required more quota than is available.",
              packageName, methodName);
      return coda != null && !coda.isEmpty() ? basicMessage + ' ' + coda : basicMessage;
    }

    private OverQuotaException(String message) {
      super(message);
    }

    public OverQuotaException(String message, Throwable cause) {
      super(message, cause);
    }

    @Override
    public OverQuotaException cloneWithoutStackTrace() {
      return new OverQuotaException(this.getMessage());
    }
  }

  public static class RequestTooLargeException extends ApiProxyException {
    private static final long serialVersionUID = -8120940444733330027L;

    @SuppressWarnings("OrphanedFormatString")
    public RequestTooLargeException(String packageName, String methodName) {
      super("The request to API call %s.%s() was too large.", packageName, methodName);
    }

    private RequestTooLargeException(String message) {
      super(message);
    }

    @Override
    public RequestTooLargeException cloneWithoutStackTrace() {
      return new RequestTooLargeException(this.getMessage());
    }
  }

  public static class ResponseTooLargeException extends ApiProxyException {
    private static final long serialVersionUID = 2897764535255354449L;

    @SuppressWarnings("OrphanedFormatString")
    public ResponseTooLargeException(String packageName, String methodName) {
      super("The response from API call %s.%s() was too large.", packageName, methodName);
    }

    private ResponseTooLargeException(String message) {
      super(message);
    }

    @Override
    public ResponseTooLargeException cloneWithoutStackTrace() {
      return new ResponseTooLargeException(this.getMessage());
    }
  }

  /**
   * An exception whose cause is not known or understood by the API code. This is sometimes the
   * result of communications problems between the API client (in the user app) and the server that
   * implements the API.
   */
  public static class UnknownException extends ApiProxyException {
    private static final long serialVersionUID = -5956196448628918508L;

    // Do not serialize the cause. Sometimes it will be an implementation-specific exception,
    // for example from Jetty, which a remote client would not necessarily have.
    private Object writeReplace() {
      if (!getClass().equals(UnknownException.class)) {
        // We never throw any subclasses of UnknownException, so we don't have to worry about
        // removing the cause. It would be annoying to try to construct a clone of the subclass.
        return this;
      }
      UnknownException replacement = new UnknownException(getMessage());
      replacement.setStackTrace(getStackTrace());
      return replacement;
    }

    // WARNING! If you use UnknownException to nest an exception, you should
    // really think hard about whether or not you want that exception to leak
    // to users in production.
    @SuppressWarnings("OrphanedFormatString")
    public UnknownException(String packageName, String methodName, Throwable nestedException) {
      super(
          "An error occurred for the API request %s.%s().",
          packageName, methodName, nestedException);
    }

    @SuppressWarnings("OrphanedFormatString")
    public UnknownException(String packageName, String methodName) {
      super("An error occurred for the API request %s.%s().", packageName, methodName);
    }

    public UnknownException(String message) {
      super(message);
    }

    @Override
    public UnknownException cloneWithoutStackTrace() {
      return new UnknownException(this.getMessage());
    }
  }

  /**
   * Class that implements the logic of handling an overflow in our buffer of deferred log records.
   * If more than MAX_SAVED_LOG_RECORDS records are logged, we keep the earliest chunk and the
   * latest chunk and drop those in between. We insert a synthetic LogRecord between the earliest
   * chunk and the latest chunk, to show how many records were dropped.
   */
  private static class DeferredLogRecords extends AbstractList<LogRecord> {
    private final List<LogRecord> earliest = new ArrayList<>();
    private final List<LogRecord> latest = new ArrayList<>();
    private int dropped = 0;

    @Override
    public boolean add(LogRecord logRecord) {
      int earliestMax = MAX_SAVED_LOG_RECORDS / 2;
      int latestMax = MAX_SAVED_LOG_RECORDS - earliestMax;
      if (earliest.size() < earliestMax) {
        earliest.add(logRecord);
        return true;
      }
      while (latest.size() >= latestMax) {
        // Removing the first element of an ArrayList is a bit expensive, but this situation should
        // be unusual, and the number of elements being copied is at most MAX_SAVED_LOG_RECORDS / 2.
        latest.remove(0);
        dropped++;
      }
      latest.add(logRecord);
      return true;
    }

    @Override
    public LogRecord get(int index) {
      if (index < earliest.size()) {
        return earliest.get(index);
      }
      if (dropped == 0) {
        // No records were dropped, so the first record in `latest` immediately follows the last
        // record in `earliest`.
        return latest.get(index - earliest.size());
      }
      // At least one record was dropped. If the index is exactly the first one after `earliest`,
      // we return a synthetic record describing the number of drops. Otherwise, we subtract 1 to
      // skip that record and return the appropriate index in `latest`.
      if (index == earliest.size()) {
        String message = "[" + dropped + " dropped records were logged between requests]";
        return new LogRecord(LogRecord.Level.warn, latest.get(0).timestamp, message);
      }
      return latest.get(index - earliest.size() - 1);
    }

    @Override
    public int size() {
      int droppedRecord = (dropped == 0) ? 0 : 1;
      return earliest.size() + droppedRecord + latest.size();
    }

    @Override
    public void clear() {
      earliest.clear();
      latest.clear();
      dropped = 0;
    }
  }
}
