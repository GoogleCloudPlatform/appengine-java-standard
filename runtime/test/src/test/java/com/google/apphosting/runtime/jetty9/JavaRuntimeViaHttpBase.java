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

import static com.google.common.base.StandardSystemProperty.FILE_SEPARATOR;
import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.base.StandardSystemProperty.JAVA_VERSION;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.apphosting.testing.PortPicker;
import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.GoogleLogger;
import com.google.common.net.HostAndPort;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;
import com.google.common.reflect.Reflection;
import com.google.errorprone.annotations.ForOverride;
import com.google.appengine.repackaged.com.google.protobuf.ByteString;
import com.google.appengine.repackaged.com.google.protobuf.ExtensionRegistry;
import com.google.appengine.repackaged.com.google.protobuf.InvalidProtocolBufferException;
import com.google.appengine.repackaged.com.google.protobuf.UninitializedMessageException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public abstract class JavaRuntimeViaHttpBase {
  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final int SERVER_START_TIMEOUT_SECONDS = 30;

  private static final String RUNTIME_LOCATION_ROOT = "java/com/google/apphosting";

  static final int RESPONSE_200 = 200;

  @FunctionalInterface
  interface ApiServerFactory<ApiServerT extends Closeable> {
    ApiServerT newApiServer(int apiPort, int runtimePort) throws IOException;
  }

  static class RuntimeContext<ApiServerT extends Closeable> implements AutoCloseable {
    private final Process runtimeProcess;
    private final ApiServerT httpApiServer;
    private final HttpClient httpClient;
    private final int jettyPort;
    private final OutputPump outPump;
    private final OutputPump errPump;

    private RuntimeContext(
        Process runtimeProcess,
        ApiServerT httpApiServer,
        HttpClient httpClient,
        int jettyPort,
        OutputPump outPump,
        OutputPump errPump) {
      this.runtimeProcess = runtimeProcess;
      this.httpApiServer = httpApiServer;
      this.httpClient = httpClient;
      this.jettyPort = jettyPort;
      this.outPump = outPump;
      this.errPump = errPump;
    }

    public int getPort() {
      return jettyPort;
    }

    @AutoValue
    abstract static class Config<ApiServerT extends Closeable> {
      abstract ImmutableMap<String, String> environmentEntries();

      abstract ImmutableList<String> launcherFlags();

      abstract ApiServerFactory<ApiServerT> apiServerFactory();

      // The default configuration uses an API server that rejects all API calls as unknown.
      // Individual tests can configure a different server, including the HttpApiServer from the SDK
      // which provides APIs using their dev app server implementations.
      static Builder<DummyApiServer> builder() {
        ApiServerFactory<DummyApiServer> apiServerFactory =
            (apiPort, runtimePort) -> DummyApiServer.create(apiPort, ImmutableMap.of());
        return builder(apiServerFactory);
      }

      static <ApiServerT extends Closeable> Builder<ApiServerT> builder(
          ApiServerFactory<ApiServerT> apiServerFactory) {
        return new AutoValue_JavaRuntimeViaHttpBase_RuntimeContext_Config.Builder<ApiServerT>()
            .setEnvironmentEntries(ImmutableMap.of())
            .setApiServerFactory(apiServerFactory);
      }

      @AutoValue.Builder
      abstract static class Builder<ApiServerT extends Closeable> {
        private boolean applicationPath;
        private boolean applicationRoot;

        /**
         * Sets the application path. In this approach, applicationPath is the complete application
         * location.
         */
        Builder<ApiServerT> setApplicationPath(String path) {
          applicationPath = true;
          launcherFlagsBuilder().add("--fixed_application_path=" + path);
          return this;
        }

        /** Sets Jetty's max request header size. */
        Builder<ApiServerT> setJettyRequestHeaderSize(int size) {
          launcherFlagsBuilder().add("--jetty_request_header_size=" + size);
          return this;
        }

        /** Sets Jetty's max response header size. */
        Builder<ApiServerT> setJettyResponseHeaderSize(int size) {
          launcherFlagsBuilder().add("--jetty_response_header_size=" + size);
          return this;
        }

        /**
         * Sets the application root. In this legacy case, you need to set the correct set of env
         * variables for "GAE_APPLICATION", "GAE_VERSION", "GAE_DEPLOYMENT_ID" with a correct
         * applicationRoot which is root to where applications/versions can be located.
         *
         * <p>In this case, the app must be staged in the correct directory structure:
         * application_root/$GAE_APPLICATION/$GAE_VERSION.$GAE_DEPLOYMENT_ID given to the runtime
         * via the AppServer file system.
         */
        Builder<ApiServerT> setApplicationRoot(String root) {
          applicationRoot = true;
          launcherFlagsBuilder().add("--application_root=" + root);
          return this;
        }

        abstract Builder<ApiServerT> setEnvironmentEntries(ImmutableMap<String, String> entries);

        abstract ImmutableList.Builder<String> launcherFlagsBuilder();

        abstract Builder<ApiServerT> setApiServerFactory(ApiServerFactory<ApiServerT> factory);

        abstract Config<ApiServerT> autoBuild();

        Config<ApiServerT> build() {
          if (applicationPath == applicationRoot) {
            throw new IllegalStateException(
                "Exactly one of applicationPath or applicationRoot must be set");
          }
          return autoBuild();
        }
      }
    }

    /** JVM flags needed for JDK above JDK8 */
    private static ImmutableList<String> optionalFlags() {
      if (!JAVA_VERSION.value().startsWith("1.8")) {
        return ImmutableList.of(
            "-showversion",
            "--add-opens",
            "java.base/java.lang=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens",
            "java.logging/java.util.logging=ALL-UNNAMED");
      }
      return ImmutableList.of("-showversion"); // Just so that the list is not empty.
    }

    static <ApiServerT extends Closeable> RuntimeContext<ApiServerT> create(
        Config<ApiServerT> config) throws IOException, InterruptedException {
      PortPicker portPicker = PortPicker.create();
      int jettyPort = portPicker.pickUnusedPort();
      int apiPort = portPicker.pickUnusedPort();

      String runtimeDirProperty = System.getProperty("appengine.runtime.dir");
      File runtimeDir =
          (runtimeDirProperty == null)
              ? new File(RUNTIME_LOCATION_ROOT, "runtime_java8/deployment_java8")
              : new File(runtimeDirProperty);
      assertWithMessage("Runtime directory %s should exist and be a directory", runtimeDir)
          .that(runtimeDir.isDirectory())
          .isTrue();
      InetSocketAddress apiSocketAddress = new InetSocketAddress(apiPort);

      ImmutableList<String> runtimeArgs =
          ImmutableList.<String>builder()
              .add(
                  JAVA_HOME.value() + "/bin/java",
                  "-Dcom.google.apphosting.runtime.jetty94.LEGACY_MODE=" + useJetty94LegacyMode(),
                  "-Duse.mavenjars=" + useMavenJars(),
                  "-cp",
                  useMavenJars()
                      ? new File(runtimeDir, "jars/runtime-main.jar").getAbsolutePath()
                      : new File(runtimeDir, "runtime-main.jar").getAbsolutePath())
              .addAll(optionalFlags())
              .addAll(jvmFlagsFromEnvironment(config.environmentEntries()))
              .add(
                  "com.google.apphosting.runtime.JavaRuntimeMainWithDefaults",
                  "--jetty_http_port=" + jettyPort,
                  "--port=" + apiPort,
                  "--trusted_host="
                      + HostAndPort.fromParts(apiSocketAddress.getHostString(), apiPort),
                  runtimeDir.getAbsolutePath())
              .addAll(config.launcherFlags())
              .build();

      Process runtimeProcess = launchRuntime(runtimeArgs, config.environmentEntries());
      OutputPump outPump = new OutputPump(runtimeProcess.getInputStream(), "[stdout] ");
      OutputPump errPump = new OutputPump(runtimeProcess.getErrorStream(), "[stderr] ");
      new Thread(outPump).start();
      new Thread(errPump).start();
      // TODO(b/192665275):
      // For some reason, a Maven build does not emit anymore this log, need to investigate.
      // For now, just wait a bit so the server is started in tests.
      Thread.sleep(SERVER_START_TIMEOUT_SECONDS * 100);

      int timeoutMillis = 30_000;
      RequestConfig requestConfig =
          RequestConfig.custom()
              .setConnectTimeout(timeoutMillis)
              .setConnectionRequestTimeout(timeoutMillis)
              .setSocketTimeout(timeoutMillis)
              .build();
      HttpClient httpClient =
          HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
      ApiServerT httpApiServer = config.apiServerFactory().newApiServer(apiPort, jettyPort);

      return new RuntimeContext<>(
          runtimeProcess, httpApiServer, httpClient, jettyPort, outPump, errPump);
    }

    private static List<String> jvmFlagsFromEnvironment(ImmutableMap<String, String> env) {
      return Splitter.on(' ').omitEmptyStrings().splitToList(env.getOrDefault("GAE_JAVA_OPTS", ""));
    }

    ApiServerT getApiServer() {
      return httpApiServer;
    }

    HttpClient getHttpClient() {
      return httpClient;
    }

    String jettyUrl(String urlPath) {
      return String.format(
          "http://%s%s",
          HostAndPort.fromParts(new InetSocketAddress(jettyPort).getHostString(), jettyPort),
          urlPath);
    }

    void executeHttpGet(String url, String expectedResponseBody, int expectedReturnCode)
        throws Exception {
      executeHttpGetWithRetries(
          url, expectedResponseBody, expectedReturnCode, /* numberOfRetries= */ 1);
    }

    String executeHttpGet(String urlPath, int expectedReturnCode) throws Exception {
      HttpGet get = new HttpGet(jettyUrl(urlPath));
      HttpResponse response = httpClient.execute(get);
      HttpEntity entity = response.getEntity();
      try {
        int retCode = response.getStatusLine().getStatusCode();
        assertThat(retCode).isEqualTo(expectedReturnCode);
        return EntityUtils.toString(entity);
      } finally {
        // When the assertion fails, the entity is never consumed because toString does not execute.
        // The end result is the associated http connections are leaked. To be safe, ensure the
        // entity is always fully consumed.
        EntityUtils.consumeQuietly(entity);
      }
    }

    void executeHttpGetWithRetries(
        String urlPath, String expectedResponse, int expectedReturnCode, int numberOfRetries)
        throws Exception {
      HttpGet get = new HttpGet(jettyUrl(urlPath));
      String content = "";
      int retCode = 0;
      for (int i = 0; i < numberOfRetries; i++) {
        HttpResponse response = httpClient.execute(get);
        retCode = response.getStatusLine().getStatusCode();
        content = EntityUtils.toString(response.getEntity());
        if ((retCode == expectedReturnCode) && content.contains(expectedResponse)) {
          return;
        }
        Thread.sleep(1000);
      }
      assertThat(content).isEqualTo(expectedResponse);
      assertThat(retCode).isEqualTo(expectedReturnCode);
    }

    void awaitStdoutLineMatching(String pattern, long timeoutSeconds) throws InterruptedException {
      outPump.awaitOutputLineMatching(pattern, timeoutSeconds);
    }

    void awaitStderrLineMatching(String pattern, long timeoutSeconds) throws InterruptedException {
      errPump.awaitOutputLineMatching(pattern, timeoutSeconds);
    }

    private static Process launchRuntime(
        ImmutableList<String> args, ImmutableMap<String, String> environmentEntries)
        throws IOException {
      ProcessBuilder pb = new ProcessBuilder(args);
      pb.environment().putAll(environmentEntries);
      return pb.start();
    }

    Process runtimeProcess() {
      return runtimeProcess;
    }

    @Override
    public void close() throws IOException {
      runtimeProcess.destroy();
      httpApiServer.close();
    }
  }

  static boolean useMavenJars() {
    return Boolean.getBoolean("use.mavenjars");
  }

  static boolean useJetty94LegacyMode() {
    return Boolean.getBoolean("com.google.apphosting.runtime.jetty94.LEGACY_MODE");
  }

  static class OutputPump implements Runnable {
    private final BufferedReader stream;
    private final String echoPrefix;
    private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();

    OutputPump(InputStream instream, String echoPrefix) {
      this.stream = new BufferedReader(new InputStreamReader(instream, UTF_8));
      this.echoPrefix = echoPrefix;
    }

    @Override
    public void run() {
      String line;
      try {
        while ((line = stream.readLine()) != null) {
          System.out.println(echoPrefix + line);
          outputQueue.add(line);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    void awaitOutputLineMatching(String pattern, long timeoutSeconds) throws InterruptedException {
      long timeoutMillis = MILLISECONDS.convert(timeoutSeconds, SECONDS);
      long deadline = System.currentTimeMillis() + timeoutMillis;
      while (true) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          throw new InterruptedException("Did not see pattern before deadline: " + pattern);
        }
        String line = outputQueue.poll(remaining, MILLISECONDS);
        if (line != null && line.matches(pattern)) {
          return;
        }
      }
    }
  }

  private static final Pattern JAR_URL_PATTERN = Pattern.compile("jar:file:(.*)!(.*)");
  private static final Pattern JAR_FILE_URL_PATTERN = Pattern.compile("file:(.*\\.jar)");

  /**
   * Extract the test app with the given name into the given output directory. The situation is that
   * we have a jar file in our classpath that contains this app, plus maybe a bunch of other stuff.
   * We expect the jar to contain both classes and our XML configuration files, {@code
   * appengine-web.xml} and optionally {@code web.xml}. We can find the jar file by looking for
   * {@code appengine-web.xml} as a resource. Then we'll copy the jar into {@code WEB-INF/lib/} in
   * the output directory, and we'll copy everything we find in the jar starting with the app prefix
   * into the same place in the output directory. For example, we'll copy {@code
   * com/google/apphosting/runtime/jetty9/WEB-INF/web.xml} to {@code WEB-INF/web.xml}. When appName
   * contains "/", we use it as the absolute resource path, otherwise it is relative to this class
   * path.
   */
  static void copyAppToDir(String appName, Path dir) throws IOException {
    Class<?> myClass = JavaRuntimeViaHttpBase.class;
    ClassLoader myClassLoader = myClass.getClassLoader();
    String appPrefix;
    if (appName.contains("/")) {
      appPrefix = appName + "/";
    } else {
      appPrefix = Reflection.getPackageName(myClass).replace('.', '/') + "/" + appName + "/";
    }
    String appEngineWebXmlResource = appPrefix + "WEB-INF/appengine-web.xml";
    URL appEngineWebXmlUrl = myClassLoader.getResource(appEngineWebXmlResource);
    assertWithMessage("Resource %s not found", appEngineWebXmlResource)
        .that(appEngineWebXmlUrl)
        .isNotNull();
    Matcher urlMatcher = JAR_URL_PATTERN.matcher(appEngineWebXmlUrl.toString());
    boolean found = urlMatcher.matches();
    assertWithMessage("Resource URL %s should match %s", appEngineWebXmlUrl, JAR_URL_PATTERN)
        .that(found)
        .isTrue();
    Path webInfLib = dir.resolve("WEB-INF/lib");
    Files.createDirectories(dir.resolve(webInfLib));
    String fileName = urlMatcher.group(1);
    if (FILE_SEPARATOR.value().equals("\\")) {
      // On Windows, remove leading 3 chars like /C: to avoid URL exception.
      fileName = fileName.substring(3);
    }
    Path fromJar = Paths.get(fileName);
    Path toJar = webInfLib.resolve(fromJar.getFileName());
    Files.copy(fromJar, toJar);

    // If the app includes APIs, like ThreadManager, then copy the API jar to WEB-INF/lib.
    copyJarContainingClass("com.google.appengine.api.ThreadManager", webInfLib);
    // Guava library.
    copyJarContainingClass("com.google.common.io.CountingInputStream", webInfLib);

    ImmutableSet<ResourceInfo> resources =
        ClassPath.from(myClassLoader).getResources().stream()
            .filter(info -> info.getResourceName().startsWith(appPrefix))
            .collect(toImmutableSet());
    assertThat(resources).isNotEmpty();
    for (ResourceInfo resource : resources) {
      String relative = resource.getResourceName().substring(appPrefix.length());
      try (InputStream in = resource.url().openStream()) {
        Path to = dir.resolve(relative);
        Files.createDirectories(to.getParent());
        Files.copy(in, to);
      }
    }
  }

  private static void copyJarContainingClass(String className, Path toPath) throws IOException {
    try {
      Class<?> threadManager = Class.forName(className);
      URL jarUrl = threadManager.getProtectionDomain().getCodeSource().getLocation();
      Matcher jarUrlMatcher = JAR_FILE_URL_PATTERN.matcher(jarUrl.toString());
      boolean jarUrlFound = jarUrlMatcher.matches();
      assertWithMessage("jar URL %s should match %s", jarUrl, JAR_URL_PATTERN)
          .that(jarUrlFound)
          .isTrue();
      String fileName = jarUrlMatcher.group(1);
      if (FILE_SEPARATOR.value().equals("\\")) {
        // On Windows, remove leading 3 chars like /C: to avoid URL exception.
        fileName = fileName.substring(3);
      }
      Path fromJar = Paths.get(fileName);
      Path toJar = toPath.resolve(fromJar.getFileName());
      Files.copy(fromJar, toJar, REPLACE_EXISTING);
    } catch (ClassNotFoundException e) {
      // OK: the app presumably doesn't need this.
    }
  }

  /**
   * An API server that handles API calls via the supplied handler map. Each incoming API call is
   * looked up as <i>{@code service.method}</i>, for example {@code urlfetch.Fetch}, to discover a
   * handler function, which is a {@code Function<ByteString, ByteString>}. If there is none then
   * the API call is rejected. If a handler is found, then it is called with the payload of the
   * {@link RemoteApiPb.Request} as input, which is conventionally a serialized protobuf specific to
   * the service and method. It is expected to return another serialized protobuf that will be used
   * as the payload of the returned {@link RemoteApiPb.Response}.
   */
  static class DummyApiServer implements Closeable {
    private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

    static DummyApiServer create(
        int apiPort, ImmutableMap<String, Function<ByteString, ByteString>> handlerMap)
        throws IOException {
      return create(apiPort, handlerMap, request -> {});
    }

    static DummyApiServer create(
        int apiPort,
        ImmutableMap<String, Function<ByteString, ByteString>> handlerMap,
        Consumer<RemoteApiPb.Request> requestObserver)
        throws IOException {
      InetSocketAddress address = new InetSocketAddress(apiPort);
      HttpServer httpServer = HttpServer.create(address, 0);
      DummyApiServer apiServer = new DummyApiServer(httpServer, handlerMap::get, requestObserver);
      httpServer.createContext("/", apiServer::handle);
      httpServer.setExecutor(Executors.newCachedThreadPool());
      httpServer.start();
      return apiServer;
    }

    private final HttpServer httpServer;
    private final Function<String, Function<ByteString, ByteString>> handlerLookup;
    private final Consumer<RemoteApiPb.Request> requestObserver;

    DummyApiServer(
        HttpServer httpServer, Function<String, Function<ByteString, ByteString>> handlerLookup) {
      this(httpServer, handlerLookup, request -> {});
    }

    private DummyApiServer(
        HttpServer httpServer,
        Function<String, Function<ByteString, ByteString>> handlerLookup,
        Consumer<RemoteApiPb.Request> requestObserver) {
      this.httpServer = httpServer;
      this.handlerLookup = handlerLookup;
      this.requestObserver = requestObserver;
    }

    @Override
    public void close() {
      httpServer.stop(0);
    }

    @ForOverride
    RemoteApiPb.Response.Builder newResponseBuilder() {
      return RemoteApiPb.Response.newBuilder();
    }

    void handle(HttpExchange exchange) throws IOException {
      try (InputStream in = exchange.getRequestBody();
          OutputStream out = exchange.getResponseBody()) {
        RemoteApiPb.Request requestPb;
        try {
          requestPb = RemoteApiPb.Request.parseFrom(in, ExtensionRegistry.getEmptyRegistry());
        } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
          logger.atWarning().withCause(e).log("Couldn't parse received RemoteApiPb.Request");
          exchange.sendResponseHeaders(HTTP_BAD_REQUEST, 0);
          return;
        }
        requestObserver.accept(requestPb);
        String method = requestPb.getServiceName() + "." + requestPb.getMethod();
        Function<ByteString, ByteString> handler = handlerLookup.apply(method);
        if (handler == null) {
          logger.atWarning().log("Unexpected API call %s", method);
          exchange.sendResponseHeaders(HTTP_BAD_REQUEST, 0);
          return;
        }
        ByteString requestPayload = requestPb.getRequest();
        ByteString responsePayload;
        try {
          responsePayload = handler.apply(requestPayload);
        } catch (RuntimeException | Error e) {
          logger.atWarning().withCause(e).log("Exception handling %s", method);
          exchange.sendResponseHeaders(HTTP_INTERNAL_ERROR, 0);
          return;
        }
        RemoteApiPb.Response responsePb = newResponseBuilder().setResponse(responsePayload).build();
        exchange.sendResponseHeaders(HTTP_OK, responsePb.getSerializedSize());
        responsePb.writeTo(out);
      }
    }
  }
}
