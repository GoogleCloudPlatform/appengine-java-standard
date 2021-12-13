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

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A client that uses OAuth for authentication. Both on- and off-App Engine
 * use cases are supported via the HttpTransport object provided via
 * {@link RemoteApiOptions}.
 *
 * <p>This class is thread-safe.
 */
class OAuthClient extends BaseRemoteApiClient {

  private final HttpRequestFactory requestFactory;

  OAuthClient(RemoteApiOptions options, String appId) {
    super(options, appId);
    if (options.getOAuthCredential() == null) {
      throw new IllegalArgumentException("options does not contain an OAuth credential");
    }
    if (options.getHttpTransport() == null) {
      // RemoteApiOptions should ensure that if the OAuth credential is set,
      // then so is the HttpTransport.
      throw new IllegalStateException("options does not contain an HttpTransport");
    }
    requestFactory =
        options.getHttpTransport().createRequestFactory(options.getOAuthCredential());
  }

  @Override
  public String serializeCredentials() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AppEngineClient.Response post(String path, String mimeType, byte[] body)
      throws IOException {
    HttpContent content = new ByteArrayHttpContent(mimeType, body);
    HttpRequest request = requestFactory.buildPostRequest(resolveUrl(path), content);
    addBaseHeaders(request);
    // Don't redirect to a login page if authentication fails. We will report
    // the 302 and it will be handled as an error.
    request.setFollowRedirects(false);
    return toResponse(request.execute());
  }

  @Override
  public AppEngineClient.Response get(String path) throws IOException {
    HttpRequest request = requestFactory.buildGetRequest(resolveUrl(path));
    addBaseHeaders(request);
    // Don't redirect to a login page if authentication fails. We will report
    // the 302 and it will be handled as an error.
    request.setFollowRedirects(false);
    return toResponse(request.execute());
  }

  private void addBaseHeaders(HttpRequest request) {
    HttpHeaders headers = request.getHeaders();
    for (String[] nameAndValue : getHeadersBase()) {
      headers.put(nameAndValue[0], nameAndValue[1]);
    }
    getHeadersBase();
  }

  private GenericUrl resolveUrl(String path) {
    return new GenericUrl(makeUrl(path));
  }

  private AppEngineClient.Response toResponse(HttpResponse httpResponse) throws IOException {
    byte[] body = (httpResponse.getContent() == null)
        ? null
        : ByteStreams.toByteArray(httpResponse.getContent());
    return new AppEngineClient.Response(httpResponse.getStatusCode(), body,
        httpResponse.getContentCharset());
  }

  /**
   * Simple adapter between an byte array and an {@link HttpContent}.
   */
  private static class ByteArrayHttpContent extends AbstractHttpContent {

    private final byte[] body;
    
    private ByteArrayHttpContent(String mediaType, byte[] body) {
      super(mediaType);
      this.body = body;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
      out.write(body);
    }
  }
}
