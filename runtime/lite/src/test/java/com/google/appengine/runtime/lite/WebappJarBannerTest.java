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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.io.ByteStreams;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class WebappJarBannerTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  Path testDataPath;
  File app;
  File webInf;
  File webInfLib;
  File webInfClasses;

  @Before
  public void makeApp() throws IOException {
    app = temporaryFolder.newFolder("app");
    webInf = new File(app, "WEB-INF");
    webInfLib = new File(webInf, "lib");
    webInfLib.mkdirs();
    webInfClasses = new File(webInf, "classes");
    webInfClasses.mkdirs();
    Files.write(webInf.toPath().resolve("web.xml"), "Blah blah".getBytes(UTF_8));
  }

  private static void addFileToJar(File source, Path relativeTo, JarOutputStream jos)
      throws Exception {
    if (source.isDirectory()) {
      JarEntry entry = new JarEntry(relativeTo.relativize(source.toPath()) + "/");
      jos.putNextEntry(entry);
      for (File f : source.listFiles()) {
        addFileToJar(f, relativeTo, jos);
      }
      return;
    }

    JarEntry entry = new JarEntry(relativeTo.relativize(source.toPath()).toString());
    jos.putNextEntry(entry);
    try (FileInputStream fis = new FileInputStream(source)) {
      ByteStreams.copy(fis, jos);
      jos.closeEntry();
    }
  }

  @Test
  public void badWar() throws Exception {
    File warFile = temporaryFolder.newFile("app.war");
    Files.write(warFile.toPath(), "This is not a valid war".getBytes(UTF_8));
    IOException ex =
        assertThrows(IOException.class, () -> WebappJarBanner.checkWebappPath(warFile.toPath()));
    assertThat(ex).hasMessageThat().contains("Could not process");
  }

  private void doCheckWebappPath(boolean useWar) throws Exception {
    if (useWar) {
      File warFile = temporaryFolder.newFile("app.war");
      try (FileOutputStream fos = new FileOutputStream(warFile);
          JarOutputStream jos = new JarOutputStream(fos)) {
        addFileToJar(app, app.toPath(), jos);
      }
      WebappJarBanner.checkWebappPath(warFile.toPath());
    } else {
      WebappJarBanner.checkWebappPath(app.toPath());
    }
  }

  @Test
  public void appWithoutJars_works(@TestParameter boolean useWar) throws Exception {
    doCheckWebappPath(useWar);
  }

  @Test
  public void jarInWebappPath_fails(@TestParameter boolean useWar) throws Exception {
    new File(webInfLib, "foo.jar").createNewFile();
    IOException ex = assertThrows(IOException.class, () -> doCheckWebappPath(useWar));
    assertThat(ex).hasMessageThat().contains("WEB-INF/lib/foo.jar");
  }

  @Test
  public void persistenceXmlInWebappPath_fails(@TestParameter boolean useWar) throws Exception {
    new File(webInfClasses, "persistence.xml").createNewFile();
    IOException ex = assertThrows(IOException.class, () -> doCheckWebappPath(useWar));
    assertThat(ex).hasMessageThat().contains("WEB-INF/classes/persistence.xml");
  }
}
