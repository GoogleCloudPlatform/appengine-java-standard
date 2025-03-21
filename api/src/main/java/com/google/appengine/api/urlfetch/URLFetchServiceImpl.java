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

package com.google.appengine.api.urlfetch;

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse.Header;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError.ErrorCode;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import org.jspecify.annotations.Nullable;

class URLFetchServiceImpl implements URLFetchService {
  static final String PACKAGE = "urlfetch";

  private static final Logger logger = Logger.getLogger(URLFetchServiceImpl.class.getName());

  @Override
  public HTTPResponse fetch(URL url) throws IOException {
    return fetch(new HTTPRequest(url));
  }

  @Override
  public HTTPResponse fetch(HTTPRequest request) throws IOException {
    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Fetch", convertToPb(request).toByteArray(),
          createApiConfig(request.getFetchOptions()));
    } catch (ApiProxy.RequestTooLargeException ex) {
      throw new IOException("The request exceeded the maximum permissible size");
    } catch (ApiProxy.ApplicationException ex) {
      Throwable cause = convertApplicationException(request.getURL(), ex);
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof IOException) {
        throw (IOException) cause;
      } else {
        throw new RuntimeException(cause);
      }
    } catch (ApiProxy.ApiDeadlineExceededException ex) {
      throw new SocketTimeoutException("Timeout while fetching URL: " + request.getURL());
    } catch (ApiProxy.ApiProxyException ex) {
      throw new IOException(ex);
    }

    URLFetchResponse responseProto =
        URLFetchResponse.parseFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
    if (!request.getFetchOptions().getAllowTruncate() && responseProto.getContentWasTruncated()) {
      throw new ResponseTooLargeException(request.getURL().toString());
    }
    return convertFromPb(responseProto);
  }

  @Override
  public Future<HTTPResponse> fetchAsync(URL url) {
    return fetchAsync(new HTTPRequest(url));
  }

  @Override
  public Future<HTTPResponse> fetchAsync(HTTPRequest request) {
    final FetchOptions fetchOptions = request.getFetchOptions();
    final URL url = request.getURL();

    Future<byte[]> response = ApiProxy.makeAsyncCall(
        PACKAGE, "Fetch", convertToPb(request).toByteArray(), createApiConfig(fetchOptions));

    // Request is not held onto after return to avoid holding more memory than needed.
    return new FutureWrapper<byte[], HTTPResponse>(response) {
      @Override
      protected HTTPResponse wrap(byte @Nullable[] responseBytes) throws IOException {
        URLFetchResponse responseProto =
            URLFetchResponse.newBuilder().mergeFrom(responseBytes).build();
        if (!fetchOptions.getAllowTruncate() && responseProto.getContentWasTruncated()) {
          throw new ResponseTooLargeException(url.toString());
        }
        return convertFromPb(responseProto);
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException) {
          return convertApplicationException(url, (ApiProxy.ApplicationException) cause);
        } else if (cause instanceof ApiProxy.ApiDeadlineExceededException) {
          return new SocketTimeoutException("Timeout while fetching URL: " + url);
        }
        return cause;
      }
    };
  }

  // Holder class for global default URLFetch deadline.
  // @VisibleForTesting
  static class DeadlineParser {
    static final DeadlineParser INSTANCE = initializedDeadlineParser();
    volatile int deadlineMs = -1;

    private DeadlineParser() {
    }

    private static DeadlineParser initializedDeadlineParser() {
      DeadlineParser parser = new DeadlineParser();
      parser.refresh();
      return parser;
    }

    // @VisibleForTesting
    void refresh() {
      String globalDefault = System.getProperty(URLFetchService.DEFAULT_DEADLINE_PROPERTY);
      if (globalDefault != null) {
        try {
          deadlineMs = (int) (Double.parseDouble(globalDefault) * 1000);
        } catch (NumberFormatException e) {
          deadlineMs = -1;
          logger.warning("Cannot parse deadline: " + globalDefault);
        }
      } else {
        deadlineMs = -1;
      }
    }
  }

  private ApiProxy.ApiConfig createApiConfig(FetchOptions options) {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();

    Double deadline = options.getDeadline();
    if (deadline != null) {
      apiConfig.setDeadlineInSeconds(deadline);
    } else if (DeadlineParser.INSTANCE.deadlineMs >= 0) {
      // Use global default deadline if one has been defined
      apiConfig.setDeadlineInSeconds(DeadlineParser.INSTANCE.deadlineMs / 1000.0);
    }

    return apiConfig;
  }

  private String getURLExceptionMessage(
      String formatString, String url, @Nullable String errorDetail) {
    if (errorDetail == null || errorDetail.trim().isEmpty()) {
      return String.format(formatString, url);
    }
    return String.format(formatString + ", error: %s", url, errorDetail);
  }

  private Throwable convertApplicationException(URL requestUrl, ApiProxy.ApplicationException ex) {
    String urlString = requestUrl.toString();
    // Keep in sync with urlfetch.py
    ErrorCode errorCode = ErrorCode.forNumber(ex.getApplicationError());
    String errorDetail = ex.getErrorDetail();
    switch (errorCode) {
      case INVALID_URL:
        return new MalformedURLException(
            getURLExceptionMessage("Invalid URL specified: %s", urlString, errorDetail));
      case PAYLOAD_TOO_LARGE:
        return new RequestPayloadTooLargeException(urlString);
      case CLOSED:
        return new IOException(getURLExceptionMessage(
            "Connection closed unexpectedly by server at URL: %s", urlString, null));
      case TOO_MANY_REDIRECTS:
        return new IOException(getURLExceptionMessage(
            "Too many redirects at URL: %s with redirect=true", urlString, null));
      case MALFORMED_REPLY:
        return new IOException(getURLExceptionMessage(
            "Malformed HTTP reply received from server at URL: %s", urlString, errorDetail));
      case RESPONSE_TOO_LARGE:
        return new ResponseTooLargeException(urlString);
      case DNS_ERROR:
        return new UnknownHostException(
            getURLExceptionMessage("DNS host lookup failed for URL: %s", urlString, null));
      case FETCH_ERROR:
        return new IOException(
            getURLExceptionMessage("Could not fetch URL: %s", urlString, errorDetail));
      case INTERNAL_TRANSIENT_ERROR:
        return new InternalTransientException(urlString);
      case DEADLINE_EXCEEDED:
        return new SocketTimeoutException(
            getURLExceptionMessage("Timeout while fetching URL: %s", urlString, null));
      case SSL_CERTIFICATE_ERROR:
        return new SSLHandshakeException(getURLExceptionMessage(
            "Could not verify SSL certificate for URL: %s", urlString, null));
      case UNSPECIFIED_ERROR:
      default:
        return new IOException(ex.getErrorDetail());
    }
  }

  private URLFetchRequest convertToPb(HTTPRequest request) {
    URLFetchRequest.Builder requestProto = URLFetchRequest.newBuilder();
    requestProto.setUrl(request.getURL().toExternalForm());

    byte[] payload = request.getPayload();
    if (payload != null) {
      requestProto.setPayload(ByteString.copyFrom(payload));
    }

    switch (request.getMethod()) {
      case GET:
        requestProto.setMethod(RequestMethod.GET);
        break;
      case POST:
        requestProto.setMethod(RequestMethod.POST);
        break;
      case HEAD:
        requestProto.setMethod(RequestMethod.HEAD);
        break;
      case PUT:
        requestProto.setMethod(RequestMethod.PUT);
        break;
      case DELETE:
        requestProto.setMethod(RequestMethod.DELETE);
        break;
      case PATCH:
        requestProto.setMethod(RequestMethod.PATCH);
        break;
      default:
        throw new IllegalArgumentException("unknown method: " + request.getMethod());
    }

    for (HTTPHeader header : request.getHeaders()) {
      URLFetchRequest.Header.Builder headerProto = URLFetchRequest.Header.newBuilder();
      headerProto.setKey(header.getName());
      headerProto.setValue(header.getValue());
      requestProto.addHeader(headerProto);
    }

    requestProto.setFollowRedirects(request.getFetchOptions().getFollowRedirects());

    switch (request.getFetchOptions().getCertificateValidationBehavior()) {
      case VALIDATE:
        requestProto.setMustValidateServerCertificate(true);
        break;
      case DO_NOT_VALIDATE:
        requestProto.setMustValidateServerCertificate(false);
        break;
      default:
        // Leave mustValidateServerCertificate unset and the behavior up to the
        // urlfetch implementation.
    }

    return requestProto.build();
  }

  private HTTPResponse convertFromPb(URLFetchResponse responseProto) {

    byte[] content = responseProto.hasContent() ? responseProto.getContent().toByteArray() : null;

    List<HTTPHeader> headers = new ArrayList<>(responseProto.getHeaderCount());
    for (Header header : responseProto.getHeaderList()) {
      headers.add(new HTTPHeader(header.getKey(), header.getValue()));
    }

    URL finalURL = null;
    if (responseProto.hasFinalUrl() && responseProto.getFinalUrl().length() > 0) {
      try {
        finalURL = new URL(responseProto.getFinalUrl());
      } catch (MalformedURLException e) {
        logger.severe("malformed final URL: " + e);
      }
    }

    return new HTTPResponse(responseProto.getStatusCode(), content, finalURL, headers);
  }
}
