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
import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LegacyModeTest extends JavaRuntimeViaHttpBase {
  private static RuntimeContext<DummyApiServer> runtime;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return Arrays.asList(
        new Object[][] {
          {"java17", "9.4", "EE6", true},
          //       {"java17", "12.0", "EE8"},
          //       {"java17", "12.0", "EE10"},
          //       {"java17", "12.1", "EE11"},
          //       {"java21", "12.0", "EE8"},
          //       {"java21", "12.0", "EE10"},
          //        {"java21", "12.1", "EE11"},
          //        {"java25", "12.1", "EE8"},
          //        {"java25", "12.1", "EE11"},
        });
  }

  public LegacyModeTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector) {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
    if (Boolean.getBoolean("test.running.internally")) { // Internal can only do EE6
      System.setProperty("appengine.use.EE8", "false");
      System.setProperty("appengine.use.EE10", "false");
      System.setProperty("appengine.use.EE11", "false");
      System.setProperty("GAE_RUNTIME", "java17");
      System.setProperty("appengine.use.jetty121", "false");
    }
  }

  @AfterClass
  public static void afterClass() throws IOException {
    runtime.close();
  }

  @Test
  public void testProxiedGet() throws Exception {
    Path appPath = temporaryFolder.newFolder("app").toPath();
    copyAppToDir("echoapp", appPath);
    File appDir = appPath.toFile();

    RuntimeContext.Config<DummyApiServer> config =
        RuntimeContext.Config.builder().setApplicationPath(appDir.getAbsolutePath()).build();
    runtime = createRuntimeContext(config);

    String response =
        executeHttpDirect(
            """
                          GET /some/path HTTP/1.0
                          Some: Header

                          """);
    assertThat(response).contains("HTTP/1.1 200 OK");
    assertThat(response).contains("GET /some/path HTTP/1.0");
    assertThat(response).contains("Some: Header");

    response =
        executeHttpDirect(
            "POST /some/path HTTP/1.0\r\n"
                + "Some: Header\r\n"
                + "Content-Length: 10\r\n"
                + "\r\n"
                + "01234567\r\n");
    assertThat(response).contains("HTTP/1.1 200 OK");
    assertThat(response).contains("POST /some/path HTTP/1.0");
    assertThat(response).contains("Some: Header");
    assertThat(response).contains("01234567");

    response =
        executeHttpDirect(
            "POST /some/path HTTP/1.0\r\n"
                + "Some: Header\r\n"
                + "Content-Length: 10\r\n"
                + "Content-Encoding: unknown\r\n"
                + "\r\n"
                + "01234567\r\n");
    assertThat(response).contains("HTTP/1.1 200 OK");
    assertThat(response).contains("POST /some/path HTTP/1.0");
    assertThat(response).contains("Some: Header");
    assertThat(response).contains("01234567");

    response =
        executeHttpDirect(
            """
                          GET /s%u006Fme/p%u0061th HTTP/1.0
                          Some: Header

                          """);

    // Microsoft encoding supported until jetty-10
    assertThat(response).contains("HTTP/1.1 200 OK");
    assertThat(response).contains("GET /some/path HTTP/1.0");
    assertThat(response).contains("Some: Header");

    response =
        executeHttpDirect(
            """
                                 Get /some/path HTTP/1.0
                                 Some: Header

                                 """);
    assertThat(response).contains("HTTP/1.1 200 OK");
    assertThat(response).contains("Some: Header");
    assertThat(response.toLowerCase(Locale.ROOT)).contains("get /some/path http/1.0");

    response =
        executeHttpDirect(
            "POST /some/path HTTP/1.0\r\n"
                + "Some: Header\r\n"
                + "Content-Length: 10\r\n"
                + "Content-Length: 10\r\n"
                + "Content-Encoding: unknown\r\n"
                + "\r\n"
                + "01234567\r\n");

    // Multiple content lengths always rejected by the proxy.
    assertThat(response).contains("HTTP/1.1 400 ");
  }

  private static String executeHttpDirect(String request) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(Inet4Address.getLocalHost(), runtime.getPort()));
      socket.getOutputStream().write(request.getBytes(ISO_8859_1));
      ByteArrayOutputStream response = new ByteArrayOutputStream();
      InputStream in = socket.getInputStream();
      byte[] buffer = new byte[4096];
      int len = in.read(buffer);
      while (len > 0) {
        response.write(buffer, 0, len);
        len = in.read(buffer);
      }
      return response.toString(ISO_8859_1.toString());
    }
  }
}
