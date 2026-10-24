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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests HTTP sessions with async session persistence when the Task Queue API is routed to Cloud
 * Tasks via {@code APPENGINE_USE_CLOUDTASK_PUSH_QUEUE}.
 *
 * <p>With async persistence, session writes are enqueued by the runtime itself (see {@code
 * DeferredDatastoreSessionStore}), from the runtime's private class loader rather than from the
 * application. The runtime jar must therefore contain the Cloud Tasks client and all of its
 * dependencies, not only the application's {@code appengine-api-1.0-sdk} jar.
 */
@RunWith(Parameterized.class)
public final class CloudTasksSessionTest extends JavaRuntimeViaHttpBase {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

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

  @Before
  public void startRuntime() throws Exception {
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
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder(apiServerFactory)
            .setApplicationPath(appRoot.toString())
            .setEnvironmentEntries(
                ImmutableMap.of(
                    "GAE_VERSION", "allinone",
                    "GOOGLE_CLOUD_PROJECT", "1",
                    "APPENGINE_USE_CLOUDTASK_PUSH_QUEUE", "true"))
            .build();
    runtime = createRuntimeContext(config);
  }

  @After
  public void close() throws IOException {
    runtime.close();
  }

  @Test
  public void sessionWithAsyncPersistenceUsingCloudTasks() throws Exception {
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
