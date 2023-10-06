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

package com.google.appengine.tools.development.ee10;

import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.WebModule;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * This filter is not currently used.  It was originally written for
 * IBM so they could see how to use our API's from their own webserver.
 * We kept it around because we'd ultimately like to use this ourselves but
 * we don't currently have a way to ensure that this filter runs before any
 * other filters get initialized.
 *
 * {@code LocalApiProxyServletFilter} is a servlet {@link Filter} that
 * sets up {@link ApiProxy} for use with the stub API implementations.
 *
 * <p>There are two parts to this:<ul>

 * <li>At initialization, this filter installs a {@link
 * ApiProxy.Delegate} instance that locates any API stub
 * implementations on the classpath and registers them for use by
 * future requests.  It also looks for an App Engine-specific
 * deployment descriptor ({@code WEB-INF/appengine-web.xml}) and
 * parses it to obtain some metadata about the application
 * (e.g. application identifier).</li>
 *
 * <li>Around each request, a {@link ApiProxy.Environment} instance is
 * installed into a {@link ThreadLocal} managed by {@link ApiProxy}.
 * This environment instance contains the application metadata that
 * was extracted earlier, and also provides access to the
 * authentication data maintained by the stub implementation of the
 * Users API.</li> </ul>
 *
 * N.B. Does not support Modules.
 *
 */
@Deprecated
public class LocalApiProxyServletFilter implements Filter {
  private static final Logger logger = Logger.getLogger(LocalApiProxyServletFilter.class.getName());
  private static final String AE_WEB_XML = "/WEB-INF/appengine-web.xml";

  private AppEngineWebXml appEngineWebXml;

  /**
   * Register a custom {@link ApiProxy.Delegate instance}.
   */
  @Override
  public void init(FilterConfig config) {
    // We want to use local (stub) implementations of any API.  This
    // will search our classpath for services that contain the
    // @AutoService annotation and register them.
    logger.info("Filter initialization invoked -- registering ApiProxy delegate.");
    ApiProxyLocalFactory factory = new ApiProxyLocalFactory();
    ApiProxy.setDelegate(factory.create(getLocalServerEnvironment(config)));

    logger.info("Parsing custom deployment descriptor (" + AE_WEB_XML + ").");
    ServletAppEngineWebXmlReader reader =
        new ServletAppEngineWebXmlReader(config.getServletContext());
    appEngineWebXml = reader.readAppEngineWebXml();
    logger.info("Application identifier is: " + appEngineWebXml.getAppId());
  }

  private LocalServerEnvironment getLocalServerEnvironment(final FilterConfig config) {
    return new LocalServerEnvironment() {

      @Override
      public File getAppDir() {
        return new File(".");
      }

      @Override
      public String getAddress() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getPort() {
        throw new UnsupportedOperationException();
      }

      @Override
      public String getHostName() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void waitForServerToStart() { }

      @Override
      public boolean simulateProductionLatencies() {
        return true;
      }

      @Override
      public boolean enforceApiDeadlines() {
        return false;
      }
    };
  }

  /**
   * Remove the custom {@link ApiProxy.Delegate} instance.
   */
  @Override
  public void destroy() {
    logger.info("Filter destruction invoked -- removing delegate.");
    ApiProxy.setDelegate(null);
  }

  /**
   * Wrap the request with calls to the environment-management method
   * on {@link ApiProxy}.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // We depend on cookies for authentication, so upcast the request.
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    logger.fine("Filter received a request, setting environment ThreadLocal.");
    ApiProxy.setEnvironmentForCurrentThread(new LocalHttpRequestEnvironment(
        appEngineWebXml.getAppId(), WebModule.getModuleName(appEngineWebXml),
        appEngineWebXml.getMajorVersionId(), LocalEnvironment.MAIN_INSTANCE,
        request.getLocalPort(), httpRequest, null, null));
    try {
      chain.doFilter(request, response);
    } finally {
      logger.fine("Request has completed.  Removing environment ThreadLocal.");
      ApiProxy.clearEnvironmentForCurrentThread();
    }
  }

  /**
   * {@code ServletAppEngineWebXmlReader} is a specialization of
   * {@link AppEngineWebXmlReader} that reads the custom deployment
   * descriptor ({@code WEB-INF/appengine-web.xml}) from a {@link
   * ServletContext} rather than looking for an actual file on the
   * filesystem.
   */
  private static class ServletAppEngineWebXmlReader extends AppEngineWebXmlReader {
    private final ServletContext context;

    public ServletAppEngineWebXmlReader(ServletContext context) {
      super("");  // Used for debugging -- errors will just report AE_WEB_XML.
      this.context = context;
    }

    @Override
    protected InputStream getInputStream() {
      return context.getResourceAsStream(AE_WEB_XML);
    }
  }
}
