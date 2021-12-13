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

import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Holds the current trace context visible to user code. If present, this object will be stored
 * under the KEY-variable in an Environment's property.
 *
 * This class is used to share the current trace context between run time and user code. It is not
 * responsible for managing contexts. It only keeps the most current context for a given thread.
 */
public abstract class CloudTrace {
  // The name that this object is stored in the environment attribute map.
  private static final String KEY = "com.google.apphosting.api.cloudTrace";

  private static final ThreadLocal<CloudTraceContext> perThreadContext = new ThreadLocal<>();

  /**
   * Returns the corresponding CloudTrace object for a given environment. If the
   * environment does not have a CloudTrace object, null will be returned.
   */
  @Nullable
  private static CloudTrace get(ApiProxy.Environment env) {
    return (CloudTrace) env.getAttributes().get(KEY);
  }

  /**
   * Returns the current trace context for the given environment and the current thread.
   * @param env the current environment.
   */
  @Nullable
  public static CloudTraceContext getCurrentContext(ApiProxy.Environment env) {
    CloudTrace cloudTrace = CloudTrace.get(env);
    return (cloudTrace == null) ? null : cloudTrace.getCurrentContext();
  }

  /**
   * Returns the current trace context for the current thread.
   */
  @Nullable
  private CloudTraceContext getCurrentContext() {
    CloudTraceContext context = perThreadContext.get();
    if (context == null) {
      context = getDefaultContext();
      perThreadContext.set(context);
    }
    return context;
  }

  /**
   * Sets the current trace context for the current environment and the current thread.
   * @param env the current environment.
   * @param context the current trace context.
   */
  public static void setCurrentContext(
      ApiProxy.Environment env,
      @Nullable CloudTraceContext context) {
    CloudTrace cloudTrace = CloudTrace.get(env);
    if (cloudTrace != null) {
      perThreadContext.set(context);
    }
  }

  /**
   * Creates a new CloudTrace object and binds it to a given Environment.
   * @param env the environment object to bind this object to.
   */
  public CloudTrace(ApiProxy.Environment env){
    bind(env);
  }

  /**
   * Binds this object to a particular environment.
   * @param env the Environment object to bind this object to.
   */
  private void bind(ApiProxy.Environment env) {
    CloudTrace original;
    Map<String, Object> attrs = env.getAttributes();

    synchronized (attrs) {
      original = (CloudTrace) attrs.get(KEY);
      if (original == null) {
        attrs.put(KEY, this);
        return;
      }
    }
    // Behave nicely if the same object is bound twice
    if (original != this) {
      throw new IllegalStateException("Cannot replace existing CloudTrace object");
    }
  }

  /**
   * Returns the default context when a thread-specific one doesn't exist.
   */
  @Nullable
  protected abstract CloudTraceContext getDefaultContext();

  /**
   * Starts a new span as the child of the given context. The given context must match the current
   * context in the environment. After the call, the returned context becomes current.
   * @param env the environment object to get the current context from.
   * @param context parent context of the child span. Must match the current context.
   * @param name name of the child span.
   * @return context of the child span or null if the environment does not have trace context.
   */
  @Nullable
  public static CloudTraceContext startChildSpan(
      ApiProxy.Environment env, CloudTraceContext context, String name) {
    CloudTrace cloudTrace = CloudTrace.get(env);
    if (cloudTrace == null) {
      return null;
    }

    if (context != perThreadContext.get()) {
      throw new IllegalStateException("Can not startChildSpan for a different context");
    }
    CloudTraceContext childContext = cloudTrace.startChildSpanImpl(context, name);
    perThreadContext.set(childContext);
    return childContext;
  }

  /**
   * Sets a key:value label on the span for the given context. The given context must match the
   * current context in the environment.
   * @param env the environment object to get the current context.
   * @param context context of the span. Must match the current context.
   * @param key key of the label.
   * @param value value of the label.
   */
  public static void setLabel(
      ApiProxy.Environment env, CloudTraceContext context, String key, String value) {
    CloudTrace cloudTrace = CloudTrace.get(env);
    if (cloudTrace == null) {
      return;
    }

    if (context != perThreadContext.get()) {
      throw new IllegalStateException("Can not setLabel for a different context");
    }
    cloudTrace.setLabelImpl(context, key, value);
  }

  /**
   * Ends the span for the given context. The given context must match the current context in the
   * environment. After the call, the parent context becomes current.
   * @param env the environment object to get the current context.
   * @param context context of the span. Must match the current context.
   * @param parent parent context of the span. It becomes the current context after the call.
   */
  public static void endSpan(
      ApiProxy.Environment env, CloudTraceContext context, CloudTraceContext parent) {
    CloudTrace cloudTrace = CloudTrace.get(env);
    if (cloudTrace == null) {
      return;
    }

    if (context != perThreadContext.get()) {
      throw new IllegalStateException("Can not endSpan for a different context");
    }
    cloudTrace.endSpanImpl(context);
    perThreadContext.set(parent);
  }

  @Nullable
  protected abstract CloudTraceContext startChildSpanImpl(CloudTraceContext context, String name);
  protected abstract void setLabelImpl(CloudTraceContext context, String key, String value);
  protected abstract void endSpanImpl(CloudTraceContext context);
}
