/*
 * Copyright 2026 Google LLC
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
package com.google.appengine.api.taskqueue;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests verifying Task Queue classloading isolation and shaded Cloud Tasks / gRPC /
 * Google Auth / PerfMark dependency completeness in the packaged {@code appengine-api-1.0-sdk.jar}.
 */
@RunWith(JUnit4.class)
public final class ShadingIT {
  private static final String REPACKAGED = "com.google.appengine.repackaged.";
  private static final Pattern JDEPS_CLASS_EDGE =
      Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$");

  /**
   * External or optional package prefixes intentionally excluded from the shaded SDK JAR:
   *
   * <ul>
   *   <li>{@code javax.servlet.} / {@code jakarta.servlet.}: provided by the servlet container.
   *   <li>{@code com.google.appengine.repackaged.io.grpc.alts.}: DirectPath/ALTS is out of scope
   *       ({@code CloudTasksStubSettings} leaves DirectPath disabled and uses standard TLS).
   *   <li>Compile-time annotation packages ({@code com.google.auto.value.}, {@code
   *       com.google.j2objc.annotations.}, {@code org.jetbrains.annotations.}, {@code
   *       org.jspecify.annotations.}).
   *   <li>Optional logging and SSL provider facades probed dynamically with JDK fallbacks ({@code
   *       org.slf4j.}, {@code org.apache.log4j.}, {@code org.apache.logging.log4j.}, {@code
   *       org.bouncycastle.}, {@code org.conscrypt.}).
   * </ul>
   */
  private static final List<String> KNOWN_EXTERNAL_OR_OPTIONAL_PREFIXES =
      List.of(
          "javax.servlet.",
          "jakarta.servlet.",
          REPACKAGED + "io.grpc.alts.",
          "com.google.auto.value.",
          "com.google.j2objc.annotations.",
          "org.jetbrains.annotations.",
          "org.jspecify.annotations.",
          "org.slf4j.",
          "org.apache.log4j.",
          "org.apache.logging.log4j.",
          "org.bouncycastle.",
          "org.conscrypt.");

  private record DepEdge(String target, boolean notFound) {}

  private static File getShadedSdkJar() {
    String shadedJarPath = System.getProperty("sdk.shaded.jar");
    assertWithMessage("System property sdk.shaded.jar must be set").that(shadedJarPath).isNotNull();
    File shadedJar = new File(shadedJarPath);
    assertWithMessage("Expected shaded jar %s to exist", shadedJar)
        .that(shadedJar.isFile())
        .isTrue();
    return shadedJar;
  }

  @Test
  public void queueImpl_whenCloudTasksDisabled_purgesViaApiProxyWithoutLoadingCloudTasksOrAuth()
      throws Exception {
    File shadedJar = getShadedSdkJar();
    List<String> blockedLoadAttempts = new ArrayList<>();
    List<String> recordedApiCalls = new ArrayList<>();
    ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();

    try (FilteringUrlClassLoader isolatedLoader =
        new FilteringUrlClassLoader(
            new URL[] {shadedJar.toURI().toURL()},
            ClassLoader.getPlatformClassLoader(),
            REPACKAGED,
            blockedLoadAttempts)) {
      Thread.currentThread().setContextClassLoader(isolatedLoader);

      Class<?> queueImplClass =
          Class.forName("com.google.appengine.api.taskqueue.QueueImpl", true, isolatedLoader);
      assertThat(queueImplClass.getProtectionDomain().getCodeSource().getLocation())
          .isEqualTo(shadedJar.toURI().toURL());

      Method isEnabledMethod = queueImplClass.getDeclaredMethod("isCloudTaskBackendEnabled");
      isEnabledMethod.setAccessible(true);
      Method setEnabledMethod =
          queueImplClass.getDeclaredMethod("setCloudTaskEnabledForTesting", Boolean.class);
      setEnabledMethod.setAccessible(true);

      if (!Boolean.parseBoolean(System.getenv("APPENGINE_USE_CLOUDTASK_PUSH_QUEUE"))) {
        assertThat((Boolean) isEnabledMethod.invoke(null)).isFalse();
      }

      Class<?> apiProxyClass =
          Class.forName("com.google.apphosting.api.ApiProxy", true, isolatedLoader);
      Class<?> envInterface =
          Class.forName("com.google.apphosting.api.ApiProxy$Environment", true, isolatedLoader);
      Class<?> delegateInterface =
          Class.forName("com.google.apphosting.api.ApiProxy$Delegate", true, isolatedLoader);

      installStubApiProxy(
          isolatedLoader, apiProxyClass, envInterface, delegateInterface, recordedApiCalls);
      try {
        setEnabledMethod.invoke(null, false);
        Class<?> queueFactoryClass =
            Class.forName("com.google.appengine.api.taskqueue.QueueFactory", true, isolatedLoader);
        Class<?> queueInterface =
            Class.forName("com.google.appengine.api.taskqueue.Queue", true, isolatedLoader);
        Object defaultQueue = queueFactoryClass.getMethod("getDefaultQueue").invoke(null);
        Method purgeMethod = queueInterface.getMethod("purge");

        purgeMethod.invoke(defaultQueue);
        assertThat(recordedApiCalls).containsExactly("taskqueue.PurgeQueue");
        assertThat(blockedLoadAttempts).isEmpty();

        setEnabledMethod.invoke(null, true);
        InvocationTargetException error =
            assertThrows(InvocationTargetException.class, () -> purgeMethod.invoke(defaultQueue));
        assertThat(error).hasCauseThat().isInstanceOf(NoClassDefFoundError.class);
        assertThat(error).hasCauseThat().hasMessageThat().contains("CloudTasksClientWrapper");
        assertThat(blockedLoadAttempts)
            .containsExactly("com.google.appengine.api.taskqueue.CloudTasksClientWrapper");
      } finally {
        setEnabledMethod.invoke(null, (Object) null);
        apiProxyClass.getMethod("setDelegate", delegateInterface).invoke(null, (Object) null);
        apiProxyClass.getMethod("clearEnvironmentForCurrentThread").invoke(null);
      }
    } finally {
      Thread.currentThread().setContextClassLoader(previousContextLoader);
    }
  }

  @Test
  public void cloudTasksClient_whenEnabled_shadesGrpcAuthAndPerfMarkRuntimePath() throws Exception {
    File shadedJar = getShadedSdkJar();
    ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();

    try (URLClassLoader isolatedLoader =
        new URLClassLoader(
            new URL[] {shadedJar.toURI().toURL()}, ClassLoader.getPlatformClassLoader())) {
      Thread.currentThread().setContextClassLoader(isolatedLoader);

      Class<?> perfMarkClass =
          Class.forName(REPACKAGED + "io.perfmark.PerfMark", true, isolatedLoader);
      assertThat(perfMarkClass.getMethod("createTag").invoke(null)).isNotNull();

      Class<?> channelProviderClass =
          Class.forName(REPACKAGED + "io.grpc.ManagedChannelProvider", true, isolatedLoader);
      assertThat(channelProviderClass.getMethod("provider").invoke(null)).isNotNull();

      verifyWrapperClientsAndLoopbackRpcs(isolatedLoader, shadedJar, REPACKAGED);
    } finally {
      Thread.currentThread().setContextClassLoader(previousContextLoader);
    }
  }

  @Test
  public void taskQueueAndGrpcSpi_haveCompleteShadedDependencyClosure() throws Exception {
    Optional<ToolProvider> jdeps = ToolProvider.findFirst("jdeps");
    assumeTrue("jdeps tool is required for dependency closure check", jdeps.isPresent());

    File shadedJar = getShadedSdkJar();
    Set<String> jarClasses = new HashSet<>();
    List<String> grpcSpiProviders = new ArrayList<>();
    try (JarFile jarFile = new JarFile(shadedJar)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
          jarClasses.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
        }
      }
      Enumeration<JarEntry> serviceEntries = jarFile.entries();
      while (serviceEntries.hasMoreElements()) {
        JarEntry entry = serviceEntries.nextElement();
        if (entry.getName().startsWith("META-INF/services/" + REPACKAGED + "io.grpc.")) {
          try (BufferedReader reader =
              new BufferedReader(new InputStreamReader(jarFile.getInputStream(entry), UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
              int commentIdx = line.indexOf('#');
              String provider = (commentIdx >= 0 ? line.substring(0, commentIdx) : line).trim();
              if (!provider.isEmpty() && jarClasses.contains(provider)) {
                grpcSpiProviders.add(provider);
              }
            }
          }
        }
      }
    }
    assertThat(grpcSpiProviders).isNotEmpty();

    StringWriter stdout = new StringWriter();
    StringWriter stderr = new StringWriter();
    int exitCode =
        jdeps
            .get()
            .run(
                new PrintWriter(stdout),
                new PrintWriter(stderr),
                "-verbose:class",
                "-filter:none",
                shadedJar.getAbsolutePath());
    assertWithMessage("jdeps failed: %s", stderr).that(exitCode).isEqualTo(0);

    Map<String, List<DepEdge>> edges = new HashMap<>();
    for (String line : stdout.toString().split("\n", -1)) {
      Matcher matcher = JDEPS_CLASS_EDGE.matcher(line);
      if (matcher.matches()) {
        String source = matcher.group(1);
        String target = matcher.group(2);
        boolean notFound = matcher.group(3).trim().equals("not found");
        edges
            .computeIfAbsent(source, unused -> new ArrayList<>())
            .add(new DepEdge(target, notFound));
      }
    }

    Deque<String> queue = new ArrayDeque<>();
    Set<String> visited = new HashSet<>();
    Map<String, String> parent = new HashMap<>();
    for (String className : jarClasses) {
      if (className.startsWith("com.google.appengine.api.taskqueue.")) {
        visited.add(className);
        queue.add(className);
      }
    }
    for (String provider : grpcSpiProviders) {
      if (visited.add(provider)) {
        queue.add(provider);
      }
    }

    Map<String, String> unexpectedMissing = new TreeMap<>();
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      for (DepEdge edge : edges.getOrDefault(current, Collections.emptyList())) {
        if (edge.notFound()) {
          boolean isKnownOptional =
              KNOWN_EXTERNAL_OR_OPTIONAL_PREFIXES.stream().anyMatch(edge.target()::startsWith);
          if (!isKnownOptional && !unexpectedMissing.containsKey(edge.target())) {
            StringBuilder chain = new StringBuilder(edge.target()).append(" <- ").append(current);
            String cursor = current;
            while (parent.containsKey(cursor)) {
              cursor = parent.get(cursor);
              chain.append(" <- ").append(cursor);
            }
            unexpectedMissing.put(edge.target(), chain.toString());
          }
        } else if (jarClasses.contains(edge.target()) && visited.add(edge.target())) {
          parent.put(edge.target(), current);
          queue.addLast(edge.target());
        }
      }
    }

    assertThat(visited).contains(REPACKAGED + "io.perfmark.PerfMark");
    assertWithMessage("Unexpected missing shaded dependencies in %s", shadedJar)
        .that(unexpectedMissing)
        .isEmpty();
  }

  private static void verifyWrapperClientsAndLoopbackRpcs(
      ClassLoader loader, File expectedJar, String prefix) throws Exception {
    Class<?> wrapperClass =
        Class.forName("com.google.appengine.api.taskqueue.CloudTasksClientWrapper", true, loader);
    assertThat(wrapperClass.getProtectionDomain().getCodeSource().getLocation())
        .isEqualTo(expectedJar.toURI().toURL());

    Method setCredsToken =
        wrapperClass.getDeclaredMethod("setCredentialsTokenForTesting", String.class);
    setCredsToken.setAccessible(true);
    Method setAccessToken =
        wrapperClass.getDeclaredMethod("setAccessTokenForTesting", String.class, long.class);
    setAccessToken.setAccessible(true);
    Method getClient = wrapperClass.getDeclaredMethod("getClient");
    getClient.setAccessible(true);
    Method getV2Beta3Client = wrapperClass.getDeclaredMethod("getV2Beta3Client");
    getV2Beta3Client.setAccessible(true);

    Class<?> v2ClientClass =
        Class.forName(prefix + "com.google.cloud.tasks.v2.CloudTasksClient", true, loader);
    Class<?> v2Beta3ClientClass =
        Class.forName(prefix + "com.google.cloud.tasks.v2beta3.CloudTasksClient", true, loader);
    Method setSharedClient =
        wrapperClass.getDeclaredMethod("setSharedClientForTesting", v2ClientClass);
    setSharedClient.setAccessible(true);
    Method setSharedV2Beta3Client =
        wrapperClass.getDeclaredMethod("setSharedV2Beta3ClientForTesting", v2Beta3ClientClass);
    setSharedV2Beta3Client.setAccessible(true);

    setCredsToken.invoke(null, "shading-it-token");
    setAccessToken.invoke(null, "shading-it-token", System.currentTimeMillis() + 3_600_000L);
    Object v2Client = null;
    Object v2Beta3Client = null;
    try {
      v2Client = getClient.invoke(null);
      assertThat(v2Client).isNotNull();
      v2Beta3Client = getV2Beta3Client.invoke(null);
      assertThat(v2Beta3Client).isNotNull();
    } finally {
      if (v2Client instanceof AutoCloseable closeable) {
        closeable.close();
      }
      if (v2Beta3Client instanceof AutoCloseable closeable) {
        closeable.close();
      }
      setSharedClient.invoke(null, (Object) null);
      setSharedV2Beta3Client.invoke(null, (Object) null);
      setCredsToken.invoke(null, (Object) null);
      setAccessToken.invoke(null, (Object) null, 0L);
    }

    runLoopbackGrpcRpc(
        loader,
        prefix,
        prefix + "com.google.cloud.tasks.v2.CloudTasksSettings",
        prefix + "com.google.cloud.tasks.v2.CloudTasksClient",
        prefix + "com.google.cloud.tasks.v2.CreateTaskRequest",
        "createTask");
    runLoopbackGrpcRpc(
        loader,
        prefix,
        prefix + "com.google.cloud.tasks.v2beta3.CloudTasksSettings",
        prefix + "com.google.cloud.tasks.v2beta3.CloudTasksClient",
        prefix + "com.google.cloud.tasks.v2beta3.GetQueueRequest",
        "getQueue");
  }

  private static void runLoopbackGrpcRpc(
      ClassLoader loader,
      String prefix,
      String settingsClassName,
      String clientClassName,
      String requestClassName,
      String rpcMethodName)
      throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
      int port = serverSocket.getLocalPort();
      Thread acceptThread =
          new Thread(
              () -> {
                while (!serverSocket.isClosed()) {
                  try (Socket accepted = serverSocket.accept()) {
                    // Immediately close each accepted socket to trigger gRPC UNAVAILABLE after full
                    // client-side marshalling, PerfMark tracing, and Netty transport execution.
                  } catch (IOException expected) {
                    // ServerSocket closed before or during accept teardown.
                    break;
                  }
                }
              });
      acceptThread.setDaemon(true);
      acceptThread.start();

      Class<?> settingsClass = Class.forName(settingsClassName, true, loader);
      Object settingsBuilder = settingsClass.getMethod("newBuilder").invoke(null);
      settingsBuilder
          .getClass()
          .getMethod("setEndpoint", String.class)
          .invoke(settingsBuilder, "127.0.0.1:" + port);
      Class<?> noCredsClass =
          Class.forName(prefix + "com.google.api.gax.core.NoCredentialsProvider", true, loader);
      Class<?> credsProviderClass =
          Class.forName(prefix + "com.google.api.gax.core.CredentialsProvider", true, loader);
      Object noCreds = noCredsClass.getMethod("create").invoke(null);
      settingsBuilder
          .getClass()
          .getMethod("setCredentialsProvider", credsProviderClass)
          .invoke(settingsBuilder, noCreds);
      Object callSettingsBuilder =
          settingsBuilder.getClass().getMethod(rpcMethodName + "Settings").invoke(settingsBuilder);
      callSettingsBuilder
          .getClass()
          .getMethod("setRetryableCodes", Set.class)
          .invoke(callSettingsBuilder, Collections.emptySet());
      Object settings = settingsBuilder.getClass().getMethod("build").invoke(settingsBuilder);

      Class<?> clientClass = Class.forName(clientClassName, true, loader);
      Object client = clientClass.getMethod("create", settingsClass).invoke(null, settings);
      try {
        Class<?> requestClass = Class.forName(requestClassName, true, loader);
        Object defaultRequest = requestClass.getMethod("getDefaultInstance").invoke(null);
        InvocationTargetException error =
            assertThrows(
                InvocationTargetException.class,
                () ->
                    clientClass
                        .getMethod(rpcMethodName, requestClass)
                        .invoke(client, defaultRequest));
        assertThat(error.getCause()).isNotNull();
        assertThat(error.getCause().getClass().getName())
            .isEqualTo(prefix + "com.google.api.gax.rpc.UnavailableException");
      } finally {
        ((AutoCloseable) client).close();
      }
    }
  }

  private static void installStubApiProxy(
      ClassLoader loader,
      Class<?> apiProxyClass,
      Class<?> envInterface,
      Class<?> delegateInterface,
      List<String> recordedApiCalls)
      throws Exception {
    Map<String, Object> attributes = new ConcurrentHashMap<>();
    Object envProxy =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {envInterface},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getAppId" -> "test-app";
                  case "getModuleId" -> "default";
                  case "getVersionId" -> "v1.1";
                  case "getEmail", "getRequestNamespace" -> "";
                  case "getAuthDomain" -> "gmail.com";
                  case "isLoggedIn", "isAdmin" -> false;
                  case "getAttributes" -> attributes;
                  case "getRemainingMillis" -> Long.MAX_VALUE;
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  case "toString" -> "ShadingITEnvironment";
                  default -> null;
                });
    Object delegateProxy =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {delegateInterface},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "makeSyncCall" -> {
                    recordedApiCalls.add(args[1] + "." + args[2]);
                    yield new byte[0];
                  }
                  case "makeAsyncCall" -> {
                    recordedApiCalls.add(args[1] + "." + args[2]);
                    yield CompletableFuture.completedFuture(new byte[0]);
                  }
                  case "getRequestThreads" -> Collections.emptyList();
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  case "toString" -> "ShadingITDelegate";
                  default -> null;
                });
    apiProxyClass.getMethod("setEnvironmentForCurrentThread", envInterface).invoke(null, envProxy);
    apiProxyClass.getMethod("setDelegate", delegateInterface).invoke(null, delegateProxy);
  }

  private static final class FilteringUrlClassLoader extends URLClassLoader {
    private final String repackagedPrefix;
    private final List<String> blockedLoadAttempts;

    FilteringUrlClassLoader(
        URL[] urls, ClassLoader parent, String repackagedPrefix, List<String> blockedLoadAttempts) {
      super(urls, parent);
      this.repackagedPrefix = repackagedPrefix;
      this.blockedLoadAttempts = blockedLoadAttempts;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        if (name.equals("com.google.appengine.api.taskqueue.CloudTasksClientWrapper")
            || name.startsWith(repackagedPrefix + "com.google.auth.")
            || name.startsWith(repackagedPrefix + "com.google.cloud.tasks.")
            || name.startsWith(repackagedPrefix + "com.google.api.gax.")) {
          blockedLoadAttempts.add(name);
          throw new ClassNotFoundException("Blocked in disabled mode: " + name);
        }
        return super.loadClass(name, resolve);
      }
    }
  }
}
