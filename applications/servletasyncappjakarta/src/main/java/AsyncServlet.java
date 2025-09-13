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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ThreadPoolExecutor;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Test Servlet Async application for AppServer tests. */
public class AsyncServlet extends HttpServlet {

  /**
   * Process HTTP request and return simple string.
   *
   * @param req is the HTTP servlet request
   * @param resp is the HTTP servlet response
   * @exception IOException
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    long startTime = System.currentTimeMillis();
    System.out.println(
        "AsyncServlet Start::Name="
            + Thread.currentThread().getName()
            + "::ID="
            + Thread.currentThread().getId());

    String time = req.getParameter("time");
    int millisecs = 1000;
    if (time != null) {
      millisecs = Integer.parseInt(time);
    }
    // max 10 seconds
    if (millisecs > 10000) {
      millisecs = 10000;
    }

    // Puts this request into asynchronous mode, and initializes its AsyncContext.
    AsyncContext asyncContext = req.startAsync(req, resp);
    asyncContext.addListener(new AppAsyncListener());
    ServletRequest servReq = asyncContext.getRequest();

    PrintWriter out = resp.getWriter();
    out.println("isAsyncStarted : " + servReq.isAsyncStarted());
    // This excecutor should be created in the init phase of AppContextListener.
    ThreadPoolExecutor executor =
        (ThreadPoolExecutor) req.getServletContext().getAttribute("executor");

    executor.execute(new LongProcessingRunnable(asyncContext, millisecs));
    long endTime = System.currentTimeMillis();
    System.out.println(
        "AsyncServlet End::Thread Name="
            + Thread.currentThread().getName()
            + "::Thread ID="
            + Thread.currentThread().getId()
            + "::Time Taken="
            + (endTime - startTime)
            + " ms.");
  }
}
