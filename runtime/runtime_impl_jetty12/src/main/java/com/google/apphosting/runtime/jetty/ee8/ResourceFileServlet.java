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

import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.JettyConstants;
import com.google.apphosting.utils.config.AppYaml;
import com.google.common.base.Ascii;
import com.google.common.flogger.GoogleLogger;
import java.io.IOException;
import java.util.Objects;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHandler;
import org.eclipse.jetty.http.pathmap.MappedResource;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

/**
 * {@code ResourceFileServlet} is a copy of {@code org.mortbay.jetty.servlet.DefaultServlet} that
 * has been trimmed down to only support the subset of features that we want to take advantage of
 * (e.g. no gzipping, no chunked encoding, no buffering, etc.). A number of Jetty-specific
 * optimizations and assumptions have also been removed (e.g. use of custom header manipulation
 * API's, use of {@code ByteArrayBuffer} instead of Strings, etc.).
 *
 * <p>A few remaining Jetty-centric details remain, such as use of the {@link
 * ContextHandler.APIContext} class, and Jetty-specific request attributes, but these are specific
 * cases where there is no servlet-engine-neutral API available. This class also uses Jetty's {@link
 * Resource} class as a convenience, but could be converted to use {@link
 * ServletContext#getResource(String)} instead.
 *
 */
public class ResourceFileServlet extends HttpServlet {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private Resource resourceBase;
  private String[] welcomeFiles;
  private FileSender fSender;
  ContextHandler chandler;
  ServletContext context;

  /**
   * Initialize the servlet by extracting some useful configuration data from the current {@link
   * ServletContext}.
   */
  @Override
  public void init() throws ServletException {
    context = getServletContext();
    AppVersion appVersion =
        (AppVersion) context.getAttribute(JettyConstants.APP_VERSION_CONTEXT_ATTR);
    chandler = ContextHandler.getContextHandler(context);

    AppYaml appYaml =
        (AppYaml) chandler.getServer().getAttribute(JettyConstants.APP_YAML_ATTRIBUTE_TARGET);
    fSender = new FileSender(appYaml);
    // AFAICT, there is no real API to retrieve this information, so
    // we access Jetty's internal state.
    welcomeFiles = chandler.getWelcomeFiles();

    try {
      // TODO: review use of root factory.
      resourceBase = ResourceFactory.root().newResource(context.getResource("/" + appVersion.getPublicRoot()));
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  /** Retrieve the static resource file indicated. */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String servletPath;
    String pathInfo;

    boolean included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null;
    if (included) {
      servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
      pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
      if (servletPath == null) {
        servletPath = request.getServletPath();
        pathInfo = request.getPathInfo();
      }
    } else {
      included = Boolean.FALSE;
      servletPath = request.getServletPath();
      pathInfo = request.getPathInfo();
    }

    boolean forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null;
    String pathInContext = URIUtil.addPaths(servletPath, pathInfo);

    // The servlet spec says "No file contained in the WEB-INF
    // directory may be served directly a client by the container.
    // However, ... may be exposed using the RequestDispatcher calls."
    // Thus, we only allow these requests for includes and forwards.
    //
    // TODO: I suspect we should allow error handlers here somehow.
    if (isProtectedPath(pathInContext) && !included && !forwarded) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (maybeServeWelcomeFile(pathInContext, included, request, response)) {
      // We served a welcome file (either via redirecting, forwarding, or including).
      return;
    }

    if (pathInContext.endsWith("/")) {
      // N.B.: Resource.addPath() trims off trailing
      // slashes, which may result in us serving files for strange
      // paths (e.g. "/index.html/").  Since we already took care of
      // welcome files above, we just return a 404 now if the path
      // ends with a slash.
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // RFC 2396 specifies which characters are allowed in URIs:
    //
    // http://tools.ietf.org/html/rfc2396#section-2.4.3
    //
    // See also RFC 3986, which specifically mentions handling %00,
    // which would allow security checks to be bypassed.
    for (int i = 0; i < pathInContext.length(); i++) {
      int c = pathInContext.charAt(i);
      if (c < 0x20 || c == 0x7F) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        logger.atWarning().log(
            "Attempted to access file containing control character, returning 400.");
        return;
      }
    }

    // Find the resource
    Resource resource = null;
    try {
      resource = getResource(pathInContext);

      if (resource == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (StringUtil.endsWithIgnoreCase(resource.getName(), ".jsp")) {
        // General paranoia: don't ever serve raw .jsp files.
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Handle resource
      if (resource.isDirectory()) {
        if (included || !fSender.checkIfUnmodified(request, response, resource)) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
      } else {
        if (resource == null || !resource.exists()) {
          logger.atWarning().log("Non existent resource: %s = %s", pathInContext, resource);
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
          if (included || !fSender.checkIfUnmodified(request, response, resource)) {
            fSender.sendData(context, response, included, resource, request.getRequestURI());
          }
        }
      }
    } finally {
      if (resource != null) {
        // TODO: do we need to release.
        // resource.release();
      }
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doGet(request, response);
  }

  @Override
  protected void doTrace(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }

  protected boolean isProtectedPath(String target) {
    target = Ascii.toLowerCase(target);
    return target.contains("/web-inf/") || target.contains("/meta-inf/");
  }

  /**
   * Get Resource to serve.
   *
   * @param pathInContext The path to find a resource for.
   * @return The resource to serve.
   */
  private Resource getResource(String pathInContext) {
    try {
      if (resourceBase != null) {
        return resourceBase.resolve(pathInContext);
      }
    } catch (Exception ex) {
      logger.atWarning().withCause(ex).log("Could not find: %s", pathInContext);
    }
    return null;
  }

  /**
   * Finds a matching welcome file for the supplied path and, if found, serves it to the user. This
   * will be the first entry in the list of configured {@link #welcomeFiles welcome files} that
   * exists within the directory referenced by the path. If the resource is not a directory, or no
   * matching file is found, then <code>null</code> is returned. The list of welcome files is read
   * from the {@link ContextHandler} for this servlet, or <code>"index.jsp" , "index.html"</code> if
   * that is <code>null</code>.
   *
   * @return true if a welcome file was served, false otherwise
   */
  private boolean maybeServeWelcomeFile(
      String path, boolean included, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    if (welcomeFiles == null) {
      System.err.println("No welcome files");
      return false;
    }

    // Add a slash for matching purposes.  If we needed this slash, we
    // are not doing an include, and we're not going to redirect
    // somewhere else we'll redirect the user to add it later.
    if (!path.endsWith("/")) {
      path += "/";
    }

    AppVersion appVersion =
        (AppVersion) getServletContext().getAttribute(JettyConstants.APP_VERSION_CONTEXT_ATTR);
    ServletHandler handler = chandler.getChildHandlerByClass(ServletHandler.class);

    MappedResource<ServletHandler.MappedServlet> defaultEntry = handler.getHolderEntry("/");

    for (String welcomeName : welcomeFiles) {
      String welcomePath = path + welcomeName;
      String relativePath = welcomePath.substring(1);

      if (!Objects.equals(handler.getHolderEntry(welcomePath), defaultEntry)) {
        // It's a path mapped to a servlet.  Forward to it.
        RequestDispatcher dispatcher = request.getRequestDispatcher(path + welcomeName);
        return serveWelcomeFileAsForward(dispatcher, included, request, response);
      }
      if (appVersion.isResourceFile(relativePath)) {
        // It's a resource file.  Forward to it.
        RequestDispatcher dispatcher = request.getRequestDispatcher(path + welcomeName);
        return serveWelcomeFileAsForward(dispatcher, included, request, response);
      }
      if (appVersion.isStaticFile(relativePath)) {
        // It's a static file (served from blobstore).  Redirect to it
        return serveWelcomeFileAsRedirect(path + welcomeName, included, request, response);
      }
    }

    return false;
  }

  private boolean serveWelcomeFileAsRedirect(
      String path, boolean included, HttpServletRequest request, HttpServletResponse response)
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

  private boolean serveWelcomeFileAsForward(
      RequestDispatcher dispatcher,
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

  private void redirectToAddSlash(HttpServletRequest request, HttpServletResponse response)
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
}
