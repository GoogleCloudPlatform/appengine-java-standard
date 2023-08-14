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

package com.google.apphosting.runtime.jetty.sharedthreadpoolapp;

import com.google.appengine.api.ThreadManager;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SharedThreadPoolServlet extends HttpServlet {
  /**
   * A thread pool that is shared between every request to this servlet. We don't recommend doing
   * this, but the servlet tests that the thread pool does not get shut down if you do. The code
   * here uses the <a href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">
   * holder pattern</a> so that we're sure there actually is a current request when we create the
   * thread pool.
   */
  private static class ThreadPoolHolder {
    static final ExecutorService sharedThreadPool =
        Executors.newCachedThreadPool(ThreadManager.currentRequestThreadFactory());
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    if ("true".equals(req.getParameter("setShutdownProperty"))) {
      System.setProperty("com.google.appengine.force.thread.pool.shutdown", "true");
    }
    ExecutorService threadPool = ThreadPoolHolder.sharedThreadPool;
    Future<?> doNothingResult = threadPool.submit(() -> {});
    checkFuture(doNothingResult);
    resp.setContentType("text/plain");
    resp.getWriter().println("OK");
  }

  private static void checkFuture(Future<?> future) {
    try {
      future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
