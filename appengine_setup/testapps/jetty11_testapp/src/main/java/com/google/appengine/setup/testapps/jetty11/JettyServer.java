/*
 * Copyright 2022 Google LLC
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
package com.google.appengine.setup.testapps.jetty11;

import com.google.appengine.setup.testapps.jetty11.servlets.DatastoreTestServlet;
import com.google.appengine.setup.testapps.jetty11.servlets.GAEInfoServlet;
import com.google.appengine.setup.testapps.jetty11.servlets.HomeServlet;
import com.google.appengine.setup.testapps.jetty11.servlets.ImageProcessingServlet;
import com.google.appengine.setup.testapps.jetty11.servlets.MemcacheTestServlet;
import com.google.appengine.setup.testapps.jetty11.servlets.StatusServlet;
import com.google.appengine.setup.testapps.jetty11.servlets.TaskQueueTestServlet;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

class JettyServer {

    private Server server;

    public static void main(String[] args) throws Exception {
        JettyServer jettyServer = new JettyServer();
        jettyServer.start();
    }

    void start() throws Exception {
        int maxThreads = 100;
        int minThreads = 10;
        int idleTimeout = 120;

        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);

        server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.setConnectors(new Connector[] { connector });

        ServletHandler servletHandler = new ServletHandler();
        server.setHandler(servletHandler);

        servletHandler.addFilterWithMapping(ApiProxyFilter.class, "/*",
                EnumSet.of(DispatcherType.REQUEST));

        servletHandler.addServletWithMapping(HomeServlet.class, "/");
        servletHandler.addServletWithMapping(StatusServlet.class, "/status");
        servletHandler.addServletWithMapping(ImageProcessingServlet.class, "/image");
        servletHandler.addServletWithMapping(GAEInfoServlet.class, "/system");
        servletHandler.addServletWithMapping(DatastoreTestServlet.class, "/datastore");
        servletHandler.addServletWithMapping(TaskQueueTestServlet.class, "/taskqueue");
        servletHandler.addServletWithMapping(MemcacheTestServlet.class, "/memcache");

        server.start();
    }
}