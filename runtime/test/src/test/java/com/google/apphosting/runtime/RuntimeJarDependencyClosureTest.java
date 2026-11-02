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
package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Checks that every class reachable from the App Engine classes bundled in a runtime-impl jar can
 * be loaded from the runtime class path.
 *
 * <p>The runtime jars bundle App Engine API classes (e.g. all of {@code
 * com.google.appengine.api.taskqueue}) that the runtime uses from its own class loader, for
 * instance for async session persistence. When those classes start using a new library, the
 * library must also be added to the jar's shade {@code artifactSet}, otherwise the runtime fails
 * at run time with a {@code NoClassDefFoundError}. This test runs {@code jdeps} on the jar, with
 * the other jars of the runtime deployment on the class path, follows every reference starting
 * from {@code com.google.appengine} and {@code com.google.apphosting} classes, and fails on any
 * missing class that is not a known, unused optional dependency.
 */
@RunWith(Parameterized.class)
public final class RuntimeJarDependencyClosureTest {

  /**
   * Packages that are known to be referenced but not on the runtime class path, on code paths the
   * runtime does not use. Matches the package itself and its subpackages.
   */
  private static final ImmutableMap<String, String> KNOWN_MISSING_PACKAGES =
      ImmutableMap.<String, String>builder()
          .put("org.antlr.runtime", "borgcron Groc schedule parser from appengine-utils")
          .put(
              "com.google.api.client.auth.oauth2",
              "Cloud Datastore v1 client (CloudDatastoreV1ClientImpl, com.google.datastore.v1)")
          .put(
              "com.google.api.client.googleapis",
              "Cloud Datastore v1 client (CloudDatastoreV1ClientImpl, com.google.datastore.v1)")
          .put("io.grpc.alts", "gax-grpc DirectPath, which is not enabled")
          .put("org.eclipse.jetty.alpn.client", "ALPN support of the Jetty HTTP client")
          .put("javax.transaction", "Jetty JNDI UserTransaction, needs a transaction manager")
          .put("jakarta.transaction", "Jetty JNDI UserTransaction, needs a transaction manager")
          .buildOrThrow();

  private static final ImmutableList<String> ROOT_PREFIXES =
      ImmutableList.of("com.google.appengine.", "com.google.apphosting.");

  /** A {@code jdeps -verbose:class} dependency line: source, target, and where it was found. */
  private static final Pattern DEPENDENCY_LINE =
      Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+?)\\s*$");

  @Parameters(name = "{0}")
  public static List<Object[]> jars() {
    return Arrays.asList(
        new Object[][] {{"runtime-impl-jetty12.jar"}, {"runtime-impl-jetty121.jar"}});
  }

  private final String jarName;

  public RuntimeJarDependencyClosureTest(String jarName) {
    this.jarName = jarName;
  }

  @Test
  public void allReachableClassesAreOnRuntimeClassPath() {
    Optional<ToolProvider> jdeps = ToolProvider.findFirst("jdeps");
    assumeTrue("jdeps is only available in a full JDK", jdeps.isPresent());
    String runtimeDirProperty = System.getProperty("appengine.runtime.dir");
    assumeTrue("appengine.runtime.dir is not set", runtimeDirProperty != null);
    File runtimeDir = new File(runtimeDirProperty);
    File jar = new File(runtimeDir, jarName);
    assertWithMessage("Runtime jar %s should exist", jar).that(jar.isFile()).isTrue();

    // The jar is loaded together with runtime-main.jar and one runtime-shared jar; use all of them
    // so that only classes missing from the whole deployment are reported.
    File[] otherJars =
        runtimeDir.listFiles(
            (dir, name) -> name.endsWith(".jar") && !name.startsWith("runtime-impl-"));
    String classPath =
        Arrays.stream(otherJars)
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(File.pathSeparator));

    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    int exitCode =
        jdeps
            .get()
            .run(
                new PrintWriter(out),
                new PrintWriter(err),
                "--multi-release",
                Integer.toString(Runtime.version().feature()),
                "-verbose:class",
                "--ignore-missing-deps",
                "--class-path",
                classPath,
                jar.getAbsolutePath());
    assertWithMessage("jdeps failed: %s", err).that(exitCode).isEqualTo(0);

    Map<String, Set<String>> bundledEdges = new HashMap<>();
    Map<String, Set<String>> missingEdges = new HashMap<>();
    Set<String> classes = new HashSet<>();
    for (String line : out.toString().split("\n")) {
      Matcher m = DEPENDENCY_LINE.matcher(line);
      if (!m.matches()) {
        continue;
      }
      String from = m.group(1);
      String to = m.group(2);
      String location = m.group(3);
      classes.add(from);
      if (location.equals("not found")) {
        missingEdges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
      } else if (location.equals(jarName)) {
        bundledEdges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
      }
    }

    // Breadth-first walk over the classes of this jar, starting from the App Engine classes and
    // remembering how each class was reached, to explain any failure.
    Map<String, String> reachedFrom = new HashMap<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    for (String c : classes) {
      if (ROOT_PREFIXES.stream().anyMatch(c::startsWith)) {
        reachedFrom.put(c, null);
        queue.add(c);
      }
    }
    assertWithMessage("No App Engine classes found in %s", jar).that(queue).isNotEmpty();
    Map<String, String> unexpectedMissing = new TreeMap<>();
    while (!queue.isEmpty()) {
      String c = queue.remove();
      for (String missing : missingEdges.getOrDefault(c, Set.of())) {
        if (!isKnownMissing(missing)) {
          unexpectedMissing.putIfAbsent(missing, c);
        }
      }
      for (String next : bundledEdges.getOrDefault(c, Set.of())) {
        if (!reachedFrom.containsKey(next)) {
          reachedFrom.put(next, c);
          queue.add(next);
        }
      }
    }

    List<String> report = new ArrayList<>();
    unexpectedMissing.forEach(
        (missing, from) -> report.add(missing + "  <-  " + referenceChain(from, reachedFrom)));
    assertWithMessage(
            "Classes referenced from App Engine code in %s are missing from the runtime class"
                + " path. Add the library that provides them to the shade artifactSet of the"
                + " runtime_impl pom (and to the internal BUILD deps), or, if the code path is"
                + " never used by the runtime, to KNOWN_MISSING_PACKAGES with a reason",
            jarName)
        .that(report)
        .isEmpty();
  }

  private static boolean isKnownMissing(String className) {
    int lastDot = className.lastIndexOf('.');
    String pkg = lastDot < 0 ? "" : className.substring(0, lastDot);
    return KNOWN_MISSING_PACKAGES.keySet().stream()
        .anyMatch(known -> pkg.equals(known) || pkg.startsWith(known + "."));
  }

  /** Returns {@code from}'s reference chain back to an App Engine class, innermost first. */
  private static String referenceChain(String from, Map<String, String> reachedFrom) {
    List<String> chain = new ArrayList<>();
    for (String c = from; c != null && chain.size() < 10; c = reachedFrom.get(c)) {
      chain.add(c);
    }
    return String.join(" <- ", chain);
  }
}
