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

package com.google.apphosting.runtime.jetty.ee8;

import com.google.apphosting.runtime.jetty.CacheControlHeader;
import com.google.apphosting.utils.config.AppYaml;
import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.resource.Resource;

/** Cass that sends data with headers. */
public class FileSender {

  private final AppYaml appYaml;

  public FileSender(AppYaml appYaml) {
    this.appYaml = appYaml;
  }

  /** Writes or includes the specified resource. */
  public void sendData(
      ServletContext servletContext,
      HttpServletResponse response,
      boolean include,
      Resource resource,
      String urlPath)
      throws IOException {
    long contentLength = resource.length();
    if (!include) {
      writeHeaders(servletContext, response, resource, contentLength, urlPath);
    }

    // Get the output stream (or writer)
    OutputStream out = null;
    try {
      out = response.getOutputStream();
    } catch (IllegalStateException e) {
      out = new WriterOutputStream(response.getWriter());
    }
    IO.copy(resource.newInputStream(), out, contentLength);
  }

  /** Writes the headers that should accompany the specified resource. */
  private void writeHeaders(
      ServletContext servletContext,
      HttpServletResponse response,
      Resource resource,
      long contentCount,
      String urlPath)
      throws IOException {
    String contentType = servletContext.getMimeType(resource.getName());
    if (contentType != null) {
      response.setContentType(contentType);
    }

    if (contentCount != -1) {
      if (contentCount < Integer.MAX_VALUE) {
        response.setContentLength((int) contentCount);
      } else {
        response.setContentLengthLong(contentCount);
      }
    }

    response.setDateHeader(HttpHeader.LAST_MODIFIED.asString(), resource.lastModified().toEpochMilli());
    if (appYaml != null) {
      // Add user specific static headers
      Optional<AppYaml.Handler> maybeHandler =
          appYaml.getHandlers().stream()
              .filter(
                  handler ->
                      handler.getStatic_files() != null
                          && handler.getRegularExpression() != null
                          && handler.getRegularExpression().matcher(urlPath).matches())
              .findFirst();

      maybeHandler.ifPresent(
          handler -> {
            String cacheControlValue =
                CacheControlHeader.fromExpirationTime(handler.getExpiration()).getValue();
            response.setHeader(HttpHeader.CACHE_CONTROL.asString(), cacheControlValue);
            Map<String, String> headersFromHandler = handler.getHttp_headers();
            if (headersFromHandler != null) {
              for (Map.Entry<String, String> entry : headersFromHandler.entrySet()) {
                response.addHeader(entry.getKey(), entry.getValue());
              }
            }
          });
    }

    if (Strings.isNullOrEmpty(response.getHeader(HttpHeader.CACHE_CONTROL.asString()))) {
      response.setHeader(
          HttpHeader.CACHE_CONTROL.asString(), CacheControlHeader.getDefaultInstance().getValue());
    }
  }

  /**
   * Check the headers to see if content needs to be sent.
   *
   * @return true if the content is sent, false otherwise.
   */
  public boolean checkIfUnmodified(
      HttpServletRequest request, HttpServletResponse response, Resource resource)
      throws IOException {
    if (!request.getMethod().equals(HttpMethod.HEAD.asString())) {
      String ifms = request.getHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
      if (ifms != null) {
        long ifmsl = -1;
        try {
          ifmsl = request.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.asString());
        } catch (IllegalArgumentException e) {
          // Ignore bad date formats.
        }
        if (ifmsl != -1) {
          if (resource.lastModified().toEpochMilli() <= ifmsl) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.flushBuffer();
            return true;
          }
        }
      }

      // Parse the if[un]modified dates and compare to resource
      long date = -1;
      try {
        date = request.getDateHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString());
      } catch (IllegalArgumentException e) {
        // Ignore bad date formats.
      }
      if (date != -1) {
        if (resource.lastModified().toEpochMilli() > date) {
          response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
          return true;
        }
      }
    }
    return false;
  }
}
