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

package com.google.apphosting.runtime;

import static java.util.stream.Collectors.partitioningBy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The primary entry point for starting up a {@link JavaRuntime}. This class creates a new {@link
 * RuntimeClassLoader} with an appropriate classpath and launches a new {@code JavaRuntime} in that
 * {@code ClassLoader} via {@link JavaRuntimeFactory}.
 */
public class JavaRuntimeMainWithDefaults {
  private static final String PORT_ENV_VARIABLE_NAME = "PORT";
  private static final String GAE_DISABLE_NGINX = "GAE_DISABLE_NGINX";
  private static final String API_HOSTPORT_ENV_VARIABLE_NAME = "LOCAL_API_HOSTPORT";
  private static final String GAE_PARTITION_ENV_VARIABLE_NAME = "GAE_PARTITION";

  private static String getLocation(File runtimeLocation, String fileName) {
    File loc = new File(runtimeLocation, fileName);
    if (!loc.exists()) {
      throw new RuntimeException(loc.getAbsolutePath() + " does not exist.");
    }
    return loc.getAbsolutePath();
  }

  private static void setRuntimeOptions(File runtimeLocation) {

    System.setProperty("appengine.api.urlfetch.defaultTlsValidation", "true");
    System.setProperty("classpath.runtimebase", getLocation(runtimeLocation, ""));
    System.setProperty("com.google.apphosting.runtime.disableChdir", "true");
    System.setProperty("com.google.common.logging.EventId.allowLoopbackIP", "true");
    System.setProperty("file.encoding", "UTF-8");
    System.setProperty("java.awt.headless", "true");
    System.setProperty(
        "java.class.path",
        useMavenJars()
            ? getLocation(runtimeLocation, "jars/runtime-main.jar")
            : getLocation(runtimeLocation, "runtime-main.jar"));
    System.setProperty("java.library.path", getLocation(runtimeLocation, ""));
    System.setProperty("java.security.egd", "file:/dev/urandom");
    System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
    System.setProperty("user.timezone", "UTC");
  }

  static boolean useMavenJars() {
    return Boolean.getBoolean("use.mavenjars");
  }

  private static List<String> getRuntimeFlags() {
    // Can't use Guava here, as the main class has minimal deps in order to have a small class
    // loader.
    String jettyPort = System.getenv().getOrDefault(PORT_ENV_VARIABLE_NAME, "8080");
    boolean disableNGinx = "true".equals(System.getenv(GAE_DISABLE_NGINX));
    if (disableNGinx) {
      // Having Jetty listening to default nginx port will disable nginx.
      jettyPort = "8080";
    }

    // By convention, inherited from the managed vm work, when running locally, we should define
    // the GAE_PARTION variable to "dev"... This is used in the runtime image common /serve.
    // In local mode, the API server should be configured on localhost:$LOCAL_API_PORT
    // In prod, the API server is hardcoded to appengine.googleapis.internal:10001
    boolean executionIsLocal = "dev".equals(System.getenv(GAE_PARTITION_ENV_VARIABLE_NAME));
    String trustedPort =
        executionIsLocal
            ? System.getenv().getOrDefault(API_HOSTPORT_ENV_VARIABLE_NAME, "localhost:8089")
            : "appengine.googleapis.internal:10001";
    return Arrays.asList(
        "--jetty_http_port=" + jettyPort,
        "--trusted_host=" + trustedPort);
  }

  public static void main(String[] args) {
    ArrayList<String> defaultFlags = new ArrayList<>(getRuntimeFlags());

    Map<Boolean, List<String>> paramsAndFlags =
        Arrays.stream(args).collect(partitioningBy(arg -> arg.startsWith("--")));
    // Runtime location is the only arg not starting with --
    List<String> runtimeLocations = paramsAndFlags.get(false);
    String runtimeLocation;
    switch (runtimeLocations.size()) { // Iterables.getOnlyElement would be nice, but again no Guava
      case 0:
        runtimeLocation = new File("").getAbsolutePath();
        break;
      case 1:
        runtimeLocation = runtimeLocations.get(0);
        break;
      default:
        System.err.println("Error: only 1 parameter is accepted for the runtime location.");
        System.err.println("Got: " + runtimeLocations);
        System.exit(1);
        throw new Error(); // not reached
    }
    List<String> userFlags = paramsAndFlags.get(true);
    boolean applicationLocationSet =
        userFlags.stream().anyMatch(s -> s.startsWith("--fixed_application_path="));
    if (!applicationLocationSet) {
      System.err.println("Error: --fixed_application_path flag is mandatory.");
      System.exit(1);
    }
    setRuntimeOptions(new File(runtimeLocation));
    // User flags after default flags so they can override them.
    defaultFlags.addAll(userFlags);

    String[] newArgs = defaultFlags.toArray(new String[0]);
    System.out.println("Starting the server with these flags: " + defaultFlags);
    JavaRuntimeMain.main(newArgs);
  }
}
