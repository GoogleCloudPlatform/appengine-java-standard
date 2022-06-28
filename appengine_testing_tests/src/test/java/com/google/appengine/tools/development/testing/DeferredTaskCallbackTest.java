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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig.DeferredTaskCallback;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** */
@RunWith(JUnit4.class)
public class DeferredTaskCallbackTest {

  public static class CustomDeferredTaskCallback extends DeferredTaskCallback {
    static boolean executeCalled;
    static String namespace = null;

    @Override
    public int executeNonDeferredRequest(URLFetchServicePb.URLFetchRequest req) {
      namespace = NamespaceManager.get();
      try {
        return super.executeNonDeferredRequest(req);
      } finally {
        executeCalled = true;
      }
    }
  }

  public static class MyDeferredTask implements DeferredTask {
    static boolean ran = false;

    @Override
    public void run() {
      ran = true;
    }
  }

  public static class NamespaceDeferredTask implements DeferredTask {
    static String namespace = null;

    @Override
    public void run() {
      namespace = NamespaceManager.get();
    }
  }

  private static LocalTaskQueueTestConfig newLocalTaskQueueTestConfig() {
    return new LocalTaskQueueTestConfig()
        .setCallbackClass(CustomDeferredTaskCallback.class)
        .setDisableAutoTaskExecution(false);
  }

  private LocalServiceTestHelper helper = new LocalServiceTestHelper(newLocalTaskQueueTestConfig());

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    CustomDeferredTaskCallback.executeCalled = false;
    CustomDeferredTaskCallback.namespace = null;
    MyDeferredTask.ran = false;
    NamespaceDeferredTask.namespace = null;
  }

  @After
  public void done() throws Exception {
    helper.tearDown();
  }

  @Test
  public void testNotDeferred() throws InterruptedException {
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add();
    // We don't have a latch we can block on so we'll just poll.
    long start = System.currentTimeMillis();
    while ((System.currentTimeMillis() - start) < 5000) { // wait 5 seconds
      if (CustomDeferredTaskCallback.executeCalled) {
        break;
      }
      Thread.sleep(500);
    }
    assertThat(CustomDeferredTaskCallback.executeCalled).isTrue();
    assertThat(MyDeferredTask.ran).isFalse();
  }

  @Test
  public void testDeferred() throws InterruptedException {
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add(TaskOptions.Builder.withPayload(new MyDeferredTask()));
    // We don't have a latch we can block on so we'll just poll.
    long start = System.currentTimeMillis();
    while ((System.currentTimeMillis() - start) < 5000) { // wait 5 seconds
      if (MyDeferredTask.ran) {
        break;
      }
      Thread.sleep(500);
    }
    assertThat(CustomDeferredTaskCallback.executeCalled).isFalse();
    assertThat(MyDeferredTask.ran).isTrue();
  }

  @Test
  public void testDeferredWithBarrier() throws InterruptedException {
    helper.tearDown();
    CountDownLatch latch = new CountDownLatch(1);
    helper = new LocalServiceTestHelper(newLocalTaskQueueTestConfig().setTaskExecutionLatch(latch));
    helper.setUp();
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add(TaskOptions.Builder.withPayload(new MyDeferredTask()));
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(MyDeferredTask.ran).isTrue();
  }

  @Test
  public void testNotDeferredWithNamespace() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    helper = new LocalServiceTestHelper(newLocalTaskQueueTestConfig().setTaskExecutionLatch(latch));
    helper.setUp();
    String previousNamepace = NamespaceManager.get();
    try {
      NamespaceManager.set("ns2");
      Queue queue = QueueFactory.getDefaultQueue();
      queue.add();
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat("ns2").isEqualTo(CustomDeferredTaskCallback.namespace);
    } finally {
      NamespaceManager.set(previousNamepace);
    }
  }

  @Test
  public void testDeferredWithNamespace() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    helper = new LocalServiceTestHelper(newLocalTaskQueueTestConfig().setTaskExecutionLatch(latch));
    helper.setUp();
    String previousNamepace = NamespaceManager.get();
    try {
      NamespaceManager.set("ns1");
      Queue queue = QueueFactory.getDefaultQueue();
      queue.add(TaskOptions.Builder.withPayload(new NamespaceDeferredTask()));
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat("ns1").isEqualTo(NamespaceDeferredTask.namespace);
    } finally {
      NamespaceManager.set(previousNamepace);
    }
  }
}
