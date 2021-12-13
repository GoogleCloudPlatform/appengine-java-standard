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

package com.google.appengine.tools.development;

import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineWebXml;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides the backing servlet container support for the {@link DevAppServer},
 * as discovered via {@link ServiceProvider}.
 * <p>
 * More specifically, this interface encapsulates the interactions between the
 * {@link DevAppServer} and the underlying servlet container, which by default
 * uses Jetty.
 *
 */
public interface ContainerService {

  /**
   * Sets up the necessary configuration parameters.
   *
   * @param devAppServerVersion Version of the devAppServer.
   * @param address The address on which the module instance will run
   * @param port The port to which the module instance will be bound.  If 0, an
   *        available port will be selected.
   * @param moduleConfigurationHandle Handle to access and reread the configuration.
   * @param externalResourceDirectory If not {@code null}, a resource directory external
   *        to the applicationDirectory. This will be searched before
   *        applicationDirectory when looking for resources.
   * @param instance the 0 based instance number for this container's instance or
   *        {@link LocalEnvironment#MAIN_INSTANCE}.
   * @param containerConfigProperties Additional properties used in the
   *        configuration of the specific container implementation.  This map travels
   *        across classloader boundaries, so all values in the map must be JRE
   *        classes.
   *
   * @return A LocalServerEnvironment describing the environment in which
   * the module instance is running.
   */
  LocalServerEnvironment configure(String devAppServerVersion, String address, int port,
      ModuleConfigurationHandle moduleConfigurationHandle, File externalResourceDirectory,
      Map<String, Object> containerConfigProperties, int instance, DevAppServer devAppServer);

  /**
   * Create's this containers network connections. After this returns
   * {@link #getAddress}, {@link #getPort} and {@link getHostName} return
   * correct values for this container.
   */
  void createConnection() throws Exception;

  /**
   * Sets the {@link com.google.apphosting.api.ApiProxy.Delegate}.
   * <p>
   * Note that this provides access to the original delegate which was established by
   * the {@link DevAppServer}. Though this delegate is usually available by calling
   * {@Link ApiProxy#getDelegate()} the delegate can be changed by the application so
   * we keep this reference to the original.
   *
   * @param apiProxyDelegate
   */
  void setApiProxyDelegate(ApiProxy.Delegate<?> apiProxyDelegate);

  /**
   * Starts up the servlet container.
   *
   * @throws Exception Any exception from the container will be rethrown as is.
   */
  void startup() throws Exception;

  /**
   * Shuts down the servlet container.
   *
   * @throws Exception Any exception from the container will be rethrown as is.
   */
  void shutdown() throws Exception;

  /**
   * Returns the listener network address, however it's decided during
   * the servlet container deployment.
   */
  String getAddress();

  /**
   * Returns the listener port number, however it's decided during the servlet
   * container deployment.
   */
  int getPort();

  /**
   * Returns the host name of the module instance, however it's decided during the
   * the servlet container deployment.
   */
  String getHostName();

  /**
   * Returns the context representing the currently executing webapp.
   */
  AppContext getAppContext();

  /**
   * Return the AppEngineWebXml configuration of this container
   */
  AppEngineWebXml getAppEngineWebXmlConfig();

  /**
   * Get a set of properties to be passed to each service, based on the
   * AppEngineWebXml configuration.
   *
   * @return the map of properties to be passed to each service.
   */
  Map<String, String> getServiceProperties();

  /**
   * Forwards an HttpRequest request to this container.
   */
  void forwardToServer(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException, ServletException;

}
