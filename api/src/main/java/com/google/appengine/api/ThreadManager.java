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

package com.google.appengine.api;

import com.google.apphosting.api.ApiProxy;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;

/**
 * {@code ThreadManager} exposes a {@link ThreadFactory} that allows
 * App Engine applications to spawn new threads.
 *
 * Refer to <a href="https://cloud.google.com/appengine/docs/java/runtime#threads">
 * this discussion of threads</a> for drawbacks of thread usage and possible
 * alternatives.
 *
 */
public final class ThreadManager {
  private static final String REQUEST_THREAD_FACTORY_ATTR =
      "com.google.appengine.api.ThreadManager.REQUEST_THREAD_FACTORY";

  private static final String BACKGROUND_THREAD_FACTORY_ATTR =
      "com.google.appengine.api.ThreadManager.BACKGROUND_THREAD_FACTORY";

  /**
   * Returns a {@link ThreadFactory} which will create threads scoped to the current request. These
   * threads will be interrupted at the end of the current request and must complete within the
   * request deadline. If they fail to, the instance containing them may be terminated.
   *
   * <p>The principal reason to use this method is so that the created threads can make App Engine
   * API calls ({@code com.google.appengine.api.*}). In general, threads not associated with a
   * request cannot make these API calls.
   *
   * <p>The returned factory is typically used with a call like {@link
   * java.util.concurrent.Executors#newCachedThreadPool(ThreadFactory)}. Do not use the {@link
   * java.util.concurrent.ExecutorService ExecutorService} returned by this call after the request
   * that created it has completed.
   *
   * <p>Note that calling {@link ThreadFactory#newThread} on the returned instance may throw any of
   * the unchecked exceptions mentioned by {@link #createBackgroundThread}.
   *
   * @throws NullPointerException if the calling thread is not associated with a request.
   */
  public static ThreadFactory currentRequestThreadFactory() {
    ApiProxy.Environment environment = getCurrentEnvironmentOrThrow();
    return (ThreadFactory) environment.getAttributes().get(REQUEST_THREAD_FACTORY_ATTR);
  }

  /**
   * Returns an Optional {@link ThreadFactory} which will create threads scoped to the current
   * request. These threads will be interrupted at the end of the current request and must complete
   * within the request deadline. If they fail to, the instance containing them may be terminated.
   *
   * <p>If this method is not called from an App Engine request thread, returns an empty Optional
   * instance.
   *
   * <p>The principal reason to use this method is so that the created threads can make App Engine
   * API calls ({@code com.google.appengine.api.*}). In general, threads not associated with a
   * request cannot make these API calls.
   *
   * <p>The returned factory is typically used with a call like {@link
   * java.util.concurrent.Executors#newCachedThreadPool(ThreadFactory)}. Do not use the {@link
   * java.util.concurrent.ExecutorService ExecutorService} returned by this call after the request
   * that created it has completed.
   *
   * <p>Note that calling {@link ThreadFactory#newThread} on the returned instance may throw any of
   * the unchecked exceptions mentioned by {@link #createBackgroundThread}.
   */
  public static Optional<ThreadFactory> currentRequestThreadFactoryOptional() {
    return Optional.ofNullable(ApiProxy.getCurrentEnvironment())
        .flatMap(
            environment ->
                Optional.ofNullable(
                    (ThreadFactory) environment.getAttributes().get(REQUEST_THREAD_FACTORY_ATTR)));
  }

  /**
   * Create a new {@link Thread} that executes {@code runnable} for
   * the duration of the current request.  Calling this method is
   * equivalent to invoking {@link ThreadFactory#newThread} on the
   * ThreadFactory returned from {@link #currentRequestThreadFactory}.
   * This thread will be interrupted at the end of the current request
   * and must complete within the request deadline. If it fails to,
   * the instance containing it may be terminated.
   *
   * @throws IllegalStateException if you try to create more than 50 threads in a single request.
   * @throws NullPointerException if the calling thread is not associated with a request.
   * @throws ApiProxy.FeatureNotEnabledException If this application
   *     cannot use this feature.
   */
  public static Thread createThreadForCurrentRequest(Runnable runnable) {
    return currentRequestThreadFactory().newThread(runnable);
  }

  /**
   * Returns a {@link ThreadFactory} that will create threads that are
   * independent of the current request.
   *
   * <p>This ThreadFactory can currently only be used by backends.
   *
   * <p>Note that calling {@link ThreadFactory#newThread} on the
   * returned instance may throw any of the unchecked exceptions
   * mentioned by {@link #createBackgroundThread}.
   */
  public static ThreadFactory backgroundThreadFactory() {
    ApiProxy.Environment environment = getCurrentEnvironmentOrThrow();
    return (ThreadFactory) environment.getAttributes().get(BACKGROUND_THREAD_FACTORY_ATTR);
  }

  /**
   * Create a new {@link Thread} that executes {@code runnable}
   * independent of the current request.  Calling this method is
   * equivalent to invoking {@link ThreadFactory#newThread} on the
   * ThreadFactory returned from {@link #backgroundThreadFactory}.
   *
   * <p>This method can currently only be used by backends.
   *
   * @throws ApiProxy.FeatureNotEnabledException If this application
   *     cannot use this feature.
   * @throws ApiProxy.CancelledException If the request was interrupted
   *     while creating the new thread.
   * @throws ApiProxy.ApiDeadlineExceededException If creation of the
   *     new thread took too long.
   */
  public static Thread createBackgroundThread(Runnable runnable) {
    return backgroundThreadFactory().newThread(runnable);
  }

  private static ApiProxy.Environment getCurrentEnvironmentOrThrow() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      // IllegalStateException would make more sense, but this is compatible with code that
      // detected the absence of an environment by the NullPointerException it provoked due
      // to unprotected dereferences of `environment` in earlier versions.
      throw new NullPointerException(
          "Current thread is not associated with any request and is not a background thread");
    }
    return environment;
  }
}
