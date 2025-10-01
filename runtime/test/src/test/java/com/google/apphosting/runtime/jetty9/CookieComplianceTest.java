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

package com.google.apphosting.runtime.jetty9;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CookieComplianceTest extends JavaRuntimeViaHttpBase {

  // This is set in the app appengine-web.xml file
  static {
    System.setProperty("com.google.apphosting.runtime.jetty94.LEGACY_MODE", "true");
  }

    @Parameterized.Parameters
    public static List<Object[]> version() {
        return Arrays.asList(
                new Object[][] {
                        {"java17", "9.4", "EE6", false},
                        {"java17", "12.0", "EE8", false},
                        {"java17", "12.0", "EE10", false},
                        {"java17", "12.1", "EE11", false},
                        {"java21", "12.0", "EE8", false},
                        {"java21", "12.0", "EE10", false},
                        {"java21", "12.1", "EE11", false},
                        {"java25", "12.1", "EE8", false},
                        {"java25", "12.1", "EE11", false},
                        {"java17", "9.4", "EE6", true},
                        {"java17", "12.0", "EE8", true},
                        {"java17", "12.0", "EE10", true},
                        {"java17", "12.1", "EE11", true},
                        {"java21", "12.0", "EE8", true},
                        {"java21", "12.0", "EE10", true},
                        {"java21", "12.1", "EE11", true},
                        {"java25", "12.1", "EE8", true},
                        {"java25", "12.1", "EE11", true},
                });
    }

  public CookieComplianceTest(String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector) {
      super(runtimeVersion, jettyVersion, version, useHttpConnector);
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void copyAppToTemp() throws Exception {
      // Internal testing is limited to Jetty 9.4 EE6 for now.
      boolean internal = Boolean.getBoolean("test.running.internally");
      assumeTrue(!internal || "EE6".equals(jakartaVersion));

      String app = "com/google/apphosting/runtime/jetty9/cookiecomplianceapp/";
      if (isJakarta()) {
          app = app + "jakarta";
      } else {
          app = app + "javax";
      }
      copyAppToDir(app, temp.getRoot().toPath());
  }

  @Test
  public void testCookieCompliance() throws Exception {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      HttpClient httpClient = runtime.getHttpClient();
      String url = runtime.jettyUrl("/");
      HttpGet get = new HttpGet(url);
      HttpResponse response = httpClient.execute(get);

      // The servlet sets a cookie which is illegal to be set under the rules of RFC6265.
      // We expect this to still work in 9.4 if the CookieCompliance mode is set to RFC2965.
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(RESPONSE_200);
      String responseContent = EntityUtils.toString(response.getEntity());
      assertThat(responseContent).isEqualTo("cookieTestServletContent");
      Header[] cookies = response.getHeaders("Set-Cookie");
      assertThat(cookies).hasLength(1);
      assertThat(cookies[0].getValue()).startsWith("invalidRFC6265Cookie=\"value\\\"1234\"");
    }
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return createRuntimeContext(config);
  }
}
