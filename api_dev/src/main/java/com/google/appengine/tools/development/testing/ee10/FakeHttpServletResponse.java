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

package com.google.appengine.tools.development.testing.ee10;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Simple fake implementation of {@link HttpServletResponse}. */
public class FakeHttpServletResponse implements HttpServletResponse {
  private static final String DEFAULT_CHARSET = "ISO-8859-1";
  private final ListMultimap<String, String> headers = LinkedListMultimap.create();
  // The API docs says the default is implicitly set to "ISO-8859-1". But
  // the application should not rely on this default. So we set null
  // here to catch it if the application doesn't set any encoding
  // explicitly.
  private String characterEncoding;
  private ByteArrayOutputStream actualBody;
  private int status = 200;
  private boolean committed;
  private ServletOutputStream outputStream;
  private PrintWriter writer;
  protected HttpServletRequest request = null;

  public FakeHttpServletResponse() {
    this(null);
  }

  public FakeHttpServletResponse(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public synchronized void flushBuffer() throws IOException {
    if (outputStream != null) {
      outputStream.flush();
    }
    if (writer != null) {
      writer.flush();
    }
    committed = true;
  }

  @Override
  public int getBufferSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCharacterEncoding() {
    return characterEncoding;
  }

  @Override
  public String getContentType() {
    List<String> types = headers.get(HttpHeaders.CONTENT_TYPE);
    if (types.isEmpty()) {
      return null;
    }
    return types.get(0);
  }

  @Override
  public Locale getLocale() {
    return Locale.US;
  }

  @Override
  public synchronized ServletOutputStream getOutputStream() {
    checkCommit();
    checkState(writer == null, "getWriter() already called");
    if (outputStream == null) {
      actualBody = new ByteArrayOutputStream();
      outputStream = new FakeServletOutputStream(actualBody);
    }
    return outputStream;
  }

  /**
   * Return the body of the response that would be sent to the client as string
   *
   * @return null if there's no response body
   */
  public synchronized String getOutputString() {
    if (outputStream == null) {
      return null;
    }
    try {
      actualBody.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (characterEncoding == null) {
      return actualBody.toString();
    } else {
      try {
        return actualBody.toString(characterEncoding);
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  /**
   * Return the body of the response that would be sent to the client as bytes
   *
   * @return null if there's no response body
   */
  public synchronized byte[] getOutputBytes() {
    if (outputStream == null) {
      return null;
    }
    try {
      actualBody.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ;
    return actualBody.toByteArray();
  }

  @Override
  public synchronized PrintWriter getWriter() throws UnsupportedEncodingException {
    checkCommit();
    if (outputStream != null) {
      throw new IllegalStateException("getOutputStream() has been called before");
    }
    if (getCharacterEncoding() == null) {
      throw new UnsupportedEncodingException("charset not found");
    }
    if (writer == null) {
      actualBody = new ByteArrayOutputStream();
      writer = new PrintWriter(new OutputStreamWriter(outputStream, getCharacterEncoding()));
    }
    return writer;
  }

  @Override
  public synchronized boolean isCommitted() {
    return committed;
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resetBuffer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBufferSize(int sz) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharacterEncoding(String encoding) {
    // use the default character
    // encoding if the encoding argument is null or "".
    characterEncoding = DEFAULT_CHARSET;
    if (encoding != null && encoding.length() > 0) {
      characterEncoding = encoding;
    }
  }

  @Override
  public void setContentLength(int length) {
    headers.removeAll(HttpHeaders.CONTENT_LENGTH);
    headers.put(HttpHeaders.CONTENT_LENGTH, Integer.toString(length));
  }

  @Override
  public void setContentLengthLong(long l) {
    headers.removeAll(HttpHeaders.CONTENT_LENGTH);
    headers.put(HttpHeaders.CONTENT_LENGTH, Long.toString(l));

  }

  @Override
  public void setContentType(String type) {
    headers.removeAll(HttpHeaders.CONTENT_TYPE);
    headers.put(HttpHeaders.CONTENT_TYPE, type);
    String encoding = getCharSet(type);
    // Mimic the real HttpResponse which only resets the character encoding
    // when it's explicitly set in the content type
    if (encoding != null) {
      setCharacterEncoding(encoding);
    }
  }

  /**
   * Parses a MIME Content-Type string to extract the charset, unquoting as necessary. Example:
   * "text/html; charset=ISO-8859-1" will return "ISO-8859-1".
   *
   * @return null if the charset cannot be found
   */
  private static String getCharSet(String contentType) {
    int index = contentType.indexOf("charset");
    if (index < 0) {
      return null;
    }

    String charset = contentType.substring(index + "charset=".length()).trim();
    if (charset.startsWith("\"") && charset.endsWith("\"")) {
      // Support RFC3023 style charsets.
      charset = charset.substring(1, charset.length() - 1);
    }
    return charset;
  }

  @Override
  public void setLocale(Locale locale) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addCookie(Cookie cookie) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDateHeader(String name, long value) {
    addHeader(name, String.valueOf(value));
  }

  @Override
  public void addHeader(String name, String value) {
    headers.put(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    headers.put(name, Integer.toString(value));
  }

  @Override
  public boolean containsHeader(String name) {
    return !headers.get(name).isEmpty();
  }

  @Override
  public String encodeRedirectURL(String url) {
    return url;
  }

  @Override
  public String encodeURL(String url) {
    if ((request != null) && (request.getSession(false) != null) && (url != null)) {
      if (url.contains("?")) {
        url += "&";
      } else {
        url += "?";
      }
      url += "gsessionid=" + request.getSession().getId();
    }
    return url;
  }

  @Override
  public synchronized void sendError(int sc) {
    status = sc;
    committed = true;
  }

  @Override
  public synchronized void sendError(int sc, String msg) {
    status = sc;
    committed = true;
  }

  @Override
  public synchronized void sendRedirect(String location) {
    if (request != null) {
      try {
        URL url = new URL(new URL(request.getRequestURL().toString()), location);
        location = url.toString();
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    status = SC_FOUND;
    setHeader(HttpHeaders.LOCATION, location);
    committed = true;
  }

  @Override
  public void setDateHeader(String name, long value) {
    setHeader(name, Long.toString(value));
  }

  @Override
  public void setHeader(String name, String value) {
    headers.removeAll(name);
    addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    headers.removeAll(name);
    addIntHeader(name, value);
  }

  @Override
  public synchronized void setStatus(int sc) {
    status = sc;
  }

  public synchronized int getStatus() {
    return status;
  }

  public String getHeader(String name) {
    return Iterables.getFirst(headers.get(checkNotNull(name)), null);
  }

  @Override
  public Collection<String> getHeaders(String s) {
    return headers.get(checkNotNull(s));
  }

  @Override
  public Collection<String> getHeaderNames() {
    return headers.keys();
  }

  private void checkCommit() {
    if (isCommitted()) {
      throw new IllegalStateException("Response is already committed");
    }
  }

  private static class FakeServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream byteStream;
    private long count;

    FakeServletOutputStream(ByteArrayOutputStream byteStream) {
      this.byteStream = byteStream;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      byteStream.write(b, off, len);
      count += len;
    }

    @Override
    public void write(byte[] b) throws IOException {
      byteStream.write(b);
      count += b.length;
    }

    @Override
    public void write(int b) throws IOException {
      byteStream.write(b);
      count++;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {}

    @Override
    public boolean isReady() {
      return true;
    }

    long getCount() {
      return count;
    }
  }
}
