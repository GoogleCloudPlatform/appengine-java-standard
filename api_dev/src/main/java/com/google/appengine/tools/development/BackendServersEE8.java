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

import com.google.common.flogger.GoogleLogger;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Controls backend servers configured in appengine-web.xml. Each server is started on a separate
 * port. All servers run the same code as the main app. This one is serving javax.servlet based
 * applications.
 */
public class BackendServersEE8 extends BackendServersBase {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Forward a request to a specific server and instance. This will call the specified instance
   * request dispatcher so the request is handled in the right server context.
   */
  public void forwardToServer(
      String requestedServer,
      int instance,
      HttpServletRequest hrequest,
      HttpServletResponse hresponse)
      throws IOException, ServletException {
    ServerWrapper server = getServerWrapper(requestedServer, instance);
    logger.atFinest().log("forwarding request to server: %s", server);
    ((ContainerServiceEE8) server.getContainer()).forwardToServer(hrequest, hresponse);
  }
}
