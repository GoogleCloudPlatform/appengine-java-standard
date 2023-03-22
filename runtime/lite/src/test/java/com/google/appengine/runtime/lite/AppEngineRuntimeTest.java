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

package com.google.appengine.runtime.lite;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.SystemServicePb.StartBackgroundRequestRequest;
import com.google.apphosting.base.protos.SystemServicePb.StartBackgroundRequestResponse;
import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.apphosting.datastore.DatastoreV3Pb.PutResponse;
import com.google.apphosting.runtime.ApiDeadlineOracle;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.ApplicationEnvironment;
import com.google.apphosting.runtime.RequestManager;
import com.google.apphosting.runtime.SessionsConfig;
import com.google.apphosting.runtime.http.FakeHttpApiHost;
import com.google.apphosting.runtime.jetty94.AppInfoFactory;
import com.google.apphosting.testing.PortPicker;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class AppEngineRuntimeTest {

  private static final double YEAR_SECONDS = 31536000.0;
  private static final String BACKGROUND_REQUEST_ID = "foobar";
  private static final RemoteApiPb.Response BACKGROUND_API_RESPONSE;
  private static final RemoteApiPb.Response MEMCACHE_GET_RESPONSE;
  private static final RemoteApiPb.Response MEMCACHE_SET_RESPONSE;
  private static final RemoteApiPb.Response DATASTORE_PUT_RESPONSE;

  // Initialize some dummy API Server responses:
  static {
    BACKGROUND_API_RESPONSE =
        RemoteApiPb.Response.newBuilder()
            .setResponse(
                StartBackgroundRequestResponse.newBuilder()
                    .setRequestId(BACKGROUND_REQUEST_ID)
                    .build()
                    .toByteString())
            .build();

    MEMCACHE_GET_RESPONSE =
        RemoteApiPb.Response.newBuilder()
            .setResponse(MemcacheGetResponse.getDefaultInstance().toByteString())
            .build();

    MemcacheSetResponse.Builder memcacheSetResponse = MemcacheSetResponse.newBuilder();
    memcacheSetResponse.addSetStatus(MemcacheSetResponse.SetStatusCode.STORED);
    MEMCACHE_SET_RESPONSE =
        RemoteApiPb.Response.newBuilder()
            .setResponse(memcacheSetResponse.build().toByteString())
            .build();

    com.google.storage.onestore.v3.OnestoreEntity.Path path =
        new com.google.storage.onestore.v3.OnestoreEntity.Path();
    path.addElement().setType("bogus_type").setId(1234);
    DATASTORE_PUT_RESPONSE =
        RemoteApiPb.Response.newBuilder()
            .setResponse(
                new PutResponse()
                    .addKey(new Reference().setApp("bogus_app").setPath(path))
                    .toByteString())
            .build();
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private HostAndPort listenAddress;
  private URI uri;
  private Optional<HttpClient> httpClient = Optional.empty();
  private HostAndPort fakeHttpApiHostAddress;
  private Optional<FakeHttpApiHost> fakeHttpApiHost = Optional.empty();
  private final ArrayDeque<RemoteApiPb.Request> apiRequests = new ArrayDeque<>();
  private final ArrayDeque<RemoteApiPb.Response> apiResponses = new ArrayDeque<>();
  private static final String EXCEPTION_THROWING_WEB_XML =
      Joiner.on("\n")
          .join(
              "<web-app version=\"2.5\">",
              "  <servlet>",
              "    <servlet-name>ExceptionThrower</servlet-name>",
              "    <servlet-class>com.google.appengine.runtime.lite.AppEngineRuntimeTest$"
                  + "ExceptionThrowingServlet</servlet-class>",
              "  </servlet>",
              "  <servlet-mapping>",
              "    <servlet-name>ExceptionThrower</servlet-name>",
              "    <url-pattern>/throw/*</url-pattern>",
              "  </servlet-mapping>",
              "  <error-page>",
              "    <exception-type>java.io.IOException</exception-type>",
              "    <location>/error.html</location>",
              "  </error-page>",
              "  <error-page>",
              "    <error-code>404</error-code>",
              "    <location>/error.html</location>",
              "  </error-page>",
              "</web-app>");

  /** Simulates a servlet in an app which just prints "Hello, World!". */
  public static final class HelloWorldServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setContentType("text/plain");
      PrintWriter out = resp.getWriter();
      out.print("Hello, World!");
    }
  }

  /** Simulates a servlet in an app which does nothing but throw exceptions. */
  public static final class ExceptionThrowingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      if ("/throw/oom".equals(req.getRequestURI())) {
        throw new OutOfMemoryError("This is a fake OOM for testing");
      }

      throw new IOException("This is a fake IOException for testing");
    }
  }

  /**
   * Simulates a servlet in an app which just echoes the incoming request body with the prefix
   * "ECHO:"
   */
  public static final class EchoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String input;
      try (InputStreamReader reader = new InputStreamReader(req.getInputStream(), UTF_8)) {
        input = CharStreams.toString(reader);
      }

      PrintWriter out = resp.getWriter();
      out.print("ECHO:" + input);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      doGet(req, resp);
    }
  }

  /** Simulates a servlet in an app which just echoes header "echo" in the incoming request. */
  public static final class EchoHeaderServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setHeader("echo", req.getHeader("echo"));
      resp.setContentType("text/plain");
      PrintWriter out = resp.getWriter();
      out.print("OK");
    }
  }

  /** Simulates a servlet which simply validates that getRemoteAddr equals X-AppEngine-User-IP. */
  public static final class GetRemoteAddrVerification extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      assertThat(req.getHeader("X-AppEngine-User-IP")).isEqualTo(req.getRemoteAddr());
      resp.setContentType("text/plain");
      PrintWriter out = resp.getWriter();
      out.print("OK");
    }
  }

  /** Simulates a servlet in an app which kicks off and then joins a background thread. */
  public static final class BackgroundThreadServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setContentType("text/plain");

      AtomicInteger testVal = new AtomicInteger(0);

      Runnable task =
          () -> {
            // Just to validate that we can make API calls from a background thread, make an
            // arbitrary API call (Using a memcache "get" because it's easy to fake):
            MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
            memcacheService.get("qux");

            // The main thread sets this to 1 before start()ing us:
            assertThat(testVal.get()).isEqualTo(1);

            // Now reset the value, indicating the thread has succeeded:
            testVal.set(0);
          };

      Thread thread = ThreadManager.createBackgroundThread(task);

      if (req.getParameter("slow_thread_start").equals("true")) {
        Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(2));
      }

      testVal.set(1);

      thread.start();
      try {
        thread.join(Duration.ofSeconds(1).toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
        throw new IOException("thread join interrupted", e);
      }

      assertThat(testVal.get()).isEqualTo(0);

      resp.getWriter().print("OK");
    }
  }

  /**
   * A ServletContextListener which uses BackgroundThreads.
   *
   * <p>This is tricky because it means the server must be able to receive inbound HTTP requests (to
   * service the inbound call to /_ah/background) while the webapp is still initializing, otherwise
   * initialization will deadlock.
   */
  public static class ServletContextListenerWhichUsesBackgroundThreads
      implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
      AtomicInteger testVal = new AtomicInteger(0);

      Runnable task = () -> testVal.set(1);

      Thread thread = ThreadManager.createBackgroundThread(task);

      thread.start();
      try {
        thread.join(Duration.ofSeconds(1).toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
        throw new IllegalStateException("thread join interrupted", e);
      }

      assertThat(testVal.get()).isEqualTo(1);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {}
  }

  /** Simulates a servlet which uses HTTP sessions. */
  public static final class SessionUsingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      req.getSession().setAttribute("foo", "bar");

      resp.setContentType("text/plain");
      PrintWriter out = resp.getWriter();
      out.print("OK");
    }
  }

  class ApiRequestHandler implements FakeHttpApiHost.ApiRequestHandler {
    @Override
    public RemoteApiPb.Response handle(RemoteApiPb.Request request) {
      apiRequests.addLast(request);
      return apiResponses.removeFirst();
    }
  }

  private void prepWebApp(String servletClass) throws Exception {
    prepWebAppWithWebXml(
        Joiner.on("\n")
            .join(
                "<web-app version=\"2.5\">",
                "  <servlet>",
                "    <servlet-name>Main</servlet-name>",
                "    <servlet-class>com.google.appengine.runtime.lite.AppEngineRuntimeTest$"
                    + servletClass
                    + "</servlet-class>",
                "  </servlet>",
                "  <servlet-mapping>",
                "    <servlet-name>Main</servlet-name>",
                "    <url-pattern>/*</url-pattern>",
                "  </servlet-mapping>",
                "</web-app>"));
  }

  private void prepWebAppWithWebXml(String webXml) throws Exception {
    File webInf = new File(temporaryFolder.getRoot(), "WEB-INF");
    webInf.mkdirs();
    Files.write(webInf.toPath().resolve("web.xml"), webXml.getBytes(UTF_8));
    Files.write(
        temporaryFolder.getRoot().toPath().resolve("error.html"),
        "<html><body>Error!</body></html>".getBytes(UTF_8));

    listenAddress = HostAndPort.fromParts("localhost", PortPicker.create().pickUnusedPort());
    uri = URI.create("http://localhost:" + listenAddress.getPort());

    HttpClient c = new HttpClient();
    c.start();
    httpClient = Optional.of(c);

    fakeHttpApiHostAddress =
        HostAndPort.fromParts("localhost", PortPicker.create().pickUnusedPort());
    fakeHttpApiHost =
        Optional.of(
            FakeHttpApiHost.create(fakeHttpApiHostAddress.getPort(), new ApiRequestHandler()));
  }

  @After
  public void tearDown() throws Exception {
    if (httpClient.isPresent()) {
      httpClient.get().stop();
    }

    fakeHttpApiHost.ifPresent(FakeHttpApiHost::stop);
  }

  @Test
  public void runtime_starts() throws Exception {
    prepWebApp("HelloWorldServlet");

    AppEngineRuntime.builder()
        .setServletWebappPath(temporaryFolder.getRoot().toPath())
        .setListenAddress(listenAddress)
        .setApiHostAddress(fakeHttpApiHostAddress)
        .build()
        .run()
        .close();
  }

  @Test
  public void runtime_rejectsWebInfJars() throws Exception {
    prepWebApp("HelloWorldServlet");

    File webInfLib = new File(temporaryFolder.getRoot(), "WEB-INF/lib");
    webInfLib.mkdirs();
    new File(webInfLib, "foo.jar").createNewFile();

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                AppEngineRuntime.builder()
                    .setServletWebappPath(temporaryFolder.getRoot().toPath())
                    .setListenAddress(listenAddress)
                    .setApiHostAddress(fakeHttpApiHostAddress)
                    .build()
                    .run()
                    .close());

    assertThat(exception).hasMessageThat().contains("WEB-INF/lib/foo.jar");
  }

  @Test
  public void runtime_allowsWebInfJars() throws Exception {
    prepWebApp("HelloWorldServlet");

    File webInfLib = new File(temporaryFolder.getRoot(), "WEB-INF/lib");
    webInfLib.mkdirs();
    new File(webInfLib, "foo.jar").createNewFile();

    AppEngineRuntime.builder()
        .setServletWebappPath(temporaryFolder.getRoot().toPath())
        .setAllowWebInfJars(true)
        .setListenAddress(listenAddress)
        .setApiHostAddress(fakeHttpApiHostAddress)
        .build()
        .run()
        .close();
  }

  @Test
  public void runtime_routesRequest() throws Exception {
    prepWebApp("HelloWorldServlet");

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      Request req = httpClient.get().newRequest(uri);
      response = new String(req.send().getContent(), UTF_8);
    }

    assertThat(response).contains("Hello, World!");
  }

  @Test
  public void runtime_handlesExceptionWithGenericPage() throws Exception {
    prepWebAppWithWebXml(EXCEPTION_THROWING_WEB_XML);

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      Request req = httpClient.get().newRequest(uri + "/throw/oom");
      response = new String(req.send().getContent(), UTF_8);
    }

    assertThat(response).contains("This is a fake OOM for testing");
  }

  @Test
  public void runtime_handlesExceptionWithCustomPage() throws Exception {
    prepWebAppWithWebXml(EXCEPTION_THROWING_WEB_XML);

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      // This will generate an IOException which should be handled by the app's custom error page:
      Request req = httpClient.get().newRequest(uri + "/throw/ioe");
      response = new String(req.send().getContent(), UTF_8);
    }

    assertThat(response).contains("Error!");
  }

  @Test
  public void runtime_handles404WithCustomPage() throws Exception {
    prepWebAppWithWebXml(EXCEPTION_THROWING_WEB_XML);

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      // This will generate an error 404 which should be handled by the app's custom error page:
      Request req = httpClient.get().newRequest(uri + "/doesnotexist");
      response = new String(req.send().getContent(), UTF_8);
    }

    assertThat(response).contains("Error!");
  }

  enum GzipTestMethod {
    GET,
    POST
  }

  @Test
  public void runtime_gzips(@TestParameter GzipTestMethod testMethod) throws Exception {
    prepWebApp("EchoServlet");
    // Create enough bytes to cross the minimum threshold for gzipped response:
    String inputData = String.join(" ", Collections.nCopies(40, "Hello world!"));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream output = new GZIPOutputStream(baos)) {
      output.write(inputData.getBytes(UTF_8));
    }
    byte[] inputBytes = baos.toByteArray();
    // The Jetty HTTP client will automatically unzip gzipped response content. For this test, we
    // want to do it manually, so remove that decoder:
    httpClient.get().getContentDecoderFactories().clear();

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    ContentResponse response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      Request req =
          httpClient
              .get()
              .newRequest(uri)
              .header("Accept-Encoding", "gzip")
              .header("Content-Length", Integer.toString(inputBytes.length))
              .header("Content-type", "text/plain")
              .header("Content-Encoding", "gzip")
              .content(new BytesContentProvider(inputBytes));
      if (testMethod == GzipTestMethod.GET) {
        req = req.method(HttpMethod.GET);
      } else {
        req = req.method(HttpMethod.POST);
      }
      response = req.send();
    }

    assertThat(response.getHeaders().get("Content-Encoding")).isEqualTo("gzip");
    String gunzippedResponse =
        CharStreams.toString(
            new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(response.getContent())), UTF_8));
    assertThat(gunzippedResponse).isEqualTo("ECHO:" + inputData);
  }

  @Test
  public void runtime_supportsLargeHeaders() throws Exception {
    prepWebApp("EchoHeaderServlet");
    String headerVal = String.join("", Collections.nCopies(260000, "a"));
    httpClient.get().setRequestBufferSize(262000);

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    ContentResponse response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      response = httpClient.get().newRequest(uri).header("echo", headerVal).send();
    }

    assertThat(response.getHeaders().get("echo")).isEqualTo(headerVal);
  }

  @Test
  public void runtime_noServerHeader() throws Exception {
    prepWebApp("HelloWorldServlet");

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    ContentResponse response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      response = httpClient.get().newRequest(uri).send();
    }

    assertThat(response.getHeaders().get("Server")).isNull();
  }

  @Test
  public void runtime_setsRemoteAddrToAppEngineUserIp() throws Exception {
    prepWebApp("GetRemoteAddrVerification");

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      response =
          new String(
              httpClient
                  .get()
                  .newRequest(uri)
                  .header("x-appengine-user-ip", "1.2.3.4")
                  .send()
                  .getContent(),
              UTF_8);
    }

    assertThat(response).contains("OK");
  }

  /**
   * Background threads work by having the App Engine serving infrastructure send a request back to
   * the app; this method simulates such a request.
   */
  private String sendBackgroundThreadRequest() throws Exception {
    return new String(
        httpClient
            .get()
            .newRequest(uri + "/_ah/background")
            .header("x-appengine-user-ip", "0.1.0.3")
            .header("X-AppEngine-BackgroundRequest", BACKGROUND_REQUEST_ID)
            .send()
            .getContent(),
        UTF_8);
  }

  /**
   * Verify we can launch, run, and join an App Engine Background thread. This has various sleeps to
   * try and encourage race conditions.
   *
   * @param slowRequestingThread Simulates the thread requesting the background thread being slower
   *     than the background thread supplied by the infrastructure.
   * @param slowBackgroundThread Simulates the background thread supplied by the infrastructure
   *     being slow to arrive.
   * @param slowThreadStart Simulates the thread requesting the background thread taking a long time
   *     to call thread.start().
   */
  @Test
  public void runtime_startsBackgroundThread(
      @TestParameter boolean slowRequestingThread,
      @TestParameter boolean slowBackgroundThread,
      @TestParameter boolean slowThreadStart)
      throws Exception {
    prepWebApp("BackgroundThreadServlet");
    // The app we call starts a background thread, which issues a background request to the API
    // server. Put in a canned response:
    apiResponses.addLast(BACKGROUND_API_RESPONSE);
    // The app's background thread uses memcache.get which also calls the API server. Put in an
    // arbitrary canned response:
    apiResponses.addLast(MEMCACHE_GET_RESPONSE);

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    Future<String> mainRequestResponse;
    Future<String> backgroundRequestResponse;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      ExecutorService executor = Executors.newCachedThreadPool();
      try {
        // Make a call to app:
        mainRequestResponse =
            executor.submit(
                () -> {
                  if (slowRequestingThread) {
                    Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(2));
                  }
                  Request req =
                      httpClient.get().newRequest(uri + "?slow_thread_start=" + slowThreadStart);
                  return new String(req.send().getContent(), UTF_8);
                });

        // The app will have requested a background thread. We service that request by calling it
        // back at the special /_ah/background endpoint:
        backgroundRequestResponse =
            executor.submit(
                () -> {
                  if (slowBackgroundThread) {
                    Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(2));
                  }
                  return sendBackgroundThreadRequest();
                });
        mainRequestResponse.get();
        backgroundRequestResponse.get();
      } finally {
        executor.shutdown();
      }
    }

    assertThat(mainRequestResponse.get()).contains("OK");
    assertThat(backgroundRequestResponse.get()).contains("OK");
    // The app should have made two API calls which we intercepted:
    assertThat(apiRequests).hasSize(2);
    // The first API call was a background thread request:
    RemoteApiPb.Request request = apiRequests.removeFirst();
    StartBackgroundRequestRequest unused =
        StartBackgroundRequestRequest.parseFrom(
            request.getRequest(), ExtensionRegistry.getEmptyRegistry());
    // The second API call was a memcache get:
    request = apiRequests.removeFirst();
    MemcacheGetRequest memcacheGetRequest =
        MemcacheGetRequest.parseFrom(request.getRequest(), ExtensionRegistry.getEmptyRegistry());
    assertThat(memcacheGetRequest.getKeyList())
        .containsExactly(ByteString.copyFrom("\"qux\"", UTF_8));
  }

  /**
   * Verify that if the infrastructure sends a request to /_ah/background with no background thread
   * ID, the request is rejected.
   */
  @Test
  public void runtime_incompleteBackgroundRequest() throws Exception {
    prepWebApp("HelloWorldServlet");

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      Request req =
          httpClient
              .get()
              .newRequest(uri + "/_ah/background")
              .header("x-appengine-user-ip", "0.1.0.3");
      // We intentionally don't send the background thread ID:
      // .header("X-AppEngine-BackgroundRequest", BACKGROUND_REQUEST_ID);
      response = new String(req.send().getContent(), UTF_8);
    }

    assertThat(response).contains("Did not receive a background request identifier.");
  }

  @Test
  public void runtime_supportsBackgroundThreadsDuringServletContextInitialization()
      throws Exception {
    apiResponses.addLast(BACKGROUND_API_RESPONSE);
    prepWebAppWithWebXml(
        Joiner.on("\n")
            .join(
                "<web-app version=\"2.5\">",
                "  <listener>",
                "    <listener-class>com.google.appengine.runtime.lite.AppEngineRuntimeTest$"
                    + "ServletContextListenerWhichUsesBackgroundThreads</listener-class>",
                "  </listener>",
                "  <servlet>",
                "    <servlet-name>Main</servlet-name>",
                "    <servlet-class>com.google.appengine.runtime.lite.AppEngineRuntimeTest$"
                    + "HelloWorldServlet</servlet-class>",
                "  </servlet>",
                "  <servlet-mapping>",
                "    <servlet-name>Main</servlet-name>",
                "    <url-pattern>/*</url-pattern>",
                "  </servlet-mapping>",
                "</web-app>"));

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .build();
    Future<String> mainRequestResponse;
    Future<String> backgroundRequestResponse;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      ExecutorService executor = Executors.newCachedThreadPool();
      try {
        // Make a call to app:
        mainRequestResponse =
            executor.submit(
                () -> {
                  Request req = httpClient.get().newRequest(uri);
                  return new String(req.send().getContent(), UTF_8);
                });

        // The app will have requested a background thread. We service that request by calling it
        // back at the special /_ah/background endpoint:
        backgroundRequestResponse = executor.submit(this::sendBackgroundThreadRequest);
        mainRequestResponse.get();
        backgroundRequestResponse.get();
      } finally {
        executor.shutdown();
      }
    }

    assertThat(mainRequestResponse.get()).contains("Hello, World!");
    assertThat(backgroundRequestResponse.get()).contains("OK");
    // The app should have made one API call which we intercepted:
    assertThat(apiRequests).hasSize(1);
    // The API call was a background thread request:
    RemoteApiPb.Request request = apiRequests.removeFirst();
    StartBackgroundRequestRequest unused =
        StartBackgroundRequestRequest.parseFrom(
            request.getRequest(), ExtensionRegistry.getEmptyRegistry());
  }

  @Test
  public void runtime_canStoreSessions() throws Exception {
    prepWebApp("SessionUsingServlet");
    // Session storage currently involves two trips to store datastore + memcache data. Provide
    // stock responses:
    apiResponses.addLast(DATASTORE_PUT_RESPONSE);
    apiResponses.addLast(MEMCACHE_SET_RESPONSE);
    apiResponses.addLast(DATASTORE_PUT_RESPONSE);
    apiResponses.addLast(MEMCACHE_SET_RESPONSE);

    AppEngineRuntime runtime =
        AppEngineRuntime.builder()
            .setServletWebappPath(temporaryFolder.getRoot().toPath())
            .setListenAddress(listenAddress)
            .setApiHostAddress(fakeHttpApiHostAddress)
            .setSessionsConfig(new SessionsConfig(true, false, ""))
            .build();
    String response;
    try (AppEngineRuntime.RunningRuntime runningRuntime = runtime.run()) {
      Request req = httpClient.get().newRequest(uri);
      response = new String(req.send().getContent(), UTF_8);
    }

    assertThat(response).contains("OK");
    // The app should have made four API calls which we intercepted:
    assertThat(apiRequests).hasSize(4);
  }

  @Test
  public void makeRequestManagerBuilder_validate(@TestParameter boolean reusePort) {
    HostAndPort apiHostAddress = HostAndPort.fromParts("foobar", 1212);
    BackgroundRequestDispatcher dispatcher = new BackgroundRequestDispatcher();

    ApiProxyImpl apiProxy =
        AppEngineRuntime.makeApiProxyImplBuilder(apiHostAddress, dispatcher).build();

    RequestManager.Builder builder = AppEngineRuntime.makeRequestManagerBuilder(apiProxy);

    assertThat(builder.softDeadlineDelay()).isEqualTo(10600);
    assertThat(builder.hardDeadlineDelay()).isEqualTo(10200);
    assertThat(builder.disableDeadlineTimers()).isTrue();
    assertThat(builder.maxOutstandingApiRpcs()).isEqualTo(100);
    assertThat(builder.threadStopTerminatesClone()).isTrue();
    assertThat(builder.interruptFirstOnSoftDeadline()).isTrue();
    assertThat(builder.enableCloudDebugger()).isFalse();
    assertThat(builder.cyclesPerSecond()).isEqualTo(1000000000L);
    assertThat(builder.waitForDaemonRequestThreads()).isFalse();
  }

  private static void validateDeadline(
      ApiDeadlineOracle oracle, String packageName, double defOnline, double defOffline) {
    assertThat(oracle.getDeadline(packageName, false, null)).isEqualTo(defOnline);
    assertThat(oracle.getDeadline(packageName, false, Double.MAX_VALUE)).isEqualTo(YEAR_SECONDS);
    assertThat(oracle.getDeadline(packageName, true, null)).isEqualTo(defOffline);
    assertThat(oracle.getDeadline(packageName, true, Double.MAX_VALUE)).isEqualTo(YEAR_SECONDS);
  }

  @Test
  public void makeApiProxyImplBuilder_validate() {
    HostAndPort apiHostAddress = HostAndPort.fromParts("foobar", 1212);
    BackgroundRequestDispatcher dispatcher = new BackgroundRequestDispatcher();

    ApiProxyImpl.Builder builder =
        AppEngineRuntime.makeApiProxyImplBuilder(apiHostAddress, dispatcher);

    ApiDeadlineOracle oracle = builder.deadlineOracle();
    validateDeadline(oracle, "app_config_service", 60.0, 60.0);
    validateDeadline(oracle, "blobstore", 15.0, 15.0);
    validateDeadline(oracle, "datastore_v3", 60.0, 60.0);
    validateDeadline(oracle, "datastore_v4", 60.0, 60.0);
    validateDeadline(oracle, "file", 30.0, 30.0);
    validateDeadline(oracle, "images", 30.0, 30.0);
    validateDeadline(oracle, "logservice", 60.0, 60.0);
    validateDeadline(oracle, "modules", 60.0, 60.0);
    validateDeadline(oracle, "rdbms", 60.0, 60.0);
    validateDeadline(oracle, "remote_socket", 60.0, 60.0);
    validateDeadline(oracle, "search", 10.0, 10.0);
    validateDeadline(oracle, "stubby", 10.0, 10.0);
    validateDeadline(oracle, "taskqueue", 10.0, 5.0);
    validateDeadline(oracle, "urlfetch", 10.0, 5.0);

    assertThat(builder.coordinator()).isSameInstanceAs(dispatcher);
    assertThat(builder.externalDatacenterName()).isEqualTo("MARS");
    assertThat(builder.byteCountBeforeFlushing()).isEqualTo(100 * 1024L);
    assertThat(builder.maxLogFlushTime()).isEqualTo(Duration.ofMinutes(1));
    assertThat(builder.cloudSqlJdbcConnectivityEnabled()).isTrue();
    assertThat(builder.disableApiCallLogging()).isTrue();
  }

  @Test
  public void createAppVersion_withLegacy() throws Exception {
    ImmutableMap<String, String> mockEnv =
        ImmutableMap.of(
            "GOOGLE_CLOUD_PROJECT",
            "bogus-project",
            "GAE_APPLICATION",
            "k~bogus-app",
            "GAE_VERSION",
            "4321");

    Path webAppPath = Paths.get("/bogus/webapp/path");

    AppInfoFactory appInfoFactory = new AppInfoFactory(mockEnv);
    AppinfoPb.AppInfo appInfo = appInfoFactory.getAppInfoWithApiVersion("user_defined");

    SessionsConfig givenSc =
        new SessionsConfig(
            /*enabled=*/ true,
            /*asyncPersistence=*/ false,
            /*asyncPersistenceQueueName=*/ "a_queue");

    AppVersion appVersion =
        AppEngineRuntime.createAppVersion(
            webAppPath, appInfo, Optional.of(givenSc), /*publicRoot=*/ Optional.of("/blargh"));

    assertThat(appVersion.getKey().toString()).isEqualTo("k~bogus-app/4321");
    assertThat(appVersion.getSessionsConfig()).isSameInstanceAs(givenSc);
    assertThat(appVersion.getPublicRoot()).isEqualTo("blargh/");
    assertThat(appVersion.getThreadGroupPool()).isNull();
    assertThat(appVersion.getRootDirectory().toString()).matches(webAppPath.toString());
    assertThat(appVersion.getClassLoader()).isEqualTo(ClassLoader.getSystemClassLoader());

    ApplicationEnvironment appEnv = appVersion.getEnvironment();
    assertThat(appEnv.getAppId()).isEqualTo("k~bogus-app");
    assertThat(appEnv.getVersionId()).isEqualTo("4321");
    assertThat(appEnv.getEnvironmentVariables()).isEmpty();
    assertThat(appEnv.getUseGoogleConnectorJ()).isTrue();

    ApplicationEnvironment.RuntimeConfiguration runtimeConfig = appEnv.getRuntimeConfiguration();
    assertThat(runtimeConfig.getCloudSqlJdbcConnectivityEnabled()).isTrue();
    assertThat(runtimeConfig.getUseGoogleConnectorJ()).isTrue();
  }

  @Test
  public void createAppVersion_noLegacy() throws Exception {
    ImmutableMap<String, String> mockEnv =
        ImmutableMap.of(
            "GOOGLE_CLOUD_PROJECT",
            "bogus-project",
            "GAE_APPLICATION",
            "k~bogus-app",
            "GAE_VERSION",
            "4321");

    Path webAppPath = Paths.get("/bogus/webapp/path");

    AppInfoFactory appInfoFactory = new AppInfoFactory(mockEnv);
    AppinfoPb.AppInfo appInfo = appInfoFactory.getAppInfoWithApiVersion("user_defined");

    AppVersion appVersion =
        AppEngineRuntime.createAppVersion(
            webAppPath,
            appInfo,
            /*sessionsConfig=*/ Optional.empty(),
            /*publicRoot=*/ Optional.empty());

    assertThat(appVersion.getKey().toString()).isEqualTo("k~bogus-app/4321");
    assertThat(appVersion.getPublicRoot()).isEmpty();
    assertThat(appVersion.getThreadGroupPool()).isNull();
    assertThat(appVersion.getRootDirectory().toString()).matches(webAppPath.toString());
    assertThat(appVersion.getClassLoader()).isEqualTo(ClassLoader.getSystemClassLoader());

    ApplicationEnvironment appEnv = appVersion.getEnvironment();
    assertThat(appEnv.getAppId()).isEqualTo("k~bogus-app");
    assertThat(appEnv.getVersionId()).isEqualTo("4321");
    assertThat(appEnv.getEnvironmentVariables()).isEmpty();
    assertThat(appEnv.getUseGoogleConnectorJ()).isTrue();

    ApplicationEnvironment.RuntimeConfiguration runtimeConfig = appEnv.getRuntimeConfiguration();
    assertThat(runtimeConfig.getCloudSqlJdbcConnectivityEnabled()).isTrue();
    assertThat(runtimeConfig.getUseGoogleConnectorJ()).isTrue();

    SessionsConfig sessionsConfig = appVersion.getSessionsConfig();
    assertThat(sessionsConfig.isEnabled()).isFalse();
    assertThat(sessionsConfig.isAsyncPersistence()).isFalse();
    assertThat(sessionsConfig.getAsyncPersistenceQueueName()).isNull();
  }
}
