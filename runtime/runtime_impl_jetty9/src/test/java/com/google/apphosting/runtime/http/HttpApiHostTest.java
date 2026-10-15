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

package com.google.apphosting.runtime.http;

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.apphosting.testing.PortPicker;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for HTTP-based APIHost traffic. */
@RunWith(JUnit4.class)
public class HttpApiHostTest {
  private static final String ECHO_SERVICE = "echo";
  private static final String ECHO_METHOD = "Echo";

  private static FakeHttpApiHost fakeHttpApiHost;

  @BeforeClass
  public static void setUp() throws Exception {
    int port = PortPicker.create().pickUnusedPort();
    fakeHttpApiHost = FakeHttpApiHost.create(port, new EchoHandler());
  }

  @AfterClass
  public static void tearDown() {
    fakeHttpApiHost.stop();
  }

  private static class EchoHandler implements FakeHttpApiHost.ApiRequestHandler {
    @Override
    public RemoteApiPb.Response handle(RemoteApiPb.Request request) {
      if (!request.getServiceName().equals(ECHO_SERVICE)
          || !request.getMethod().equals(ECHO_METHOD)) {
        throw new IllegalArgumentException("Unexpected request: " + request);
      }
      ByteString payload = request.getRequest();
      return RemoteApiPb.Response.newBuilder().setResponse(payload).build();
    }
  }

  @Test
  public void echo() throws Exception {
    ByteString payload = ByteString.copyFrom(new byte[] {1, 2, 3, 4});
    RemoteApiPb.Request requestPb =
        RemoteApiPb.Request.newBuilder()
            .setServiceName(ECHO_SERVICE)
            .setMethod(ECHO_METHOD)
            .setRequest(payload)
            .build();
    URL httpApiHostUrl = fakeHttpApiHost.getUrl();
    HttpURLConnection urlConnection = (HttpURLConnection) httpApiHostUrl.openConnection();
    urlConnection.setDoOutput(true);
    urlConnection.setRequestMethod("POST");
    urlConnection.setRequestProperty(
        FakeHttpApiHost.RPC_ENDPOINT_HEADER, FakeHttpApiHost.RPC_ENDPOINT_VALUE);
    urlConnection.setRequestProperty(
        FakeHttpApiHost.RPC_METHOD_HEADER, FakeHttpApiHost.RPC_METHOD_VALUE);
    urlConnection.setRequestProperty(
        FakeHttpApiHost.CONTENT_TYPE_HEADER, FakeHttpApiHost.CONTENT_TYPE_VALUE);
    urlConnection.setRequestProperty(FakeHttpApiHost.DEADLINE_HEADER, "60.0");
    try (OutputStream out = urlConnection.getOutputStream()) {
      requestPb.writeTo(out);
    }
    RemoteApiPb.Response responsePb;
    try (InputStream in = urlConnection.getInputStream()) {
      responsePb = RemoteApiPb.Response.parseFrom(in, ExtensionRegistry.getEmptyRegistry());
      assertThat(in.read()).isLessThan(0);
    }
    assertThat(responsePb.getResponse()).isEqualTo(payload);
    assertThat(urlConnection.getHeaderField("Content-Type")).isEqualTo("application/octet-stream");
  }
}
