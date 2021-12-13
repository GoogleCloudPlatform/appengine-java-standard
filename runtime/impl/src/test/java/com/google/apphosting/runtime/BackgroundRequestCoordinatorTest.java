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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link BackgroundRequestCoordinator}. */
@RunWith(JUnit4.class)
public class BackgroundRequestCoordinatorTest {

  private BackgroundRequestCoordinator coordinator;
  private String requestId;

  @Before
  public void setUp() throws Exception {
    coordinator = new BackgroundRequestCoordinator();
    requestId = UUID.randomUUID().toString();
  }

  @SuppressWarnings("InterruptedExceptionSwallowed")
  private void waitAndAssert(Runnable runnable) {
    try {
      assertThat(coordinator.waitForThreadStart(requestId, runnable, 5000))
          .isSameInstanceAs(Thread.currentThread());
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  @Test
  public void testExchangeSuccessful() throws TimeoutException, InterruptedException {
    Runnable runnable = mock(Runnable.class);
    Thread thread = new Thread(() -> waitAndAssert(runnable));
    thread.start();
    assertThat(coordinator.waitForUserRunnable(requestId, thread, 5000)).isSameInstanceAs(runnable);
    thread.join();
  }

  @Test
  public void testExchangeTimeout_waitForUserRunnable() throws InterruptedException {
    assertThrows(
        TimeoutException.class,
        () -> coordinator.waitForUserRunnable(requestId, new Thread(), 1000));
  }

  @Test
  public void testExchangeTimeout_waitForThreadStart() throws InterruptedException {
    Runnable runnable = mock(Runnable.class);
    assertThrows(
        TimeoutException.class, () -> coordinator.waitForThreadStart(requestId, runnable, 1000));
  }
}
