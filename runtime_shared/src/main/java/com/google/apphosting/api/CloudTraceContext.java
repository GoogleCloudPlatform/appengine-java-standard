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

import java.util.Random;

/**
 * Stores tracing context including IDs and settings.
 */
public class CloudTraceContext {
  private static final long INVALID_SPAN_ID = 0;
  private static final long MASK_TRACE_ENABLED = 0x01;
  private static final Random random = new Random();

  private final byte[] traceId;
  private final long parentSpanId;
  private final long spanId;
  private final long traceMask;

  /**
   * Create a new context with the given parameters.
   * @param traceId id of the trace.
   * @param spanId current span id.
   * @param traceMask trace options.
   */
  public CloudTraceContext(byte[] traceId, long spanId, long traceMask) {
    this(traceId, INVALID_SPAN_ID, spanId, traceMask);
  }

  /**
   * Create a new context with the given parameters.
   * @param traceId id of the trace.
   * @param parentSpanId parent span id.
   * @param spanId current span id.
   * @param traceMask trace options.
   */
  public CloudTraceContext(byte[] traceId, long parentSpanId, long spanId, long traceMask) {
    this.traceId = traceId;
    this.parentSpanId = parentSpanId;
    this.spanId = spanId;
    this.traceMask = traceMask;
  }

  /**
   * Creates a child context of the current context.
   */
  public CloudTraceContext createChildContext() {
    return new CloudTraceContext(traceId, spanId, random.nextLong(), getTraceMask());
  }

  public byte[] getTraceId() {
    return traceId;
  }

  public long getParentSpanId() {
    return parentSpanId;
  }

  public long getSpanId() {
    return spanId;
  }

  public long getTraceMask() {
    return traceMask;
  }

  public boolean isTraceEnabled() {
    return (traceMask & MASK_TRACE_ENABLED) != 0;
  }
}
