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

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withMaxBackoffSeconds;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withMaxDoublings;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withMinBackoffSeconds;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskAgeLimitSeconds;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;

import junit.framework.TestCase;

/**
 * Unit tests for {@link RetryOptions}.
 *
 */
public class RetryOptionsTest extends TestCase {

  public void testRetryOptions() throws Exception {
    assertEquals((Integer) 30, withTaskRetryLimit(30).getTaskRetryLimit());
    assertEquals((Long) 86400L, withTaskAgeLimitSeconds(86400L).getTaskAgeLimitSeconds());
    assertEquals(2.4, withMinBackoffSeconds(2.4).getMinBackoffSeconds(), 0.0);
    assertEquals(3600.0, withMaxBackoffSeconds(3600).getMaxBackoffSeconds(), 0.0);
    assertEquals((Integer) 2, withMaxDoublings(2).getMaxDoublings());
  }

  public void testEquals() {
    RetryOptions retry1 =
        withTaskRetryLimit(10)
            .taskAgeLimitSeconds(86400)
            .minBackoffSeconds(0.1)
            .maxBackoffSeconds(3600)
            .maxDoublings(10);
    RetryOptions retry2 =
        withTaskRetryLimit(10)
            .taskAgeLimitSeconds(86400)
            .minBackoffSeconds(0.1)
            .maxBackoffSeconds(3600)
            .maxDoublings(10);

    assertEquals(retry1, retry2);
    assertEquals(retry2, retry1);

    assertFalse(retry1.equals(withTaskRetryLimit(10)));
  }

  public void testHashEquals() {
    RetryOptions retry1 =
        withTaskRetryLimit(10)
            .taskAgeLimitSeconds(86400)
            .minBackoffSeconds(0.1)
            .maxBackoffSeconds(3600)
            .maxDoublings(10);
    RetryOptions retry2 =
        withTaskRetryLimit(10)
            .taskAgeLimitSeconds(86400)
            .minBackoffSeconds(0.1)
            .maxBackoffSeconds(3600)
            .maxDoublings(10);

    assertEquals(retry1.hashCode(), retry2.hashCode());
  }

  public void testToString() {
    RetryOptions retry =
        withTaskRetryLimit(10)
            .taskAgeLimitSeconds(86400)
            .minBackoffSeconds(0.1)
            .maxBackoffSeconds(3600)
            .maxDoublings(10);

    assertEquals(
        "RetryOptions[taskRetryLimit=10, taskAgeLimitSeconds=86400, "
            + "minBackoffSeconds=0.1, maxBackoffSeconds=3600.0, maxDoublings=10]",
        retry.toString());
  }
}
