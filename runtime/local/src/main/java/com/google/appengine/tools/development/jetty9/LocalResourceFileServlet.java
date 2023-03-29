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

package com.google.appengine.tools.development.jetty9;

import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.WebXml;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.servlet.ServletHandler.MappedServlet;
import org.eclipse.jetty.http.pathmap.MappedResource;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHandler;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.util.resource.ResourceFactory;

/**
 * {@code ResourceFileServlet} is a copy of {@code
 * org.mortbay.jetty.servlet.DefaultServlet} that has been trimmed
 * down to only support the subset of features that we want to take
 * advantage of (e.g. no gzipping, no chunked encoding, no buffering,
 * etc.).  A number of Jetty-specific optimizations and assumptions
 * have also been removed (e.g. use of custom header manipulation
 * API's, use of {@code ByteArrayBuffer} instead of Strings, etc.).
 *
 * <p>A few remaining Jetty-centric details remain, such as use of the
 * {@link ContextHandler.APIContext} class, and Jetty-specific request
 * attributes, but these are specific cases where there is no
 * servlet-engine-neutral API available.  This class also uses Jetty's
 * {@link Resource} class as a convenience, but could be converted to
 * use {@link javax.servlet.ServletContext#getResource(String)} instead.
 *
 */
public class LocalResourceFileServlet extends HttpServlet {
  private static final Logger logger =
      Logger.getLogger(LocalResourceFileServlet.class.getName());

  private StaticFileUtils staticFileUtils;
  private Resource resourceBase;
  private String[] welcomeFiles;
  private String resourceRoot;

  /**
   * Initialize the servlet by extracting some useful configuration
   * data from the current {@link javax.servlet.ServletContext}.
   */
  @Override
  public void init() throws ServletException {
    ContextHandler.APIContext context = (ContextHandler.APIContext) getServletContext();
    staticFileUtils = new StaticFileUtils(context);

    // AFAICT, there is no real API to retrieve this information, so
    // we access Jetty's internal state.
    welcomeFiles = context.getContextHandler().getWelcomeFiles();

    AppEngineWebXml appEngineWebXml = (AppEngineWebXml) getServletContext().getAttribute(
        "com.google.appengine.tools.development.appEngineWebXml");

    resourceRoot = appEngineWebXml.getPublicRoot();
    try {

      String base;
      if (resourceRoot.startsWith("/")) {
        base = resourceRoot;
      } else {
        base = "/" + resourceRoot;
      }
      // In Jetty 9 "//public" is not seen as "/public" .
      resourceBase = ResourceFactory.root().newResource(context.getResource(base));
    } catch (MalformedURLException ex) {
      logger.log(Level.WARNING, "Could not initialize:", ex);
      throw new ServletException(ex);
    }
  }

  public static final java.lang.String __INCLUDE_JETTY = "javax.servlet.include.request_uri";
  public static final java.lang.String __INCLUDE_SERVLET_PATH =
      "javax.servlet.include.servlet_path";
  public static final java.lang.String __INCLUDE_PATH_INFO = "javax.servlet.include.path_info";
  public static final java.lang.String __FORWARD_JETTY = "javax.servlet.forward.request_uri";

  /**
   * Retrieve the static resource file indicated.
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String servletPath;
    String pathInfo;

    AppEngineWebXml appEngineWebXml = (AppEngineWebXml) getServletContext().getAttribute(
        "com.google.appengine.tools.development.appEngineWebXml");

    WebXml webXml = (WebXml) getServletContext().getAttribute(
        "com.google.appengine.tools.development.webXml");

    Boolean forwarded = request.getAttribute(__FORWARD_JETTY) != null;
    if (forwarded == null) {
      forwarded = Boolean.FALSE;
    }

    Boolean included = request.getAttribute(__INCLUDE_JETTY) != null;
    if (included != null && included) {
      servletPath = (String) request.getAttribute(__INCLUDE_SERVLET_PATH);
      pathInfo = (String) request.getAttribute(__INCLUDE_PATH_INFO);
      if (servletPath == null) {
        servletPath = request.getServletPath();
        pathInfo = request.getPathInfo();
      }
    } else {
      included = Boolean.FALSE;
      servletPath = request.getServletPath();
      pathInfo = request.getPathInfo();
    }

    String pathInContext = URIUtil.addPaths(servletPath, pathInfo);

    if (maybeServeWelcomeFile(pathInContext, included, request, response)) {
      // We served a welcome file (either via redirecting, forwarding, or including).
      return;
    }

    // Find the resource
    Resource resource = null;
    try {
      resource = getResource(pathInContext);

      // Handle resource
      if (resource != null && resource.isDirectory()) {
        if (included || staticFileUtils.passConditionalHeaders(request, response, resource)) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
      } else {
        if (resource == null || !resource.exists()) {
          logger.warning("No file found for: " + pathInContext);
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
          boolean isStatic = appEngineWebXml.includesStatic(resourceRoot + pathInContext);
          boolean isResource = appEngineWebXml.includesResource(
              resourceRoot + pathInContext);
          boolean usesRuntime = webXml.matches(pathInContext);
          Boolean isWelcomeFile = (Boolean)
              request.getAttribute("com.google.appengine.tools.development.isWelcomeFile");
          if (isWelcomeFile == null) {
            isWelcomeFile = false;
          }

          if (!isStatic && !usesRuntime && !(included || forwarded)) {
            logger.warning(
                "Can not serve "
                    + pathInContext
                    + " directly.  "
                    + "You need to include it in <static-files> in your "
                    + "appengine-web.xml.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          } else if (!isResource && !isWelcomeFile && (included || forwarded)) {
            logger.warning(
                "Could not serve "
                    + pathInContext
                    + " from a forward or "
                    + "include.  You need to include it in <resource-files> in "
                    + "your appengine-web.xml.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          }
          // passConditionalHeaders will set response headers, and
          // return true if we also need to send the content.
          if (included || staticFileUtils.passConditionalHeaders(request, response, resource)) {
            staticFileUtils.sendData(request, response, included, resource);
          }
        }
      }
    } finally {
      if (resource != null) {
        // TODO: how to release
        // resource.release();
      }
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doGet(request, response);
  }

  /**
   * Get Resource to serve.
   * @param pathInContext The path to find a resource for.
   * @return The resource to serve.  Can be null.
   */
  private Resource getResource(String pathInContext) {
    try {
      if (resourceBase != null) {
        return resourceBase.resolve(pathInContext);
      }
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Could not find: " + pathInContext, t);
    }
    return null;
  }

  /**
   * Finds a matching welcome file for the supplied path and, if
   * found, serves it to the user.  This will be the first entry in
   * the list of configured {@link #welcomeFiles welcome files} that
   * exists within the directory referenced by the path.  If the
   * resource is not a directory, or no matching file is found, then
   * <code>null</code> is returned.  The list of welcome files is read
   * from the {@link ContextHandler} for this servlet, or
   * <code>"index.jsp" , "index.html"</code> if that is
   * <code>null</code>.
   *
   * @return true if a welcome file was served, false otherwise
   * @throws IOException
   * @throws MalformedURLException
   */
  private boolean maybeServeWelcomeFile(String path,
                                        boolean included,
                                        HttpServletRequest request,
                                        HttpServletResponse response)
      throws IOException, ServletException {
    if (welcomeFiles == null) {
      return false;
    }

    // Add a slash for matching purposes.  If we needed this slash, we
    // are not doing an include, and we're not going to redirect
    // somewhere else we'll redirect the user to add it later.
    if (!path.endsWith("/")) {
      path += "/";
    }

    AppEngineWebXml appEngineWebXml = (AppEngineWebXml) getServletContext().getAttribute(
        "com.google.appengine.tools.development.appEngineWebXml");

    ContextHandler.APIContext context = (ContextHandler.APIContext) getServletContext();
    ServletHandler handler = ((WebAppContext) context.getContextHandler()).getServletHandler();
    MappedResource<MappedServlet> defaultEntry = handler.getHolderEntry("/");
    MappedResource<MappedServlet> jspEntry = handler.getHolderEntry("/foo.jsp");

    // Search for dynamic welcome files.
    for (String welcomeName : welcomeFiles) {
      String welcomePath = path + welcomeName;
      String relativePath = welcomePath.substring(1);

      MappedResource<MappedServlet> entry = handler.getHolderEntry(welcomePath);
      if (!Objects.equals(entry, defaultEntry) && !Objects.equals(entry, jspEntry)) {
        // It's a path mapped to a servlet.  Forward to it.
        RequestDispatcher dispatcher = request.getRequestDispatcher(path + welcomeName);
        return staticFileUtils.serveWelcomeFileAsForward(dispatcher, included, request, response);
      }

      Resource welcomeFile = getResource(path + welcomeName);
      if (welcomeFile != null && welcomeFile.exists()) {
        if (!Objects.equals(entry, defaultEntry)) {
          RequestDispatcher dispatcher = request.getRequestDispatcher(path + welcomeName);
          return staticFileUtils.serveWelcomeFileAsForward(dispatcher, included, request, response);
        }
        if (appEngineWebXml.includesResource(relativePath)) {
          // It's a resource file.  Forward to it.
          RequestDispatcher dispatcher = request.getRequestDispatcher(path + welcomeName);
          return staticFileUtils.serveWelcomeFileAsForward(dispatcher, included, request, response);
        }
      }
      RequestDispatcher namedDispatcher = context.getNamedDispatcher(welcomeName);
      if (namedDispatcher != null) {
        // It's a servlet name (allowed by Servlet 2.4 spec).  We have
        // to forward to it.
        return staticFileUtils.serveWelcomeFileAsForward(namedDispatcher, included,
                                                         request, response);
      }
    }

    return false;
  }
}
