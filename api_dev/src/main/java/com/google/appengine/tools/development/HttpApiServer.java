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

import com.google.common.base.VerifyException;
import com.google.common.flogger.GoogleLogger;
import java.io.Closeable;
import java.io.IOException;
import org.eclipse.jetty.ee8.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

/**
 * Google App Engine API HTTP server, using the SDK API stubs for local testing of API calls.
 *
 */
public class HttpApiServer implements Closeable {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String API_SERVER_PORT = "--api_server_port=";
  private static final String HELP = "--help";
  private static final String HELP_MESSAGE =
      "Usage: com.google.appengine.tools.development.HttpApiServer [--api_server_port=8089]"
          + " [--runtime_server_port=8080] [--runtime_server_host=localhost]";
  private static final String API_SERVER_PORT_ERROR_MESSAGE = "Incorrect --api_server_port value";
  private static final String RUNTIME_SERVER_PORT = "--runtime_server_port=";
  private static final String RUNTIME_SERVER_HOST = "--runtime_server_host=";
  private static final String RUNTIME_PORT_HOST_ERROR_MESSAGE =
      "Incorrect --runtime_server_port or --runtime_server_host value";
  private static final String REQUEST_ENDPOINT = "/rpc_http";
  private final Server server;

  /**
   * Simple command line interface to start an API server locally.
   *
   * @param args the command line arguments: --api_server_port, --api_server_host and
   *     --runtime_server_port supported.
   */
  public static void main(String[] args) throws IOException {
    int apiServerPort = 8089;
    int appEngineServerPort = 8080;
    String appEngineServerHost = "localhost";

    for (String element : args) {
      if (element.startsWith(API_SERVER_PORT)) {
        apiServerPort = extractValue(element, API_SERVER_PORT_ERROR_MESSAGE);
      } else if (element.startsWith(RUNTIME_SERVER_PORT)) {
        appEngineServerPort = extractValue(element, RUNTIME_PORT_HOST_ERROR_MESSAGE);
      } else if (element.startsWith(RUNTIME_SERVER_HOST)) {
        appEngineServerHost = extractStringValue(element, RUNTIME_PORT_HOST_ERROR_MESSAGE);
      } else if (element.startsWith(HELP)) {
        logger.atInfo().log("%s", HELP_MESSAGE);
        System.exit(0);
      } else {
        logger.atInfo().log("%s", HELP_MESSAGE);
        System.exit(1);
      }
    }
    HttpApiServer apiserver =
        new HttpApiServer(apiServerPort, appEngineServerHost, appEngineServerPort);
    apiserver.start(true);
  }

  /**
   * Create an API server. the start() method will have to be called.
   *
   * @param apiServerPort port number of the API server
   * @param appEngineServerPort: port number of the GAE runtime using this API server. Needed for
   *     taskqueue call back only.
   * @param appEngineServerHost: hostname of the GAE runtime using this API server.
   */
  public HttpApiServer(int apiServerPort, String appEngineServerHost, int appEngineServerPort) {
    server = new Server(apiServerPort);
    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    ServletHolder servletHolder = new ServletHolder(ApiServlet.class);
    servletHolder.setInitParameter("java_runtime_port", Integer.toString(appEngineServerPort));
    servletHolder.setInitParameter("java_runtime_host", appEngineServerHost);
    context.addServlet(servletHolder, REQUEST_ENDPOINT);
    ErrorPageErrorHandler error = new ErrorPageErrorHandler();
    // Make debugging of config issues easy.
    error.setShowStacks(true);
    context.setErrorHandler(error);
    server.setHandler(context);
  }

  /**
   * Starts the Api Server.
   *
   * @param blocking: Determines if the Jetty server should be joined.
   */
  public void start(boolean blocking) {
    try {
      server.start();
      if (blocking) {
        server.join();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Shutdown the Api Server. */
  @Override
  public void close() {
    try {
      server.stop();
    } catch (Exception e) {
      throw new VerifyException(e);
    }
  }

  private static int extractValue(String argument, String errorMessage) {
    return Integer.parseInt(extractStringValue(argument, errorMessage));
  }

  private static String extractStringValue(String argument, String errorMessage) {
    int indexOfEqualSign = argument.indexOf('=');
    if (indexOfEqualSign == -1) {
      logger.atSevere().log("%s", errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }
    return argument.substring(indexOfEqualSign + 1);
  }
}
