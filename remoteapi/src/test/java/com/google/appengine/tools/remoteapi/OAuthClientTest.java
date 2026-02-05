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

package com.google.appengine.tools.remoteapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.appengine.tools.remoteapi.AppEngineClient.Response;
import com.google.appengine.tools.remoteapi.testing.StubCredential;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link OAuthClient}.
 */
@RunWith(JUnit4.class)
public class OAuthClientTest {

  private static final String APP_ID = "appid";

  private static class SimpleHttpTransport extends MockHttpTransport {
    private final byte[] responseBytes;

    private String lastMethod = null;
    private String lastUrl = null;

    SimpleHttpTransport(byte[] responseBytes) {
      this.responseBytes = responseBytes;
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      lastMethod = method;
      lastUrl = url;
      return new MockLowLevelHttpRequest() {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
          response.setContent(Arrays.copyOf(responseBytes, responseBytes.length));
          return response;
        }
      };
    }

    String getLastMethod() {
      return lastMethod;
    }

    String getLastUrl() {
      return lastUrl;
    }
  }

  @Test
  public void testConstructor() {
    RemoteApiOptions noOAuthCredential = new RemoteApiOptions()
        .credentials("email", "password")
        .httpTransport(new SimpleHttpTransport(new byte[] {}));
    assertThrows(IllegalArgumentException.class, () -> new OAuthClient(noOAuthCredential, APP_ID));

    RemoteApiOptions noHttpTransport = new RemoteApiOptions()
        .oauthCredential(new StubCredential());
    assertThrows(IllegalStateException.class, () -> new OAuthClient(noHttpTransport, APP_ID));
  }

  @Test
  public void testGet() throws Exception {
    byte[] expectedResponseBytes = new byte[] {42};
    
    SimpleHttpTransport transport = new SimpleHttpTransport(expectedResponseBytes);
    RemoteApiOptions options = new RemoteApiOptions()
        .server("example.com", 8080)
        .oauthCredential(new StubCredential())
        .httpTransport(transport);

    Response response = new OAuthClient(options, APP_ID)
        .get("/foo");
    assertTrue(Arrays.equals(expectedResponseBytes, response.getBodyAsBytes()));
    assertEquals("GET", transport.getLastMethod());
    assertEquals("http://example.com:8080/foo", transport.getLastUrl());
  }

  @Test
  public void testPost() throws Exception {
    byte[] expectedResponseBytes = new byte[] {42};

    SimpleHttpTransport transport = new SimpleHttpTransport(expectedResponseBytes);
    RemoteApiOptions options = new RemoteApiOptions()
        .server("example.com", 443)
        .oauthCredential(new StubCredential())
        .httpTransport(transport);

    Response response = new OAuthClient(options, APP_ID)
        .post("/foo", "application/json", new byte[]{1});
    assertTrue(Arrays.equals(expectedResponseBytes, response.getBodyAsBytes()));
    assertEquals("POST", transport.getLastMethod());
    assertEquals("https://example.com:443/foo", transport.getLastUrl());
  }
}
