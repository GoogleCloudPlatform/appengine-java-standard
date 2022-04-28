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
package app;

import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Returns a trivial binary response, using a default or specified size.
 *
 * <p>Author: hazarm@google.com (Houman Azarm)
 */
@WebServlet(name = "LargeResponseServlet", value = "/large")
public class LargeResponseServlet extends HttpServlet {
  private static Logger logger = Logger.getLogger(LargeResponseServlet.class.getName());

  /**
   * Gets the amount of free memory for the current running jvm by the current free memory + the
   * amount of memory that the jvm may be able to allocate for the process to accommodate the need.
   */
  private long getCurrentFreeMemory() {
    return Runtime.getRuntime().freeMemory() +
        (Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory());
  }

  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    int size = 1024 * 1024;
    if (req.getParameter("size") != null) {
      size = Integer.parseInt(req.getParameter("size"));
    }

    res.setContentType("application/octet-stream");
    ServletOutputStream stream = res.getOutputStream();

    long free = getCurrentFreeMemory();

    logger.info("LargeResponseServlet called with size=" + size + " with " + free + " bytes free");

    if (free < size) {
      // If the request would cause an OOM, then just return server error instead and log.
      logger.warning("Request for " + size + " bytes would exceed the free memory " + free);
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } else {
      logger.info("Free memory prior to writing stream [" + getCurrentFreeMemory() + "]");
      for (int i = 0; i < size; i++) {
        stream.write((byte) 'x');
      }
      logger.info("Free memory after writing stream [" + getCurrentFreeMemory() + "]");
    }
  }
}
