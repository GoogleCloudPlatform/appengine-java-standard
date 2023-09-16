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

package com.google.appengine.tools.development.jetty;

import java.io.File;
import java.lang.reflect.Method;
import org.eclipse.jetty.quickstart.QuickStartWebApp;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.IO;

/**
 * Simple generator of the Jetty quickstart-web.xml based on an exploded War
 * directory. The file, if present will be deleted before being regenerated.
 *
 **/
public class QuickStartGenerator {

  /**
   * 2 arguments are expected: the path to a Web Application Archive root directory.
   * and the path to a webdefault.xml file.
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.println("Usage: pass 2 arguments:");
      System.out.println("       first argument contains the path to a web application");
      System.out.println("       second argument contains the path to a webdefault.xml file.");
      System.exit(1);
    }
    String path = args[0];
    String webDefault = args[1];
    File fpath = new File(path);
    if (!fpath.exists()) {
      System.out.println("Error: Web Application directory does not exist: " + fpath);
      System.exit(1);
    }
    File fWebDefault = new File(webDefault);
    if (!fWebDefault.exists()) {
      System.out.println("Error: webdefault.xml file does not exist: " + fWebDefault);
      System.exit(1);
    }
    fpath = new File(fpath, "WEB-INF");
    if (!fpath.exists()) {
      System.out.println("Error: Path does not exist: " + fpath);
      System.exit(1);
    }
    // Keep Jetty silent for INFO messages.
    System.setProperty("org.eclipse.jetty.server.LEVEL", "WARN");
    System.setProperty("org.eclipse.jetty.quickstart.LEVEL", "WARN");
    boolean success = generate(path, fWebDefault);
    System.exit(success ? 0 : 1);
  }

  public static boolean generate(String appDir, File webDefault) {
    // We delete possible previously generated quickstart-web.xml
    File qs = new File(appDir, "WEB-INF/quickstart-web.xml");
    if (qs.exists()) {
      boolean deleted = IO.delete(qs);
      if (!deleted) {
        System.err.println("Error: File exists and cannot be deleted: " + qs);
        return false;
      }
    }
    try {
      final Server server = new Server();
      QuickStartWebApp webapp = new QuickStartWebApp();
      webapp.setResourceBase(appDir);
      webapp.setPreconfigure(true);
      try {
        // Tricky: we compile the code with the latest Jetty 9.3.18 which has the new
        // setGenerateOrigin(boolean) API.
        // But we used the compiled class with 2 different Jetty versions (9.2 for the deprecated
        // Managed VMs, and 9.3.18 for the new Jetty9/Java 8 standard runtime).
        // See https://github.com/eclipse/jetty.project/issues/877 for context, fixed for us.
        Method setGenerateOrigin =
            QuickStartWebApp.class.getMethod("setGenerateOrigin", boolean.class);
        setGenerateOrigin.invoke(webapp, true);
      } catch (ReflectiveOperationException e) {
        // Ignore, the GAE SDK has been called with the vm:true Jetty 9.2 classpath.
      }
      webapp.setDefaultsDescriptor(webDefault.getCanonicalPath());
      server.setHandler(webapp);
      server.start();
      server.stop();
      if (qs.exists()) {
        return true;
      } else {
        System.out.println("Failed to generate " + qs);
        return false;
      }
    } catch (Exception e) {
      System.out.println("Error during quick start generation: " + e);
      return false;
    }
  }
}
