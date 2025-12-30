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

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.cookie.DefaultCookieSpec;
import org.apache.http.message.BasicHeader;

/**
 * {@link ClientLogin} implementation for use inside an App Engine container.
 * We use {@link URLFetchService} to issue HTTP requests since that's the only
 * way to do it from inside App Engine.
 *
 */
public class HostedClientLogin extends ClientLogin {

  private final URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

  @Override
  PostResponse executePost(String urlStr, List<String[]> postParams) throws IOException {
    URL url = new URL(urlStr);
    StringBuilder payload = new StringBuilder();
    for (String[] param : postParams) {
      payload.append(String.format("%s=%s&", param[0], param[1]));
    }
    payload.setLength(payload.length() - 1);
    HTTPRequest req = new HTTPRequest(url, HTTPMethod.POST);
    req.setPayload(payload.toString().getBytes());
    HTTPResponse resp = fetchService.fetch(req);
    HostedAppEngineClient.Response response =
        HostedAppEngineClient.createResponse(resp);
    return new PostResponse(resp.getResponseCode(), response.getBodyAsString());
  }

  @Override
  List<Cookie> getAppEngineLoginCookies(String urlStr) throws IOException {
    FetchOptions fetchOptions = FetchOptions.Builder.doNotFollowRedirects();
    URL url = new URL(urlStr);
    HTTPRequest req = new HTTPRequest(url, HTTPMethod.GET, fetchOptions);
    HTTPResponse resp = fetchService.fetch(req);
    if (resp.getResponseCode() != 302) {
      throw new LoginException("unexpected response from app engine: " + resp.getResponseCode());
    }
    List<Cookie> cookies = new ArrayList<>();
    for (HTTPHeader header : resp.getHeaders()) {
      if (header.getName().toLowerCase().equals("set-cookie")) {
        // We'll get a little help from HttpClient to parse the cookie
        // headers.
        CookieSpec spec = new DefaultCookieSpec();
        CookieOrigin cookieOrigin = new CookieOrigin(
            url.getHost(), url.getPort() == -1 ? 0 : url.getPort(), url.getPath(),
                url.getProtocol().equals("https"));
        Header h = new BasicHeader(header.getName(), header.getValue());
        try {
          cookies.addAll(spec.parse(h, cookieOrigin));
        } catch (MalformedCookieException e) {
          throw new IOException(e);
        }
      }
    }
    return cookies;
  }
}
