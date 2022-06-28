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

package com.google.appengine.tools.development.testing;

import static org.junit.Assert.assertEquals;

import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig.TaskCountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** */
@RunWith(JUnit4.class)
public class TaskCountDownLatchTest {

  final ExecutorService executor = Executors.newSingleThreadExecutor();

  @After
  public void tearDown() throws Exception {
        executor.shutdown();
      }

  @Test
  public void testReset() throws InterruptedException {
    final TaskCountDownLatch latch = new TaskCountDownLatch(2);
    assertEquals(2, latch.getCount());
    Runnable runnable =
        () -> {
          latch.countDown();
        };
    executor.execute(runnable);
    executor.execute(runnable);
    latch.await(5, TimeUnit.SECONDS);
    assertEquals(0, latch.getCount());

    latch.reset();

    assertEquals(2, latch.getCount());
    executor.execute(runnable);
    executor.execute(runnable);
    latch.await(5, TimeUnit.SECONDS);
    assertEquals(0, latch.getCount());

    latch.reset(1);

    assertEquals(1, latch.getCount());
    executor.execute(runnable);
    latch.await(5, TimeUnit.SECONDS);
    assertEquals(0, latch.getCount());
  }

  @Test
  public void testAwaitReset() throws InterruptedException {
    final TaskCountDownLatch latch = new TaskCountDownLatch(2);
    assertEquals(2, latch.getCount());
    Runnable runnable =
        () -> {
          latch.countDown();
        };
    executor.execute(runnable);
    executor.execute(runnable);
    latch.awaitAndReset(5, TimeUnit.SECONDS);
    assertEquals(2, latch.getCount());

    executor.execute(runnable);
    executor.execute(runnable);
    latch.awaitAndReset(5, TimeUnit.SECONDS, 1);
    assertEquals(1, latch.getCount());

    executor.execute(runnable);
    latch.await(5, TimeUnit.SECONDS);
    assertEquals(0, latch.getCount());
  }
}
