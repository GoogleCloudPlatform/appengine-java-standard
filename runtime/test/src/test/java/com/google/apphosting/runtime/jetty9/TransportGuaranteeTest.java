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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.flogger.GoogleLogger;
import java.util.List;
import java.util.Objects;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TransportGuaranteeTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private HttpClient httpClient;
  private RuntimeContext<?> runtime;

  public TransportGuaranteeTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector)
      throws Exception {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return createRuntimeContext(config);
  }

  @Before
  public void before() throws Exception {
    String app = "transportguaranteeapp-";
    if (isJakarta()) {
      app = app + "ee10";
    } else {
      app = app + "ee8";
    }
    copyAppToDir(app, temp.getRoot().toPath());

    SslContextFactory ssl = new SslContextFactory.Client(true);
    httpClient = new HttpClient(ssl);
    httpClient.start();
    runtime = runtimeContext();
    logger.atInfo().log(
        "%s: env=%s, httpMode=%s",
        this.getClass().getSimpleName(), jakartaVersion, useHttpConnector);
  }

  @After
  public void after() throws Exception {
    if (httpClient != null) {
      httpClient.stop();
    }
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  public void testSecureRequest() throws Exception {
    String url = runtime.jettyUrl("/");
    assertThat(url, startsWith("http://"));

    ContentResponse response = httpClient.newRequest(url).header("x-appengine-https", "on").send();
    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    String expectedUrl = url.replace("http://", "https://");
    assertThat(response.getContentAsString(), containsString("requestURL=" + expectedUrl));
    assertThat(response.getContentAsString(), containsString("isSecure=true"));
  }

  @Test
  public void testInsecureRequest() throws Exception {
    String url = runtime.jettyUrl("/");
    assertThat(url, startsWith("http://"));

    ContentResponse response = httpClient.newRequest(url).send();
    assertThat(response.getStatus(), equalTo(HttpStatus.FORBIDDEN_403));
    if (!Objects.equals(jakartaVersion, "EE10")) {
      assertThat(response.getContentAsString(), containsString("!Secure"));
    }
  }
}
