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

package com.google.apphosting.runtime.jetty94;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.Callback;

/**
 * A handler that can limit the size of message bodies in requests and responses.
 *
 * <p>The optional request and response limits are imposed by checking the {@code Content-Length}
 * header or observing the actual bytes seen by the handler. Handler order is important, in as much
 * as if this handler is before a the {@link org.eclipse.jetty.server.handler.gzip.GzipHandler},
 * then it will limit compressed sized, if it as after the {@link
 * org.eclipse.jetty.server.handler.gzip.GzipHandler} then the limit is applied to uncompressed
 * bytes. If a size limit is exceeded then {@link BadMessageException} is thrown with a {@link
 * org.eclipse.jetty.http.HttpStatus#PAYLOAD_TOO_LARGE_413} status.
 */
public class SizeLimitHandler extends HandlerWrapper {
  private final long requestLimit;
  private final long responseLimit;

  /**
   * @param requestLimit The request body size limit in bytes or -1 for no limit
   * @param responseLimit The response body size limit in bytes or -1 for no limit
   */
  public SizeLimitHandler(long requestLimit, long responseLimit) {
    this.requestLimit = requestLimit;
    this.responseLimit = responseLimit;
  }

  protected void checkRequestLimit(long size) {
    if (requestLimit >= 0 && size > requestLimit) {
      throw new BadMessageException(413, "Request body is too large: " + size + ">" + requestLimit);
    }
  }

  protected void checkResponseLimit(long size) {
    if (responseLimit >= 0 && size > responseLimit) {
      throw new BadMessageException(
          500, "Response body is too large: " + size + ">" + responseLimit);
    }
  }

  @Override
  public void handle(
      String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    if (requestLimit >= 0 || responseLimit >= 0) {
      HttpOutput httpOutput = baseRequest.getResponse().getHttpOutput();
      HttpOutput.Interceptor interceptor = httpOutput.getInterceptor();
      LimitInterceptor limit = new LimitInterceptor(interceptor);

      if (requestLimit >= 0) {
        long contentLength = baseRequest.getContentLengthLong();
        checkRequestLimit(contentLength);
        if (contentLength < 0) {
          baseRequest.getHttpInput().addInterceptor(limit);
        }
      }

      if (responseLimit > 0) {
        httpOutput.setInterceptor(limit);
        response = new LimitResponse(response);
      }
    }

    super.handle(target, baseRequest, request, response);
  }

  private class LimitInterceptor implements HttpOutput.Interceptor, HttpInput.Interceptor {
    private final HttpOutput.Interceptor nextOutput;
    long read;
    long written;

    public LimitInterceptor(HttpOutput.Interceptor nextOutput) {
      this.nextOutput = nextOutput;
    }

    @Override
    public HttpOutput.Interceptor getNextInterceptor() {
      return nextOutput;
    }

    @Override
    public boolean isOptimizedForDirectBuffers() {
      return nextOutput.isOptimizedForDirectBuffers();
    }

    @Nullable
    @Override
    public HttpInput.Content readFrom(HttpInput.Content content) {
      if (content == null) {
        return null;
      }

      if (content.hasContent()) {
        read += content.remaining();
        checkResponseLimit(read);
      }
      return content;
    }

    @Override
    public void write(ByteBuffer content, boolean last, Callback callback) {
      if (content.hasRemaining()) {
        written += content.remaining();

        try {
          checkResponseLimit(written);
        } catch (Throwable t) {
          callback.failed(t);
          return;
        }
      }
      getNextInterceptor().write(content, last, callback);
    }

    @Override
    public void resetBuffer() {
      written = 0;
      getNextInterceptor().resetBuffer();
    }
  }

  private class LimitResponse extends HttpServletResponseWrapper {
    public LimitResponse(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setContentLength(int len) {
      checkResponseLimit(len);
      super.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long len) {
      checkResponseLimit(len);
      super.setContentLengthLong(len);
    }

    @Override
    public void setHeader(String name, String value) {
      if (HttpHeader.CONTENT_LENGTH.is(name)) {
        checkResponseLimit(Long.parseLong(value));
      }
      super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
      if (HttpHeader.CONTENT_LENGTH.is(name)) {
        checkResponseLimit(Long.parseLong(value));
      }
      super.addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
      if (HttpHeader.CONTENT_LENGTH.is(name)) {
        checkResponseLimit(value);
      }
      super.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
      if (HttpHeader.CONTENT_LENGTH.is(name)) {
        checkResponseLimit(value);
      }
      super.addIntHeader(name, value);
    }
  }
}
