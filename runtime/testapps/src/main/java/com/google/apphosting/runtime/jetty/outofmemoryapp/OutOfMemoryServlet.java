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

package com.google.apphosting.runtime.jetty.outofmemoryapp;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet used to prove that the runtime is being launched with {@code -XX:ExitOnOutOfMemoryError}.
 * If so, we expect {@code OutOfMemoryError} to cause an immediate JVM exit, which the calling test
 * will detect. If we don't have the flag, then the thread that got {@code OutOfMemoryError} will
 * die but the JVM will live and the test will fail.
 */
public class OutOfMemoryServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(OutOfMemoryServlet.class.getName());
  private static final int BIG_ARRAY = 2_000_000_000;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      exhaustMemory();
    } catch (OutOfMemoryError e) {
      int count = Arrays.asList(arrays).indexOf(null);
      logger.log(
          Level.SEVERE,
          "Caught OutOfMemoryError which should have caused JVM exit, allocated {0} arrays of {1}"
              + " longs",
          new Object[] {count, BIG_ARRAY});
    }
  }

  // volatile to foil any compiler cleverness that might optimize away the array creation
  private volatile long[][] arrays = new long[10_000][];

  private void exhaustMemory() {
    for (int i = 0; i < arrays.length; i++) {
      arrays[i] = new long[2_000_000_000];
    }
  }
}
