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

package com.google.apphosting.utils.servlet.ee10;

import com.google.common.io.ByteStreams;
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
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code MultipartMimeUtils} is a collection of static utility clases
 * that facilitate the parsing of multipart/form-data and
 * multipart/mixed requests using the {@link MimeMultipart} class
 * provided by JavaMail.
 *
 */
public class MultipartMimeUtils {
  /**
   * Parse the request body and return a {@link MimeMultipart}
   * representing the request.
   */
  public static MimeMultipart parseMultipartRequest(HttpServletRequest req)
      throws IOException, MessagingException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteStreams.copy(req.getInputStream(), baos);

    return new MimeMultipart(createDataSource(req.getContentType(), baos.toByteArray()));
  }

  /**
   * Create a read-only {@link DataSource} with the specific content type and body.
   */
  public static DataSource createDataSource(String contentType, byte[] data) {
    return new StaticDataSource(contentType, data);
  }

  /**
   * Extract the form name from the Content-Disposition in a
   * multipart/form-data request.
   */
  public static String getFieldName(BodyPart part) throws MessagingException {
    String[] values = part.getHeader("Content-Disposition");
    String name = null;
    if (values != null && values.length > 0) {
      name = new ContentDisposition(values[0]).getParameter("name");
    }
    return (name != null) ? name : "unknown";
  }

  /**
   * Extract the text content for a {@link BodyPart}, assuming the default
   * encoding.
   */
  public static String getTextContent(BodyPart part) throws MessagingException, IOException {
    ContentType contentType = new ContentType(part.getContentType());
    String charset = contentType.getParameter("charset");
    if (charset == null) {
      // N.B.: The MIME spec doesn't seem to provide a
      // default charset, but the default charset for HTTP is
      // ISO-8859-1.  That seems like a reasonable default.
      charset = "ISO-8859-1";
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteStreams.copy(part.getInputStream(), baos);
    try {
      return new String(baos.toByteArray(), charset);
    } catch (UnsupportedEncodingException ex) {
      return new String(baos.toByteArray());
    }
  }

  /**
   * A read-only {@link DataSource} backed by a content type and a
   * fixed byte array.
   */
  private static class StaticDataSource implements DataSource {
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
