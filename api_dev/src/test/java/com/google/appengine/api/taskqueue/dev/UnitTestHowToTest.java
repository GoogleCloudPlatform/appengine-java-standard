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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This is the approach to task queue unit tests that we recommend in
 * http://code.google.com/appengine/docs/java/howto/unittesting.html
 *
 * <p>If any of these tests are failing or are not compiling, the change we make to fix the problem
 * should also be made in the above article.
 *
 */
@RunWith(JUnit4.class)
public class UnitTestHowToTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String QUEUE_XML_NAME = "custom_queue.xml";
  private LocalServiceTestHelper helper;

  @Before
  public void setUp() throws Exception {
    Path queueXmlPath = temporaryFolder.newFile(QUEUE_XML_NAME).toPath();
    try (InputStream in = getClass().getResourceAsStream(QUEUE_XML_NAME)) {
      Files.copy(in, queueXmlPath, REPLACE_EXISTING);
    }
    this.helper =
        new LocalServiceTestHelper(
            new LocalTaskQueueTestConfig()
                .setQueueXmlPath(queueXmlPath.toAbsolutePath().toString()));
    helper.setUp();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  /**
   * We'll run this test twice to demonstrate that we're not leaking state across tests. If we _are_
   * leaking state across tests we'll get an exception on the second test because there will already
   * be a task with the given name.
   */
  private void doTest() throws InterruptedException {
    // uses a custom queue to demonstrate that we're loading custom_queue.xml
    // correctly
    Queue queue = QueueFactory.getQueue("another-queue");
    queue.add(TaskOptions.Builder.withTaskName("task29"));
    // give the task time to execute if tasks are actually enabled (which they
    // aren't, but that's part of the test)
    Thread.sleep(1000);
    LocalTaskQueue ltq = LocalTaskQueueTestConfig.getLocalTaskQueue();
    QueueStateInfo qsi = ltq.getQueueStateInfo().get(queue.getQueueName());
    assertThat(qsi.getTaskInfo()).hasSize(1);
    assertThat(qsi.getTaskInfo().get(0).getTaskName()).isEqualTo("task29");
  }

  @Test
  public void testTaskGetsScheduled1() throws InterruptedException {
    doTest();
  }

  @Test
  public void testTaskGetsScheduled2() throws InterruptedException {
    doTest();
  }
}
