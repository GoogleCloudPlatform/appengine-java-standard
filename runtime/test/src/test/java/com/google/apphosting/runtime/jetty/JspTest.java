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

package com.google.apphosting.runtime.jetty;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JspTest extends JavaRuntimeViaHttpBase {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void copyAppToTemp() throws IOException {
    copyAppToDir("jspexample", temp.getRoot().toPath());
  }

  /** Tests that an app that includes a JSP-compiled class executes correctly. */
  @Test
  public void jspWithSessions() throws Exception {
    testJspWithSessions(false);
  }

  /**
   * Tests that an app that includes a JSP-compiled class executes correctly when accessed through
   * an https URL.
   */
  @Test
  public void jspWithSessionsAndHttps() throws Exception {
    testJspWithSessions(true);
  }

  private void testJspWithSessions(boolean https) throws IOException, InterruptedException {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      HttpClient httpClient = runtime.getHttpClient();

      String url = runtime.jettyUrl("/");
      HttpGet get = new HttpGet(url);
      if (https) {
        get.addHeader("X-AppEngine-Https", "on");
      }
      HttpResponse response = httpClient.execute(get);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(RESPONSE_200);
    }
  }

  private RuntimeContext<?> runtimeContext() throws IOException, InterruptedException {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }
}
