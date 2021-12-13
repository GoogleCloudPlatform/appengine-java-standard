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

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;

/**
 * An {@link AppEngineClient} implementation that uses {@link URLFetchService}.
 * This implementation must be used when the client is an App Engine container
 * since URLFetchService is the only way to make HTTP requests in this
 * environment.
 *
 */
class HostedAppEngineClient extends AppEngineClient {

  private final URLFetchService urlFetch = URLFetchServiceFactory.getURLFetchService();

  HostedAppEngineClient(RemoteApiOptions options, List<Cookie> authCookies,
      String appId) {
    super(options, authCookies, appId);
  }

  private void addCookies(HTTPRequest req) {
    for (Cookie cookie : getAuthCookies()) {
      req.addHeader(
          new HTTPHeader("Cookie", String.format("%s=%s", cookie.getName(), cookie.getValue())));
    }
  }

  @Override
  public Response get(String path) throws IOException {
    return createResponse(doGet(path));
  }

  private HTTPResponse doGet(String path) throws IOException {
    HTTPRequest req = new HTTPRequest(new URL(makeUrl(path)), HTTPMethod.GET);
    req.getFetchOptions().doNotFollowRedirects();
    for (String[] headerPair : getHeadersForGet()) {
      req.addHeader(new HTTPHeader(headerPair[0], headerPair[1]));
    }
    addCookies(req);
    return urlFetch.fetch(req);
  }

  @Override
  public Response post(String path, String mimeType, byte[] body) throws IOException {
    return createResponse(doPost(path, mimeType, body));
  }

  private HTTPResponse doPost(String path, String mimeType, byte[] body)
      throws IOException {
    HTTPRequest req = new HTTPRequest(new URL(makeUrl(path)), HTTPMethod.POST);
    req.getFetchOptions().doNotFollowRedirects();
    for (String[] headerPair : getHeadersForPost(mimeType)) {
      req.addHeader(new HTTPHeader(headerPair[0], headerPair[1]));
    }
    addCookies(req);
    req.setPayload(body);
    return urlFetch.fetch(req);
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

  static Response createResponse(HTTPResponse resp) {
    return new Response(resp.getResponseCode(), resp.getContent(), getCharset(resp));
  }

  static LegacyResponse createLegacyResponse(HTTPResponse resp) {
    return new LegacyResponse(resp.getResponseCode(), resp.getContent(), getCharset(resp));
  }

  static Charset getCharset(HTTPResponse resp) {
    for (HTTPHeader header : resp.getHeaders()) {
      if (header.getName().toLowerCase().equals("content-type")) {
        ContentType contentType = ContentType.parse(header.getValue());
        return contentType.getCharset();
      }
    }
    return null;
  }

}
