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


import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JaxRsTest extends DevAppServerTestBase {

  @Parameterized.Parameters
  public static List<Object[]> version() {
      // Only EE8 app.
return Arrays.asList(
        new Object[][] {
      //       {"java17", "9.4", "EE6"},
              {"java17", "12.0", "EE8"},
      //        {"java21", "12.0", "EE8"},
     //         {"java25", "12.1", "EE8"},

        });
  
  }
  public JaxRsTest(String runtimeVersion, String jettyVersion, String jakartaVersion) {
    super(runtimeVersion, jettyVersion, jakartaVersion);
  }

  @Before
  public void setUpClass() throws IOException, InterruptedException {
    File currentDirectory = new File("").getAbsoluteFile();
    File appRoot =
        new File(
            currentDirectory,
            "../../applications/jaxrs/target/jaxrs"
                + "-"
                + System.getProperty("appengine.projectversion"));
    setUpClass(appRoot);
  }

  @Test
  public void testJaxRs() throws Exception {
    // App Engine Memcache access.
    executeHttpGet(
        "/hello",
        "hello\n",
        RESPONSE_200);

  }

}
