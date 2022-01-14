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

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError.ErrorCode;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LatencyPercentiles;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy.ApplicationException;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.AccessController;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

/**
 * {@link LocalURLFetchService} fetches URLs using Apache HttpClient. This implementation should
 * only be used within the dev appserver environment.
 *
 */
@AutoService(LocalRpcService.class)
public class LocalURLFetchService extends AbstractLocalRpcService {

  // Use a single timeout for all operations.  If at some point we introduce
  // the notion of timeouts in the dev appserver we will revisit.
  private static final int DEFAULT_TIMEOUT_IN_MS = 600000;

  // Keep all in sync with apphosting/api/URLFetchServiceStub._Dynamic_Fetch
  static final int DEFAULT_MAX_RESPONSE_LENGTH = 4 << 23; // 32MB
  static final int DEFAULT_MAX_REDIRECTS = 5;

  /** The package name for this service. */
  public static final String PACKAGE = "urlfetch";

  // Size of buffer used for copying response body into response proto.
  private static final int TEMPORARY_RESPONSE_BUFFER_LENGTH = 1 << 12; // 4 KB

  // Whether the HTTP client should automatically re-use cookies across requests.
  private static final String REUSE_COOKIES_LOCALLY_PROPERTY =
      "appengine.urlfetch.reuseCookiesLocally";

  // exposed for testing
  int maxResponseLength = DEFAULT_MAX_RESPONSE_LENGTH;
  int maxRedirects = DEFAULT_MAX_REDIRECTS;

  // exposed for testing
  Logger logger = Logger.getLogger(LocalURLFetchService.class.getName());

  // HttpClient instances for making requests that validate SSL certs or not,
  // respectively. Both clients can be used for normal HTTP requests. Clients
  // are thread-safe and shared between all URLfetch threads.
  private HttpClient validatingClient;
  private HttpClient nonValidatingClient;

  /**
   * Instantiates an appropriate concrete subclass of {@link HttpRequestBase} for the provided
   * request.
   */
  private interface MethodFactory {
    HttpRequestBase buildMethod(URLFetchRequest request);
  }

  private static class ReuseCookiesLocallyHolder {
    static final boolean INSTANCE = Boolean.getBoolean(REUSE_COOKIES_LOCALLY_PROPERTY);
  }

  // maps the method constants defined in urlfetch_service.proto to MethodFactory instances
  private static final ImmutableMap<RequestMethod, MethodFactory> METHOD_FACTORY_MAP =
      buildMethodFactoryMap();

  private static ImmutableMap<RequestMethod, MethodFactory> buildMethodFactoryMap() {
    return ImmutableMap.<RequestMethod, MethodFactory>builder()
        .put(RequestMethod.GET, request -> new HttpGet(request.getUrl()))
        .put(RequestMethod.DELETE, request -> new HttpDelete(request.getUrl()))
        .put(RequestMethod.HEAD, request -> new HttpHead(request.getUrl()))
        // only post, put, and patch support payload
        .put(
            RequestMethod.POST,
            request -> {
              HttpPost post = new HttpPost(request.getUrl());
              if (request.hasPayload()) {
                ByteArrayEntity requestEntity =
                    new ByteArrayEntity(request.getPayload().toByteArray());
                post.setEntity(requestEntity);
              }
              return post;
            })
        .put(
            RequestMethod.PUT,
            request -> {
              HttpPut put = new HttpPut(request.getUrl());
              if (request.hasPayload()) {
                ByteArrayEntity requestEntity =
                    new ByteArrayEntity(request.getPayload().toByteArray());
                put.setEntity(requestEntity);
              }
              return put;
            })
        .put(
            RequestMethod.PATCH,
            request -> {
              // HttpPatch included in the package
              HttpPatch patch = new HttpPatch(request.getUrl());
              if (request.hasPayload()) {
                ByteArrayEntity requestEntity =
                    new ByteArrayEntity(request.getPayload().toByteArray());
                patch.setEntity(requestEntity);
              }
              return patch;
            })
        .build();
  }

  private int timeoutInMs = DEFAULT_TIMEOUT_IN_MS;

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  public void setTimeoutInMs(int timeoutInMs) {
    this.timeoutInMs = timeoutInMs;
  }

  // Location of a Java keystore file that contains the CAs to trust for
  // certificate validation.
  private static final String TRUST_STORE_LOCATION =
      "/com/google/appengine/api/urlfetch/dev/cacerts";

  private KeyStore getTrustStore()
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
    try (InputStream is = getClass().getResourceAsStream(TRUST_STORE_LOCATION)) {
      if (is == null) {
        throw new IOException("Couldn't get trust store stream");
      }
      KeyStore ks = KeyStore.getInstance("JKS");
      ks.load(is, null);
      return ks;
    }
  }

  // Generates and returns a Scheme that validates SSL certificates using our
  // CA truststore. If there is an error, an Exception of some variety will
  // be thrown. Since we'd rather have SSL validation be disabled than
  // have the dev appserver be broken, we just swallow and log all exceptions in
  // the next layer up.
  private Scheme createValidatingScheme() throws Exception {
    KeyManagerFactory kmFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmFactory.init(null, null);
    KeyManager[] keyManagers = kmFactory.getKeyManagers();
    TrustManagerFactory tmFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmFactory.init(getTrustStore());
    TrustManager[] trustManagers = tmFactory.getTrustManagers();
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagers, trustManagers, null /* secureRandom */);
    SSLSocketFactory strictSocketFactory = new SSLSocketFactory(sslContext);
    strictSocketFactory.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
    return new Scheme("https", strictSocketFactory, 443);
  }

  // Generates and returns a Scheme that does no certificate validation
  // whatsoever. If creating this scheme doesn't work, then your JDK is
  // probably pretty unfortunate anyways, so we're less gung-ho about catching
  // any and all exceptions.
  private Scheme createNonvalidatingScheme()
      throws KeyManagementException, NoSuchAlgorithmException {
    // This trust manager trusts /everything/
    X509TrustManager poorLifeChoicesTrustManager =
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String atuhType) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return null;
          }
        };

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, new TrustManager[] {poorLifeChoicesTrustManager}, null);
    return new Scheme(
        "https",
        new SSLSocketFactory(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER),
        443);
  }

  public HttpClient createHttpClient(boolean validateHttps) {
    // This variable will definitely be initialized by one of the following two
    // if statement blocks, but Java's static analysis doesn't realize this.
    Scheme urlfetchHttps = null;
    if (validateHttps) {
      try {
        urlfetchHttps = createValidatingScheme();
      } catch (Exception e) {
        validateHttps = false;
        logger.log(
            Level.WARNING,
            "Encountered exception trying to initialize SSL. SSL certificate validation will be "
                + "disabled",
            e);
      }
    }

    if (!validateHttps) {
      try {
        urlfetchHttps = createNonvalidatingScheme();
      } catch (KeyManagementException kme) {
        logger.log(
            Level.WARNING,
            "Encountered exception trying to initialize SSL. All HTTPS fetches will be disabled.",
            kme);
        urlfetchHttps = null;
      } catch (NoSuchAlgorithmException nsae) {
        logger.log(
            Level.WARNING,
            "Encountered exception trying to initialize SSL. All HTTPS fetches will be disabled.",
            nsae);
        urlfetchHttps = null;
      }
    }

    Scheme urlfetchHttp = new Scheme("http", PlainSocketFactory.getSocketFactory(), 80);
    SchemeRegistry sr = new SchemeRegistry();
    if (urlfetchHttps != null) {
      sr.register(urlfetchHttps);
    }
    sr.register(urlfetchHttp);
    DefaultHttpClient client =
        new DefaultHttpClient(
            new ThreadSafeClientConnManager(new BasicHttpParams(), sr), new BasicHttpParams());

    if (!ReuseCookiesLocallyHolder.INSTANCE) {
      client.removeRequestInterceptorByClass(RequestAddCookies.class);
    }

    client.getParams().setIntParameter(ClientPNames.MAX_REDIRECTS, maxRedirects);
    client.setRedirectStrategy(new AllMethodsRedirectStrategy());

    ProxySelectorRoutePlanner routePlanner =
        new ProxySelectorRoutePlanner(
            client.getConnectionManager().getSchemeRegistry(), ProxySelector.getDefault());
    client.setRoutePlanner(routePlanner);

    return client;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {}

  @Override
  public void start() {}

  @Override
  public void stop() {}

  // Essentially reimplements the Guava ByteStreams.toByteArray() method
  // since this is SDK-land and we don't have Guava.
  private byte[] responseToByteArray(HttpEntity responseEntity) throws IOException {
    InputStream responseInputStream = responseEntity.getContent();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] tempBuffer = new byte[TEMPORARY_RESPONSE_BUFFER_LENGTH];
    while (true) {
      int result = responseInputStream.read(tempBuffer);
      if (result == -1) {
        break;
      }
      baos.write(tempBuffer, 0, result);
    }
    return baos.toByteArray();
  }

  @LatencyPercentiles(latency50th = 5)
  public URLFetchResponse fetch(Status status, URLFetchRequest request) {
    if (status == null) {
      throw new NullPointerException("status cannot be null.");
    }

    if (request == null) {
      throw new NullPointerException("request cannot be null.");
    }

    if (!hasValidURL(request)) {
      throw new ApplicationException(
          ErrorCode.INVALID_URL.getNumber(), "Invalid URL: " + request.getUrl());
    }

    MethodFactory methodFactory = METHOD_FACTORY_MAP.get(request.getMethod());
    if (methodFactory == null) {
      throw new ApplicationException(
          ErrorCode.INVALID_URL.getNumber(), "Unsupported method: " + request.getMethod());
    }
    HttpRequestBase method = methodFactory.buildMethod(request);
    HttpParams params = new BasicHttpParams();
    HttpClientParams.setRedirecting(params, request.getFollowRedirects());

    // TODO set these timeouts according to the RPC deadline.
    // see http://b/1488459 for more info
    // how long we'll wait to establish a connection
    HttpConnectionParams.setConnectionTimeout(params, timeoutInMs);
    // how long we'll let the socket stay open
    HttpConnectionParams.setSoTimeout(params, timeoutInMs);
    method.setParams(params);

    boolean sawContentType = false;
    for (URLFetchRequest.Header pbHeader : request.getHeaderList()) {
      // Ignore user-set Content-Length header. It causes HttpClient to throw
      // an exception, and this behavior matches production.
      if (pbHeader.getKey().equalsIgnoreCase("Content-Length")) {
        continue;
      }

      method.addHeader(pbHeader.getKey(), pbHeader.getValue());

      if (pbHeader.getKey().equalsIgnoreCase("Content-Type")) {
        sawContentType = true;
      }
    }

    // See comment in apphosting/api/urlfetch/urlfetch_request_options.cc
    // TODO: Should we check on PUT/PATCH? What would the default be?
    if (!sawContentType && (request.getMethod() == RequestMethod.POST) && request.hasPayload()) {
      method.addHeader("Content-Type", "application/x-www-form-urlencoded");
    }

    URLFetchResponse.Builder response = URLFetchResponse.newBuilder();
    try {
      HttpResponse httpResponse = doPrivilegedExecute(request, method, response);
      int responseCode = httpResponse.getStatusLine().getStatusCode();
      if (responseCode < 100 || responseCode >= 600) {
        // Note, response codes in the range [100, 600) are valid.
        throw new ApplicationException(
            ErrorCode.FETCH_ERROR.getNumber(),
            "Status code "
                + responseCode
                + " unknown when making "
                + method.getMethod()
                + " request to URL: "
                + request.getUrl());
      }
      HttpEntity responseEntity = httpResponse.getEntity();
      if (responseEntity != null) {
        byte[] responseBuffer = responseToByteArray(responseEntity);
        if (responseBuffer.length > maxResponseLength) {
          responseBuffer = Arrays.copyOf(responseBuffer, maxResponseLength);
          response.setContentWasTruncated(true);
        }
        response.setContent(ByteString.copyFrom(responseBuffer));
      }
      httpclientHeadersToPbHeaders(httpResponse.getAllHeaders(), response);
    } catch (SocketTimeoutException ste) {
      throw new ApplicationException(
          ErrorCode.DEADLINE_EXCEEDED.getNumber(),
          "http method " + method.getMethod() + " against URL " + request.getUrl() + " timed out.");
    } catch (SSLException e) {
      throw new ApplicationException(
          ErrorCode.SSL_CERTIFICATE_ERROR.getNumber(),
          "Couldn't validate the server's SSL certificate for URL "
              + request.getUrl()
              + ": "
              + e.getMessage());
    } catch (IOException e) {
      if (e.getCause() != null
          && e.getCause().getMessage().matches("Maximum redirects \\([0-9]+\\) exceeded")) {
        throw new ApplicationException(
            ErrorCode.TOO_MANY_REDIRECTS.getNumber(),
            "Received exception executing http method "
                + method.getMethod()
                + " against URL "
                + request.getUrl()
                + ": "
                + e.getCause().getMessage());
      } else {
        throw new ApplicationException(
            ErrorCode.FETCH_ERROR.getNumber(),
            "Received exception executing http method "
                + method.getMethod()
                + " against URL "
                + request.getUrl()
                + ": "
                + e.getMessage());
      }
    }
    return response.build();
  }

  private HttpResponse doPrivilegedExecute(
      final URLFetchRequest request,
      final HttpRequestBase method,
      final URLFetchResponse.Builder response)
      throws IOException {
    try {
      return AccessController.doPrivileged(
          new PrivilegedExceptionAction<HttpResponse>() {
            @Override
            public HttpResponse run() throws IOException {
              HttpContext context = new BasicHttpContext();
              // Does some thread ops we need to do in a privileged block.
              HttpResponse httpResponse;
              // TODO: Default behavior reverted to not validating cert for
              // 1.4.2 CP due to wildcard cert validation problems. Revert for
              // 1.4.4 after we're confident that the new HttpClient has fixed the
              // behavior.
              if (request.hasMustValidateServerCertificate()
                  && request.getMustValidateServerCertificate()) {
                httpResponse = getValidatingClient().execute(method, context);
              } else {
                httpResponse = getNonValidatingClient().execute(method, context);
              }
              response.setStatusCode(httpResponse.getStatusLine().getStatusCode());
              HttpHost lastHost =
                  (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
              HttpUriRequest lastReq =
                  (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
              String lastUrl = lastHost.toURI() + lastReq.getURI();
              if (!lastUrl.equals(method.getURI().toString())) {
                response.setFinalUrl(lastUrl);
              }
              return httpResponse;
            }
          });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      throw new RuntimeException(e);
    }
  }

  boolean isAllowedPort(int port) {
    // Keep this in sync with the FastNet's
    // --outbound_port_denylist flag, defined in
    // fastnet/server/fastnetservice.cc.
    // For details, see: http://b/2084859
    return port == -1 || (port >= 80 && port <= 90) || (port >= 440 && port <= 450) || port >= 1024;
  }

  boolean hasValidURL(URLFetchRequest request) {
    // this logic was ported from apphosting/api/urlfetch_stub.py:
    // URLFetchServiceStub._Dynamic_Fetch
    if (!request.hasUrl() || request.getUrl().length() == 0) {
      return false;
    }
    URL url;
    try {
      url = new URL(request.getUrl());
    } catch (MalformedURLException e) {
      return false;
    }
    if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
      return false;
    }
    if (!isAllowedPort(url.getPort())) {
      logger.log(
          Level.WARNING,
          String.format(
              "urlfetch received %s ; port %s is not allowed in production!", url, url.getPort()));
      // fall through here, as the developer should be allowed to make any
      // connection they wish within their own environment.
    }
    return true;
  }

  /** Converts a set of HttpClient headers into a set of URLFetchService headers. */
  void httpclientHeadersToPbHeaders(Header[] headers, URLFetchResponse.Builder response) {
    for (Header header : headers) {
      response.addHeader(
          URLFetchResponse.Header.newBuilder()
              .setKey(header.getName())
              .setValue(header.getValue()));
    }
  }

  @Override
  public Double getMaximumDeadline(boolean isOfflineRequest) {
    return isOfflineRequest ? 600.0 : 60.0;
  }

  @Override
  public Integer getMaxApiRequestSize() {
    // Keep this in sync with MAX_REQUEST_SIZE in <internal2>.
    return 10 << 20; // 10 MB
  }

  private synchronized HttpClient getNonValidatingClient() {
    if (nonValidatingClient == null) {
      nonValidatingClient = createHttpClient(false);
    }
    return nonValidatingClient;
  }

  private synchronized HttpClient getValidatingClient() {
    if (validatingClient == null) {
      validatingClient = createHttpClient(true);
    }
    return validatingClient;
  }
}
