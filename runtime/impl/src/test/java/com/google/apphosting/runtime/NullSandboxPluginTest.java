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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NullSandboxPlugin}. */
@RunWith(JUnit4.class)
public class NullSandboxPluginTest {
  @Test
  public void classDirsScannedIfProperty() throws Exception {
    ImmutableMap<String, String> systemProperties = ImmutableMap.of(
        NullSandboxPlugin.ALWAYS_SCAN_CLASS_DIRS_PROPERTY, "true");
    testClassDirScanning(systemProperties, true);
  }

  @Test
  public void classDirsNotScannedIfNotFlagAndNotProperty() throws Exception {
    testClassDirScanning(ImmutableMap.of(), false);
  }

  private void testClassDirScanning(
      ImmutableMap<String, String> systemProperties,
      boolean alwaysScanClassDirs) throws MalformedURLException {
    NullSandboxPlugin plugin = new NullSandboxPlugin();
    URL[] urls = {new URL("file:///tmp/nonexistentdirectory/")};
    File contextRoot = new File(".");
    ClassPathUtils classPathUtils = mockClassPathUtils();
    ApplicationEnvironment applicationEnvironment = mockApplicationEnvironment(systemProperties);
    String appsRoot = "";
    plugin.createRuntimeClassLoader(classPathUtils, appsRoot);
    ApplicationClassLoader loader = (ApplicationClassLoader) plugin.doCreateApplicationClassLoader(
        urls, contextRoot, applicationEnvironment);
    if (alwaysScanClassDirs) {
      assertThat(loader.getActualUrls()).isEqualTo(loader.getURLs());
    } else {
      assertThat(loader.getActualUrls()).isNotEqualTo(loader.getURLs());
    }
  }

  private ClassPathUtils mockClassPathUtils() {
    ClassPathUtils classPathUtils = mock(ClassPathUtils.class);
    when(classPathUtils.getRuntimeSharedUrls()).thenReturn(new URL[0]);
    when(classPathUtils.getRuntimeImplUrls()).thenReturn(new URL[0]);
    when(classPathUtils.getPrebundledUrls()).thenReturn(new URL[0]);
    return classPathUtils;
  }

  private ApplicationEnvironment mockApplicationEnvironment(
      ImmutableMap<String, String> systemProperties) {
    ApplicationEnvironment applicationEnvironment = mock(ApplicationEnvironment.class);
    when(applicationEnvironment.getSystemProperties()).thenReturn(systemProperties);
    when(applicationEnvironment.getRuntimeConfiguration())
        .thenReturn(ApplicationEnvironment.RuntimeConfiguration.DEFAULT_FOR_TEST);
    return applicationEnvironment;
  }
}
