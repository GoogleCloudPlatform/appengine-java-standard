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

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import junit.framework.TestCase;

/**
 * An integration test for the purpose of checking the functionality of the method {@link
 * LocalTaskQueueTestConfig#setShouldCopyApiProxyEnvironment(boolean)}. If
 * setShouldCopyApiProxyEnvironment(true) is invoked then the ApiProxy.Environment from the main
 * test thread should get copied to background task threads. Conversely if
 * setShouldCopyApiProxyEnvironment(false) is invoked, then the background task threads should have
 * a default ApiProxy.Environment.
 *
 */
public class ApiProxyEnvTest extends TestCase {

  private static final String OUR_APP_ID = ApiProxyEnvTest.class.getName() + ".appId";
  private static final String OUR_EMAIL = ApiProxyEnvTest.class.getName() + ".email";
  private static final String OUR_ATTRIBUTE_NAME = "foo";
  private static final Object OUR_ATTRIBUTE_VALUE = new Object();

  // This will be set from the task thread during the test
  private static volatile ApiProxy.Environment environmentFromTaskThread;
  // This will be used to indicate that the task thread is done.
  private static volatile CountDownLatch countdownLatch;

  private LocalServiceTestHelper helper;

  @Override
  public void tearDown() throws Exception {
    helper.tearDown();
    super.tearDown();
  }

  /** Tests the positive case */
  public void testWithPushingEnvironment() throws Exception {
    doTest(true);
  }

  /** Tests the negative case */
  public void testWithoutPushingEnvironment() throws Exception {
    doTest(false);
  }

  public void doTest(boolean shouldCopyApiProxyEnvironment) throws Exception {
    setup(shouldCopyApiProxyEnvironment);
    environmentFromTaskThread = null;
    QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withTaskName("dummy"));
    countdownLatch.await(5, SECONDS);
    assertNotNull(environmentFromTaskThread);
    Object retrievedAttribute = environmentFromTaskThread.getAttributes().get(OUR_ATTRIBUTE_NAME);
    String retrievedAppId = environmentFromTaskThread.getAppId();
    String retrievedEmail = environmentFromTaskThread.getEmail();
    if (shouldCopyApiProxyEnvironment) {
      assertEquals(OUR_APP_ID, retrievedAppId);
      assertEquals(OUR_EMAIL, retrievedEmail);
      assertEquals(OUR_ATTRIBUTE_VALUE, retrievedAttribute);
    } else {
      assertFalse(OUR_APP_ID.equals(retrievedAppId));
      assertFalse(OUR_EMAIL.equals(retrievedEmail));
      assertNull(retrievedAttribute);
    }
  }

  private void setup(boolean shouldCopyApiProxyEnvironment) {
    LocalTaskQueueTestConfig taskQueueConfig = new LocalTaskQueueTestConfig();
    taskQueueConfig.setCallbackClass(TestingTaskQueueCallback.class);
    taskQueueConfig.setDisableAutoTaskExecution(false);
    taskQueueConfig.setShouldCopyApiProxyEnvironment(shouldCopyApiProxyEnvironment);
    helper = new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig(), taskQueueConfig);
    helper.setEnvAppId(OUR_APP_ID);
    helper.setEnvEmail(OUR_EMAIL);
    helper.setEnvAttributes(ImmutableMap.of(OUR_ATTRIBUTE_NAME, OUR_ATTRIBUTE_VALUE));
    helper.setUp();
    countdownLatch = new CountDownLatch(1);
  }

  /**
   * A TaskQueueCallback that does nothing but capture the ApiProxy.Environment from the thread in
   * which it is invoked.
   */
  public static class TestingTaskQueueCallback implements LocalTaskQueueCallback {
    @Override
    public int execute(URLFetchServicePb.URLFetchRequest req) {
      try {
        environmentFromTaskThread = ApiProxy.getCurrentEnvironment();
        return 0;
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        countdownLatch.countDown();
      }
    }

    @Override
    public void initialize(Map<String, String> properties) {
      // no initialization necessary
    }
  }
}
