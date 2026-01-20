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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.appengine.api.ThreadManager;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/** Simple ThreadPool Servlet */
@WebServlet(name = "ThreadPoolServlet", value = "/threadpool")
public class ThreadPoolServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    printTimeStamp("start 1");
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html");
    ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              printTimeStamp("start 2");
              Thread thread = ThreadManager.currentRequestThreadFactory().newThread(runnable);
              printTimeStamp("start 3");
              thread.setName("ThreadPoolTest Thread");
              thread.setDaemon(true);
              return thread;
            });
    Runnable beeper =
        () -> {
          printTimeStamp("in beeper");
          System.out.println("beep");
          executor.shutdown();
        };

    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = executor.schedule(beeper, 1, SECONDS);
    resp.getWriter().println("Request completed.");
    printTimeStamp("end");
    // We intentionally do NOT shutdown the executor service in this method before returning.
    // The runtime will not wait for daemon threads to complete,
    // and so the request should still finish, even though we have an active daemon thread.
    // Then when the "beeper" runs, it will shut down the executor.
  }

  private static void printTimeStamp(String message) {
    System.out.println(
        "ThreadPoolServlet "
            + message
            + ": "
            + new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()));
  }
}
