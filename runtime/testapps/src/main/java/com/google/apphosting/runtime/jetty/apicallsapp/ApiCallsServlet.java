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

package com.google.apphosting.runtime.jetty.apicallsapp;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import com.google.apphosting.api.ApiProxy;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that accepts a query parameter, {@code ?count=N}, and performs N asynchronous API calls
 * in parallel. It waits for all of them to complete before returning.
 */
@WebServlet(urlPatterns = "/*")
public class ApiCallsServlet extends HttpServlet {
  @SuppressWarnings("CatchAndPrintStackTrace")
  // We don't know what the http server will do if we let the exception propagate.
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");
    PrintWriter writer = resp.getWriter();
    boolean ok = true;
    try {
      handle(req);
    } catch (Throwable t) {
      ok = false;
      t.printStackTrace(writer);
    }
    // The runtime puts every incoming request in its own thread group, and interrupts threads in
    // that thread group when the main request thread returns. If the API client created new threads
    // in the same thread group then those threads would get interrupted, which would be bad. So
    // we check that there are no other threads.
    if (ok) {
      List<Thread> threads = threadsInMyThreadGroup();
      if (threads.size() != 1) {
        writer.println("More than one thread in my thread group: " + threads);
      } else {
        writer.println("OK");
      }
    }
  }

  private static List<Thread> threadsInMyThreadGroup() {
    Thread[] threads = new Thread[1000];
    ThreadGroup myThreadGroup = Thread.currentThread().getThreadGroup();
    int n = myThreadGroup.enumerate(threads, /* recurse= */ true);
    return stream(threads).limit(n).collect(toList());
  }

  private void handle(HttpServletRequest req) throws ExecutionException, InterruptedException {
    String countString = req.getParameter("count");
    int count;
    try {
      count = Integer.parseInt(countString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Expected ?count=N in the URL", e);
    }
    List<Future<byte[]>> futures = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Future<byte[]> future = ApiProxy.makeAsyncCall("testpackage", "testmethod", new byte[0]);
      futures.add(future);
    }
    for (Future<byte[]> future : futures) {
      byte[] bytes = future.get();
      if (bytes.length > 0) {
        throw new AssertionError("Expected empty array but length was " + bytes.length);
      }
    }
  }
}
