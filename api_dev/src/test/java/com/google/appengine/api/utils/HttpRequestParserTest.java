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

package com.google.appengine.api.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.tools.development.testing.FakeHttpServletRequest;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HttpRequestParserTest {
  private class FakeHttpServletRequestWithStrictInputStream
      extends FakeHttpServletRequest {
    protected ServletInputStream inputStream;

    @Override
    public ServletInputStream getInputStream() {
      if (inputStream == null) {
        inputStream = super.getInputStream();
      }
      return inputStream;
    }
  }
  FakeHttpServletRequest req;

  @Before
  public void setUp() throws Exception {
    req = new FakeHttpServletRequestWithStrictInputStream();

    req.setContentType("multipart/form-data; boundary=\"foo\"");
    req.setPostData(
        "--foo\r\n" +
        "Content-Disposition: form-data; name=\"from\"\r\n\r\n" +
        "from@google.com\r\n" +
        "--foo\r\n" +
        "Content-Disposition: form-data; name=\"to\"\r\n\r\n" +
        "to@google.com\r\n" +
        "--foo\r\n" +
        "Content-Disposition: form-data; name=\"body\"\r\n\r\n" +
        "This is the body.\r\n" +
        "--foo\r\n" +
        "Content-Type: text/xml\r\n" +
        "Content-Disposition: form-data; name=\"stanza\"\r\n\r\n" +
        "<message from=\"from@google.com\" to=\"to@google.com\"><body>foo</body></message>\r\n",
        "US-ASCII");
  }

  @Test
  public void testParse() throws Exception {
    MimeMultipart multipart = HttpRequestParser.parseMultipartRequest(req);
    assertThat(multipart.getCount()).isEqualTo(4);
  }

  @Test
  public void testParseTwice() throws Exception {
    HttpRequestParser.parseMultipartRequest(req);
    assertThrows(IllegalStateException.class, () -> HttpRequestParser.parseMultipartRequest(req));
  }
}
