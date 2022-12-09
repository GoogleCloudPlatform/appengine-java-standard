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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.StringBuilder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Returns memory related info for every running process from /tmp/PID/status.
 */
@WebServlet(name = "ProcStatusMemoryInfoServlet", value = "/proc_status")
public class ProcStatusMemoryInfoServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(ProcStatusMemoryInfoServlet.class.getName());

  public static boolean isPid(String name) {
    if (name == null) {
        return false;
    }
    try {
        int pid  = Integer.decode(name);
        return pid > 0;
    } catch (NumberFormatException nfe) {
        return false;
    }
  }

  private static ImmutableList<String> listPidDirs() {
    return stream(new File("/proc").listFiles())
        .filter(file -> file.isDirectory() && isPid(file.getName()))
        .map(File::getName)
        .collect(toImmutableList());
  }

  private static String getMemoryInfo(String pid) throws IOException {
    StringBuilder result = new StringBuilder();
    String file = "/proc/" + pid + "/status";
    try (BufferedReader br = Files.newBufferedReader(Paths.get(file), UTF_8)) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("Name:")) {
          String name = line.replace("Name:\t", "");
          result.append(name);
        }
        if (line.startsWith("VmSize:")) {
          String size = line.replaceAll("VmSize:\\s*","");
          size = size.replace("kB", "");
          result.append(" VmSizeKB:").append(size);
        }
        if (line.startsWith("VmRSS:")) {
          String size = line.replaceAll("VmRSS:\\s*","");
          size = size.replace("kB", "");
          result.append(" VmRSSKB:").append(size);
        }
        if (line.startsWith("VmData:")) {
          String size = line.replaceAll("Data:\\s*","");
          size = size.replace("kB", "");
          result.append(" VmDataKB:").append(size);
        }
    }
  }
  return result.toString();
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    logger.log(Level.INFO, "ProcStatusMemoryInfoServlet - Executing servlet request for:" + req.getServletPath());
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html");
    resp.getWriter().println("ProcStatusMemoryInfoServlet");
    ImmutableList<String> pidDirs = listPidDirs();
    resp.getWriter().println("pidDirs="+pidDirs);
    for (String pidDir:pidDirs) {
      resp.getWriter().println(getMemoryInfo(pidDir));
    }
  }
}
