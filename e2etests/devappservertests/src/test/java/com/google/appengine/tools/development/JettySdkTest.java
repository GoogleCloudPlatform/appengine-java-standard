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
package com.google.appengine.tools.development;

import static com.google.appengine.tools.development.DevAppServerTestBase.getSdkRoot;
import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.info.AppengineSdk;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JettySdkTest {

  private void assertFilesExist(Iterable<File> files) {
    for (File f : files) {
      assertThat(f.exists()).isTrue();
      System.out.println(f.getAbsolutePath());
    }
  }

  private void assertUrlsExist(List<URL> urls) throws URISyntaxException {
    for (URL url : urls) {
      assertThat(new File(url.toURI()).exists()).isTrue();
      System.out.println(new File(url.toURI()).getAbsolutePath());
    }
  }

  @Before
  public void before() {
    System.setProperty("appengine.sdk.root", getSdkRoot().getAbsolutePath());
  }

  @After
  public void after() {

    System.clearProperty("appengine.use.EE8");
    System.clearProperty("appengine.use.EE10");
    System.clearProperty("appengine.use.EE11");
    System.clearProperty("appengine.use.jetty121");
    AppengineSdk.resetSdk();
  }

  @Test
  public void testJettyEE8() throws Exception {
    System.setProperty("appengine.use.EE8", "true");
    System.setProperty("appengine.use.EE10", "false");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "false");
    System.out.println("Jetty 12 EE8");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty12Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  @Test
  public void testJettyEE10() throws Exception {
    System.setProperty("appengine.use.EE8", "false");
    System.setProperty("appengine.use.EE10", "true");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "false");
    System.out.println("Jetty 12 EE10");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty12Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  @Test
  public void testJettyEE11() throws Exception {
    System.setProperty("appengine.use.EE8", "false");
    System.setProperty("appengine.use.EE10", "false");
    System.setProperty("appengine.use.EE11", "true");
    System.setProperty("appengine.use.jetty121", "true");
    System.out.println("Jetty 12.1 EE11");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE11Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  @Test
  public void testJetty121EE8() throws Exception {
    System.setProperty("appengine.use.EE8", "true");
    System.setProperty("appengine.use.EE10", "false");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "true");
    System.out.println("Jetty 12.1 EE8");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE8Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }
}
