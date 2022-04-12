/*
 * Copyright 2022 Google LLC
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

package com.google.apphosting.runtime.jetty94;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.resource.ResourceExtractor;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for AppEngineWebAppContext. */
@RunWith(JUnit4.class)
public final class AppEngineWebAppContextTest {
  private static final String PACKAGE_PATH =
      AppEngineWebAppContextTest.class.getPackage().getName().replace('.', '/');
  private static final String PROJECT_RESOURCE_NAME =
      String.format("%s/mytestproject", PACKAGE_PATH);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path expandedAppDir;
  private Path zippedAppDir;

  @Before
  public void setUp() throws Exception {
    Path projPath = Paths.get(temporaryFolder.newFolder(PROJECT_RESOURCE_NAME).getPath());
    expandedAppDir = projPath.resolve("100.mydeployment");
    ResourceExtractor.toFile(PROJECT_RESOURCE_NAME, projPath.toString());

    // Zip the app into a jar, so we can stimulate war expansion:
    zippedAppDir = projPath.resolveSibling("mytestproject.jar");
    try (FileOutputStream fos = new FileOutputStream(zippedAppDir.toFile());
        JarOutputStream jos = new JarOutputStream(fos)) {
      addFileToJar(expandedAppDir, expandedAppDir, jos);
    }
  }

  private void addFileToJar(Path source, Path relativeTo, JarOutputStream jos) throws Exception {
    if (source.toFile().isDirectory()) {
      JarEntry entry = new JarEntry(relativeTo.relativize(source) + "/");
      jos.putNextEntry(entry);
      for (File f : source.toFile().listFiles()) {
        addFileToJar(f.toPath(), relativeTo, jos);
      }
      return;
    }

    JarEntry entry = new JarEntry(relativeTo.relativize(source).toString());
    jos.putNextEntry(entry);
    try (FileInputStream fis = new FileInputStream(source.toFile())) {
      ByteStreams.copy(fis, jos);
      jos.closeEntry();
    }
  }

  /** Given a (zipped) WAR file, AppEngineWebAppContext extracts it by default. */
  @Test
  public void extractsWar() throws Exception {
    AppEngineWebAppContext context =
        new AppEngineWebAppContext(zippedAppDir.toFile(), "test server");

    Path extractedWarPath = Paths.get(context.getWar());
    assertThat(extractedWarPath.resolve("WEB-INF/appengine-generated/app.yaml").toFile().exists())
        .isTrue();
    assertThat(context.getBaseResource().getURI())
        .isEqualTo(extractedWarPath.toAbsolutePath().toUri());
    assertThat(context.getTempDirectory()).isEqualTo(extractedWarPath.toFile());
  }

  /** Given an already-expanded WAR file, AppEngineWebAppContext accepts it as-is. */
  @Test
  public void acceptsUnpackedWar() throws Exception {
    AppEngineWebAppContext context =
        new AppEngineWebAppContext(expandedAppDir.toFile(), "test server");

    assertThat(
            Paths.get(context.getWar())
                .resolve("WEB-INF/appengine-generated/app.yaml")
                .toFile()
                .exists())
        .isTrue();
    assertThat(context.getBaseResource().getURI())
        .isEqualTo(expandedAppDir.toAbsolutePath().toUri());
    assertThat(context.getTempDirectory()).isEqualTo(expandedAppDir.toFile());
  }

  /** Given a (zipped) WAR file, AppEngineWebAppContext doesn't extract it when told to not. */
  @Test
  public void doesntExtractWar() throws Exception {
    AppEngineWebAppContext context =
        new AppEngineWebAppContext(zippedAppDir.toFile(), "test server", /*extractWar =*/ false);

    assertThat(context.getWar()).isEqualTo(zippedAppDir.toString());
    assertThat(context.getBaseResource()).isNull();
    assertThat(context.getTempDirectory()).isNull();
  }
}
