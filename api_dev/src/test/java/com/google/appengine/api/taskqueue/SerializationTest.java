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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.testing.SerializationTestBase;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Serializable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {
  private static class DeferredTaskSerialize implements DeferredTask {
    private static final long serialVersionUID = -4926367513590574531L;

    @Override
    public void run() {}
  }

  @Override
  protected Iterable<Serializable> getCanonicalObjects() {
    LocalServiceTestHelper helper =
        new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()).setEnvAppId("app");
    helper.setUp();
    try {
      return canonicalObjects();
    } finally {
      helper.tearDown();
    }
  }

  private static Iterable<Serializable> canonicalObjects() {
    return ImmutableList.of(
        new InternalFailureException("yar"),
        new InvalidQueueModeException("yar"),
        new QueueFailureException("yar"),
        RetryOptions.Builder.withTaskRetryLimit(10)
            .taskAgeLimitSeconds(12600)
            .minBackoffSeconds(1.5),
        new TaskAlreadyExistsException("yar"),
        new TaskNotFoundException("yar"),
        new TaskHandle("name", "queue", 33),
        TaskOptions.Method.DELETE,
        TaskOptions.Builder.withDefaults(),
        new TransientFailureException("yar"),
        new TransactionalTaskException(),
        new DeferredTaskCreationException(new Exception()),
        new DeferredTaskSerialize(),
        new QueueNameMismatchException("yar"),
        new TaskOptions.StringValueParam("foo", "bar"),
        new TaskOptions.ByteArrayValueParam("foofoo", "baz".getBytes(UTF_8)),
        new DatastoreDeleteDeferredTask(KeyFactory.createKey("yar", 23L)),
        new DatastorePutDeferredTask(new Entity(KeyFactory.createKey("yar", 23L))));
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return QueueFactory.class;
  }

  /** Instructions for generating new golden files are in the BUILD file in this directory. */
  public static void main(String[] args) throws IOException {
    SerializationTest st = new SerializationTest();
    st.writeCanonicalObjects();
  }
}
