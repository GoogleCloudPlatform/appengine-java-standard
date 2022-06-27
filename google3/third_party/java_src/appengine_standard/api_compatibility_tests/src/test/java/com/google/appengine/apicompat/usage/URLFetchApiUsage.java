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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.IURLFetchServiceFactory;
import com.google.appengine.api.urlfetch.IURLFetchServiceFactoryProvider;
import com.google.appengine.api.urlfetch.InternalTransientException;
import com.google.appengine.api.urlfetch.RequestPayloadTooLargeException;
import com.google.appengine.api.urlfetch.ResponseTooLargeException;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;

/** Exhaustive usage of the URLFetch Api. Used for backward compatibility checks. */
public class URLFetchApiUsage {

  /**
   * Exhaustive use of {@link URLFetchServiceFactory}.
   */
  public static class URLFetchServiceFactoryUsage
      extends ExhaustiveApiUsage<URLFetchServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      URLFetchServiceFactory unused = new URLFetchServiceFactory(); // TODO(maxr): deprecate
      URLFetchServiceFactory.getURLFetchService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IURLFetchServiceFactory}.
   */
  public static class IURLFetchServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IURLFetchServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IURLFetchServiceFactory iURLFetchServiceFactory) {
      iURLFetchServiceFactory.getURLFetchService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IURLFetchServiceFactoryProvider}.
   */
  public static class IURLFetchServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IURLFetchServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IURLFetchServiceFactoryProvider unused = new IURLFetchServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link URLFetchService}.
   */
  public static class URLFetchServiceUsage extends ExhaustiveApiInterfaceUsage<URLFetchService> {
    String ___apiConstant_DEFAULT_DEADLINE_PROPERTY;  // NOLINT
    String ___apiConstant_DEFAULT_TLS_VALIDATION_PROPERTY;  // NOLINT

    @Override
    protected Set<Class<?>> useApi(URLFetchService svc) {
      URL url = null;
      try {
        svc.fetch(url);
      } catch (IOException e) {
        // ok
      }
      HTTPRequest req = null;
      try {
        svc.fetch(req);
      } catch (IOException e) {
        // ok
      }
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError = svc.fetchAsync(url);
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError1 = svc.fetchAsync(req);
      ___apiConstant_DEFAULT_DEADLINE_PROPERTY = URLFetchService.DEFAULT_DEADLINE_PROPERTY;
      ___apiConstant_DEFAULT_TLS_VALIDATION_PROPERTY =
          URLFetchService.DEFAULT_TLS_VALIDATION_PROPERTY;
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link HTTPRequest}.
   */
  public static class HTTPRequestUsage extends ExhaustiveApiUsage<HTTPRequest> {

    @Override
    public Set<Class<?>> useApi() {
      URL url;
      try {
        url = new URL("http://www.google.com");
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      HTTPRequest req = new HTTPRequest(url);
      req = new HTTPRequest(url, HTTPMethod.PUT);
      req = new HTTPRequest(url, HTTPMethod.PUT, FetchOptions.Builder.withDefaults());
      HTTPHeader header = new HTTPHeader("this", "that");
      req.addHeader(header);
      req.setHeader(header);
      req.getFetchOptions();
      req.getHeaders();
      req.getMethod();
      req.getPayload();
      url = req.getURL();
      req.setPayload("bytes".getBytes(UTF_8));
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link HTTPResponse}.
   */
  public static class HTTPResponseUsage extends ExhaustiveApiUsage<HTTPResponse> {

    @Override
    public Set<Class<?>> useApi() {
      HTTPResponse resp = new HTTPResponse(3, null, null, Collections.<HTTPHeader>emptyList());
      resp.getContent();
      resp.getFinalUrl();
      resp.getHeaders();
      resp.getResponseCode();
      resp.getHeadersUncombined();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link HTTPMethod}.
   */
  public static class HTTPMethodUsage extends ExhaustiveApiUsage<HTTPMethod> {

    @Override
    public Set<Class<?>> useApi() {
      HTTPMethod method = HTTPMethod.PUT;
      method = HTTPMethod.GET;
      method = HTTPMethod.HEAD;
      method = HTTPMethod.POST;
      method = HTTPMethod.DELETE;
      method = HTTPMethod.PATCH;
      method = HTTPMethod.valueOf("GET");
      HTTPMethod[] methods = HTTPMethod.values();
      return classes(Object.class, Enum.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link HTTPHeader}.
   */
  public static class HTTPHeaderUsage extends ExhaustiveApiUsage<HTTPHeader> {

    @Override
    public Set<Class<?>> useApi() {
      HTTPHeader header = new HTTPHeader("this", "that");
      header.getName();
      header.getValue();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link ResponseTooLargeException}.
   */
  public static class ResponseTooLargeExceptionUsage
      extends ExhaustiveApiUsage<ResponseTooLargeException> {

    @Override
    public Set<Class<?>> useApi() {
      ResponseTooLargeException unused = new ResponseTooLargeException("this");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link RequestPayloadTooLargeExceptionUsage}.
   */
  public static class RequestPayloadTooLargeExceptionUsage
      extends ExhaustiveApiUsage<RequestPayloadTooLargeException> {

    @Override
    public Set<Class<?>> useApi() {
      RequestPayloadTooLargeException unused = new RequestPayloadTooLargeException("this");
      return classes(Object.class, MalformedURLException.class, IOException.class, Exception.class,
          Throwable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link InternalTransientException}.
   */
  public static class InternalTransientExceptionUsage
      extends ExhaustiveApiUsage<InternalTransientException> {

    @Override
    public Set<Class<?>> useApi() {
      InternalTransientException unused = new InternalTransientException("yar");
      return classes(Object.class, IOException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link FetchOptions}.
   */
  public static class FetchOptionsUsage extends ExhaustiveApiUsage<FetchOptions> {

    boolean ___apiConstant_DEFAULT_ALLOW_TRUNCATE;
    boolean ___apiConstant_DEFAULT_FOLLOW_REDIRECTS;
    Double ___apiConstant_DEFAULT_DEADLINE;

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_DEFAULT_ALLOW_TRUNCATE = FetchOptions.DEFAULT_ALLOW_TRUNCATE;
      ___apiConstant_DEFAULT_FOLLOW_REDIRECTS = FetchOptions.DEFAULT_FOLLOW_REDIRECTS;
      ___apiConstant_DEFAULT_DEADLINE = FetchOptions.DEFAULT_DEADLINE;
      FetchOptions opts = FetchOptions.Builder.withDefaults();
      opts = opts.allowTruncate();
      opts = opts.disallowTruncate();
      opts = opts.doNotFollowRedirects();
      opts = opts.followRedirects();
      opts = opts.doNotValidateCertificate();
      Double doubleVal = 23.5d;
      opts = opts.setDeadline(doubleVal);
      opts = opts.validateCertificate();
      opts.getValidateCertificate();
      opts.getDeadline();
      opts.getAllowTruncate();
      opts.getFollowRedirects();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link FetchOptions.Builder}.
   */
  public static class FetchOptionsBuilderUsage extends ExhaustiveApiUsage<FetchOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      FetchOptions.Builder.withDefaults();
      FetchOptions.Builder.doNotValidateCertificate();
      FetchOptions.Builder.doNotFollowRedirects();
      FetchOptions.Builder.followRedirects();
      FetchOptions.Builder.validateCertificate();
      FetchOptions.Builder.allowTruncate();
      FetchOptions.Builder.disallowTruncate();
      Double doubleVal = 23.5d;
      FetchOptions.Builder.withDeadline(doubleVal);
      return classes(Object.class);
    }
  }
}
