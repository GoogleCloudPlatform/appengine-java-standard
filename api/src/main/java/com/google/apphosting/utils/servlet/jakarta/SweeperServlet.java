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

package com.google.apphosting.utils.servlet.jakarta;

import com.google.common.base.Ascii;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jakarta Servlet endpoint invoked by App Engine Cron to periodically query and process pending
 * Cloud Tasks stored in Datastore ({@code _AE_PendingCloudTask}).
 *
 * <p>Acts as a reliable background sweeper to recover and dispatch transactional push tasks that
 * were not immediately dispatched by real-time post-commit execution.
 *
 * <p>Example {@code web.xml} configuration:
 *
 * <pre>
 *   &lt;servlet&gt;
 *     &lt;servlet-name&gt;cloudtask_sweep&lt;/servlet-name&gt;
 *     &lt;servlet-class&gt;com.google.apphosting.utils.servlet.jakarta.SweeperServlet&lt;/servlet-class&gt;
 *   &lt;/servlet&gt;
 *   &lt;servlet-mapping&gt;
 *     &lt;servlet-name&gt;cloudtask_sweep&lt;/servlet-name&gt;
 *     &lt;url-pattern&gt;/_ah/cloudtask/sweep&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 *
 * <p>Example {@code cron.xml} configuration:
 *
 * <pre>
 *   &lt;cron&gt;
 *     &lt;url&gt;/_ah/cloudtask/sweep&lt;/url&gt;
 *     &lt;description&gt;Sweep pending transactional tasks&lt;/description&gt;
 *     &lt;schedule&gt;every 1 minutes&lt;/schedule&gt;
 *   &lt;/cron&gt;
 * </pre>
 */
public class SweeperServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(SweeperServlet.class.getName());

  static final String HEADER_CRON = "X-AppEngine-Cron";
  static final String HEADER_HTTP_CRON = "X-Appengine-Cron";
  private static final String TASK_PROCESSOR_CLASSNAME =
      "com.google.appengine.api.taskqueue.TaskProcessor";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String cronHeader = req.getHeader(HEADER_CRON);
    String httpCronHeader = req.getHeader(HEADER_HTTP_CRON);
    boolean isCron =
        (cronHeader != null && Ascii.equalsIgnoreCase("true", cronHeader))
            || (httpCronHeader != null && Ascii.equalsIgnoreCase("true", httpCronHeader));
    String env = System.getProperty("com.google.appengine.runtime.environment", "");
    boolean isDev =
        Ascii.equalsIgnoreCase("Development", env)
            || System.getProperty("java.class.path", "").contains("appengine-local-runtime");
    if (!isCron && !isDev) {
      resp.sendError(
          HttpServletResponse.SC_FORBIDDEN,
          "Access denied: endpoint only accessible via App Engine Cron.");
      return;
    }

    logger.info("*** CLOUDTASK: Sweeper Cron Triggered ***");
    int processedCount = 0;
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Class<?> taskProcessorClass =
          (loader == null)
              ? Class.forName(TASK_PROCESSOR_CLASSNAME)
              : loader.loadClass(TASK_PROCESSOR_CLASSNAME);
      Method sweepMethod = taskProcessorClass.getDeclaredMethod("sweep");
      sweepMethod.setAccessible(true);
      processedCount = (Integer) sweepMethod.invoke(null);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to invoke TaskProcessor.sweep via reflection", e);
    }
    resp.getWriter().println("Sweeper completed. Processed " + processedCount + " tasks.");
  }
}
