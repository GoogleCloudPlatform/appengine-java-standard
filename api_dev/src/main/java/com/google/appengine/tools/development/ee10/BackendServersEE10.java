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

import com.google.appengine.tools.development.BackendServersBase;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Controls backend servers configured in appengine-web.xml. Each server is started on a separate
 * port. All servers run the same code as the main app. This one is serving jakarta.servlet based
 * applications.
 */
public class BackendServersEE10 extends BackendServersBase {

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
    logger.finest("forwarding request to server: " + server);
    ((ContainerServiceEE10) server.getContainer()).forwardToServer(hrequest, hresponse);
  }
}
