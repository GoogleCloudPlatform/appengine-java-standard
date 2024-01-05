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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.info.AppengineSdk;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the Application where EE10 should be set automatically */
public class EE10Test extends TestCase {
  private static final String SDK_ROOT_PROPERTY = "appengine.sdk.root";

  private static final String TEST_JAKARTA_APP = getWarPath("allinone_jakarta");

  private static final String SDK_ROOT = getSDKRoot();

  private String oldSdkRoot;
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  public EE10Test() {
    System.setProperty("appengine.sdk.root", "../../sdk_assembly/target/appengine-java-standard");
    AppengineSdk.resetSdk();
  }

  private static String getWarPath(String directoryName) {
    File currentDirectory = new File("").getAbsoluteFile();

    String appRoot =
        new File(
                currentDirectory,
                "../testlocalapps/"
                    + directoryName
                    + "/target/"
                    + directoryName
                    + "-"
                    + System.getProperty("appengine.projectversion"))
            .getAbsolutePath();

    return appRoot;
  }

  private static String getSDKRoot() {
    File currentDirectory = new File("").getAbsoluteFile();
    String sdkRoot = null;
    try {
      sdkRoot =
          new File(currentDirectory, "../../sdk_assembly/target/appengine-java-sdk")
              .getCanonicalPath();
    } catch (IOException ex) {
      Logger.getLogger(EE10Test.class.getName()).log(Level.SEVERE, null, ex);
    }
    return sdkRoot;
  }

  /** Set the appengine.sdk.root system property to make SdkInfo happy. */
  @Before
  public void setUp() {
    oldSdkRoot = System.setProperty(SDK_ROOT_PROPERTY, SDK_ROOT);
  }

  @After
  public void tearDown() {
    if (oldSdkRoot != null) {
      System.setProperty(SDK_ROOT_PROPERTY, oldSdkRoot);
    } else {
      System.clearProperty(SDK_ROOT_PROPERTY);
    }
  }

  @Test
  public void testEE10() throws IOException {
    Application ignored = Application.readApplication(TEST_JAKARTA_APP);
    assertThat(Boolean.getBoolean("appengine.use.EE10")).isTrue();
  }
}
