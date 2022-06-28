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

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueueCallback;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig.TaskCountDownLatch;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 */
public class LocalTaskQueueTestConfigTest extends TestCase {

  private static URLFetchServicePb.URLFetchRequest request;

  public static final class TestCallback implements LocalTaskQueueCallback {
    @Override
    public int execute(URLFetchServicePb.URLFetchRequest req) {
      request = req;
      return 200;
    }
    @Override
    public void initialize(Map<String, String> properties){
      // no initialization necessary
    }
  }

  private final TestCallback callback = new TestCallback();

  private final TaskCountDownLatch latch = new TaskCountDownLatch(1);

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalTaskQueueTestConfig().setDisableAutoTaskExecution(false)
          .setCallbackClass(callback.getClass()).setTaskExecutionLatch(latch));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    helper.setUp();
    latch.reset(1);
    request = null;
  }

  @Override
  public void tearDown() throws Exception {
    helper.tearDown();
    super.tearDown();
  }

  // we'll run this test twice to prove that we're not leaking any state across
  // tests
  private void doTest() throws InterruptedException {
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add(TaskOptions.Builder.withParam("p1", "val1").param("p2", "val2").method(
      TaskOptions.Method.GET));
    assertTrue(latch.await(3, TimeUnit.SECONDS));
    assertNotNull(request);
    assertEquals(URLFetchServicePb.URLFetchRequest.RequestMethod.GET, request.getMethod());
    assertEquals("http://localhost:8080/_ah/queue/default?p1=val1&p2=val2", request.getUrl());
    // this will fail if tasks are not getting cleared out in between tests
    queue.add(TaskOptions.Builder.withTaskName("delay").etaMillis(1000 * 60));
  }

  public void testInsert1() throws InterruptedException {
    doTest();
  }

  public void testInsert2() throws InterruptedException {
    doTest();
  }
}
