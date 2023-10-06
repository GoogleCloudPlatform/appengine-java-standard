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
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.InvalidPathException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.nested.ContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

/**
 * {@code StaticFileFilter} is a {@link Filter} that replicates the
 * static file serving logic that is present in the PFE and AppServer.
 * This logic was originally implemented in {@link
 * LocalResourceFileServlet} but static file serving needs to take
 * precedence over all other servlets and filters.
 *
 */
public class StaticFileFilter implements Filter {
  private static final Logger logger =
      Logger.getLogger(StaticFileFilter.class.getName());

  private StaticFileUtils staticFileUtils;
  private AppEngineWebXml appEngineWebXml;
  private Resource resourceBase;
  private String[] welcomeFiles;
  private String resourceRoot;
  private ContextHandler.APIContext servletContext;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    servletContext = ServletContextHandler.getServletContextHandler(servletContext).getServletContext();
    staticFileUtils = new StaticFileUtils(servletContext);

    // AFAICT, there is no real API to retrieve this information, so
    // we access Jetty's internal state.
    welcomeFiles = servletContext.getContextHandler().getWelcomeFiles();

    appEngineWebXml = (AppEngineWebXml) servletContext.getAttribute(
        "com.google.appengine.tools.development.appEngineWebXml");
    resourceRoot = appEngineWebXml.getPublicRoot();

    try {
      String base;
      if (resourceRoot.startsWith("/")) {
        base = resourceRoot;
      } else {
        base = "/" + resourceRoot;
      }
      // in Jetty 9 "//public" is not seen as "/public".
      resourceBase = ResourceFactory.root().newResource(servletContext.getResource(base));
    } catch (MalformedURLException ex) {
      logger.log(Level.WARNING, "Could not initialize:", ex);
      throw new ServletException(ex);
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Boolean forwarded = (Boolean) request.getAttribute(LocalResourceFileServlet.__FORWARD_JETTY);
    if (forwarded == null) {
      forwarded = Boolean.FALSE;
    }

    Boolean included = (Boolean) request.getAttribute(LocalResourceFileServlet.__INCLUDE_JETTY);
    if (included == null) {
      included = Boolean.FALSE;
    }

    if (forwarded || included) {
      // If we're forwarded or included, the request is already in the
      // runtime and static file serving is not relevant.
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    String servletPath = httpRequest.getServletPath();
    String pathInfo = httpRequest.getPathInfo();
    String pathInContext = URIUtil.addPaths(servletPath, pathInfo);

    if (maybeServeWelcomeFile(pathInContext, httpRequest, httpResponse)) {
      // We served a welcome file.
      return;
    }

    // Find the resource
    Resource resource = null;
    try {
      resource = getResource(pathInContext);

      // Handle resource
      if (resource != null && resource.exists() && !resource.isDirectory()) {
        if (appEngineWebXml.includesStatic(resourceRoot + pathInContext)) {
          // passConditionalHeaders will set response headers, and
          // return true if we also need to send the content.
          if (staticFileUtils.passConditionalHeaders(httpRequest, httpResponse, resource)) {
            staticFileUtils.sendData(httpRequest, httpResponse, false, resource);
          }
          return;
        }
      }
    } finally {
      if (resource != null) {
        // TODO: how to release
        // resource.release();
      }
    }
    chain.doFilter(request, response);
  }

  /**
   * Get Resource to serve.
   * @param pathInContext The path to find a resource for.
   * @return The resource to serve.
   */
  private Resource getResource(String pathInContext) {
    try {
      if (resourceBase != null) {
        return resourceBase.resolve(pathInContext);
      }
    } catch (InvalidPathException ex) {
      // Do not warn for Windows machines for trying to access invalid paths like
      // "hello/po:tato/index.html" that gives a InvalidPathException: Illegal char <:> error.
      // This is definitely not a static resource.
      if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
        logger.log(Level.WARNING, "Could not find: " + pathInContext, ex);
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
   * exists within the directory referenced by the path.
   * @param path
   * @param request
   * @param response
   * @return true if a welcome file was served, false otherwise
   * @throws IOException
   * @throws MalformedURLException
   */
  private boolean maybeServeWelcomeFile(String path,
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

    // First search for static welcome files.
    for (String welcomeName : welcomeFiles) {
      final String welcomePath = path + welcomeName;

      Resource welcomeFile = getResource(path + welcomeName);
      if (welcomeFile != null && welcomeFile.exists()) {
        if (appEngineWebXml.includesStatic(resourceRoot + welcomePath)) {
          // In production, we optimize this case by routing requests
          // for static welcome files directly to the static file
          // (without a redirect).  This logic is here to emulate that
          // case.
          //
          // Note that we want to forward to *our* default servlet,
          // even if the default servlet for this webapp has been
          // overridden.
          RequestDispatcher dispatcher = servletContext.getNamedDispatcher("_ah_default");
          // We need to pass in the new path so it doesn't try to do
          // its own (dynamic) welcome path logic.
          request = new HttpServletRequestWrapper(request) {
              @Override
              public String getServletPath() {
                return welcomePath;
              }

              @Override
              public String getPathInfo() {
                return "";
              }
          };
          return staticFileUtils.serveWelcomeFileAsForward(dispatcher, false, request, response);
        }
      }
    }

    return false;
  }

  @Override
  public void destroy() {}
}
