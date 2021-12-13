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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * An {@link AppEngineClient} implementation that uses apache's
 * {@link HttpClient}.  This implementation must be used when the client is
 * not an App Engine container, since it cannot not rely on the availability of
 * the local urlfetch service.
 *
 */
class StandaloneAppEngineClient extends AppEngineClient {
  private final HttpClient httpClient;

  StandaloneAppEngineClient(RemoteApiOptions options, List<Cookie> authCookies, String appId) {
    super(options, authCookies, appId);
    CookieStore cookieStore = new BasicCookieStore();
    for (Cookie cookie : authCookies) {
      cookieStore.addCookie(cookie);
    }
    HttpClient httpClient = HttpClientBuilder.create()
        // Don't redirect to a login page if authentication fails. We will report the 302 and it
        // will be handled as an error.
        .disableRedirectHandling()
        .setConnectionManager(new PoolingHttpClientConnectionManager())
        .setDefaultCookieStore(cookieStore)
        .build();
    this.httpClient = httpClient;
  }

  @Override
  public Response get(String path) throws IOException {
    return createResponse(doGet(path));
  }

  private HttpResponse doGet(String path) throws IOException {
    HttpGet method = new HttpGet(makeUrl(path));

    addHeaders(method, getHeadersForGet());
    return httpClient.execute(method);
  }

  @Override
  public Response post(String path, String mimeType, byte[] body) throws IOException {
    return createResponse(doPost(path, mimeType, body));
  }

  private HttpResponse doPost(String path, String mimeType, byte[] body) throws IOException {
    HttpPost post = new HttpPost(makeUrl(path));

    addHeaders(post, getHeadersForPost(mimeType));
    post.setEntity(new ByteArrayEntity(body));
    return httpClient.execute(post);
  }

  @Override
  public LegacyResponse legacyGet(String path) throws IOException {
    return createLegacyResponse(doGet(path));
  }

  @Override
  public LegacyResponse legacyPost(String path, String mimeType, byte[] body)
      throws IOException {
    return createLegacyResponse(doPost(path, mimeType, body));
  }

  private void addHeaders(HttpRequestBase method, List<String[]> headers) {
    for (String[] headerPair : headers) {
      method.addHeader(headerPair[0], headerPair[1]);
    }
  }

  private Response createResponse(HttpResponse response) throws IOException {
    // Copy all data to make sure there are no I/O errors after returning from
    // AppEngineClient methods.
    byte[] body = responseBytes(response, getMaxResponseSize());
    return new Response(
        response.getStatusLine().getStatusCode(),
        body,
        ContentType.get(response.getEntity()).getCharset());
  }

  private LegacyResponse createLegacyResponse(HttpResponse response) throws IOException {
    // Copy all data to make sure there are no I/O errors after returning from
    // AppEngineClient methods.
    byte[] body = responseBytes(response, getMaxResponseSize());
    return new LegacyResponse(
        response.getStatusLine().getStatusCode(),
        body,
        ContentType.get(response.getEntity()).getCharset());
  }

  static byte[] responseBytes(HttpResponse response, int max) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    byte[] buf = new byte[65536];
    try (InputStream in = response.getEntity().getContent()) {
      int n;
      while (bout.size() < max
          && (n = in.read(buf, 0, Math.min(buf.length, max - bout.size()))) > 0) {
        bout.write(buf, 0, n);
      }
    }
    return bout.toByteArray();
  }
}
