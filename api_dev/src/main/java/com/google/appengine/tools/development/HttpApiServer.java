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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.logging.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Google App Engine API HTTP server, using the SDK API stubs for local testing of API calls.
 *
 */
public class HttpApiServer implements Closeable {
  private static final Logger logger = Logger.getLogger(HttpApiServer.class.getName());

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
  private final ShutdownHandler shutdownHandler;

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
        logger.info(HELP_MESSAGE);
        System.exit(0);
      } else {
        logger.info(HELP_MESSAGE);
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

    HandlerList handlers = new HandlerList();
    ServletHandler handler = new ServletHandler();
    ServletHolder servletHolder = handler.addServletWithMapping(ApiServlet.class, REQUEST_ENDPOINT);
    servletHolder.setInitParameter("java_runtime_port", Integer.toString(appEngineServerPort));
    servletHolder.setInitParameter("java_runtime_host", appEngineServerHost);

    ErrorPageErrorHandler error = new ErrorPageErrorHandler();
    // Make debugging of config issues easy.
    error.setShowStacks(true);
    error.setServer(server);
    server.addBean(error);
    server.setHandler(handler);
    handlers.addHandler(handler);
    shutdownHandler = new ShutdownHandler("stop", false, true);
    handlers.addHandler(shutdownHandler);
    server.setHandler(handlers);
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
      shutdownHandler.sendShutdown();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int extractValue(String argument, String errorMessage) {
    return Integer.parseInt(extractStringValue(argument, errorMessage));
  }

  private static String extractStringValue(String argument, String errorMessage) {
    int indexOfEqualSign = argument.indexOf('=');
    if (indexOfEqualSign == -1) {
      logger.severe(errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }
    return argument.substring(indexOfEqualSign + 1);
  }
}
