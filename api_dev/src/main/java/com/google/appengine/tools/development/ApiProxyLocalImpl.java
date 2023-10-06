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

package com.google.appengine.tools.development;

import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.tools.development.LocalRpcService.Status;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.CallNotFoundException;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.ApiProxy.RequestTooLargeException;
import com.google.apphosting.api.ApiProxy.UnknownException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements ApiProxy.Delegate such that the requests are dispatched to local service
 * implementations. Used for both the {@link com.google.appengine.tools.development.DevAppServer}
 * and for unit testing services.
 *
 */
public class ApiProxyLocalImpl implements ApiProxyLocal, DevServices {
  /**
   * The maximum size of any given API request.
   */
  private static final int MAX_API_REQUEST_SIZE = 1048576;

  private static final String API_DEADLINE_KEY =
      "com.google.apphosting.api.ApiProxy.api_deadline_key";

  static public final String IS_OFFLINE_REQUEST_KEY = "com.google.appengine.request.offline";

  /**
   * Implementation of the {@link LocalServiceContext} interface
   */
  private class LocalServiceContextImpl implements LocalServiceContext {

    /**
     * The local server environment
     */
    private final LocalServerEnvironment localServerEnvironment;

    private final LocalCapabilitiesEnvironment localCapabilitiesEnvironment =
        new LocalCapabilitiesEnvironment(System.getProperties());

    /**
     * Creates a new context, for the given application.
     *
     * @param localServerEnvironment The environment for the local server.
     */
    public LocalServiceContextImpl(LocalServerEnvironment localServerEnvironment) {
      this.localServerEnvironment = localServerEnvironment;
    }

    @Override
    public LocalServerEnvironment getLocalServerEnvironment() {
      return localServerEnvironment;
    }

    @Override
    public LocalCapabilitiesEnvironment getLocalCapabilitiesEnvironment() {
      return localCapabilitiesEnvironment;
    }

    @Override
    public Clock getClock() {
      return clock;
    }

    @Override
    public LocalRpcService getLocalService(String packageName) {
      return ApiProxyLocalImpl.this.getService(packageName);
    }
  }

  private static final Logger logger = Logger.getLogger(ApiProxyLocalImpl.class.getName());

  private final Map<String, LocalRpcService> serviceCache =
      new ConcurrentHashMap<String, LocalRpcService>();

  private final Map<String, Method> methodCache = new ConcurrentHashMap<String, Method>();
  final Map<Method, LatencySimulator> latencySimulatorCache =
      new ConcurrentHashMap<Method, LatencySimulator>();

  private final Map<String, String> properties = new HashMap<String, String>();

  private final ExecutorService apiExecutor = Executors.newCachedThreadPool(
      new DaemonThreadFactory(Executors.defaultThreadFactory()));

  private final LocalServiceContext context;

  private Clock clock = Clock.DEFAULT;

  /**
   * Creates the local proxy in a given context
   *
   * @param environment the local server environment.
   */
  public ApiProxyLocalImpl(LocalServerEnvironment environment) {
    this.context = new LocalServiceContextImpl(environment);
  }

  /**
   * Provides a local proxy in a given context that delegates some calls to a Python API server.
   *
   * @param environment the local server environment.
   * @param applicationName the application name to pass to the ApiServer binary.
   */
  static ApiProxyLocal getApiProxyLocal(
      LocalServerEnvironment environment,
      String applicationName) {

    return new ApiProxyLocalImpl(environment);
  }

  @Override
  public void log(Environment environment, LogRecord record) {
    logger.log(toJavaLevel(record.getLevel()), record.getMessage());
  }

  @Override
  public void flushLogs(Environment environment) {
    System.err.flush();
  }

  @Override
  public byte[] makeSyncCall(ApiProxy.Environment environment, String packageName,
      String methodName, byte[] requestBytes) {
    ApiProxy.ApiConfig apiConfig = null;
    Double deadline = (Double) environment.getAttributes().get(API_DEADLINE_KEY);
    if (deadline != null) {
      apiConfig = new ApiProxy.ApiConfig();
      apiConfig.setDeadlineInSeconds(deadline);
    }

    Future<byte[]> future =
        makeAsyncCall(environment, packageName, methodName, requestBytes, apiConfig);
    try {
      return future.get();
    } catch (InterruptedException ex) {
      // Someone else called Thread.interrupt().  We probably
      // shouldn't swallow this, so propagate it as the closest
      // exception that we have.  Note that we specifically do not
      // re-set the interrupt bit because we don't want future API
      // calls to immediately throw this exception.
      throw new ApiProxy.CancelledException(packageName, methodName);
    } catch (CancellationException ex) {
      throw new ApiProxy.CancelledException(packageName, methodName);
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof RuntimeException) {
        throw (RuntimeException) ex.getCause();
      } else if (ex.getCause() instanceof Error) {
        throw (Error) ex.getCause();
      } else {
        throw new ApiProxy.UnknownException(packageName, methodName, ex.getCause());
      }
    }
  }

  @Override
  public Future<byte[]> makeAsyncCall(
      Environment environment,
      final String packageName,
      final String methodName,
      byte[] requestBytes,
      ApiProxy.@Nullable ApiConfig apiConfig) {
    // If this is null, we simply do not limit the number of async API
    // calls.  This may be the case during filter initialization
    // requests, or in some unit tests.
    Semaphore semaphore = (Semaphore) environment.getAttributes().get(
        LocalEnvironment.API_CALL_SEMAPHORE);
    if (semaphore != null) {
      try {
        semaphore.acquire();
      } catch (InterruptedException ex) {
        // We never do this, so just propagate it as a RuntimeException for now.
        throw new RuntimeException("Interrupted while waiting on semaphore:", ex);
      }
    }
    AsyncApiCall asyncApiCall =
        new AsyncApiCall(environment, packageName, methodName, requestBytes, semaphore);

    boolean offline = environment.getAttributes().get(IS_OFFLINE_REQUEST_KEY) != null;
    boolean success = false;
    try {
      // Despite the name, privilegedCallable() just arranges for this
      // callable to be run with the current privileges.
      Callable<byte[]> callable = Executors.privilegedCallable(asyncApiCall);

      // Now we need to escalate privileges so we have permission to
      // spin up new threads, if necessary.  The callable itself will
      // run with the previous privileges.
      Future<byte[]> resultFuture = AccessController.doPrivileged(
          new PrivilegedApiAction(callable, asyncApiCall));
      success = true;
      if (context.getLocalServerEnvironment().enforceApiDeadlines()) {
        long deadlineMillis = (long) (1000.0 * resolveDeadline(packageName, apiConfig, offline));
        resultFuture = new TimedFuture<byte[]>(resultFuture, deadlineMillis, clock) {
          @Override
          protected RuntimeException createDeadlineException() {
            return new ApiProxy.ApiDeadlineExceededException(packageName, methodName);
          }
        };
      }
      return resultFuture;
    } finally {
      if (!success) {
        // If we failed to schedule the task we need to release our lock.
        asyncApiCall.tryReleaseSemaphore();
      }
    }
  }

  @Override
  public List<Thread> getRequestThreads(Environment environment) {
    // TODO Do something more intelligent here.
    return Arrays.asList(new Thread[]{Thread.currentThread()});
  }

  private double resolveDeadline(
      String packageName, ApiProxy.@Nullable ApiConfig apiConfig, boolean isOffline) {
    LocalRpcService service = getService(packageName);
    Double deadline = null;
    if (apiConfig != null) {
      deadline = apiConfig.getDeadlineInSeconds();
    }
    if (deadline == null && service != null) {
      deadline = service.getDefaultDeadline(isOffline);
    }
    if (deadline == null) {
      deadline = 5.0;
    }

    Double maxDeadline = null;
    if (service != null) {
      maxDeadline = service.getMaximumDeadline(isOffline);
    }
    if (maxDeadline == null) {
      maxDeadline = 10.0;
    }
    return Math.min(deadline, maxDeadline);
  }

  private class PrivilegedApiAction implements PrivilegedAction<Future<byte[]>> {

    private final Callable<byte[]> callable;
    private final AsyncApiCall asyncApiCall;

    PrivilegedApiAction(Callable<byte[]> callable, AsyncApiCall asyncApiCall) {
      this.callable = callable;
      this.asyncApiCall = asyncApiCall;
    }

    @Override
    public Future<byte[]> run() {
      // TODO: Return something that implements
      // ApiProxy.ApiResultFuture so we can attach real wallclock
      // time information here (although CPU time is irrelevant).
      final Future<byte[]> result = apiExecutor.submit(callable);
      return new Future<byte[]>() {
        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
          // Cancel may interrupt another thread so we need to escalate privileges to avoid
          // sandbox restrictions.
          return AccessController.doPrivileged(
              new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                  // If we cancel the task before it runs it's up to us to
                  // release the semaphore.  If we cancel the task after it
                  // runs we know the task released the semaphore.  However,
                  // we can't reliably know the state of the task and it's
                  // bad news if the semaphore gets released twice.  This
                  // method ensures that the semaphore only gets released once.
                  asyncApiCall.tryReleaseSemaphore();
                  return result.cancel(mayInterruptIfRunning);
                }
              });
        }

        @Override
        public boolean isCancelled() {
          return result.isCancelled();
        }

        @Override
        public boolean isDone() {
          return result.isDone();
        }

        @Override
        public byte[] get() throws InterruptedException, ExecutionException {
          return result.get();
        }

        @Override
        public byte[] get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
          return result.get(timeout, unit);
        }
      };
    }
  }

  @Override
  public void setProperty(String serviceProperty, String value) {
    if (serviceProperty == null) {
      throw new NullPointerException("Property key must not be null.");
    }
    String[] propertyComponents = serviceProperty.split("\\.");
    if (propertyComponents.length < 2) {
      throw new IllegalArgumentException(
          "Property string must be of the form {service}.{property}, received: " + serviceProperty);
    }

    properties.put(serviceProperty, value);
  }

  /**
   * Resets the service properties to {@code properties}.
   *
   * @param properties a maybe {@code null} set of properties for local services.
   */
  @Override
  public void setProperties(Map<String, String> properties) {
    this.properties.clear();
    if (properties != null) {
      this.appendProperties(properties);
    }
  }

  /**
   * Appends the given service properties to {@code properties}.
   *
   * @param properties a set of properties to append for local services.
   */
  @Override
  public void appendProperties(Map<String, String> properties) {
    this.properties.putAll(properties);
  }

  /** Stops all services started by this ApiProxy and releases all of its resources. */
  // TODO When we fix DevAppServer to support hot redeployment,
  // it <b>MUST</b> call into {@code stop} when it is attempting to GC
  // a webapp (otherwise background threads won't be stopped, etc...)
  @Override
  public void stop() {
    for (LocalRpcService service : serviceCache.values()) {
      service.stop();
    }

    serviceCache.clear();
    methodCache.clear();
    latencySimulatorCache.clear();
    apiExecutor.shutdown();
  }

  int getMaxApiRequestSize(LocalRpcService rpcService) {
    Integer size = rpcService.getMaxApiRequestSize();
    if (size == null) {
      return MAX_API_REQUEST_SIZE;
    }
    return size;
  }

  private Method getDispatchMethod(LocalRpcService service, String packageName, String methodName) {
    // e.g. RunQuery --> runQuery
    String dispatchName = Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
    // e.g. datastore_v3.runQuery
    String methodId = packageName + "." + dispatchName;
    Method method = methodCache.get(methodId);
    if (method != null) {
      return method;
    }
    for (Method candidate : service.getClass().getMethods()) {
      if (dispatchName.equals(candidate.getName())) {
        methodCache.put(methodId, candidate);
        LatencyPercentiles latencyPercentiles = candidate.getAnnotation(LatencyPercentiles.class);
        if (latencyPercentiles == null) {
          // TODO: Consider looking on the superclass and interfaces.

          // Nothing on the method so check the class
          latencyPercentiles = service.getClass().getAnnotation(LatencyPercentiles.class);
        }
        if (latencyPercentiles != null) {
          latencySimulatorCache.put(candidate, new LatencySimulator(latencyPercentiles));
        }
        return candidate;
      }
    }
    throw new CallNotFoundException(packageName, methodName);
  }

  private class AsyncApiCall implements Callable<byte[]> {

    private final Environment environment;
    private final String packageName;
    private final String methodName;
    private final byte[] requestBytes;
    private final Semaphore semaphore;
    // True if the semaphore we claimed when we were instantiated has been
    // released, false otherwise.  Access to this member must be synchronized.
    private boolean released;

    public AsyncApiCall(
        Environment environment,
        String packageName,
        String methodName,
        byte[] requestBytes,
        @Nullable Semaphore semaphore) {
      this.environment = environment;
      this.packageName = packageName;
      this.methodName = methodName;
      this.requestBytes = requestBytes;
      this.semaphore = semaphore;
    }

    @Override
    public byte[] call() {
      try {
        return callInternal();
      } finally {
        // We acquired the semaphore in doAsyncCall above.
        tryReleaseSemaphore();
      }
    }

    private byte[] callInternal() {
      // Chaining of calls may be required so we borrow the
      // caller's environment so that it may be used.
      // TODO Consider making a copy of environment here
      //                to avoid possible race conditions.  Should
      //                be safe for now as it's only used by
      //                datastore service to add tasks to taskqueue
      //                service.
      // N.B. . We set the environment prior
      // to invoking getService() because the environment is
      // needed by some of the services (at least TaskQueue)
      // during initialization.
      ApiProxy.setEnvironmentForCurrentThread(environment);
      try {
        LocalCapabilitiesEnvironment capEnv = context.getLocalCapabilitiesEnvironment();
        CapabilityStatus capabilityStatus = capEnv
            .getStatusFromMethodName(packageName, methodName);
        if (!CapabilityStatus.ENABLED.equals(capabilityStatus)) {
          // TODO return the same error message we return in prod
          throw new ApiProxy.CapabilityDisabledException(
              "Setup in local configuration.", packageName, methodName);
        }
        return invokeApiMethodJava(packageName, methodName, requestBytes);
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new UnknownException(packageName, methodName, e.getCause());
      } catch (ReflectiveOperationException e) {
        throw new UnknownException(packageName, methodName, e);
      } finally {
        // Must remove reference to environment on end of call.
        ApiProxy.clearEnvironmentForCurrentThread();
      }
    }

    /**
     * Invokes an API call using the Java implementations.
     *
     * @param packageName the name of the API service, eg datastore_v3
     * @param methodName the name of the API method, eg Query
     * @param requestBytes the serialized proto, eg DatastoreV3Pb.Query
     * @return the serialized API response
     */
    public byte[] invokeApiMethodJava(String packageName, String methodName, byte[] requestBytes)
        throws IllegalAccessException, InstantiationException, InvocationTargetException,
            NoSuchMethodException {
      logger.log(
          Level.FINE,
          "Making an API call to a Java implementation: " + packageName + "." + methodName);
      LocalRpcService service = getService(packageName);
      if (service == null) {
        throw new CallNotFoundException(packageName, methodName);
      }

      if (requestBytes.length > getMaxApiRequestSize(service)) {
        throw new RequestTooLargeException(packageName, methodName);
      }

      Method method = getDispatchMethod(service, packageName, methodName);
      Status status = new Status();
      Class<?> requestClass = method.getParameterTypes()[1];
      Object request = ApiUtils.convertBytesToPb(requestBytes, requestClass);

      long start = clock.getCurrentTime();
      try {
        return ApiUtils.convertPbToBytes(method.invoke(service, status, request));
      } finally {
        // Add latency to the call
        LatencySimulator latencySimulator = latencySimulatorCache.get(method);
        if (latencySimulator != null) {
          if (context.getLocalServerEnvironment().simulateProductionLatencies()) {
            latencySimulator.simulateLatency(clock.getCurrentTime() - start, service, request);
          }
        }
      }
    }

    /**
     * Synchronized method that ensures the semaphore that was claimed for this API call only gets
     * released once.
     */
    synchronized void tryReleaseSemaphore() {
      if (!released && semaphore != null) {
        semaphore.release();
        released = true;
      }
    }
  }

  /**
   * Method needs to be synchronized to ensure that we don't end up starting multiple instances of
   * the same service.  As an example, we've seen a race condition where the local datastore service
   * has not yet been initialized and two datastore requests come in at the exact same time. The
   * first request looks in the service cache, doesn't find it, starts a new local datastore
   * service, registers it in the service cache, and uses that local datastore service to handle the
   * first request.  Meanwhile the second request looks in the service cache, doesn't find it,
   * starts a new local datastore service, registers it in the service cache (stomping on the
   * original one), and uses that local datastore service to handle the second request.  If both of
   * these requests start txns we can end up with 2 requests receiving the same txn id, and that
   * yields all sorts of exciting behavior.  So, we synchronize this method to ensure that we only
   * register a single instance of each service type.
   */
  @Override
  public final synchronized LocalRpcService getService(final String pkg) {
    LocalRpcService cachedService = serviceCache.get(pkg);
    if (cachedService != null) {
      return cachedService;
    }

    return AccessController.doPrivileged(
        new PrivilegedAction<LocalRpcService>() {
          @Override
          public LocalRpcService run() {
            return startServices(pkg);
          }
        });
  }

  @Override
  public DevLogService getLogService() {
    return (DevLogService) getService(DevLogService.PACKAGE);
  }

  private LocalRpcService startServices(String pkg) {
    // N.B.: Service.providers() actually instantiates every
    // service it finds so it's important that our local service
    // implementations really respect the init/start/stop contract.
    // We don't want services doing anything meaningful when they
    // are constructed.
    for (LocalRpcService service :
        ServiceLoader.load(LocalRpcService.class, ApiProxyLocalImpl.class.getClassLoader())) {
      if (service.getPackage().equals(pkg)) {
        service.init(context, properties);
        service.start();
        serviceCache.put(pkg, service);
        return service;
      }
    }
    return null;
  }

  private static Level toJavaLevel(ApiProxy.LogRecord.Level apiProxyLevel) {
    switch (apiProxyLevel) {
      case debug:
        return Level.FINE;
      case info:
        return Level.INFO;
      case warn:
        return Level.WARNING;
      case error:
        return Level.SEVERE;
      case fatal:
        return Level.SEVERE;
      default:
        return Level.WARNING;
    }
  }

  @Override
  public Clock getClock() {
    return clock;
  }

  @Override
  public void setClock(Clock clock) {
    this.clock = clock;
  }

  private static class DaemonThreadFactory implements ThreadFactory {

    private final ThreadFactory parent;

    public DaemonThreadFactory(ThreadFactory parent) {
      this.parent = parent;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = parent.newThread(r);
      thread.setDaemon(true);
      return thread;
    }
  }
}
