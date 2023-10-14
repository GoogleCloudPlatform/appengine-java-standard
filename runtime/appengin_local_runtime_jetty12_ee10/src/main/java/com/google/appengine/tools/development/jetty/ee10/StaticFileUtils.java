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

package com.google.appengine.tools.development.jetty.ee10;

import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.resource.Resource;

/**
 * {@code StaticFileUtils} is a collection of utilities shared by
 * {@link LocalResourceFileServlet} and {@link StaticFileFilter}.
 *
 */
public class StaticFileUtils {
  private static final String DEFAULT_CACHE_CONTROL_VALUE = "public, max-age=600";

  private final ServletContext servletContext;

  public StaticFileUtils(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  public boolean serveWelcomeFileAsRedirect(String path,
                                            boolean included,
                                            HttpServletRequest request,
                                            HttpServletResponse response)
      throws IOException {
    if (included) {
      // This is an error.  We don't have the file so we can't
      // include it in the request.
      return false;
    }

    // Even if the trailing slash is missing, don't bother trying to
    // add it.  We're going to redirect to a full file anyway.
    response.setContentLength(0);
    String q = request.getQueryString();
    if (q != null && q.length() != 0) {
      response.sendRedirect(path + "?" + q);
    } else {
      response.sendRedirect(path);
    }
    return true;
  }

  public boolean serveWelcomeFileAsForward(RequestDispatcher dispatcher,
                                           boolean included,
                                           HttpServletRequest request,
                                           HttpServletResponse response)
      throws IOException, ServletException {
    // If the user didn't specify a slash but we know we want a
    // welcome file, redirect them to add the slash now.
    if (!included && !request.getRequestURI().endsWith("/")) {
      redirectToAddSlash(request, response);
      return true;
    }

    request.setAttribute("com.google.appengine.tools.development.isWelcomeFile", true);
    if (dispatcher != null) {
      if (included) {
        dispatcher.include(request, response);
      } else {
        dispatcher.forward(request, response);
      }
      return true;
    }
    return false;
  }

  public void redirectToAddSlash(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    StringBuffer buf = request.getRequestURL();
    int param = buf.lastIndexOf(";");
    if (param < 0) {
      buf.append('/');
    } else {
      buf.insert(param, '/');
    }
    String q = request.getQueryString();
    if (q != null && q.length() != 0) {
      buf.append('?');
      buf.append(q);
    }
    response.setContentLength(0);
    response.sendRedirect(response.encodeRedirectURL(buf.toString()));
  }

  /**
   * Check the headers to see if content needs to be sent.
   * @return true if the content should be sent, false otherwise.
   */
  public boolean passConditionalHeaders(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Resource resource) throws IOException {
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
            return false;
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
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Write or include the specified resource.
   */
  public void sendData(HttpServletRequest request,
                        HttpServletResponse response,
                        boolean include,
                        Resource resource) throws IOException {
    long contentLength = resource.length();
    if (!include) {
      writeHeaders(response, request.getRequestURI(), resource, contentLength);
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

  /**
   * Write the headers that should accompany the specified resource.
   */
  public void writeHeaders(
      HttpServletResponse response, String requestPath, Resource resource, long count) {
    // Set Content-Length. Users are not allowed to override this. Therefore, we
    // may do this before adding custom static headers.
    if (count != -1) {
      if (count < Integer.MAX_VALUE) {
        response.setContentLength((int) count);
      } else {
        response.setHeader(HttpHeader.CONTENT_LENGTH.asString(), String.valueOf(count));
      }
    }

    Set<String> headersApplied = addUserStaticHeaders(requestPath, response);

    // Set Content-Type.
    if (!headersApplied.contains("content-type")) {
      String contentType = servletContext.getMimeType(resource.getName());
      if (contentType != null) {
        response.setContentType(contentType);
      }
    }

    // Set Last-Modified.
    if (!headersApplied.contains("last-modified")) {
      response.setDateHeader(HttpHeader.LAST_MODIFIED.asString(), resource.lastModified().toEpochMilli());
    }

    // Set Cache-Control to the default value if it was not explicitly set.
    if (!headersApplied.contains(HttpHeader.CACHE_CONTROL.asString().toLowerCase())) {
      response.setHeader(HttpHeader.CACHE_CONTROL.asString(), DEFAULT_CACHE_CONTROL_VALUE);
    }
  }

  /**
   * Adds HTTP Response headers that are specified in appengine-web.xml. The user may specify
   * headers explicitly using the {@code http-header} element. Also the user may specify cache
   * expiration headers implicitly using the {@code expiration} attribute. There is no check for
   * consistency between different specified headers.
   *
   * @param localFilePath The path to the static file being served.
   * @param response The HttpResponse object to which headers will be added
   * @return The Set of the names of all headers that were added, canonicalized to lower case.
   */
  @VisibleForTesting
  Set<String> addUserStaticHeaders(String localFilePath, HttpServletResponse response) {
    AppEngineWebXml appEngineWebXml =
        (AppEngineWebXml)
            servletContext.getAttribute("com.google.appengine.tools.development.appEngineWebXml");

    Set<String> headersApplied = new HashSet<>();
    for (AppEngineWebXml.StaticFileInclude include : appEngineWebXml.getStaticFileIncludes()) {
      Pattern pattern = include.getRegularExpression();
      if (pattern.matcher(localFilePath).matches()) {
        for (Map.Entry<String, String> entry : include.getHttpHeaders().entrySet()) {
          response.addHeader(entry.getKey(), entry.getValue());
          headersApplied.add(entry.getKey().toLowerCase());
        }
        String expirationString = include.getExpiration();
        if (expirationString != null) {
          addCacheControlHeaders(headersApplied, expirationString, response);
        }
        break;
      }
    }
    return headersApplied;
  }

  /**
   * Adds HTTP headers to the response to describe cache expiration behavior, based on the
   * {@code expires} attribute of the {@code includes} element of the {@code static-files} element
   * of appengine-web.xml.
   * <p>
   * We follow the same logic that is used in production App Engine. This includes:
   * <ul>
   * <li>There is no coordination between these headers (implied by the 'expires' attribute) and
   * explicitly specified headers (expressed with the 'http-header' sub-element). If the user
   * specifies contradictory headers then we will include contradictory headers.
   * <li>If the expiration time is zero then we specify that the response should not be cached using
   * three different headers: {@code Pragma: no-cache}, {@code Expires: 0} and
   * {@code Cache-Control: no-cache, must-revalidate}.
   * <li>If the expiration time is positive then we specify that the response should be cached for
   * that many seconds using two different headers: {@code Expires: num-seconds} and
   * {@code Cache-Control: public, max-age=num-seconds}.
   * <li>If the expiration time is not specified then we use a default value of 10 minutes
   * </ul>
   *
   * Note that there is one aspect of the production App Engine logic that is not replicated here.
   * In production App Engine if the url to a static file is protected by a security constraint in
   * web.xml then {@code Cache-Control: private} is used instead of {@code Cache-Control: public}.
   * In the development App Server {@code Cache-Control: public} is always used.
   * <p>
   * Also if the expiration time is specified but cannot be parsed as a non-negative number of
   * seconds then a RuntimeException is thrown.
   *
   * @param headersApplied Set of headers that have been applied, canonicalized to lower-case. Any
   *        new headers applied in this method will be added to the set.
   * @param expiration The expiration String specified in appengine-web.xml
   * @param response The HttpServletResponse into which we will write the HTTP headers.
   */
  private static void addCacheControlHeaders(
      Set<String> headersApplied, String expiration, HttpServletResponse response) {
    // The logic in this method is replicating and should be kept in sync with
    // the corresponding logic in production App Engine which is implemented
    // in AppServerResponse::SetExpiration() in the file
    // apphosting/appserver/appserver_response.cc. See also
    // HTTPResponse::SetNotCacheable(), HTTPResponse::SetCacheablePrivate(),
    // and HTTPResponse::SetCacheablePublic() in webutil/http/httpresponse.cc

    int expirationSeconds = parseExpirationSpecifier(expiration);
    if (expirationSeconds == 0) {
      response.addHeader("Pragma", "no-cache");
      response.addHeader(HttpHeader.CACHE_CONTROL.asString(), "no-cache, must-revalidate");
      response.addDateHeader(HttpHeader.EXPIRES.asString(), 0);
      headersApplied.add(HttpHeader.CACHE_CONTROL.asString().toLowerCase());
      headersApplied.add(HttpHeader.EXPIRES.asString().toLowerCase());
      headersApplied.add("pragma");
      return;
    }
    if (expirationSeconds > 0) {
      // TODO If we wish to support the corresponding logic
      // in production App Engine, we would now determine if the current
      // request URL is protected by a security constraint in web.xml and
      // if so we would use Cache-Control: private here instead of public.
      response.addHeader(
          HttpHeader.CACHE_CONTROL.asString(), "public, max-age=" + expirationSeconds);
      response.addDateHeader(
          HttpHeader.EXPIRES.asString(), System.currentTimeMillis() + expirationSeconds * 1000L);
      headersApplied.add(HttpHeader.CACHE_CONTROL.asString().toLowerCase());
      headersApplied.add(HttpHeader.EXPIRES.asString().toLowerCase());
      return;
    }
    throw new RuntimeException("expirationSeconds is negative: " + expirationSeconds);
  }

  /**
   * Parses an expiration specifier String and returns the number of seconds it represents. A valid
   * expiration specifier is a white-space-delimited list of components, each of which is a sequence
   * of digits, optionally followed by a single letter from the set {D, d, H, h, M, m, S, s}. For
   * example {@code 21D 4H 30m} represents the number of seconds in 21 days, 4.5 hours.
   *
   * @param expirationSpecifier The non-null, non-empty expiration specifier String to parse
   * @return The non-negative number of seconds represented by this String.
   */
  @VisibleForTesting
  static int parseExpirationSpecifier(String expirationSpecifier) {
    // The logic in this and the following few methods is replicating and should be kept in
    // sync with the corresponding logic in production App Engine which is implemented in
    // apphosting/api/appinfo.py. See in particular in that file _DELTA_REGEX,
    // _EXPIRATION_REGEX, _EXPIRATION_CONVERSION, and ParseExpiration().
    expirationSpecifier = expirationSpecifier.trim();
    if (expirationSpecifier.isEmpty()) {
      throwExpirationParseException("", expirationSpecifier);
    }
    String[] components = expirationSpecifier.split("(\\s)+");
    int expirationSeconds = 0;
    for (String componentSpecifier : components) {
      expirationSeconds +=
          parseExpirationSpeciferComponent(componentSpecifier, expirationSpecifier);
    }
    return expirationSeconds;
  }

  // A Pattern for matching one component of an expiration specifier String
  private static final Pattern EXPIRATION_COMPONENT_PATTERN = Pattern.compile("^(\\d+)([dhms]?)$");

  /**
   * Parses a single component of an expiration specifier, and returns the number of seconds that
   * the component represents. A valid component specifier is a sequence of digits, optionally
   * followed by a single letter from the set {D, d, H, h, M, m, S, s}, indicating days, hours,
   * minutes and seconds. A lack of a trailing letter is interpreted as seconds.
   *
   * @param componentSpecifier The component specifier to parse
   * @param fullSpecifier The full specifier of which {@code componentSpecifier} is a component.
   *        This will be included in an error message if necessary.
   * @return The number of seconds represented by {@code componentSpecifier}
   */
  private static int parseExpirationSpeciferComponent(
      String componentSpecifier, String fullSpecifier) {
    Matcher matcher = EXPIRATION_COMPONENT_PATTERN.matcher(componentSpecifier.toLowerCase());
    if (!matcher.matches()) {
      throwExpirationParseException(componentSpecifier, fullSpecifier);
    }
    String numericString = matcher.group(1);
    int numSeconds = parseExpirationInteger(numericString, componentSpecifier, fullSpecifier);
    String unitString = matcher.group(2);
    if (unitString.length() > 0) {
      switch (unitString.charAt(0)) {
        case 'd':
          numSeconds *= 24 * 60 * 60;
          break;
        case 'h':
          numSeconds *= 60 * 60;
          break;
        case 'm':
          numSeconds *= 60;
          break;
      }
    }
    return numSeconds;
  }

  /**
   * Parses a String from an expiration specifier as a non-negative integer. If successful returns
   * the integer. Otherwise throws an {@link IllegalArgumentException} indicating that the specifier
   * could not be parsed.
   *
   * @param intString String to parse
   * @param componentSpecifier The component of the specifier being parsed
   * @param fullSpecifier The full specifier
   * @return The parsed integer
   */
  private static int parseExpirationInteger(
      String intString, String componentSpecifier, String fullSpecifier) {
    int seconds = 0;
    try {
      seconds = Integer.parseInt(intString);
    } catch (NumberFormatException e) {
      throwExpirationParseException(componentSpecifier, fullSpecifier);
    }
    if (seconds < 0) {
      throwExpirationParseException(componentSpecifier, fullSpecifier);
    }
    return seconds;
  }

  /**
   * Throws an {@link IllegalArgumentException} indicating that an expiration specifier String was
   * not able to be parsed.
   *
   * @param componentSpecifier The component that could not be parsed
   * @param fullSpecifier The full String
   */
  private static void throwExpirationParseException(
      String componentSpecifier, String fullSpecifier) {
    throw new IllegalArgumentException(
        "Unable to parse cache expiration specifier '"
            + fullSpecifier
            + "' at component '"
            + componentSpecifier
            + "'");
  }
}
