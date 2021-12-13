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

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

/**
 * {@link ClientLogin} implementation for use inside a standalone Java app. We use {@link
 * HttpClient} to issue HTTP requests.
 *
 */
class StandaloneClientLogin extends ClientLogin {
  private static final int MAX_RESPONSE_SIZE = 1024 * 1024;

  StandaloneClientLogin() {}

  @Override
  PostResponse executePost(String url, List<String[]> postParams) throws IOException {
    HttpPost post = new HttpPost(url);
    List<NameValuePair> params =
        postParams.stream()
            .map(strings -> new BasicNameValuePair(strings[0], strings[1]))
            .collect(toList());
    post.setEntity(new UrlEncodedFormEntity(params));
    HttpClient client = HttpClientBuilder.create().build();
    HttpResponse response = client.execute(post);
    byte[] responseBytes = StandaloneAppEngineClient.responseBytes(response, MAX_RESPONSE_SIZE);
    Charset charset = ContentType.get(response.getEntity()).getCharset();
    String body = new String(responseBytes, charset);
    return new PostResponse(response.getStatusLine().getStatusCode(), body);
  }

  @Override
  List<Cookie> getAppEngineLoginCookies(String url) throws IOException {
    HttpGet get = new HttpGet(url);
    CookieStore cookieStore = new BasicCookieStore();
    HttpClient client =
        HttpClientBuilder.create()
            .setDefaultCookieStore(cookieStore)
            .disableRedirectHandling()
            .build();
    HttpResponse response = client.execute(get);

    if (response.getStatusLine().getStatusCode() == 302) {
      return new ArrayList<>(cookieStore.getCookies());
    } else {
      throw new LoginException("unexpected response from app engine: " + response.getStatusLine());
    }
  }
}
