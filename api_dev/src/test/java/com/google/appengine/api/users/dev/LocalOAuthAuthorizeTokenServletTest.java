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

package com.google.appengine.api.users.dev;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link LocalOAuthAuthorizeTokenServlet}. */
@RunWith(JUnit4.class)
public class LocalOAuthAuthorizeTokenServletTest {
  private static final String BAD_FORM_VALUE = "bad' value";

  @Test
  public void testContinueIsEscaped() throws Exception {
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintWriter writer = new PrintWriter(buffer);

    when(req.getParameter("oauth_callback")).thenReturn(BAD_FORM_VALUE);
    when(resp.getWriter()).thenReturn(writer);

    new LocalOAuthAuthorizeTokenServlet().doGet(req, resp);
    writer.flush();

    assertWithMessage("no output detected!").that(buffer.toString()).isNotEmpty();
    assertWithMessage("unescaped value detected")
        .that(buffer.toString())
        .doesNotContain(BAD_FORM_VALUE);
  }
}
