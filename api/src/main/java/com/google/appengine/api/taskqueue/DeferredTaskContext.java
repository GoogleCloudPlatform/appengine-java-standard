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

package com.google.appengine.api.taskqueue;

import com.google.apphosting.api.ApiProxy;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Resources for managing {@link DeferredTask}.
 *
 */
public class DeferredTaskContext {
  /** The content type of a serialized {@link DeferredTask}. */
  public static final String RUNNABLE_TASK_CONTENT_TYPE =
      "application/x-binary-app-engine-java-runnable-task";

  /** The URL the DeferredTask servlet is mapped to by default. */
  public static final String DEFAULT_DEFERRED_URL = "/_ah/queue/__deferred__";

  static final String DEFERRED_TASK_SERVLET_KEY =
      DeferredTaskContext.class.getName() + ".httpServlet";
  static final String DEFERRED_TASK_REQUEST_KEY =
      DeferredTaskContext.class.getName() + ".httpServletRequest";
  static final String DEFERRED_TASK_RESPONSE_KEY =
      DeferredTaskContext.class.getName() + ".httpServletResponse";
  static final String DEFERRED_DO_NOT_RETRY_KEY =
      DeferredTaskContext.class.getName() + ".doNotRetry";
  static final String DEFERRED_MARK_RETRY_KEY = DeferredTaskContext.class.getName() + ".markRetry";

  /**
   * Returns the {@link HttpServlet} instance for the current running deferred task for the current
   * thread or {@code null} if there is no current deferred task active for this thread.
   */
  public static HttpServlet getCurrentServlet() {
    Map<String, Object> attributes = getCurrentEnvironmentOrThrow().getAttributes();
    return (HttpServlet) attributes.get(DEFERRED_TASK_SERVLET_KEY);
  }

  /**
   * Returns the {@link HttpServletRequest} instance for the current running deferred task for the
   * current thread or {@code null} if there is no current deferred task active for this thread.
   */
  public static HttpServletRequest getCurrentRequest() {
    Map<String, Object> attributes = getCurrentEnvironmentOrThrow().getAttributes();
    return (HttpServletRequest) attributes.get(DEFERRED_TASK_REQUEST_KEY);
  }

  /**
   * Returns the {@link HttpServletResponse} instance for the current running deferred task for the
   * current thread or {@code null} if there is no current deferred task active for this thread.
   */
  public static HttpServletResponse getCurrentResponse() {
    Map<String, Object> attributes = getCurrentEnvironmentOrThrow().getAttributes();
    return (HttpServletResponse) attributes.get(DEFERRED_TASK_RESPONSE_KEY);
  }

  /**
   * Sets the action on task failure. Normally when an exception is thrown, the task will be
   * retried, however if {@code setDoNotRetry} is set to {@code true}, the task will not be retried.
   */
  public static void setDoNotRetry(boolean value) {
    Map<String, Object> attributes = getCurrentEnvironmentOrThrow().getAttributes();
    attributes.put(DEFERRED_DO_NOT_RETRY_KEY, value);
  }

  /**
   * Request a retry of this task, even if an exception was not thrown. If an exception was thrown
   * and {@link #setDoNotRetry} is set to {@code true} the request will not be retried.
   */
  public static void markForRetry() {
    Map<String, Object> attributes = getCurrentEnvironmentOrThrow().getAttributes();
    attributes.put(DEFERRED_MARK_RETRY_KEY, true);
  }

  private static ApiProxy.Environment getCurrentEnvironmentOrThrow() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      throw new IllegalStateException(
          "Operation not allowed in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager");
    }
    return environment;
  }

  private DeferredTaskContext() {}
}
