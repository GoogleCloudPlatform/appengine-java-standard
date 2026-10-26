/*
 * Copyright 2026 Google LLC
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
package com.google.apphosting.runtime.jetty;

import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.development.HttpApiServer;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests HTTP sessions with async session persistence across both the default {@code ApiProxy} Task
 * Queue path ({@code APPENGINE_USE_CLOUDTASK_PUSH_QUEUE} unset) and the Cloud Tasks client path
 * ({@code APPENGINE_USE_CLOUDTASK_PUSH_QUEUE=true}).
 *
 * <p>With async persistence, session writes are enqueued by the runtime itself (see {@code
 * DeferredDatastoreSessionStore}), from the runtime's private class loader rather than from the
 * application. When {@code APPENGINE_USE_CLOUDTASK_PUSH_QUEUE} is unset, {@code QueueImpl} must
 * route through {@code ApiProxy} without loading {@code CloudTasksClientWrapper}; when enabled, the
 * runtime jar must contain the Cloud Tasks client and all of its dependencies, not only the
 * application's {@code appengine-api-1.0-sdk} jar.
 */
@RunWith(Parameterized.class)
public final class CloudTasksSessionTest extends JavaRuntimeViaHttpBase {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private static final int NUMBER_OF_RETRIES = 5;

  private static final Pattern CLASS_LOADING_ERROR =
      Pattern.compile("ClassNotFoundException|NoClassDefFoundError");

  private RuntimeContext<?> runtime;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  public CloudTasksSessionTest(String runtimeVersion, String jettyVersion, String jakartaVersion) {
    super(runtimeVersion, jettyVersion, jakartaVersion);
  }

  private void startRuntime(ImmutableMap<String, String> extraEnvironmentEntries) throws Exception {
    Path appRoot = temp.getRoot().toPath();
    if (isJakarta()) {
      copyAppToDir("com/google/apphosting/loadtesting/allinone/ee10", appRoot);
    } else {
      copyAppToDir("com/google/apphosting/loadtesting/allinone", appRoot);
    }
    enableAsyncSessionPersistence(appRoot.resolve("WEB-INF/appengine-web.xml"));
    ApiServerFactory<HttpApiServer> apiServerFactory =
        (apiPort, runtimePort) -> {
          HttpApiServer httpApiServer = new HttpApiServer(apiPort, "localhost", runtimePort);
          httpApiServer.start(false);
          return httpApiServer;
        };
    ImmutableMap<String, String> environmentEntries =
        ImmutableMap.<String, String>builder()
            .put("GAE_VERSION", "allinone")
            .put("GOOGLE_CLOUD_PROJECT", "1")
            .putAll(extraEnvironmentEntries)
            .buildOrThrow();
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder(apiServerFactory)
            .setApplicationPath(appRoot.toString())
            .setEnvironmentEntries(environmentEntries)
            .build();
    runtime = createRuntimeContext(config);
  }

  @After
  public void close() throws IOException {
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  public void sessionWithAsyncPersistenceUsingDefaultTaskQueue() throws Exception {
    startRuntime(ImmutableMap.of());
    // Storing the session makes the runtime enqueue a deferred datastore write from the runtime's
    // private classloader via QueueImpl while APPENGINE_USE_CLOUDTASK_PUSH_QUEUE is unset.
    runtime.executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);
    // Also enqueue and execute a push task from the application's appengine-api-1.0-sdk jar.
    runtime.executeHttpGet(
        "/?add_tasks=1&task_url=/?datastore_entities=7",
        "Adding 1 tasks for URL /?datastore_entities=7\n",
        RESPONSE_200);
    // ?datastore_count verifies that the HTTP session attribute "storedsession" persisted across
    // requests and that the push task added the remaining 7 datastore entities (3 + 7 = 10).
    runtime.executeHttpGetWithRetries(
        "/?datastore_count", "Found 10 entities\n", RESPONSE_200, NUMBER_OF_RETRIES);
    assertWithMessage("Runtime output should have no class loading errors")
        .that(runtime.outputLinesContaining(CLASS_LOADING_ERROR))
        .isEmpty();
  }

  @Test
  public void sessionWithAsyncPersistenceUsingCloudTasks() throws Exception {
    startRuntime(ImmutableMap.of("APPENGINE_USE_CLOUDTASK_PUSH_QUEUE", "true"));
    // Storing the session makes the runtime enqueue a deferred datastore write through
    // QueueImpl, which routes it to the Cloud Tasks client. If any class of that client is missing
    // from the runtime jar, the request fails with a NoClassDefFoundError.
    runtime.executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);
    // There is no Cloud Tasks backend in this test, so the deferred write itself cannot succeed and
    // the session content is not checked here; the request must still be served.
    runtime.executeHttpGet("/?datastore_count", RESPONSE_200);
    assertWithMessage("Runtime output should have no class loading errors")
        .that(runtime.outputLinesContaining(CLASS_LOADING_ERROR))
        .isEmpty();
  }

  private static void enableAsyncSessionPersistence(Path appEngineWebXml) throws IOException {
    String xml = Files.readString(appEngineWebXml, UTF_8);
    String sessionsEnabled = "<sessions-enabled>true</sessions-enabled>";
    assertWithMessage("%s should enable sessions", appEngineWebXml)
        .that(xml)
        .contains(sessionsEnabled);
    Files.writeString(
        appEngineWebXml,
        xml.replace(
            sessionsEnabled,
            sessionsEnabled + "\n  <async-session-persistence enabled=\"true\" />"),
        UTF_8);
  }
}
