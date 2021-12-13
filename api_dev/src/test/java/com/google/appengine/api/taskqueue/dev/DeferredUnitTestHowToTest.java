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

package com.google.appengine.api.taskqueue.dev;

import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertTrue;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This is the approach to deferred task queue unit tests that we recommend in
 * http://code.google.com/appengine/docs/java/howto/unittesting.html
 *
 * <p>If any of these tests are failing or are not compiling, the change we make to fix the problem
 * should also be made in the above article.
 *
 */
@RunWith(JUnit4.class)
public class DeferredUnitTestHowToTest {

  private final LocalTaskQueueTestConfig.TaskCountDownLatch latch =
      new LocalTaskQueueTestConfig.TaskCountDownLatch(1);

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          new LocalTaskQueueTestConfig()
              .setDisableAutoTaskExecution(false)
              .setCallbackClass(LocalTaskQueueTestConfig.DeferredTaskCallback.class)
              .setTaskExecutionLatch(latch));

  @Before
  public void setUp() throws Exception {
    helper.setUp();
  }

  @After
  public void tearDown() throws Exception {
    MyTask.taskRan = false;
    latch.reset();
    helper.tearDown();
  }

  private static class MyTask implements DeferredTask {
    private static boolean taskRan = false;

    @Override
    public void run() {
      taskRan = true;
    }
  }

  @Test
  public void testTaskGetsRun() throws InterruptedException {
    QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withPayload(new MyTask()));
    assertTrue(latch.await(5, SECONDS));
    assertTrue(MyTask.taskRan);
  }
}
