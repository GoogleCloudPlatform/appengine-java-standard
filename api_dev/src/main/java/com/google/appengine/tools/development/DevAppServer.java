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

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * {@code DevAppServer} launches a local Jetty server (by default) with a single
 * hosted web application.  It can be invoked from the command-line by
 * providing the path to the directory in which the application resides as the
 * only argument.
 *
 * Over time, the environment provided by this class should come to
 * resemble the environment provided to hosted applications in
 * production.  For example, stub applications of all of the API's
 * should be provided, and similar security restrictions should be
 * enforced.
 *
 */
public interface DevAppServer {

  /**
   * {@code DevAppServer} listens on this network address for incoming
   * HTTP requests.  This can be overriden with {@code
   * --address=<addr>}.
   */
  public static final String DEFAULT_HTTP_ADDRESS = "localhost";

  /**
   * {@code DevAppServer} listens on this port for incoming HTTP
   * requests.  This can be overriden with {@code
   * --port=NNNN}.
   */
  public static final int DEFAULT_HTTP_PORT = 8080;

  /**
   * Sets the properties that will be used by the local services to
   * configure themselves. This method must be called before the server
   * has been started.
   *
   * @param properties a, maybe {@code null}, set of properties.
   *
   * @throws IllegalStateException if the server has already been started.
   */
  public void setServiceProperties(Map<String,String> properties);

  /**
   * Get the properties that are used by the local services to
   * configure themselves.
   *
   * @return service properties.
   */
  public Map<String, String> getServiceProperties();

  /**
   * Starts the server.
   *
   * @throws IllegalStateException If the server has already been started or
   * shutdown.
   * @throws com.google.apphosting.utils.config.AppEngineConfigException
   * If no WEB-INF directory can be found or WEB-INF/appengine-web.xml does
   * not exist.
   * @return a latch that will be decremented to zero when the server is shutdown or restarted.
   */
  public CountDownLatch start() throws Exception;


  /**
   * Restart the server to reload disk and class changes.
   *
   * @throws IllegalStateException If the server has not been started or has
   * already been shutdown.
   * @return a latch that will be decremented to zero when the server is shutdown or restarted.
   */
  public CountDownLatch restart() throws Exception;

  /**
   * Shut down the server.
   *
   * @throws IllegalStateException If the server has not been started or has
   * already been shutdown.
   */
  public void shutdown() throws Exception;

  /**
   * Shut down the server after all outstanding requests have completed.
   */
  public void gracefulShutdown();

  /**
   * @return the servlet container listener port number.
   */
  public int getPort();

  /**
   * Returns the {@link AppContext} for the main container.  Useful in embedding
   * scenarios to allow the embedder to install servlets, etc.  Any such
   * modification should be done before calling {@link #start()}.
   *
   * @see ContainerService#getAppContext
   */
  public AppContext getAppContext();



  /**
   * Returns the {@link AppContext} corresponding to the HTTP request (or
   * background thread) associated with the current thread, or {@code null} if
   * the current thread is not associated with any HTTP request (or background
   * thread).
   */
  public AppContext getCurrentAppContext();

  /**
   * Reset the container EnvironmentVariableMismatchSeverity.
   */
  public void setThrowOnEnvironmentVariableMismatch(boolean throwOnMismatch);
}
