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

package com.google.appengine.runtime.lite;

import static java.util.stream.Collectors.joining;

import com.google.errorprone.annotations.CheckReturnValue;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.stream.Stream;

/**
 * A tool to verify that a Lite runtime app didn't accidentally place jars into WEB-INF/lib or
 * classes into WEB-INF/classes
 */
@CheckReturnValue
public final class WebappJarBanner {

  /**
   * Prevent accidental inclusion of "webapp jars" (jars in WEB-INF/lib) or classes in
   * WEB-INF/classes.
   *
   * <p>The "Lite" runtime does not put either location on the classpath so it's best to warn the
   * app developer of any attempts to put artifacts there.
   *
   * <p>Historically, you deliver code to App Engine Java following the classic Java Servlet
   * Container model: you put jars into WEB-INF/lib. However, this model bypasses your build
   * system's attempts to ensure (ideally just one version) of all dependencies makes it to
   * production. We recommend instead using the standard Jar packaging practice of your build system
   * to bundle your "main" file and your app, which will naturally include both this "Lite" runtime
   * and the rest of the App Engine SDK as dependencies.
   */
  public static void checkWebappPath(Path webappPath) throws IOException {
    if (webappPath.toFile().isFile()) {
      // This is a (zipped) war file.
      try (FileSystem fs = FileSystems.newFileSystem(webappPath, /*loader=*/ (ClassLoader) null)) {
        doCheckWebappPath(fs.getPath("/"));
      } catch (ProviderNotFoundException ex) {
        throw new IOException("Could not process " + webappPath, ex);
      }
    } else {
      doCheckWebappPath(webappPath);
    }
  }

  private static void doCheckWebappPath(Path webappPath) throws IOException {
    checkDir(webappPath.resolve("WEB-INF/lib"));
    checkDir(webappPath.resolve("WEB-INF/classes"));
  }

  private static void checkDir(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }

    String files;
    try (Stream<Path> stream =
        Files.find(
            dir,
            /*maxDepth=*/ 100,
            (p, a) -> !Files.isDirectory(p),
            FileVisitOption.FOLLOW_LINKS)) {
      files = stream.map(Path::toString).collect(joining("\n"));
    }
    if (files.isEmpty()) {
      return;
    }

    throw new IOException(
        "Found contents in "
            + dir
            + ":\n"
            + files
            + "\n"
            + "Please make this code an explicit dependency of your \"main\" entrypoint instead \n"
            + "of bundling it into WEB-INF/lib or WEB-INF/classes.\n\n");
  }

  private WebappJarBanner() {}
}
