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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to local services and local service methods
 * that declares latency percentiles.  These percentiles are used to simulate
 * the latency of production services in the dev appserver.
 * <p>
 * When the annotation is applied to a type it establishes a default that
 * applies to all methods that do not have their own annotation.
 * <p>
 * Only 50th percentile latency is required.  If 25th or 75th
 * percentile latency is not specified, 50th percentile latency will be used.
 * If 95th percentile latency is not specified, the 75th percentile latency
 * will be used.  If 99th percentile latency is not specified, the 95th
 * percentile latency will be used.  Latency percentile values must never get
 * smaller.  If they do, the local service will throw an
 * {@link IllegalArgumentException}.
 * <p>
 * This annotation allows the declaration of a {@link #dynamicAdjuster()}, a
 * class that can make runtime adjustments to the latency of a particular local
 * RPC taking environmental factors into account.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LatencyPercentiles {
  int UNDEFINED = Integer.MAX_VALUE;

  /**
   * 25th percentile latency for the service or method, in millis.  If not
   * defined, 50th percentile latency will be used.
   */
  int latency25th() default UNDEFINED;

  /**
   * 50th percentile latency for the service or method, in millis.
   */
  int latency50th();

  /**
   * 75th percentile latency for the service or method, in millis.  If not
   * defined, 50th percentile latency will be used.
   */
  int latency75th() default UNDEFINED;

  /**
   * 95th percentile latency for the service or method, in millis.  If not
   * defined, the next smallest percentile latency will be used (75th or 50th).
   */
  int latency95th() default UNDEFINED;

  /**
   * 99th percentile latency for the service or method, in millis.  If not
   * defined, the next smallest percentile latency will be used (95th or 75th
   * or 50th).
   */
  int latency99th() default UNDEFINED;

  /**
   * A {@link DynamicLatencyAdjuster} implementation that will be given the
   * opportunity to adjust latency based on environmental factors.  The class
   * must be public and have a public, no-arg constructor.
   */
  Class<? extends DynamicLatencyAdjuster> dynamicAdjuster()
      default DynamicLatencyAdjuster.Default.class;
}
