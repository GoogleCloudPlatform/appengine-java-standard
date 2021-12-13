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
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Throwables;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ThreadGroupPool}.
 *
 */
@RunWith(JUnit4.class)
public class ThreadGroupPoolTest {
  private ThreadGroup root;
  private ThreadGroupPool threadGroupPool;
  private BlockingQueue<Throwable> caughtException;
  private UncaughtExceptionHandler uncaughtExceptionHandler;

  @Before
  @SuppressWarnings("CatchAndPrintStackTrace")
  public void setUp() {
    root = new ThreadGroup("root");
    caughtException = new ArrayBlockingQueue<>(100);
    uncaughtExceptionHandler =
        (thread, throwable) -> {
          throwable.printStackTrace();
          caughtException.offer(throwable);
        };
    threadGroupPool =
        ThreadGroupPool.builder()
            .setParentThreadGroup(root)
            .setThreadGroupNamePrefix("subgroup-")
            .setUncaughtExceptionHandler(uncaughtExceptionHandler)
            .setIgnoreDaemonThreads(false)
            .build();
  }

  @After
  public void checkNoExceptionFromThreads() {
    Throwable caught = caughtException.poll();
    if (caught != null) {
      assertWithMessage(Throwables.getStackTraceAsString(caught)).that(caught).isNull();
    }
  }

  @Test
  public void testStartOne() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    threadGroupPool.start(
        "newThread",
        () -> {
          Thread thread = Thread.currentThread();
          assertThat(thread.getThreadGroup().getName()).isEqualTo("subgroup-0");
          assertThat(thread.getThreadGroup().getParent().getName()).isEqualTo("root");
          assertThat(thread.getName()).isEqualTo("newThread");
          latch.countDown();
        });
    await(latch);
  }

  @Test
  public void testStartOneReuseThread() throws Exception {
    final CountDownLatch latch1 = new CountDownLatch(1);
    assertThat(threadGroupPool.waitingThreadCount()).isEqualTo(0);
    threadGroupPool.start(
        "newThread1",
        () -> {
          Thread thread = Thread.currentThread();
          assertThat(thread.getThreadGroup().getName()).isEqualTo("subgroup-0");
          assertThat(thread.getThreadGroup().getParent().getName()).isEqualTo("root");
          assertThat(thread.getName()).isEqualTo("newThread1");
          latch1.countDown();
        });
    await(latch1);
    // We need to avoid a race condition here. There is a short period after the lambda above has
    // executed where we have not yet put the thread back in the pool. If we start the next
    // execution right away we might get a new pool entry instead of reusing the thread.
    for (int i = 0; i < 100; i++) {
      if (threadGroupPool.waitingThreadCount() > 0) {
        break;
      }
      Thread.sleep(10);
    }
    assertWithMessage("Thread was never returned to the pool")
        .that(threadGroupPool.waitingThreadCount())
        .isGreaterThan(0);
    final CountDownLatch latch2 = new CountDownLatch(1);
    threadGroupPool.start(
        "newThread2",
        () -> {
          Thread thread = Thread.currentThread();
          // Same thread group as before, should be the same thread as well.
          assertThat(thread.getThreadGroup().getName()).isEqualTo("subgroup-0");
          assertThat(thread.getThreadGroup().getParent().getName()).isEqualTo("root");
          assertThat(thread.getName()).isEqualTo("newThread2");
          latch2.countDown();
        });
    await(latch2);
  }

  @Test
  @SuppressWarnings({"CatchAndPrintStackTrace", "InterruptedExceptionSwallowed"})
  public void testStartTwoInParallel() throws Exception {
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final CountDownLatch latch = new CountDownLatch(2);
    threadGroupPool.start(
        "newThread1",
        () -> {
          Thread thread = Thread.currentThread();
          assertThat(thread.getThreadGroup().getName()).isEqualTo("subgroup-0");
          assertThat(thread.getThreadGroup().getParent().getName()).isEqualTo("root");
          assertThat(thread.getName()).isEqualTo("newThread1");
          try {
            barrier.await();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
          latch.countDown();
        });
    threadGroupPool.start(
        "newThread2",
        () -> {
          Thread thread = Thread.currentThread();
          // Different thread group this time.
          assertThat(thread.getThreadGroup().getName()).isEqualTo("subgroup-1");
          assertThat(thread.getThreadGroup().getParent().getName()).isEqualTo("root");
          assertThat(thread.getName()).isEqualTo("newThread2");
          try {
            barrier.await();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
          latch.countDown();
        });
    await(latch);
  }

  @Test
  public void testUncaughtException() throws Exception {
    threadGroupPool.start(
        "newThread",
        () -> {
          throw new RuntimeException("intentional");
        });
    Throwable caught = caughtException.poll(10, SECONDS);
    assertWithMessage("Timed out while waiting for expected exception").that(caught).isNotNull();
    assertThat(caught).hasMessageThat().isEqualTo("intentional");
  }

  private void await(CountDownLatch latch) throws InterruptedException {
    boolean ok = latch.await(10, SECONDS);
    // If there was an exception in the thread, then we might have timed out waiting for it, and the
    // exception is actually the interesting thing.
    checkNoExceptionFromThreads();
    // Otherwise make sure the latch wait did in fact succeed.
    assertWithMessage("Timeout while waiting for latch").that(ok).isTrue();
  }
}
