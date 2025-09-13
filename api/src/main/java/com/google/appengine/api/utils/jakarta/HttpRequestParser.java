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

package com.google.appengine.api.utils.jakarta;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMultipart;

/**
 * {@code HttpRequestParser} encapsulates helper methods used to parse incoming {@code
 * multipart/form-data} HTTP requests. Subclasses should use these methods to parse specific
 * requests into useful data structures.
 *
 */
public class HttpRequestParser {
  /**
   * Parse input stream of the given request into a MimeMultipart object.
   *
   * @params req The HttpServletRequest whose POST data should be parsed.
   *
   * @return A MimeMultipart object representing the POST data.
   *
   * @throws IOException if the input stream cannot be read.
   * @throws MessagingException if the input stream cannot be parsed.
   * @throws IllegalStateException if the request's input stream has already been
   *     read (eg. by calling getReader() or getInputStream()).
   */
  protected static MimeMultipart parseMultipartRequest(HttpServletRequest req)
      throws IOException, MessagingException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ServletInputStream inputStream = req.getInputStream();
    copy(inputStream, baos);
    if (baos.size() == 0) {
      throw new IllegalStateException("Input stream already read, or empty.");
    }

    return new MimeMultipart(new StaticDataSource(req.getContentType(), baos.toByteArray()));
  }

  protected static String getFieldName(BodyPart part) throws MessagingException {
    String[] values = part.getHeader("Content-Disposition");
    String name = null;
    if (values != null && values.length > 0) {
      name = new ContentDisposition(values[0]).getParameter("name");
    }
    return (name != null) ? name : "unknown";
  }

  protected static String getTextContent(BodyPart part) throws MessagingException, IOException {
    ContentType contentType = new ContentType(part.getContentType());
    String charset = contentType.getParameter("charset");
    if (charset == null) {
      // N.B.: The MIME spec doesn't seem to provide a
      // default charset, but the default charset for HTTP is
      // ISO-8859-1.  That seems like a reasonable default.
      charset = "ISO-8859-1";
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    copy(part.getInputStream(), baos);
    try {
      return new String(baos.toByteArray(), charset);
    } catch (UnsupportedEncodingException ex) {
      return new String(baos.toByteArray());
    }
  }

  /**
   * Copies all bytes from the input stream to the output stream. Does not close or flush either
   * stream.
   *
   * This code is copied from Guava's ByteStreams to avoid direct dependency on the library.
   * See b/20821034 for details.
   */
  private static void copy(InputStream from, OutputStream to) throws IOException {
    if (from == null) {
      throw new NullPointerException();
    }
    if (to == null) {
      throw new NullPointerException();
    }
    byte[] buf = new byte[8192];
    while (true) {
      int r = from.read(buf);
      if (r == -1) {
        break;
      }
      to.write(buf, 0, r);
    }
  }

  /**
   * A read-only {@link DataSource} backed by a content type and a
   * fixed byte array.
   */
  protected static class StaticDataSource implements DataSource {
    private final String contentType;
    private final byte[] bytes;

    public StaticDataSource(String contentType, byte[] bytes) {
      this.contentType = contentType;
      this.bytes = bytes;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public OutputStream getOutputStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return "request";
    }
  }
}
