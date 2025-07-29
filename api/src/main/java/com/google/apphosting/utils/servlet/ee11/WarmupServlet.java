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

package com.google.apphosting.utils.servlet.ee11;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * {@code WarmupServlet} does very little.  It primarily serves as a
 * placeholder that is mapped to the warmup path (/_ah/warmup) and is
 * marked &lt;load-on-startup%gt;.  This causes all other
 * &lt;load-on-startup%gt; servlets to be initialized during warmup
 * requests.
 *
 */
public class WarmupServlet extends HttpServlet {

  private static final Logger logger = Logger.getLogger(WarmupServlet.class.getName());

  @Override
  public void init() {
    logger.fine("Initializing warm-up servlet.");
  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    logger.info("Executing warm-up request.");
    // Ensure that all user jars have been processed by looking for a
    // nonexistent file.
    Thread.currentThread().getContextClassLoader().getResources("_ah_nonexistent");
  }
}
