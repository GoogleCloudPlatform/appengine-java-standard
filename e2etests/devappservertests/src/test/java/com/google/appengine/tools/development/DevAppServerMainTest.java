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
package com.google.appengine.tools.development;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.net.HostAndPort;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DevAppServerMainTest extends DevAppServerTestBase {

  public DevAppServerMainTest(String runtimeVersion, String jettyVersion, String jakartaVersion) {
    super(runtimeVersion, jettyVersion, jakartaVersion);
  }

  @Before
  public void setUpClass() throws IOException, InterruptedException {
    File appDir =
        Boolean.getBoolean("appengine.use.EE10") || Boolean.getBoolean("appengine.use.EE11")
            ? createApp("allinone_jakarta")
            : createApp("allinone");
    setUpClass(appDir);
  }

  @Test
  public void globaltest() throws Exception {
    // App Engine Memcache access.
    executeHttpGet(
        "/?memcache_loops=10&memcache_size=10",
        "Running memcache for 10 loops with value size 10\n"
            + "Cache hits: 10\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    executeHttpGet(
        "/?memcache_loops=10&memcache_size=10",
        "Running memcache for 10 loops with value size 10\n"
            + "Cache hits: 20\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    executeHttpGet(
        "/?memcache_loops=5&memcache_size=10",
        "Running memcache for 5 loops with value size 10\n"
            + "Cache hits: 25\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    // App Engine User API access.
    executeHttpGet("/?user", "Sign in with /_ah/login?continue=%2F\n", RESPONSE_200);

    // First, populate Datastore entities
    executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);

    // App Engine Taskqueue usage, queuing the addition of 7 entities.
    executeHttpGet(
        "/?add_tasks=1&task_url=/?datastore_entities=7",
        "Adding 1 tasks for URL /?datastore_entities=7\n",
        RESPONSE_200);

    // After a while, we should have 10 or more entities.
    executeHttpGetWithRetriesContains(
        "/?datastore_count", "Found ", RESPONSE_200, NUMBER_OF_RETRIES);

    HttpGet get =
        new HttpGet(
            String.format(
                "http://%s%s",
                HostAndPort.fromParts(new InetSocketAddress(jettyPort).getHostString(), jettyPort),
                "/_ah/admin/search"));
    String content;
    HttpResponse response = httpClient.execute(get);
    int retCode = response.getStatusLine().getStatusCode();
    content = EntityUtils.toString(response.getEntity());

    assertThat(content).contains("There are no Full Text Search indexes in the Empty namespace");
    assertThat(content)
        .contains(
            " <li><a href=\"/_ah/admin/datastore\" id=\"datastore_viewer_link\">Datastore"
                + " Viewer</a></li>");
    assertThat(retCode).isEqualTo(RESPONSE_200);
  }
}
