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

package com.google.appengine.api.urlfetch.dev;

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError;
import com.google.apphosting.api.ApiProxy;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * A {@code DefaultRedirectStrategy} that accepts redirects for 301,302,303,307 status
 * codes from all methods. getRedirect in DefaultRedirectStrategy correctly 
 * takes care of making the redirects to use correct methods for different status codes.
 */
public class AllMethodsRedirectStrategy extends DefaultRedirectStrategy {
  @Override
  public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
    if (response == null) {
      throw new NullPointerException("HTTP response may not be null");
    }

    int statusCode = response.getStatusLine().getStatusCode();
    switch (statusCode) {
      case HttpStatus.SC_MOVED_TEMPORARILY:
      case HttpStatus.SC_MOVED_PERMANENTLY:
      case HttpStatus.SC_TEMPORARY_REDIRECT:
      case HttpStatus.SC_SEE_OTHER:
        Header location = response.getFirstHeader("location");
        if (location == null) {
          throw new ApiProxy.ApplicationException(
            URLFetchServiceError.ErrorCode.MALFORMED_REPLY_VALUE,
            "Missing \"Location\" header for redirect.");
        }
        return true;
      default:
        return false;
    }
  }
}
