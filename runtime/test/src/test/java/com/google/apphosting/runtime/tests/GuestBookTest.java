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
package com.google.apphosting.runtime.tests;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.admin.AppCfg;
import com.google.appengine.tools.development.HttpApiServer;
import com.google.apphosting.runtime.jetty9.JavaRuntimeViaHttpBase;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class GuestBookTest extends JavaRuntimeViaHttpBase {

  private final File appRoot;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  public GuestBookTest(
      String runtimeVersion, String jettyVersion, String jakartaVersion, boolean useHttpConnector)
      throws IOException, InterruptedException {
    super(runtimeVersion, jettyVersion, jakartaVersion, useHttpConnector);
    File currentDirectory = new File("").getAbsoluteFile();
    String appName = "guestbook";
    if (isJakarta()) {
      appName = "guestbook_jakarta";
    }

    File appRootTarget =
        new File(currentDirectory.getParentFile().getParentFile(), "applications/" + appName);
    Process process =
        new ProcessBuilder(
                "../../mvnw"
                    + (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                        ? ".cmd" // Windows OS
                        : ""), // Linux OS, no extension for command name.
                "clean",
                "install",
                "-f",
                new File(appRootTarget, "pom.xml").getAbsolutePath())
            .start();
    List<String> results = readOutput(process.getInputStream());
    System.out.println("mvn process output:" + results);
    int exitCode = process.waitFor();
    assertThat(0).isEqualTo(exitCode);
    System.setProperty("appengine.sdk.root", "../../sdk_assembly/target/appengine-java-sdk");
    String[] args = {
      "stage",
      appRootTarget.getAbsolutePath() + "/target/" + appName + "-4.0.1-SNAPSHOT",
      appRootTarget.getAbsolutePath() + "/target/appengine-staging"
    };
    AppCfg.main(args);
    appRoot = new File(appRootTarget, "target/appengine-staging").getAbsoluteFile();
    assertThat(appRoot.isDirectory()).isTrue();
  }

  private RuntimeContext<?> runtimeContext() throws IOException, InterruptedException {
    ApiServerFactory<HttpApiServer> apiServerFactory =
        (apiPort, runtimePort) -> {
          HttpApiServer httpApiServer = new HttpApiServer(apiPort, "localhost", runtimePort);
          httpApiServer.start(false);
          return httpApiServer;
        };
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder(apiServerFactory)
            .setApplicationPath(appRoot.toString())
            .build();
    return createRuntimeContext(config);
  }

  private static List<String> readOutput(InputStream inputStream) throws IOException {
    try (BufferedReader output = new BufferedReader(new InputStreamReader(inputStream))) {
      return output.lines().map(l -> l + "\n").toList();
    }
  }

  @Test
  public void testGuesttBookJSPStaged() throws Exception {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      runtime.executeHttpGet("/guestbook.jsp", "<p>Guestbook 'default' has no messages.</p>", 200);

      // Now, post a message to the guestbook to activate storage in the datastore, as well as usage
      // of session manager auxiliary service.
      String postBody = "guestbookName=default&content=Hello%20from%20test";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(runtime.jettyUrl("/sign")))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(postBody))
              .build();
      // We expect a redirect to /guestbook.jsp after posting.
      // We must configure HttpClient to follow redirects, so we expect status 200
      // and the body of guestbook.jsp, which should contain the new greeting.
      HttpClient client =
          HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("Hello from test");

      // Verify again that a simple GET also contains the greeting:
      runtime.executeHttpGet("/guestbook.jsp", "Hello from test", 200);
    }
  }
}
