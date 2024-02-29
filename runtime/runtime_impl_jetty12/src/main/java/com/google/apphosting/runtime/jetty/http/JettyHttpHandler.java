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

package com.google.apphosting.runtime.jetty.http;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.JettyConstants;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import static com.google.apphosting.runtime.jetty.AppEngineHeaders.PRIVATE_APPENGINE_HEADERS;
import static com.google.apphosting.runtime.jetty.AppEngineHeaders.X_APPENGINE_HTTPS;
import static com.google.apphosting.runtime.jetty.AppEngineHeaders.X_APPENGINE_QUEUENAME;
import static com.google.apphosting.runtime.jetty.AppEngineHeaders.X_FORWARDED_PROTO;
import static com.google.apphosting.runtime.jetty.AppEngineHeaders.X_GOOGLE_INTERNAL_SKIPADMINCHECK;

public class JettyHttpHandler extends Handler.Wrapper {

  private static final String SKIP_ADMIN_CHECK_ATTR =
          "com.google.apphosting.internal.SkipAdminCheck";
  private static final String IS_TRUSTED = "1";

  private final boolean passThroughPrivateHeaders;
  private final AppVersionKey appVersionKey;

  public JettyHttpHandler(ServletEngineAdapter.Config runtimeOptions, AppVersionKey appVersionKey)
  {
    this.passThroughPrivateHeaders = runtimeOptions.passThroughPrivateHeaders();
    this.appVersionKey = appVersionKey;
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {

    HttpURI httpURI;
    boolean isSecure;
    if (requestIsHttps(request)) {
      httpURI = HttpURI.build(request.getHttpURI()).scheme(HttpScheme.HTTPS);
      isSecure = true;
    }
    else {
      httpURI = request.getHttpURI();
      isSecure = request.isSecure();
    }

    // Filter private headers defined in
    if (skipAdminCheck(request)) {
      request.setAttribute(SKIP_ADMIN_CHECK_ATTR, true);
    }

    HttpFields headers = allowedRequestHeader(request.getHeaders());
    request = new Request.Wrapper(request)
    {
      @Override
      public HttpURI getHttpURI() {
        return httpURI;
      }

      @Override
      public boolean isSecure() {
        return isSecure;
      }

      @Override
      public HttpFields getHeaders() {
        return headers;
      }
    };

    // TODO: set the environment.
    request.setAttribute(JettyConstants.APP_VERSION_KEY_REQUEST_ATTR, appVersionKey);
    return super.handle(request, response, callback);
  }


  /**
   * Determine if the request came from within App Engine via secure internal channels.
   *
   * <p>We round such cases up to "using https" to satisfy Jetty's transport-guarantee checks.
   */
  static boolean requestIsHttps(Request request) {
    HttpFields headers = request.getHeaders();
    if ("on".equals(headers.get(X_APPENGINE_HTTPS))) {
      return true;
    }

    if ("https".equals(headers.get(X_FORWARDED_PROTO))) {
      return true;
    }

    return headers.get(X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null;
  }

  static boolean skipAdminCheck(Request request) {
    HttpFields headers = request.getHeaders();
    if (headers.get(X_GOOGLE_INTERNAL_SKIPADMINCHECK) != null) {
      return true;
    }

    return headers.get(X_APPENGINE_QUEUENAME) != null;
  }

  private HttpFields allowedRequestHeader(HttpFields headers) {

    HttpFields.Mutable modifiedHeaders = HttpFields.build();
    for (HttpField field : headers) {
      String value = field.getValue();
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }

      String name = Ascii.toLowerCase(field.getName());
      if (passThroughPrivateHeaders || !PRIVATE_APPENGINE_HEADERS.contains(name)) {
        // Only non AppEngine specific headers are passed to the application.
        modifiedHeaders.put(field);
      }
    }
    return modifiedHeaders;
  }
}
