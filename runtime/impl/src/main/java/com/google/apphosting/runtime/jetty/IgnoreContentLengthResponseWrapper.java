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

package com.google.apphosting.runtime.jetty;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.http.HttpHeader;

public class IgnoreContentLengthResponseWrapper extends HttpServletResponseWrapper {

  public IgnoreContentLengthResponseWrapper(HttpServletResponse response) {
    super(response);
  }

  @Override
  public void setHeader(String name, String value) {
    if (!HttpHeader.CONTENT_LENGTH.is(name)) {
      super.setHeader(name, value);
    }
  }

  @Override
  public void addHeader(String name, String value) {
    if (!HttpHeader.CONTENT_LENGTH.is(name)) {
      super.addHeader(name, value);
    }
  }

  @Override
  public void setIntHeader(String name, int value) {
    if (!HttpHeader.CONTENT_LENGTH.is(name)) {
      super.setIntHeader(name, value);
    }
  }

  @Override
  public void addIntHeader(String name, int value) {
    if (!HttpHeader.CONTENT_LENGTH.is(name)) {
      super.addIntHeader(name, value);
    }
  }

  @Override
  public void setContentLength(int len) {
    // Do nothing.
  }

  @Override
  public void setContentLengthLong(long len) {
    // Do nothing.
  }
}
