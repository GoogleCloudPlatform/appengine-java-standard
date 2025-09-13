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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Simple App context listener that creates a ThreadPoolExecutor that creates Deamon threads, and
 * stores it in the ServletContext attribute named "executor".
 */
public class AppContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {

    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            /* corePoolSize= */ 100,
            /* maximumPoolSize= */ 200,
            /* keepAliveTime= */ 50000L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(100),
            new DaemonThreadFactory());
    servletContextEvent.getServletContext().setAttribute("executor", executor);
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    ThreadPoolExecutor executor =
        (ThreadPoolExecutor) servletContextEvent.getServletContext().getAttribute("executor");
    executor.shutdown();
  }

  static class DaemonThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, "created via ThreadPoolExecutor");
      thread.setDaemon(true);
      return thread;
    }
  }
}
