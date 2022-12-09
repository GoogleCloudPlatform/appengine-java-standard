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
import java.lang.StringBuilder;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Returns memory related info from jmx.
 */
@WebServlet(name = "JMXMemoryInfoServlet", value = "/jmx_memory_info")
public class JMXMemoryInfoServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(JMXMemoryInfoServlet.class.getName());

  @Override
  public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    logger.log(Level.INFO, "JMXMemoryInfoServlet - Executing servlet request for:" + req.getServletPath());
    StringBuilder sb = new StringBuilder();
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html");
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapUsage = memory.getHeapMemoryUsage();
    sb.append(String.format("heap.init=%d\n", heapUsage.getInit()));
    sb.append(String.format("heap.used = %d\n", heapUsage.getUsed()));
    sb.append(String.format("heap.max = %d\n", heapUsage.getMax()));
    sb.append(String.format("heap.committed = %d\n", heapUsage.getCommitted()));
    MemoryUsage nonHeapUsage = memory.getNonHeapMemoryUsage();
    sb.append(String.format("non_heap.init = %d\n", nonHeapUsage.getInit()));
    sb.append(String.format("non_heap.used = %d\n", nonHeapUsage.getUsed()));
    sb.append(String.format("non_heap.max = %d\n", nonHeapUsage.getMax()));
    sb.append(String.format("non_heap.committed = %d\n", nonHeapUsage.getCommitted()));
    ClassLoadingMXBean classLoading = ManagementFactory.getClassLoadingMXBean();
    sb.append(String.format("loaded.classes = %d\n", classLoading.getLoadedClassCount()));
    sb.append(String.format("unloaded.classes = %d\n", classLoading.getUnloadedClassCount()));
    sb.append(String.format("total.loaded.classes = %d\n", classLoading.getTotalLoadedClassCount()));
    ThreadMXBean threading = ManagementFactory.getThreadMXBean();
    sb.append(String.format("thread.count = %d\n", threading.getThreadCount()));
    sb.append(String.format("daemon.thread.count = %d\n", threading.getDaemonThreadCount()));
    sb.append(String.format("total.started.thread.count = %d\n", threading.getTotalStartedThreadCount()));
    sb.append(String.format("peak.thread.count = %d\n", threading.getPeakThreadCount()));
    CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
    sb.append(String.format("compiler.time = %d\n", compilation.getTotalCompilationTime()));
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      String name = gc.getName().replace(' ', '_');
      sb.append(String.format("gc.%s.count = %d\n", name, gc.getCollectionCount()));
      sb.append(String.format("gc.%s.time = %d\n", name, gc.getCollectionTime()));
    }
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      String name = pool.getName().replace(' ', '_');
      sb.append(
          String.format("memory.%s.type = %s\n", name, pool.getType().name()));
      sb.append(String.format("memory.%s.used = %d\n", name, pool.getUsage().getUsed()));
      sb.append(String.format("memory.%s.max = %d\n", name, pool.getUsage().getMax()));
      sb.append(String.format("memory.%s.peak.used = %d\n", name, pool.getPeakUsage().getUsed()));
      sb.append(String.format("memory.%s.peak.max = %d\n", name, pool.getPeakUsage().getMax()));
    }
    resp.getWriter().println(sb.toString());
  }
}
