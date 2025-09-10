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

package com.google.appengine.tools.development.testing.jakarta;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Simple fake implementation of {@link HttpServletRequest}. */
public class FakeHttpServletRequest implements HttpServletRequest {
  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 443;
  private static final String COOKIE_HEADER = "Cookie";
  private static final String HOST_HEADER = "Host";
  private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
  private final Map<String, Object> attributes = Maps.newConcurrentMap();
  private final Map<String, String> headers = Maps.newHashMap();
  private final ListMultimap<String, String> parameters = LinkedListMultimap.create();
  private final Map<String, Cookie> cookies = new LinkedHashMap<>();
  private String hostName = "localhost";
  private int port = 443;
  private String contextPath = "";
  private String servletPath = "";
  private String pathInfo;
  private String method;
  protected String contentType;

  // used by POST methods
  protected byte[] bodyData = new byte[0];
  protected String characterEncoding;

  // the following two booleans ensure that either getReader() or
  // getInputStream is called, but not both, to conform to specs for the
  // HttpServletRequest class.
  protected boolean getReaderCalled = false;
  protected boolean getInputStreamCalled = false;

  static final String METHOD_POST = "POST";
  static final String METHOD_PUT = "PUT";

  public FakeHttpServletRequest() {
    this(DEFAULT_HOST, DEFAULT_PORT);
  }

  public FakeHttpServletRequest(String hostName, int port) {
    constructor(hostName, port, "", "", null);
  }

  public FakeHttpServletRequest(String urlStr) {
    URL url;
    try {
      url = new URL(urlStr);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    String contextPath;
    String servletPath;
    String path = url.getPath();
    if (path.length() <= 1) {
      // path must be either empty string or "/"
      contextPath = path;
      servletPath = null;
    } else {
      // Look for the second slash which separates the servlet path from the
      // context path. e.g. "/foo/bar"
      int secondSlash = path.indexOf("/", 1);
      if (secondSlash < 0) {
        // No second slash
        contextPath = path;
        servletPath = null;
      } else {
        contextPath = path.substring(0, secondSlash);
        servletPath = path.substring(secondSlash);
      }
    }
    int port = url.getPort();
    // Call constructor() instead of this() because the later is only allowed
    // at the beginning of a constructor
    constructor(url.getHost(), port, contextPath, servletPath, url.getQuery());
  }

  /**
   * This method serves as the central constructor of this class. The reason it is not an actual
   * constructor is that Java doesn't allow calling another constructor at the end of a constructor.
   * e.g.
   *
   * <pre>
   *
   * public FakeHttpServletRequest(String foo) {
   *   // Do something here
   *   this(foo, bar);  // calling another constructor here is not allowed
   * }
   *
   * </pre>
   */
  protected void constructor(
      String host, int port, String contextPath, String servletPath, String queryString) {
    setHeader(HOST_HEADER, host);
    setPort(port);
    setContextPath(contextPath);
    setSerletPath(servletPath);
    setParametersFromQueryString(queryString);
  }

  @Override
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    return Collections.enumeration(attributes.keySet());
  }

  @Override
  public String getCharacterEncoding() {
    return UTF_8.name();
  }

  @Override
  public int getContentLength() {
    return -1;
  }

  @Override
  public long getContentLengthLong() {
    return -1;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  /**
   * Get the body of the request (i.e. the body data) as a binary stream. As per Java docs, this OR
   * getReader() may be called, but not both (attempting that will result in an
   * IllegalStateException)
   */
  @Override
  public ServletInputStream getInputStream() {
    if (getReaderCalled) {
      throw new IllegalStateException("getInputStream() called after getReader()");
    }
    getInputStreamCalled = true; // so that getReader() can no longer be called

    final InputStream in = new ByteArrayInputStream(bodyData);
    return new ServletInputStream() {
      @Override
      public int read() throws IOException {
        return in.read();
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
      }

      @Override
      public void close() throws IOException {
        in.close();
      }

      @Override
      public boolean isFinished() {
        return true;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public String getLocalAddr() {
    return "1.2.3.4";
  }

  @Override
  public String getLocalName() {
    return "localhost";
  }

  @Override
  public int getLocalPort() {
    return port;
  }

  @Override
  public ServletContext getServletContext() {
    return null;
  }

  @Override
  public AsyncContext startAsync() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAsyncStarted() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAsyncSupported() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AsyncContext getAsyncContext() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DispatcherType getDispatcherType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Locale getLocale() {
    return Locale.US;
  }

  @Override
  public Enumeration<Locale> getLocales() {
    return Collections.enumeration(Collections.singleton(Locale.US));
  }

  @Override
  public String getParameter(String name) {
    return Iterables.getFirst(parameters.get(name), null);
  }

  private static final Function<Collection<String>, String[]> STRING_COLLECTION_TO_ARRAY =
      new Function<Collection<String>, String[]>() {
        @Override
        public String[] apply(Collection<String> values) {
          return values.toArray(new String[0]);
        }
      };

  @Override
  public Map<String, String[]> getParameterMap() {
    return Collections.unmodifiableMap(
        Maps.transformValues(parameters.asMap(), STRING_COLLECTION_TO_ARRAY));
  }

  @Override
  public Enumeration<String> getParameterNames() {
    return Collections.enumeration(parameters.keySet());
  }

  @Override
  public String[] getParameterValues(String name) {
    return STRING_COLLECTION_TO_ARRAY.apply(parameters.get(name));
  }

  @Override
  public String getProtocol() {
    return "HTTP/1.1";
  }

  @Override
  public BufferedReader getReader() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRemoteAddr() {
    return "5.6.7.8";
  }

  @Override
  public String getRemoteHost() {
    return "remotehost";
  }

  @Override
  public int getRemotePort() {
    return 1234;
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getScheme() {
    return port == 443 ? "https" : "http";
  }

  @Override
  public String getServerName() {
    return hostName;
  }

  @Override
  public int getServerPort() {
    return port;
  }

  @Override
  public boolean isSecure() {
    return port == 443;
  }

  @Override
  public void removeAttribute(String name) {
    attributes.remove(name);
  }

  @Override
  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  @Override
  public void setCharacterEncoding(String env) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAuthType() {
    return null;
  }

  @Override
  public String getContextPath() {
    return contextPath;
  }

  @Override
  public Cookie[] getCookies() {
    return new Cookie[0];
  }

  @Override
  public long getDateHeader(String name) {
    String value = getHeader(name);
    if (value == null) {
      return -1;
    }

    SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT, Locale.US);
    format.setTimeZone(TimeZone.getTimeZone("GMT"));
    try {
      return format.parse(value).getTime();
    } catch (ParseException e) {
      throw new IllegalArgumentException(
          "Cannot parse number from header " + name + ":" + value, e);
    }
  }

  @Override
  public String getHeader(String name) {
    return headers.get(Ascii.toLowerCase(name));
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    return Collections.enumeration(headers.keySet());
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    List<String> values = new ArrayList<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (Ascii.equalsIgnoreCase(name, entry.getKey())) {
        values.add(entry.getValue());
      }
    }
    return Collections.enumeration(values);
  }

  @Override
  public int getIntHeader(String name) {
    return Integer.parseInt(getHeader(name));
  }

  @Override
  public String getMethod() {
    if (method == null) {
      return "GET";
    }
    return method;
  }

  @Override
  public String getPathInfo() {
    return pathInfo;
  }

  @Override
  public String getPathTranslated() {
    return pathInfo;
  }

  @Override
  public String getQueryString() {
    if (parameters.isEmpty() || !getMethod().equals("GET")) {
      return null;
    }
    return paramsToString(parameters);
  }

  @Override
  public String getRemoteUser() {
    return null;
  }

  @Override
  public String getRequestURI() {
    return contextPath + servletPath + (pathInfo == null ? "" : pathInfo);
  }

  @Override
  public StringBuffer getRequestURL() {
    StringBuffer sb = new StringBuffer();
    sb.append(getScheme());
    sb.append("://");
    sb.append(getServerName());
    sb.append(":");
    sb.append(getServerPort());
    sb.append(contextPath);
    sb.append(servletPath);
    if (pathInfo != null) {
      sb.append(pathInfo);
    }
    return sb;
  }

  @Override
  public String getRequestedSessionId() {
    return null;
  }

  @Override
  public String getServletPath() {
    return servletPath;
  }

  @Override
  public HttpSession getSession() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String changeSessionId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpSession getSession(boolean create) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Principal getUserPrincipal() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRequestedSessionIdFromCookie() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRequestedSessionIdFromURL() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean authenticate(HttpServletResponse httpServletResponse)
      throws IOException, ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void login(String s, String s1) throws ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void logout() throws ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<Part> getParts() throws IOException, ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Part getPart(String s) throws IOException, ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass)
      throws IOException, ServletException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRequestedSessionIdValid() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isUserInRole(String role) {
    throw new UnsupportedOperationException();
  }

  private static String paramsToString(ListMultimap<String, String> params) {
    try {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Map.Entry<String, String> e : params.entries()) {
        if (!first) {
          sb.append('&');
        } else {
          first = false;
        }
        sb.append(URLEncoder.encode(e.getKey(), UTF_8.name()));
        if (!"".equals(e.getValue())) {
          sb.append('=').append(URLEncoder.encode(e.getValue(), UTF_8.name()));
        }
      }
      return sb.toString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  public void setParametersFromQueryString(String qs) {
    parameters.clear();
    if (qs != null) {
      for (String entry : Splitter.on('&').split(qs)) {
        List<String> kv = ImmutableList.copyOf(Splitter.on('=').limit(2).split(entry));
        try {
          parameters.put(
              URLDecoder.decode(kv.get(0), UTF_8.name()),
              kv.size() == 2 ? URLDecoder.decode(kv.get(1), UTF_8.name()) : "");
        } catch (UnsupportedEncodingException e) {
          throw new IllegalArgumentException(e);
        }
      }
    }
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public void setPort(int port) {
    this.port = port;
  }

  /*
   * Set a header on this request.
   * Note that if the header implies other attributes of the request
   * I will set them accordingly. Specifically:
   *
   * If the header is "Cookie:" then I will automatically call
   * setCookie on all of the name-value pairs found therein.
   *
   * This makes the object easier to use because you can just feed it
   * headers and the object will remain consistent with the behavior
   * you'd expect from a request.
   */
  public void setHeader(String name, String value) {
    if (Ascii.equalsIgnoreCase(name, COOKIE_HEADER)) {
      for (String pair : Splitter.on(';').trimResults().omitEmptyStrings().split(value)) {
        int equalsPos = pair.indexOf('=');
        if (equalsPos != -1) {
          String cookieName = pair.substring(0, equalsPos);
          String cookieValue = pair.substring(equalsPos + 1);
          addToCookieMap(new Cookie(cookieName, cookieValue));
        }
      }
      setCookieHeader();
      return;
    }

    addToHeaderMap(name, value);

    if (Ascii.equalsIgnoreCase(name, HOST_HEADER)) {
      hostName = value;
    }
  }

  private void addToHeaderMap(String name, String value) {
    headers.put(Ascii.toLowerCase(name), value);
  }

  /**
   * Associates a set of cookies with this fake request.
   *
   * @param cookies the cookies associated with this request.
   */
  public void setCookies(Cookie... cookies) {
    for (Cookie cookie : cookies) {
      addToCookieMap(cookie);
    }
    setCookieHeader();
  }

  /**
   * Sets a single cookie associated with this fake request. Cookies are cumulative, but ones with
   * the same name will overwrite one another.
   *
   * @param c the cookie to associate with this request.
   */
  public void setCookie(Cookie c) {
    addToCookieMap(c);
    setCookieHeader();
  }

  private void addToCookieMap(Cookie c) {
    cookies.put(c.getName(), c);
  }

  /** Sets the "Cookie" HTTP header based on the current cookies. */
  private void setCookieHeader() {
    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;
    for (Cookie c : cookies.values()) {
      if (!isFirst) {
        sb.append("; ");
      }
      sb.append(c.getName());
      sb.append("=");
      sb.append(c.getValue());
      isFirst = false;
    }

    // We cannot use setHeader() here, because setHeader() calls this method
    addToHeaderMap(COOKIE_HEADER, sb.toString());
  }

  public void addParameter(String key, String value) {
    parameters.put(key, value);
  }

  public void setMethod(String name) {
    method = name;
  }

  void setSerletPath(String servletPath) {
    this.servletPath = servletPath;
  }

  void setContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  void setPathInfo(String pathInfo) {
    if ("".equals(pathInfo)) {
      this.pathInfo = null;
    } else {
      this.pathInfo = pathInfo;
    }
  }

  /**
   * Specify the mock POST data.
   *
   * @param postString the mock post data
   * @param encoding format with which to encode mock post data
   */
  public void setPostData(String postString, Charset encoding) throws UnsupportedEncodingException {
    setPostData(postString, encoding.name());
  }

  /**
   * Specify the mock POST data.
   *
   * @param postString the mock post data
   * @param encoding format with which to encode mock post data
   */
  public void setPostData(String postString, String encoding) throws UnsupportedEncodingException {
    setPostData(postString.getBytes(encoding));
    characterEncoding = encoding;
  }

  /**
   * Specify the mock POST data in raw binary format.
   *
   * <p>This implicitly sets character encoding to not specified.
   *
   * @param data the mock post data; this is owned by the caller, so modifications made after this
   *     call will show up when the post data is read
   */
  public void setPostData(byte[] data) {
    bodyData = data;
    characterEncoding = null;
    setMethod(METHOD_POST);
  }

  /**
   * Sets the content type.
   *
   * @param contentType of the request.
   */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  @Override
  public String getRequestId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String getProtocolRequestId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public ServletConnection getServletConnection() {
    throw new UnsupportedOperationException("Not supported yet.");
  }
}
