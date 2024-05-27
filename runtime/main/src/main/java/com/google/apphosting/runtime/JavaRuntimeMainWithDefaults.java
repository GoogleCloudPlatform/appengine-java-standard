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
    if (System.getProperty("java.class.path") == null) {
      System.setProperty(
              "java.class.path",
              useMavenJars()
                      ? getLocation(runtimeLocation, "jars/runtime-main.jar")
                      : getLocation(runtimeLocation, "runtime-main.jar"));
    }
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
        "--api_call_deadline=10",
        "--api_call_deadline_map=app_config_service:60.0,"
            + "blobstore:15.0,"
            + "datastore_v3:60.0,"
            + "datastore_v4:60.0,file:30.0,"
            + "images:30.0,"
            + "logservice:60.0,"
            + "modules:60.0,"
            + "rdbms:60.0,"
            + "remote_socket:60.0,"
            + "search:10.0,"
            + "stubby:10.0",
        "--appengine_release_name=mainwithdefaults",
        "--application_root=notused",
        "--cycles_per_second=1000000000",
        "--default_to_builtin_url_stream_handler=true",
        "--default_use_google_connectorj=true",
        "--enable_gae_cloud_sql_jdbc_connectivity=true",
        "--enable_synchronized_app_logs_writer=true",
        "--external_datacenter_name=MARS",
        // This flag must be given in this Main.
        // "--fixed_application_path=" + ...,
        "--force_readahead_on_cloudsql_socket=true",
        "--interrupt_threads_first_on_soft_deadline=true",
        "--java_hard_deadline_ms=10200",
        "--java_soft_deadline_ms=10600",
        "--jetty_http_port=" + jettyPort,
        "--jetty_request_header_size=262144",
        "--jetty_response_header_size=262144",
        "--mail_filename_prevents_inlining=true",
        "--mail_support_extended_attachment_encodings=true",
        "--max_api_call_deadline_map=app_config_service:60.0,"
            + "blobstore:30.0,"
            + "datastore_v3:270.0,"
            + "datastore_v4:270.0,"
            + "file:60.0,"
            + "images:30.0,"
            + "logservice:60.0,"
            + "modules:60.0,"
            + "rdbms:60.0,"
            + "remote_socket:60.0,"
            + "search:60.0,"
            + "stubby:60.0,"
            + "taskqueue:30.0,"
            + "urlfetch:60.0",
        "--max_offline_api_call_deadline=10",
        "--max_offline_api_call_deadline_map=app_config_service:60.0,"
            + "blobstore:30.0,"
            + "datastore_v3:270.0,"
            + "datastore_v4:270.0,"
            + "file:60.0,"
            + "images:30.0,"
            + "logservice:60.0,"
            + "modules:60.0,rdbms:600.0,"
            + "remote_socket:60.0,"
            + "search:60.0,"
            + "stubby:600.0,"
            + "taskqueue:30.0,"
            + "urlfetch:600.0",
        "--offline_api_call_deadline=5",
        "--offline_api_call_deadline_map=app_config_service:60.0,"
            + "blobstore:15.0,"
            + "datastore_v3:60.0,"
            + "datastore_v4:60.0,"
            + "file:30.0,"
            + "images:30.0,"
            + "logservice:60.0,"
            + "modules:60.0,"
            + "rdbms:60.0,"
            + "remote_socket:60.0,"
            + "search:10.0,"
            + "stubby:10.0",
        // port is only used for the Java grpc server plugin in the Java8 gen 1, but needed.
        "--port=0",
        "--runtime_http_compression=true",
        "--trusted_host=" + trustedPort,
        "--use_clone_controller_for_deadlines=true",
        "--use_env_vars_from_app_info=true",
        "--use_jetty_http_proxy=true",
        "--wait_for_daemon_request_threads=false",
        "--log_json_to_var_log=true");
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
