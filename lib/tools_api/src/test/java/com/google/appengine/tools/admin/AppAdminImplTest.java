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

package com.google.appengine.tools.admin;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AppAdminImpl}. */
@RunWith(JUnit4.class)
public class AppAdminImplTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private GenericApplication app;
  @Mock private PrintWriter errorWriter;
  @Mock private ApplicationProcessingOptions appOptions;

  private AppAdminImpl appAdmin;

  @Before
  public void setUp() {
    when(app.getAppId()).thenReturn("app1");
    when(app.getModule()).thenReturn("module1");
    when(app.getVersion()).thenReturn("v1");

    appAdmin = new AppAdminImpl(app, errorWriter, appOptions);
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testStageApplicationWithDefaultResourceLimits() throws Exception {
    temporaryFolder.create();
    File stagingDir = temporaryFolder.getRoot();
    when(app.createStagingDirectory(any(ApplicationProcessingOptions.class), eq(stagingDir)))
        .thenReturn(new File("a/b"));

    appAdmin.stageApplicationWithDefaultResourceLimits(stagingDir);

    verify(app).resetProgress();
    verify(app).setDetailsWriter(any(PrintWriter.class));
  }

  @Test
  public void testStageApplicationNonEmptyDir() throws IOException {
    temporaryFolder.create();
    File stagingDir = temporaryFolder.getRoot();
    File newFile = new File(stagingDir, "test.file");

    if (!newFile.createNewFile()) {
      throw new IOException("Failed when setting up test - could not create file");
    }

    assertThrows(
        stagingDir.getPath() + " is not a valid staging directory (it is not empty)",
        AdminException.class,
        () -> appAdmin.stageApplication(stagingDir, /* useRemoteResourceLimits= */ false));
  }

  @Test
  public void testStageApplicationRegularFile() throws IOException {
    temporaryFolder.create();
    File stagingDir = temporaryFolder.getRoot();
    File badStageDir = new File(stagingDir, "test.file");

    if (!badStageDir.createNewFile()) {
      throw new IOException("Failed when setting up test - could not create file");
    }

    assertThrows(
        badStageDir.getPath() + " is not a valid staging directory (it is a regular file)",
        AdminException.class,
        () -> appAdmin.stageApplication(badStageDir, /* useRemoteResourceLimits= */ false));
  }
}
