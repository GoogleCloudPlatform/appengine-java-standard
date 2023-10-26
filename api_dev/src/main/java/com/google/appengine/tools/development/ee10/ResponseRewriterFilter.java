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

package com.google.appengine.tools.development.ee10;

import com.google.appengine.api.log.dev.LocalLogService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.HttpHeaders;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import java.util.Vector;

/**
 * A filter that rewrites the response headers and body from the user's
 * application.
 *
 * <p>This sanitises the headers to ensure that they are sensible and the user
 * is not setting sensitive headers, such as Content-Length, incorrectly. It
 * also deletes the body if the response status code indicates a non-body
 * status.
 *
 * <p>This also strips out some request headers before passing the request to
 * the application.
 *
 */
public class ResponseRewriterFilter implements Filter {
  /**
   * A mock timestamp to use as the response completion time, for testing.
   *
   * <p>Long.MIN_VALUE indicates that this should not be used, and instead, the
   * current time should be taken.
   */
  private final long emulatedResponseTime;
  private LocalLogService logService;

  private static final String BLOB_KEY_HEADER = "X-AppEngine-BlobKey";

  /** The value of the "Server" header output by the development server. */
  private static final String DEVELOPMENT_SERVER = "Development/1.0";

  /** These statuses must not include a response body (RFC 2616). */
  private static final int[] NO_BODY_RESPONSE_STATUSES = {
      HttpServletResponse.SC_CONTINUE,              // 100
      HttpServletResponse.SC_SWITCHING_PROTOCOLS,   // 101
      HttpServletResponse.SC_NO_CONTENT,            // 204
      HttpServletResponse.SC_NOT_MODIFIED,          // 304
  };

  public ResponseRewriterFilter() {
    super();
    emulatedResponseTime = Long.MIN_VALUE;
  }

  /**
   * Creates a ResponseRewriterFilter for testing purposes, which mocks the
   * current time.
   *
   * @param mockTimestamp Indicates that the current time will be emulated with
   *        this timestamp.
   */
  public ResponseRewriterFilter(long mockTimestamp) {
    super();
    emulatedResponseTime = mockTimestamp;
  }

  /**
   * @param response
   * @return a new ResponseWriter (to override if Servlet 3.1 is needed).
   */
  protected ResponseWrapper getResponseWrapper(HttpServletResponse response) {
    return new ResponseWrapper(response);
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig filterConfig) {
    Object apiProxyDelegate = filterConfig.getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
    if (apiProxyDelegate instanceof ApiProxyLocal) {
      ApiProxyLocal apiProxyLocal = (ApiProxyLocal) apiProxyDelegate;
      logService = (LocalLogService) apiProxyLocal.getService(LocalLogService.PACKAGE);
    }
  }

  /**
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
   *                                    javax.servlet.ServletResponse,
   *                                    javax.servlet.FilterChain)
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // It is an error if the request or response are not HTTP.
    HttpServletRequest httprequest;
    HttpServletResponse httpresponse;
    try {
      httprequest = (HttpServletRequest) request;
      httpresponse = (HttpServletResponse) response;
    } catch (ClassCastException e) {
      throw new ServletException(e);
    }

    RequestWrapper wrappedRequest = new RequestWrapper(httprequest);
    ResponseWrapper wrappedResponse = getResponseWrapper(httpresponse);

    // First, run the application code to populate the response.
    chain.doFilter(wrappedRequest, wrappedResponse);

    // This should never fail because we do not allow the response to be
    // committed until after all of the rewriters have finished.
    // Note: This tests if the inner response is actually committed, not the
    // wrapped response, which pretends that it is committed when written to.
    Preconditions.checkState(!response.isCommitted(), "Response has already been committed");

    long responseTime;
    if (emulatedResponseTime == Long.MIN_VALUE) {
      responseTime = System.currentTimeMillis();
    } else {
      responseTime = emulatedResponseTime;
    }

    // Call each response header rewriter in order.
    ignoreHeadersRewriter(wrappedResponse);
    serverDateRewriter(wrappedResponse, responseTime);
    cacheRewriter(wrappedResponse, responseTime);
    blobServeRewriter(wrappedResponse);
    contentLengthRewriter(wrappedRequest, wrappedResponse);

    // Commit the response, writing the body to the client.
    wrappedResponse.reallyCommit();
  }

  // Keep this in sync with HTTPProto::kUntrustedRequestHeaders.
  // This also includes headers that are stripped out by the GFE.
  private static final String[] IGNORE_REQUEST_HEADERS = {
      HttpHeaders.ACCEPT_ENCODING,
      HttpHeaders.CONNECTION,
      "Keep-Alive",
      HttpHeaders.PROXY_AUTHORIZATION,
      HttpHeaders.TE,
      HttpHeaders.TRAILER,
      HttpHeaders.TRANSFER_ENCODING,
  };

  // Keep this in sync with HTTPProto::kUntrustedResponseHeaders.
  // This also includes headers that are stripped out by the GFE.
  // Content-Length is dealt with later (it should not be stripped in case of a
  // HEAD request).
  private static final String[] IGNORE_RESPONSE_HEADERS = {
      HttpHeaders.CONNECTION,
      HttpHeaders.CONTENT_ENCODING,
      HttpHeaders.DATE,
      "Keep-Alive",
      HttpHeaders.PROXY_AUTHENTICATE,
      HttpHeaders.SERVER,
      HttpHeaders.TRAILER,
      HttpHeaders.TRANSFER_ENCODING,
      HttpHeaders.UPGRADE,
  };

  /**
   * Removes specific response headers.
   *
   * <p>Certain response headers cannot be modified by an Application. This rewriter simply removes
   * those headers.
   *
   * @param response A response object, which may be modified.
   */
  private void ignoreHeadersRewriter(ResponseWrapper response) {
    for (String h : IGNORE_RESPONSE_HEADERS) {
      if (response.containsHeader(h)) {
        // Setting the header to null deletes it from the response.
        response.reallySetHeader(h, null);
      }
    }
  }

  /**
   * Sets the Server and Date response headers to their correct value.
   *
   * @param response A response object, which may be modified.
   * @param responseTime The timestamp indicating when the response completed.
   */
  private void serverDateRewriter(ResponseWrapper response, long responseTime) {
    response.reallySetHeader(HttpHeaders.SERVER, DEVELOPMENT_SERVER);
    response.reallySetDateHeader(HttpHeaders.DATE, responseTime);
  }

  /**
   * Determines whether the response may have a body, based on the status code.
   *
   * @param status The response status code.
   * @return true if the response may have a body.
   */
  private static boolean responseMayHaveBody(int status) {
    for (int s : NO_BODY_RESPONSE_STATUSES) {
      if (status == s) {
        return false;
      }
    }
    return true;
  }

  /**
   * Sets the default Cache-Control and Expires headers.
   *
   * <p>These are only set if the response status allows a body, and only if the headers have not
   * been explicitly set by the application.
   *
   * @param response A response object, which may be modified.
   * @param responseTime The timestamp indicating when the response completed.
   */
  private void cacheRewriter(ResponseWrapper response, long responseTime) {
    // If the response has no body, we do not need to be concerned about the
    // Cache-Control and Expires headers.
    if (!responseMayHaveBody(response.getStatus())) {
      return;
    }

    // This differs from production; we do not want caching by default in the
    // development server.
    if (!response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
      response.reallySetHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
      if (!response.containsHeader(HttpHeaders.EXPIRES)) {
        response.reallySetHeader(HttpHeaders.EXPIRES, "Mon, 01 Jan 1990 00:00:00 GMT");
      }
    }

    // This is designed to mimic the behaviour of the GFE as much as possible.
    if (response.containsHeader(HttpHeaders.SET_COOKIE)) {
      // It is a security risk to have any caching with Set-Cookie.
      // If Expires is omitted or set to a future date, and response code is
      // cacheable, set Expires to the current date.
      long expires = response.getExpires();
      if (expires == Long.MIN_VALUE || expires >= responseTime) {
        response.reallySetDateHeader(HttpHeaders.EXPIRES, responseTime);
      }

      // Remove "public" cache-control directive, and add "private" if it (or a
      // more restrictive directive) is not already present.
      Vector<String> cacheDirectives = new Vector<String>(response.getCacheControl());
      while (cacheDirectives.remove("public")) {
        // Iterate until "public" is no longer found in cacheDirectives.
      }
      if (!cacheDirectives.contains("private") && !cacheDirectives.contains("no-cache") &&
          !cacheDirectives.contains("no-store")) {
        cacheDirectives.add("private");
      }
      // Replace Cache-Control with a new single header, with all directives
      // comma-separated.
      StringBuilder newCacheControl = new StringBuilder();
      for (String directive : cacheDirectives) {
        if (newCacheControl.length() > 0) {
          newCacheControl.append(", ");
        }
        newCacheControl.append(directive);
      }
      response.reallySetHeader(HttpHeaders.CACHE_CONTROL, newCacheControl.toString());
    }
  }

  /**
   * Deletes the response body, if X-AppEngine-BlobKey is present.
   *
   * <p>Otherwise, it would be an error if we were to send text to the client and then attempt to
   * rewrite the body to serve the blob.
   *
   * @param response A response object, which may be modified.
   */
  private void blobServeRewriter(ResponseWrapper response) {
    if (response.containsHeader(BLOB_KEY_HEADER)) {
      response.reallyResetBuffer();
    }
  }

  /**
   * Rewrites the Content-Length header.
   *
   * <p>Even though Content-Length is not a user modifiable header, App Engine sends a correct
   * Content-Length to the user based on the actual response.
   *
   * <p>If the request method is HEAD or the response status indicates that the response should not
   * have a body, the body is deleted instead. The existing Content-Length header is preserved for
   * HEAD requests.
   *
   * @param request A request object, which is not modified.
   * @param response A response object, which may be modified.
   */
  private void contentLengthRewriter(HttpServletRequest request, ResponseWrapper response) {
    // Flush the print writer, to ensure that we get a valid content length (or,
    // in the case where we delete a body, to ensure that it doesn't later
    // become flushed).
    response.flushPrintWriter();
    // Set the correct content length.
    Optional<Integer> responseSize;
    if (request.getMethod().equals("HEAD")) {
      // Delete the body; keep the Content-Length.
      response.reallyResetBuffer();
      responseSize = Optional.absent();
    } else if (!responseMayHaveBody(response.getStatus())) {
      // Delete the body and Content-Length.
      response.reallySetHeader(HttpHeaders.CONTENT_LENGTH, null);
      response.reallyResetBuffer();
      responseSize = Optional.absent();
    } else {
      response.reallySetHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(response.getBodyLength()));
      responseSize = Optional.of(response.getBodyLength());
    }
    if (logService != null) {
      if (responseSize.isPresent()) {
        logService.registerResponseSize(responseSize.get());
      } else {
        logService.clearResponseSize();
      }
    }
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
  }

  /**
   * Wraps a request to strip out some of the headers.
   */
  private static class RequestWrapper extends HttpServletRequestWrapper {
    /** An Enumeration that filters out ignored header names. */
    private static class HeaderFilterEnumeration implements Enumeration<String> {
      private final Enumeration<?> allNames;   // All headers in the original list.
      private String nextName;        // The next name to return.

      HeaderFilterEnumeration(Enumeration<String> allNames) {
        this.allNames = allNames;
        getNextValidName();
      }

      /** Get the next non-ignored name from allNames and store it in nextName.
       */
      private void getNextValidName() {
        while (allNames.hasMoreElements()) {
          String name = (String) allNames.nextElement();
          if (validHeader(name)) {
            nextName = name;
            return;
          }
        }
        nextName = null;
      }

      @Override
      public boolean hasMoreElements() {
        return nextName != null;
      }

      @Override
      public String nextElement() {
        if (nextName == null) {
          throw new NoSuchElementException();
        }
        String result = nextName;
        getNextValidName();
        return result;
      }
    }

    public RequestWrapper(HttpServletRequest request) {
      super(request);
    }

    private static boolean validHeader(String name) {
      for (String h : IGNORE_REQUEST_HEADERS) {
        if (h.equalsIgnoreCase(name)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public long getDateHeader(String name) {
      return validHeader(name) ? super.getDateHeader(name) : -1;
    }

    @Override
    public String getHeader(String name) {
      return validHeader(name) ? super.getHeader(name) : null;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (validHeader(name)) {
        @SuppressWarnings("unchecked")
        Enumeration<String> headers = super.getHeaders(name);
        return headers;
      } else {
        // Return an empty enumeration.
        return new Enumeration<String>() {
          @Override
          public boolean hasMoreElements() {
            return false;
          }

          @Override
          public String nextElement() {
            throw new NoSuchElementException();
          }
        };
      }
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      @SuppressWarnings("unchecked")
      Enumeration<String> headerNames = super.getHeaderNames();
      return new HeaderFilterEnumeration(headerNames);
    }

    @Override
    public int getIntHeader(String name) {
      return validHeader(name) ? super.getIntHeader(name) : -1;
    }
  }

  /**
   * Wraps a response to buffer the entire body, and allow reading of the status, body and headers.
   *
   * <p>This buffers the entire body locally, so that the body is not streamed in chunks to the
   * client, but instead all at the end.
   *
   * <p>This is necessary to calculate the correct Content-Length at the end, and also to modify
   * headers after the application returns, but also matches production behaviour.
   *
   * <p>For the sake of compatibility, the class <em>pretends</em> not to buffer any data. (It
   * behaves as if it has a buffer size of 0.) Therefore, as with a normal {@link
   * HttpServletResponseWrapper}, you may not modify the status or headers after modifying the body.
   * Note that the {@link PrintWriter} returned by {@link #getWriter()} does its own limited
   * buffering.
   *
   * <p>This class also provides the ability to read the value of the status and some of the headers
   * (which is not available before Servlet 3.0), and the body.
   */
  public static class ResponseWrapper extends HttpServletResponseWrapper {
    private int status = SC_OK;

    /**
     * The value of the Expires header, parsed as a Java timestamp.
     *
     * <p>Long.MIN_VALUE indicates that the Expires header is missing or invalid.
     */
    private long expires = Long.MIN_VALUE;
    /** The value of the Cache-Control headers, parsed into separate directives. */
    private final Vector<String> cacheControl = new Vector<String>();

    /** A buffer to hold the body without sending it to the client. */
    protected final ByteArrayOutputStream body = new ByteArrayOutputStream();

    protected ServletOutputStream bodyServletStream = null;
    protected PrintWriter bodyPrintWriter = null;
    /** Indicates that flushBuffer() has been called. */
    private boolean committed = false;

    private static final String DATE_FORMAT_STRING =
        "E, dd MMM yyyy HH:mm:ss 'GMT'";

    public ResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
      // The user can write directly into our private buffer.
      // The response will not be committed until all rewriting is complete.
      if (bodyServletStream != null) {
        return bodyServletStream;
      } else {
        Preconditions.checkState(bodyPrintWriter == null, "getWriter has already been called");
        bodyServletStream = new ServletOutputStreamWrapper(body);
        return bodyServletStream;
      }
    }

    @Override
    public PrintWriter getWriter() throws UnsupportedEncodingException {
      // The user can write directly into our private buffer.
      // The response will not be committed until all rewriting is complete.
      if (bodyPrintWriter != null) {
        return bodyPrintWriter;
      } else {
        Preconditions.checkState(bodyServletStream == null,
                                 "getOutputStream has already been called");
        bodyPrintWriter = new PrintWriter(new OutputStreamWriter(body, getCharacterEncoding()));
        return bodyPrintWriter;
      }
    }

    @Override
    public void setCharacterEncoding(String charset) {
      // Has no effect if getWriter has been called or response committed.
      if (bodyPrintWriter != null || isCommitted()) {
        return;
      }
      super.setCharacterEncoding(charset);
    }

    @Override
    public void setContentLength(int len) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      super.setContentLength(len);
    }

    @Override
    public void setContentType(String type) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (type != null && nonAscii(type)) {
        return;
      }
      // If getWriter has been called, remove the charset part. (The
      // specification does not allow the charset to be modified afterwards.)
      // This will automatically re-add the existing charset if one has been set.
      if (bodyPrintWriter != null) {
        type = stripCharsetFromMediaType(type);
      }
      super.setContentType(type);
    }

    @Override
    public void setLocale(Locale loc) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      String oldCharacterEncoding = getCharacterEncoding();
      String oldContentType = getContentType();
      super.setLocale(loc);
      // If getWriter has been called or Content-Type has been set, revert the charset part. (The
      // specification does not allow the charset to be modified afterwards.)
      if (oldContentType != null || bodyPrintWriter != null) {
        super.setCharacterEncoding(oldCharacterEncoding);
      }
      // If Content-Type has been set, revert it to its previous value.
      if (oldContentType != null) {
        super.setContentType(oldContentType);
      }
    }

    @Override
    public void setBufferSize(int size) {
      checkNotCommitted();
      super.setBufferSize(size);
    }

    @Override
    public int getBufferSize() {
      // Emulate a response with a buffer size of 0.
      return 0;
    }

    @Override
    public void flushBuffer() {
      // Do not transmit bytes to the client.
      // Since the buffer is not to be transmitted to the client until the
      // rewriting is complete, it would not make sense to allow the user to
      // flush the buffer early.
      // However, record that the response has been committed.
      committed = true;
    }

    @Override
    public void reset() {
      checkNotCommitted();
      super.reset();
    }

    @Override
    public void resetBuffer() {
      checkNotCommitted();
      reallyResetBuffer();
    }

    @Override
    public boolean isCommitted() {
      // Report whether anything has been flushed or written to the body.
      // (Regardless of whether it has actually been sent to the client.)
      return committed || body.size() > 0;
    }

    /**
     * Checks whether {@link #isCommitted()} is true, and if so, raises
     * {@link IllegalStateException}.
     */
    void checkNotCommitted() {
      Preconditions.checkState(!isCommitted(), "Response has already been committed");
    }

    @Override
    public void addCookie(Cookie cookie) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      super.addCookie(cookie);
    }

    @Override
    public void addDateHeader(String name, long date) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (nonAscii(name)) {
        return;
      }
      super.addDateHeader(name, date);
      if (name.equalsIgnoreCase(HttpHeaders.EXPIRES)) {
        expires = date;
      }
    }

    @Override
    public void addHeader(String name, String value) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      if (value == null) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (nonAscii(name) || nonAscii(value)) {
        return;
      }
      if (name.equalsIgnoreCase(HttpHeaders.EXPIRES)) {
        // Parse the date and store it in expires.
        try {
          parseExpires(value);
        } catch (ParseException e) {
          // Do nothing (keep the previous expires value).
        }
      } else if (name.equalsIgnoreCase(HttpHeaders.CACHE_CONTROL)) {
        // Parse the directives and add them to cacheControl.
        parseCacheControl(value);
      } else if (name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
        // If getWriter has been called, remove the charset part. (The
        // specification does not allow the charset to be modified afterwards.)
        if (bodyPrintWriter != null) {
          value = stripCharsetFromMediaType(value);
        }
      }
      super.addHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (nonAscii(name)) {
        return;
      }
      super.addIntHeader(name, value);
    }

    @Override
    public void sendError(int sc) throws IOException {
      checkNotCommitted();
      // This has to be re-implemented to avoid committing the response.
      // This will set the HTTP response status description correctly, but there
      // is no way to get the description string for the HTML body.
      setStatus(sc);
      setErrorBody(Integer.toString(sc));
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      checkNotCommitted();
      // This has to be re-implemented to avoid committing the response.
      super.sendError(sc, msg);
      setErrorBody(sc + " " + HtmlEscapers.htmlEscaper().escape(msg));
    }

    /** Sets the response body to an HTML page with an error message.
     *
     * This also sets the Content-Type header.
     *
     * @param errorText A message to display in the title and page contents.
     *        Should contain an HTTP status code and optional message.
     */
    private void setErrorBody(String errorText) throws IOException {
      // This has to be re-implemented to avoid committing the response.
      setHeader(HttpHeaders.CONTENT_TYPE, "text/html; charset=iso-8859-1");
      String bodyText = "<html><head><title>Error " + errorText + "</title></head>\n"
          + "<body><h2>Error " + errorText + "</h2></body>\n"
          + "</html>";
      // Note: This will convert any non-Latin characters into "?". It would be
      // preferable to have UTF-8 output, but we use ISO-8859-1 (Latin-1)
      // because that's what the underlying sendError uses.
      body.write(bodyText.getBytes("iso-8859-1"));
    }

    @Override
    public void sendRedirect(String location) {
      checkNotCommitted();
      // This has to be re-implemented to avoid committing the response.
      // Send a 302 response, as specified by the sendRedirect documentation.
      setStatus(SC_FOUND);
      resetBuffer();
      setHeader(HttpHeaders.LOCATION, encodeRedirectURL(location));
      status = SC_FOUND;
    }

    @Override
    public void setDateHeader(String name, long date) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (nonAscii(name)) {
        return;
      }
      reallySetDateHeader(name, date);
    }

    @Override
    public void setHeader(String name, String value) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (nonAscii(name) || (value != null && nonAscii(value))) {
        return;
      }
      if (name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
        // If getWriter has been called, remove the charset part. (The
        // specification does not allow the charset to be modified afterwards.)
        if (bodyPrintWriter != null) {
          value = stripCharsetFromMediaType(value);
        }
      }
      reallySetHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      // Do not allow headers with non-ASCII characters.
      if (nonAscii(name)) {
        return;
      }
      if (name.equalsIgnoreCase(HttpHeaders.EXPIRES)) {
        // Must be invalid.
        expires = Long.MIN_VALUE;
      }
      super.setIntHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
      // Has no effect if response committed.
      if (isCommitted()) {
        return;
      }
      super.setStatus(sc);
      status = sc;
    }

    /** Gets the status code of the response. */
    public int getStatus() {
      // Note: This method is available in Servlet 3.0 on HttpServletResponse,
      // but this code needs to run on older versions. Therefore, it is not an
      // override.
      return status;
    }

    /**
     * Gets the value of the Expires header, as a Java timestamp.
     *
     * <p>Long.MIN_VALUE indicates that the Expires header is missing or invalid.
     */
    public long getExpires() {
      return expires;
    }

    /**
     * Gets the value of the Cache-Control headers, parsed into separate directives.
     */
    public Vector<String> getCacheControl() {
      return cacheControl;
    }

    /**
     * Gets the total number of bytes that have been written to the body without
     * committing.
     */
    int getBodyLength() {
      return body.size();
    }

    /**
     * Writes the body to the wrapped response's output stream.
     *
     * <p>If the body is not empty, this causes the status and headers to be
     * rewritten. This should not be called until all of the header and body
     * rewriting is complete.
     *
     * <p>If the body is empty, this has no effect, so the response can be
     * considered not committed.
     */
    void reallyCommit() throws IOException {
      flushPrintWriter();
      if (!isCommitted()) {
        return;
      }
      OutputStream stream = super.getOutputStream();
      stream.write(body.toByteArray());
      body.reset();
    }

    /**
     * Reset the output buffer.
     *
     * This works even though {@link #isCommitted()} may return true.
     */
    void reallyResetBuffer() {
      body.reset();
      bodyServletStream = null;
      bodyPrintWriter = null;
    }

    /**
     * Sets a header in the response.
     *
     * This works even though {@link #isCommitted()} may return true.
     */
    void reallySetHeader(String name, String value) {
      super.setHeader(name, value);
      if (name.equalsIgnoreCase(HttpHeaders.EXPIRES)) {
        if (value == null) {
          expires = Long.MIN_VALUE;
        } else {
          // Parse the date and store it in expires.
          try {
            parseExpires(value);
          } catch (ParseException e) {
            // Expires header is invalid.
            expires = Long.MIN_VALUE;
          }
        }
      } else if (name.equalsIgnoreCase(HttpHeaders.CACHE_CONTROL)) {
        // Parse the directives and replace the existing cacheControl.
        cacheControl.clear();
        if (value != null) {
          parseCacheControl(value);
        }
      }
    }

    /**
     * Sets a date header in the response.
     *
     * This works even though {@link #isCommitted()} may return true.
     */
    void reallySetDateHeader(String name, long date) {
      super.setDateHeader(name, date);
      if (name.equalsIgnoreCase(HttpHeaders.EXPIRES)) {
        expires = date;
      }
    }

    /**
     * Flushes the {@link PrintWriter} returned by {@link #getWriter()}, if it
     * exists.
     */
    void flushPrintWriter() {
      if (bodyPrintWriter != null) {
        bodyPrintWriter.flush();
      }
    }

    /**
     * Parse a date string and store the result in expires.
     */
    private void parseExpires(String date) throws ParseException {
      // Create a new DateFormat object every time, to avoid thread safety
      // issues.
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_STRING);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date parsedDate = dateFormat.parse(date);
      expires = parsedDate.getTime();
    }

    /**
     * Parse a comma-separated list, and add the items to cacheControl.
     */
    private void parseCacheControl(String directives) {
      String[] elements = directives.split(",");
      for (String element : elements) {
        cacheControl.add(element.trim());
      }
    }

    /**
     * Removes the charset parameter from a media type string.
     *
     * @param mediaType A media type string, such as a Content-Type value.
     * @return The media type with the charset parameter removed, if any
     *         existed. If not, returns the media type unchanged.
     */
    private static String stripCharsetFromMediaType(String mediaType) {
      String newMediaType = null;
      for (String part : mediaType.split(";")) {
        part = part.trim();
        if (!(part.length() >= 8 &&
              part.substring(0, 8).equalsIgnoreCase("charset="))) {
          newMediaType = newMediaType == null ? "" : newMediaType + "; ";
          newMediaType += part;
        }
      }
      return newMediaType;
    }

    /**
     * Tests whether a string contains any non-ASCII characters.
     */
    private static boolean nonAscii(String string) {
      for (char c : string.toCharArray()) {
        if (c >= 0x80) {
          return true;
        }
      }
      return false;
    }

    /** A ServletOutputStream that wraps some other OutputStream. */
    public static class ServletOutputStreamWrapper extends ServletOutputStream {
      private final OutputStream stream;

      protected ServletOutputStreamWrapper(OutputStream stream) {
        this.stream = stream;
      }

      @Override
      public void close() throws IOException {
        stream.close();
      }

      @Override
      public void flush() throws IOException {
        stream.flush();
      }

      @Override
      public void write(byte[] b) throws IOException {
        stream.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        stream.write(b, off, len);
      }

      @Override
      public void write(int b) throws IOException {
        stream.write(b);
      }

      // @Override Only for Servlet 3.1, but we want to also support Servlet 2.5.
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
        throw new UnsupportedOperationException();
      }
    }
  }
}
