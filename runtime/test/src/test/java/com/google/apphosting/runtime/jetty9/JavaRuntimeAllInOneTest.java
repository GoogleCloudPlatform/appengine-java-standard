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

package com.google.apphosting.runtime.jetty9;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toMap;

import com.google.appengine.tools.development.HttpApiServer;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JavaRuntimeAllInOneTest extends JavaRuntimeViaHttpBase {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private static final int NUMBER_OF_RETRIES = 5;

  private RuntimeContext<?> runtime;

  @Before
  public void startRuntime() throws Exception {
    copyAppToDir("com/google/apphosting/loadtesting/allinone", temp.getRoot().toPath());
    ApiServerFactory<HttpApiServer> apiServerFactory =
        (apiPort, runtimePort) -> {
          HttpApiServer httpApiServer = new HttpApiServer(apiPort, "localhost", runtimePort);
          httpApiServer.start(false);
          return httpApiServer;
        };
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder(apiServerFactory)
            .setApplicationPath(temp.getRoot().toString())
            .setEnvironmentEntries(
                ImmutableMap.of("GAE_VERSION", "allinone", "GOOGLE_CLOUD_PROJECT", "1"))
            .build();
    runtime = RuntimeContext.create(config);
  }

  @After
  public void close() throws IOException {
    runtime.close();
  }

  @Test
  public void invokeServletCallingDatastoresUsingJettyHttpProxy() throws Exception {
    // App Engine Datastore access.
    runtime.executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);
    runtime.executeHttpGet("/?datastore_count", "Found 3 entities\n", RESPONSE_200);
    runtime.executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);
    runtime.executeHttpGet("/?datastore_count", "Found 6 entities\n", RESPONSE_200);
    runtime.executeHttpGet("/?datastore_cron", "Persisted log entry\n", RESPONSE_200);
  }

  @Test
  public void invokeServletCallingMemcachesUsingJettyHttpProxy() throws Exception {
    // App Engine Memcache access.
    runtime.executeHttpGet(
        "/?memcache_loops=10&memcache_size=10",
        "Running memcache for 10 loops with value size 10\n"
            + "Cache hits: 10\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    runtime.executeHttpGet(
        "/?memcache_loops=10&memcache_size=10",
        "Running memcache for 10 loops with value size 10\n"
            + "Cache hits: 20\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    runtime.executeHttpGet(
        "/?memcache_loops=5&memcache_size=10",
        "Running memcache for 5 loops with value size 10\n"
            + "Cache hits: 25\n"
            + "Cache misses: 0\n",
        RESPONSE_200);
  }

  @Test
  public void invokeServletCallingUserApisUsingJettyHttpProxy() throws Exception {
    // App Engine User API access.
    runtime.executeHttpGet("/?user", "Sign in with /_ah/login?continue=%2F\n", RESPONSE_200);
  }

  @Test
  public void invokeDeferredTask() throws Exception {
    // Post the deferred task.
    runtime.executeHttpGet("/?deferred_task", "Adding a deferred task...\n", RESPONSE_200);
    // Verify the runnable of the task has been called back.
    runtime.executeHttpGet("/?deferred_task_verify", "Verify deferred task: true\n", RESPONSE_200);
  }

  @Test
  public void invokeServletCallingTaskqueuesUsingJettyHttpProxy() throws Exception {
    // First, populate Datastore entities
    runtime.executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);

    // App Engine Taskqueue usage, queuing the addition of 7 entities.
    runtime.executeHttpGet(
        "/?add_tasks=1&task_url=/?datastore_entities=7",
        "Adding 1 tasks for URL /?datastore_entities=7\n",
        RESPONSE_200);

    // After a while, we should have 10 entities.
    runtime.executeHttpGetWithRetries(
        "/?datastore_count", "Found 10 entities\n", RESPONSE_200, NUMBER_OF_RETRIES);
  }

  @Test
  public void servletAttributes() throws Exception {
    /* TODO: re-enable and fix
    // Send a request that should be forwarded. The forwarded request will set some servlet
    // attributes, then list each servlet attribute on a line of its own like {@code foo = bar}.
    // So we decode those lines and ensure that the attributes we set are listed.
    // The forwarding is needed to tickle b/169727154.
    String response =
        runtime
            .executeHttpGet("/?forward=set_servlet_attributes=foo=bar:baz=buh", RESPONSE_200)
            .trim();
    Splitter eq = Splitter.on('=');
    Map<String, String> attributes =
        Splitter.on('\n')
            .splitToStream(response)
            .map(eq::splitToList)
            .collect(toMap(list -> list.get(0).trim(), list -> list.get(1).trim()));
    // Because the request is forwarded, it acquires these javax.servlet.forward attributes.
    // (They are specified by constants in javax.servlet.RequestDispatcher, but using those runs
    // into hassles with Servlet API 2.5 vs 3.1.)
    // The "forwarded" attribute is set by our servlet and the APP_VERSION_KEY_REQUEST_ATTR one is
    // set by our infrastructure.
    assertThat(attributes)
        .containsExactly(
            "foo", "bar",
            "baz", "buh",
            "forwarded", "true",
            "javax.servlet.forward.query_string", "forward",
            "javax.servlet.forward.request_uri", "/",
            "javax.servlet.forward.servlet_path", "/",
            "javax.servlet.forward.context_path", "",
            "com.google.apphosting.runtime.jetty9.APP_VERSION_REQUEST_ATTR", "s~testapp/allinone");
     */
  }
}
