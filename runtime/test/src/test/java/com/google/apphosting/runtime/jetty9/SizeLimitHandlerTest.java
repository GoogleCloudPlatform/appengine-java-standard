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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SizeLimitHandlerTest extends JavaRuntimeViaHttpBase {

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void copyAppToTemp() throws Exception {
    copyAppToDir("sizelimithandlerapp", temp.getRoot().toPath());
  }

  @Test
  public void testSizeLimitHandler() throws Exception {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      HttpClient httpClient = runtime.getHttpClient();
      String url = runtime.jettyUrl("/?size=10");
      HttpGet get = new HttpGet(url);
      HttpResponse response = httpClient.execute(get);

      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(RESPONSE_200);
      String responseContent = EntityUtils.toString(response.getEntity());
      assertThat(responseContent).isEqualTo("cookieTestServletContent");
    }
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
            RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }
}
