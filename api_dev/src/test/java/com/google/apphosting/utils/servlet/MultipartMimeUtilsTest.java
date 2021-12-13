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

package com.google.apphosting.utils.servlet;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.development.testing.FakeHttpServletRequest;
import javax.mail.internet.MimeMultipart;
import junit.framework.TestCase;

/** Provides tests for {@link MultipartMimeUtils}. */
public class MultipartMimeUtilsTest extends TestCase {
  private static final String REQUEST =
      "--foo\r\n"
          + "Content-Disposition: form-data; name=\"string\"\r\n\r\n"
          + "Example string.\r\n"
          + "--foo\r\n"
          + "Content-type: image/png\r\n"
          + "Content-Disposition: form-data; name=\"image\"; filename=\"foo.png\"\r\n\r\n"
          + "...\r\n"
          + "--foo\r\n"
          + "Content-type: text/plain\r\n"
          + "Content-Disposition: form-data; name=\"text\"; filename=\"example.txt\"\r\n\r\n"
          + "Example content.\r\n";

  public void testParse() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"foo\"");
    req.setPostData(REQUEST, UTF_8.name());

    MimeMultipart multipart = MultipartMimeUtils.parseMultipartRequest(req);
    assertEquals(3, multipart.getCount());

    assertEquals(null, multipart.getBodyPart(0).getFileName());
    assertEquals("string", MultipartMimeUtils.getFieldName(multipart.getBodyPart(0)));
    assertEquals("Example string.", MultipartMimeUtils.getTextContent(multipart.getBodyPart(0)));

    assertEquals("foo.png", multipart.getBodyPart(1).getFileName());
    assertEquals("image", MultipartMimeUtils.getFieldName(multipart.getBodyPart(1)));
    assertEquals("image/png", multipart.getBodyPart(1).getContentType());

    assertEquals("example.txt", multipart.getBodyPart(2).getFileName());
    assertEquals("text", MultipartMimeUtils.getFieldName(multipart.getBodyPart(2)));
    assertEquals("text/plain", multipart.getBodyPart(2).getContentType());
    assertEquals(
        "Example content.\r\n", MultipartMimeUtils.getTextContent(multipart.getBodyPart(2)));
  }
}
