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

package com.google.apphosting.utils.security.urlfetch;

import static com.google.common.collect.Maps.newLinkedHashMapWithExpectedSize;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Adapts the {@link URLFetchService} to the http and https protocol 
 * handlers for {@link URL}.
 *
 */
public class URLFetchServiceStreamHandler extends URLStreamHandler {
  /**
   * Property key for system property defining whether the Connection subclass can infer response
   * messages from the HTTP status code.
   */
  static final String DERIVE_RESPONSE_MESSAGE_PROPERTY =
      "appengine.urlfetch.deriveResponseMessage";

  /**
   * Property key for system property defining whether "internal" addresses such as the metadata
   * server are resolved natively even though other addresses are sent to the urlfetch service.
   *
   * <p>This property is undocumented and subject to change.
   */
  static final String RESOLVE_INTERNAL_NATIVELY_PROPERTY =
      "com.google.appengine.urlfetch.resolve.internal.addresses.natively";

  /**
   * Default deadline for URLFetch calls.
   */
  private static final int DEFAULT_DEADLINE_MS = 5000;

  private static final ImmutableMap<Integer, String> HTTP_ERROR_CODES =
      new ImmutableMap.Builder<Integer, String>()
          // HTTP 1.1 Status Codes (RFC 2616)
          .put(100, "CONTINUE")
          .put(101, "SWITCHING_PROTOCOLS")
          .put(200, "OK")
          .put(201, "CREATED")
          .put(202, "ACCEPTED")
          .put(203, "NON_AUTHORITATIVE_INFORMATION")
          .put(204, "NO_CONTENT")
          .put(205, "RESET_CONTENT")
          .put(206, "PARTIAL_CONTENT")
          .put(300, "MULTIPLE_CHOICES")
          .put(301, "MOVED_PERMANENTLY")
          .put(302, "FOUND")
          .put(303, "SEE_OTHER")
          .put(304, "NOT_MODIFIED")
          .put(305, "USE_PROXY")
          .put(307, "TEMPORARY_REDIRECT")
          .put(400, "BAD_REQUEST")
          .put(401, "UNAUTHORIZED")
          .put(402, "PAYMENT_REQUIRED")
          .put(403, "FORBIDDEN")
          .put(404, "NOT_FOUND")
          .put(405, "METHOD_NOT_ALLOWED")
          .put(406, "NOT_ACCEPTABLE")
          .put(407, "PROXY_AUTHENTICATION_REQUIRED")
          .put(408, "REQUEST_TIMEOUT")
          .put(409, "CONFLICT")
          .put(410, "GONE")
          .put(411, "LENGTH_REQUIRED")
          .put(412, "PRECONDITION_FAILED")
          .put(413, "REQUEST_ENTITY_TOO_LARGE")
          .put(414, "REQUEST_URI_TOO_LONG")
          .put(415, "UNSUPPORTED_MEDIA_TYPE")
          .put(416, "REQUESTED_RANGE_NOT_SATISFIABLE")
          .put(417, "EXPECTATION_FAILED")
          .put(500, "INTERNAL_SERVER_ERROR")
          .put(501, "NOT_IMPLEMENTED")
          .put(502, "BAD_GATEWAY")
          .put(503, "SERVICE_UNAVAILABLE")
          .put(504, "GATEWAY_TIMEOUT")
          .put(505, "HTTP_VERSION_NOT_SUPPORTED")
          
          // Additional HTTP 1.1 Status Code (RFC 7231)
          .put(426, "UPGRADE_REQUIRED")
      
          // Additional HTTP 1.1 Status Code (RFC 7538) 
          .put(308, "PERMANENT_REDIRECT")
      
          // Webdav Status Codes (RFC 2518,  RFC 4918)
          .put(102, "PROCESSING")
          .put(207, "MULTI_STATUS")
          .put(422, "UNPROCESSABLE_ENTITY")
          .put(423, "LOCKED")
          .put(424, "FAILED_DEPENDENCY")
          .put(507, "INSUFFICIENT_STORAGE")
      
          // Nonstandard Apache extension
          .put(509, "BANDWIDTH_LIMIT_EXCEEDED")
      
          // Additional HTTP Status Codes (RFC 6585)
          .put(428, "PRECONDITION_REQUIRED")
          .put(429, "TOO_MANY_REQUESTS")
          .put(431, "REQUEST_HEADER_FIELDS_TOO_LARGE")
          .put(511, "NETWORK_AUTHENTICATION_REQUIRED").build();
  
  private static final Logger logger = 
      Logger.getLogger(URLFetchServiceStreamHandler.class.getName());

  private static class DeriveResponseMessageProperty {
    static final boolean INSTANCE =
        Boolean.getBoolean(DERIVE_RESPONSE_MESSAGE_PROPERTY);
  }

  private static Constructor<? extends HttpURLConnection> httpURLConnectionConstructor;

  private static synchronized Constructor<? extends HttpURLConnection>
      getHttpURLConnectionConstructor() throws ReflectiveOperationException {
    if (httpURLConnectionConstructor == null) {
      Class<? extends HttpURLConnection> httpURLConnectionClass =
          Class.forName("sun.net.www.protocol.http.HttpURLConnection")
              .asSubclass(HttpURLConnection.class);
      httpURLConnectionConstructor = httpURLConnectionClass.getConstructor(URL.class, Proxy.class);
    }
    return httpURLConnectionConstructor;
  }

  private static HttpURLConnection openNativeConnection(URL u) throws IOException {
    try {
      // We use reflection here to avoid referencing sun.* classes from source code. That doesn't
      // work when compiling with a recent JDK and --release 8.
      return getHttpURLConnectionConstructor().newInstance(u, null);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Could not get HttpURLConnection constructor", e);
    }
  }

  @Override
  protected HttpURLConnection openConnection(URL u) throws IOException {
    if (shouldOpenNatively(u)) {
      return openNativeConnection(u);
    }
    return new Connection(u);
  }

  @Override
  protected URLConnection openConnection(URL u, Proxy p) throws IOException {
    if (p == null) {
      throw new IllegalArgumentException("p may not be null");
    }
    if (p.equals(Proxy.NO_PROXY)) {
      return openConnection(u);
    }
    throw new UnsupportedOperationException(
        "Google App Engine does not support the use of proxies.");
  }

  private boolean shouldOpenNatively(URL url) {
    return Boolean.getBoolean(RESOLVE_INTERNAL_NATIVELY_PROPERTY) && isInternalUrl(url);
  }

  // Pattern that must match an entire address for it to be internal.
  // ?x means that spaces are ignored in the pattern.
  private static final Pattern INTERNAL = Pattern.compile(
      "(?x: .+\\.internal | (.*:)? 169\\.254\\.\\d+\\.\\d+ )");

  // @VisibleForTesting
  static boolean isInternalUrl(URL url) {
    String host = url.getHost();
    if (host == null) {
      return false;
    }
    return INTERNAL.matcher(host).matches();
  }

  /**
   * Do not resolve {@link InetAddress} objects.
   */
  @Override
  protected synchronized InetAddress getHostAddress(URL u) {
    // N.B.(schwardo): This is invoked by URL.equals() and
    // URL.hashCode() -- apparently with privileged permissions.
    return null;
  }

  /**
   * The HttpURLConnection wrapper around URLFetchService.
   */
  // @VisibleForTesting
  static class Connection extends HttpURLConnection {

    /**
     * The service used to make the fetch. We need one per connection,
     * because {@link URLFetchService} makes no promises about MT-safety. 
     */
    private final URLFetchService service = URLFetchServiceFactory.getURLFetchService();

    /**
     * The cached response.
     */
    private HTTPResponse response;

    /**
     * The cached header fields of the response, in a more usable form.
     */
    private LinkedHashMap<String,List<String>> responseFields;

    /**
     * The current OutputStream. Will be null until {@link #getOutputStream} 
     * is called.
     */
    private BufferingOutputStream outputStream;

    /**
     * The current InputStream. Will be null until {@link #getInputStream}
     * is called. 
     */
    private InputStream inputStream;

    /**
     * The current request headers.
     */
    private final LinkedHashMap<String, List<String>> requestProperties = new LinkedHashMap<>();

    // Holder class for global default URLFetch deadline.
    // @VisibleForTesting
    static class DeadlineParser {
      static final DeadlineParser INSTANCE = new DeadlineParser();
      volatile int deadlineMs = -1;

      private DeadlineParser() {
        refresh();
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
    
    public Connection(URL url) {
      super(url);
      // NB(tobyr) The JRE makes the default 0, which means "infinite timeout".
      // We don't want that to be the default (and it's not technically spec'd
      // to be the default), so we change it here.
      int deadlineMs = DeadlineParser.INSTANCE.deadlineMs;

      if (deadlineMs == -1) {
        deadlineMs = DEFAULT_DEADLINE_MS;
      }

      setConnectTimeout(deadlineMs);
      setReadTimeout(1);
    }

    @Override
    public void disconnect() {
      connected = false;
    }

    private boolean isConnected() {
      return connected;
    }

    @Override
    public boolean usingProxy() {
      // TODO We are using a proxy, but should we really return true?
      return false;
    }

    @Override
    public void setChunkedStreamingMode(int chunklen) {
      // TODO Is this relevant for URLFetchService?
      super.setChunkedStreamingMode(chunklen);
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
      method = method.toUpperCase();
      try {
        HTTPMethod.valueOf(method);
      } catch (IllegalArgumentException e) {
        throw new ProtocolException(method + " is not one of the supported http methods: " + 
            Arrays.asList(HTTPMethod.values()));
      }
      super.setRequestMethod(method);
    }

    @Override
    public int getResponseCode() throws IOException {
      getInputStream();
      return responseCode;
    }

    @Override
    public String getResponseMessage() {
      return DeriveResponseMessageProperty.INSTANCE && HTTP_ERROR_CODES.containsKey(responseCode)
          ? HTTP_ERROR_CODES.get(responseCode) : "OK";
    }

    @Override
    public InputStream getErrorStream() {
      if (connected && responseCode >= 400) {
        // Don't cause a fetch, if we haven't already fetched
        // Spec is to return null instead.
        return inputStream;
      }

      return null;
    }


    @Override
    public void connect() throws IOException {

      if (connected) {
        return;
      }

      connected = true;

      // There's no real connect phase for us, since we do the connect,
      // read, and write all in one single go with URLFetchService.
      // We can't really do anything here.

      // If it helps us at all, we do know that the "pre-connect" 
      // settings can not change after this point:
      //
      // *   <li><code>setAllowUserInteraction</code>
      // *   <li><code>setDoInput</code>
      // *   <li><code>setDoOutput</code>
      // *   <li><code>setIfModifiedSince</code>
      // *   <li><code>setUseCaches</code>
      // * </ul>
      // * <p>
      // * and the general request properties are modified using the method:
      // * <ul>
      // *   <li><code>setRequestProperty</code>

    }

    @Override
    public String getHeaderField(String name) {
      List<String> fieldValues = getHeaderFields().get(name.toLowerCase());
      if (fieldValues == null) {
        return null;
      }
      return fieldValues.get(fieldValues.size() - 1);
    }

    @Override
    public LinkedHashMap<String, List<String>> getHeaderFields() {
      try {
        getInputStream();  // force a fetch
      } catch (IOException e) {
        // It's a bug in the spec that getHeaderFields doesn't throw
        // IOException or something similar, since it has to potentially
        // force a connection.
        throw new RuntimeException("Unable to complete the HTTP request", e);
      }

      return responseFields;
    }

    @Override
    public void setRequestProperty(String key, String value) {
      List<String> values = new ArrayList<>();
      values.add(value);
      requestProperties.put(key, values);
      super.setRequestProperty(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
      List<String> values = requestProperties.get(key);
      if (values == null) {
        values = new ArrayList<>();
        requestProperties.put(key, values);
      }
      values.add(value);
      super.addRequestProperty(key, value);
    }

    @Override
    public String getHeaderFieldKey(int n) {
      Map.Entry<String, List<String>> entry = getNthEntry(n);
      if (entry != null) {
        return entry.getKey();
      }
      return null;
    }

    @Override
    public String getHeaderField(int n) {
      Map.Entry<String, List<String>> entry = getNthEntry(n);
      if (entry != null) {
        List<String> values = entry.getValue();
        if (values != null) {
          // N.B.(schwardo): According to the javadoc, I think we're
          // actually supposed to be returning the final entry in values
          // here, not all of them.  However, I don't want to break
          // anything now.
          return Joiner.on(",").useForNull("null").join(values);
        }
      }
      return null;
    }

    @Override
    public Permission getPermission() throws IOException {
      // Don't need any permissions to ask for any URL from URLFetchService
      return null;
    }

    /**
     * Returns the InputStream to be read from.
     * <p>
     * This can be called to complete a PUT or a POST, or initiate a 
     * GET, HEAD, DELETE when setDoInput is true. Calling this method
     * forces the request to take place, if it has not already.
     * <p>
     * This method may be called many times by a client, which will 
     * always expect to receive the same InputStream instance.
     * For example, {@link URLConnection} calls {@code getInputStream()} to
     * force the request to be committed before retrieving headers.  
     */
    @Override
    public InputStream getInputStream() throws IOException {
      if (inputStream != null) {
        return inputStream;
      }

      if (!getDoInput()) {
        String msg = "Input was not set on this URLConnection. Use \"setDoInput(true)\"";
        throw new IOException(msg);
      }

      fetchResponse();

      byte[] content = response.getContent();
      if (content == null) {
        content = new byte[]{};
      }
      inputStream = new ByteArrayInputStream(content);
      return inputStream;
    }

    /**
     * Returns the OutputStream to be written to.
     * <p>
     * This can be called to initiate a PUT or a POST, when setDoOutput is true.
     * There is no situation in which this may be called after 
     * {@link #getInputStream}. This method may be called many times by a 
     * client, which will always expect to receive the same OutputStream
     * instance.
     * <p>
     * Closing the returned {@code OutputStream} will force the request to
     * take place, if it has not already.
     */
    @Override
    public OutputStream getOutputStream() throws IOException {

      // It's possible we've been asked for the OutputStream before. 
      if (outputStream != null) {
        return outputStream;
      }

      if (!getDoOutput()) {
        String msg = "Output was not set on this URLConnection. Use \"setDoOutput(true)\"";
        throw new IOException(msg);
      }

      // NB(tobyr) This maintains backwards compatibility with Sun's protocol
      // implementation. If the user forgot to set POST explicitly
      // (it defaults to GET), we transparently set it for them.
      if (method.equalsIgnoreCase(HTTPMethod.GET.name())) {
        method = HTTPMethod.POST.name();
      }

      // Ensure we are "connected".
      connect();

      // Don't do a fetch - we have to wait until after the client has done
      // all of his writing to the OutputStream / calls getInputStream.

      outputStream = new BufferingOutputStream();

      return outputStream;
    }

    private Map.Entry<String, List<String>> getNthEntry(int n) {
      Iterator<Map.Entry<String, List<String>>> iterator = getHeaderFields().entrySet().iterator();
      Map.Entry<String, List<String>> last = null;
      for (int i = 0; i <= n; ++i) {
        if (iterator.hasNext()) {
          last = iterator.next();
        } else {
          return null;
        }
      }
      return last;
    }

    /**
     * Performs the actual fetch of the URL and retrieves the response.
     * This causes {@link #outputStream} to be closed, such 
     * that any more writes throw an IOException.
     */
    private HTTPResponse fetchResponse() throws IOException {
      if (response != null) {
        return response;
      }

      connect();

      String method = getRequestMethod();
      HTTPMethod httpMethod = HTTPMethod.valueOf(method);
      HTTPRequest request = new HTTPRequest(url, httpMethod);

      if (getInstanceFollowRedirects()) {
        request.getFetchOptions().followRedirects();
      } else {
        request.getFetchOptions().doNotFollowRedirects();
      }

      double deadlineSeconds;
      int connectTimeoutMillis = getConnectTimeout();
      int readTimeoutMillis = getReadTimeout();

      if (connectTimeoutMillis == 0 || readTimeoutMillis == 0) {
        // A value of 0 means an infinite deadline, so we use an arbitrarily large number
        // to represent that.
        deadlineSeconds = Integer.MAX_VALUE;
      } else {
        deadlineSeconds = (getConnectTimeout() + getReadTimeout()) / 1000.0;
      }

      if (deadlineSeconds > 0.0) {
        request.getFetchOptions().setDeadline(deadlineSeconds);
      }

      for (Map.Entry<String,List<String>> entry : requestProperties.entrySet()) {
        String name = entry.getKey();
        List<String> values = new ArrayList<String>(entry.getValue());
        for (String value : values) {
          request.addHeader(new HTTPHeader(name, value));
        }
      }

      if (outputStream != null) {
        byte[] output = outputStream.toByteArray();
        outputStream.close();
        request.setPayload(output);
      }

      // TODO
      // Consider support getIfModifiedSince() and getUseCaches()

      response = service.fetch(request);
      this.responseCode = response.getResponseCode();
      if (response.getFinalUrl() != null) {
        this.url = response.getFinalUrl();
      }

      List<HTTPHeader> headers = response.getHeadersUncombined();
      responseFields = newLinkedHashMapWithExpectedSize(headers.size());

      for (HTTPHeader header : headers) {
        List<String> values = responseFields.get(header.getName().toLowerCase());
        if (values == null) {
          values = new ArrayList<String>();
          responseFields.put(header.getName().toLowerCase(), values);
        }
        values.add(header.getValue().trim());
      }

      return response;
    }

    private class BufferingOutputStream extends OutputStream {     
      private ByteArrayOutputStream buffer;
      private boolean closed;
      
      public BufferingOutputStream() {
        buffer = new ByteArrayOutputStream();
      }

      @Override
      public void write(int b) throws IOException {
        checkOpen();
        buffer.write(b);
      }

      @Override
      public void write(byte[] b) throws IOException {
        checkOpen();
        buffer.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        checkOpen();
        buffer.write(b, off, len);
      }

      @Override
      public void flush() throws IOException {
        checkOpen();
        buffer.flush();
      }

      @Override
      public void close() throws IOException {
        buffer.close();
        closed = true;
        // Force the fetch if we know that no input will be done.
        if (!isConnected() && !getDoInput()) {
          fetchResponse();
        }
      }

      public byte[] toByteArray() {
        return buffer.toByteArray();
      }

      private void checkOpen() throws IOException {
        if (closed) {
          String msg = "The OutputStream has been committed and can no longer be written to.";
          throw new IOException(msg);
        }
      }
    }
  }


  /**
   * Trims whitespace from the front and back of {@code s}.
   */
  // @VisibleForTesting
  static String trim(String s) {
    if (s == null) {
      return null;
    }

    int notWhitespaceChar = 0;

    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (!Character.isWhitespace(c)) {
        notWhitespaceChar = i;
        break;
      }
    }

    if (notWhitespaceChar != 0) {
      s = s.substring(notWhitespaceChar);
    }

    return s.trim();
  }
}
