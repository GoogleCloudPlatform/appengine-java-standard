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

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(JUnit4.class)
public class SizeLimitHandlerTest extends JavaRuntimeViaHttpBase {

  private static final int MAX_SIZE = 32 * 1024 * 1024;
  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final org.eclipse.jetty.client.HttpClient httpClient = new org.eclipse.jetty.client.HttpClient();

  @Before
  public void before() throws Exception {
    copyAppToDir("sizedresponseapp", temp.getRoot().toPath());
    httpClient.start();
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
  }

  @Test
  public void testResponseBody() throws Exception {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      String url = runtime.jettyUrl("/?size=" + (MAX_SIZE + 1));
      ContentResponse response = httpClient.GET(url);

      String responseContent = response.getContentAsString();
      assertThat(response.getStatus(), equalTo(500));
      assertThat(responseContent, containsString("Response body is too large"));
    }
  }

  @Test
  public void testRequestBody() throws Exception {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      String url = runtime.jettyUrl("/");

      CompletableFuture<Response> responseFuture = new CompletableFuture<>();
      Utf8StringBuilder responseContent = new Utf8StringBuilder();
      httpClient.POST(url)
              .body(getContent(MAX_SIZE + 1))
              .onResponseContent((response, content) -> responseContent.append(content))
              .send(result -> responseFuture.complete(result.getResponse()));

      Response response = responseFuture.get(5, TimeUnit.SECONDS);
      assertThat(response.getStatus(), equalTo(413));
      assertThat(responseContent.toCompleteString(), containsString("Request body is too large"));
    }
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
            RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }

  public static Request.Content getContent(int size)
  {
    StringBuilder stringBuilder = new StringBuilder(size);
    for (int i = 0; i < size; i++)
    {
      stringBuilder.append('x');
    }
    return new StringRequestContent(stringBuilder.toString());
  }
}
