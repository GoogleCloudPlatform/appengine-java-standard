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

import static com.google.common.io.BaseEncoding.base64;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Random;

/**
 * Command line parameters for Java runtime, and its dependencies.
 */
@Parameters(separators = "=")
final class JavaRuntimeParams {


  private Class<? extends ServletEngineAdapter> servletEngineClass;

  @Parameter(
    description = "Root path for application data on the local filesystem.",
    names = {"--application_root"}
  )
  private String applicationRoot = "appdata";

  @Parameter(
    description = "Port number to expose our EvaluationRuntime service on.",
    names = {"--port"}
  )
  private int port = 0;

  @Parameter(
    description = "Specification used for connecting back to the appserver.",
    names = {"--trusted_host"}
  )
  private String trustedHost = "";

  @Parameter(
    description =
        "Number of milliseconds before the deadline for a request "
            + "to throw an uncatchable exception.",
    names = {"--java_hard_deadline_ms"}
  )
  private int javaHardDeadlineMs = 200;

  @Parameter(
    description =
        "Number of milliseconds before the deadline for a request "
            + "to throw a catchable exception.",
    names = {"--java_soft_deadline_ms"}
  )
  private int javaSoftDeadlineMs = 600;

  @Parameter(
    description = "Default deadline for all API RPCs, in seconds.",
    names = {"--api_call_deadline"}
  )
  private double apiCallDeadline = 5.0;

  @Parameter(
    description = "Maximum deadline for all API RPCs, in seconds.",
    names = {"--max_api_call_deadline"}
  )
  private double maxApiCallDeadline = 10.0;

  @Parameter(
    description = "Default deadline for all API RPCs by package in seconds.",
    names = {"--api_call_deadline_map"}
  )
  private String apiCallDeadlineMap = "";

  @Parameter(
    description = "Maximum deadline for all API RPCs by package in seconds.",
    names = {"--max_api_call_deadline_map"}
  )
  private String maxApiCallDeadlineMap = "";

  @Parameter(
    description = "Default deadline for all offline API RPCs, in seconds.",
    names = {"--offline_api_call_deadline"}
  )
  private double offlineApiCallDeadline = 5.0;

  @Parameter(
    description = "Maximum deadline for all offline API RPCs, in seconds.",
    names = {"--max_offline_api_call_deadline"}
  )
  private double maxOfflineApiCallDeadline = 10.0;

  @Parameter(
    description = "Default deadline for all offline API RPCs by package in seconds.",
    names = {"--offline_api_call_deadline_map"}
  )
  private String offlineApiCallDeadlineMap = "";

  @Parameter(
    description = "Maximum deadline for all offline API RPCs by package in seconds.",
    names = {"--max_offline_api_call_deadline_map"}
  )
  private String maxOfflineApiCallDeadlineMap = "";

  @Parameter(
    description = "A base-64 encoded string of entropy for the CSPRNG.",
    names = {"--entropy_string"}
  )
  private String entropyString = pseudoRandomBytes();

  @Parameter(
    description = "The name for the current release of Google App Engine.",
    names = {"--appengine_release_name"}
  )
  private String appengineReleaseName = "unknown";

  @Parameter(
    description = "If true, exceptions logged by Jetty also go to app logs.",
    names = {"--log_jetty_exceptions_to_app_logs"},
    arity = 1
  )
  private boolean logJettyExceptionsToAppLogs = true;

  @Parameter(
    description = "Identifier for this datacenter.",
    names = {"--external_datacenter_name"}
  )
  private String externalDatacenterName = null;

  @Parameter(
    description = "The maximum number of simultaneous APIHost RPCs.",
    names = {"--clone_max_outstanding_api_rpcs"}
  )
  private int cloneMaxOutstandingApiRpcs = 100;

  @Parameter(
    description = "Always terminate the clone when Thread.stop() is used.",
    names = {"--thread_stop_terminates_clone"},
    arity = 1
  )
  private boolean threadStopTerminatesClone = true;

  // TODO: this flag is no longer used and should be deleted
  @Parameter(
      description = "Deprecated.",
      names = {"--default_max_api_request_size"})
  private int maxApiRequestSize = 1048576;

  @Parameter(
      description = "Flush application logs when they grow to this size.",
      names = {"--byte_count_before_flushing"})
  private long byteCountBeforeFlushing = 100 * 1024L;

  @Parameter(
      description = "Maximum application log line size.",
      names = {"--max_log_line_size"})
  private int maxLogLineSize = 16 * 1024;

  @Parameter(
    description =
        "Maximum number of seconds a log record should be allowed to "
            + "to be cached in the runtime before being flushed to the "
            + "appserver (only applies to non-frontend requests).",
    names = {"--max_log_flush_seconds"}
  )
  private int maxLogFlushSeconds = 60;

  @Parameter(
    description =
        "Should we use CloneController.sendDeadline for request "
            + "deadlines instead of using timers.",
    names = {"--use_clone_controller_for_deadlines"},
    arity = 1
  )
  private boolean useCloneControllerForDeadlines = false;

  @Parameter(
    description = "Compress HTTP responses in the runtime.",
    names = {"--runtime_http_compression"},
    arity = 1
  )
  private boolean runtimeHttpCompression = false;

  @Parameter(
    description =
        "The maximum allowed size in bytes of the Runtime Log "
            + "per request, returned in the UPResponse.",
    names = {"--max_runtime_log_per_request"}
  )
  private long maxRuntimeLogPerRequest = 3000L * 1024L;

  @Parameter(
    description =
        "Whether to use the JDBC connectivity for accessing Cloud SQL "
            + "through the AppEngine Java applications.",
    names = {"--enable_gae_cloud_sql_jdbc_connectivity"},
    arity = 1
  )
  private boolean enableGaeCloudSqlJdbcConnectivity = false;

  @Parameter(
      description =
          "Whether to use google connector-j by default even if it's not explicitly set in"
              + " appengine-web.xml.",
      names = {"--default_use_google_connectorj"},
      arity = 1
  )
  private boolean defaultUseGoogleConnectorj = false;

  @Parameter(
    description =
        "On a soft deadline, attempt to interrupt application threads first, then "
            + "stop them only if necessary",
    names = {"--interrupt_threads_first_on_soft_deadline"},
    arity = 1
  )
  private boolean interruptThreadsFirstOnSoftDeadline = false;

  @Parameter(
    description = "Whether to enable exporting of hotspot performance metrics.",
    names = {"--enable_hotspot_performance_metrics"},
    arity = 1
  )
  private boolean enableHotspotPerformanceMetrics = false;

  @Parameter(
    description = "Enables Java Cloud Profiler CPU usage agent in the process.",
    names = {"--enable_cloud_cpu_profiler"},
    arity = 1
  )
  private boolean enableCloudCpuProfiler = false;

  @Parameter(
    description = "Enables Java Cloud Profiler heap usage agent in the process.",
    names = {"--enable_cloud_heap_profiler"},
    arity = 1
  )
  private boolean enableCloudHeapProfiler = false;

  @Parameter(
    description = "Allows URLFetch to generate response messages based on HTTP return codes.",
    names = {"--urlfetch_derive_response_message"},
    arity = 1
  )
  private boolean urlfetchDeriveResponseMessage = true;

  @Parameter(
    description = "Prevent the Mail API from inlining attachments with filenames.",
    names = {"--mail_filename_prevents_inlining"},
    arity = 1
  )
  private boolean mailFilenamePreventsInlining = false;

  @Parameter(
    description = "Support byte[] and nested Multipart-encoded Mail attachments",
    names = {"--mail_support_extended_attachment_encodings"},
    arity = 1
  )
  private boolean mailSupportExtendedAttachmentEncodings = false;

  @Parameter(
    description = "Always enable readahead on a CloudSQL socket",
    names = {"--force_readahead_on_cloudsql_socket"},
    arity = 1
  )
  private boolean forceReadaheadOnCloudsqlSocket = false;

  @Parameter(
    description = "Speed of the processor in clock cycles per second.",
    names = {"--cycles_per_second"},
    arity = 1
  )
  private long cyclesPerSecond = 0L;

  @Parameter(
    description =
        "Wait for request threads with the daemon bit set before considering a request complete.",
    names = {"--wait_for_daemon_request_threads"},
    arity = 1
  )
  private boolean waitForDaemonRequestThreads = true;

  @Parameter(
    description =
         "Poll for network connectivity before running application code.",
    names = {"--poll_for_network"},
    arity = 1
  )
  private boolean pollForNetwork = false;

  @Parameter(
    description = "Default url-stream-handler to 'native' instead of 'urlfetch'.",
    names = {"--default_to_native_url_stream_handler", "--default_to_builtin_url_stream_handler"},
    arity = 1
  )
  private boolean defaultToNativeUrlStreamHandler = false;

  @Parameter(
    description = "Force url-stream-handler to 'urlfetch' irrespective of the contents "
        + "of the appengine-web.xml descriptor.",
    names = {"--force_urlfetch_url_stream_handler"},
    arity = 1
  )
  private boolean forceUrlfetchUrlStreamHandler = false;

  @Parameter(
    description = "Enable synchronization inside of AppLogsWriter.",
    names = {"--enable_synchronized_app_logs_writer"},
    arity = 1
  )
  private boolean enableSynchronizedAppLogsWriter = true;

  @Parameter(
    description = "Use environment variables from the AppInfo instead of those "
        + "in the appengine-web.xml descriptor.",
    names = {"--use_env_vars_from_app_info"},
    arity = 1
  )
  private boolean useEnvVarsFromAppInfo = false;

  @Parameter(
    description = "Fixed path to use for the application root directory, irrespective of "
        + "the application id and version. Ignored if empty.",
    names = {"--fixed_application_path"}
  )
  private String fixedApplicationPath = null;

  @Parameter(
      description =
          "Enable a Jetty server listening to HTTP requests and forwarding via RPC to "
              + "the java runtime.",
      names = {"--use_jetty_http_proxy"},
      arity = 1)
  private boolean useJettyHttpProxy = false;

  @Parameter(
    description = "Jetty HTTP Port number to use for http access to the runtime.",
    names = {"--jetty_http_port"}
  )
  private int jettyHttpPort = 8080;

  @Parameter(
    description = "Jetty server's max size for HTTP request headers.",
    names = {"--jetty_request_header_size"}
  )
  private int jettyRequestHeaderSize = 16384;

  @Parameter(
      description = "Jetty server's max size for HTTP response headers.",
      names = {"--jetty_response_header_size"}
  )
  private int jettyResponseHeaderSize = 16384;

  @Parameter(
    description = "Disable API call logging in the runtime.",
    names = {"--disable_api_call_logging"},
    arity = 1)
  private boolean disableApiCallLogging = false;

  @Parameter(
      description = "Configure java.util.logging to log JSON messages to /var/log/app.",
      names = {"--log_json_to_var_log"},
      arity = 1)
  private boolean logJsonToVarLog = false;

  @Parameter(
    description = "Enable using riptide for user code.",
    names = {"--java8_riptide"},
    arity = 1)
  private boolean java8Riptide = false;

  private List<String> unknownParams;

  private static String pseudoRandomBytes() {
    byte[] bytes = new byte[32];
    new Random().nextBytes(bytes);
    return base64().encode(bytes);
  }

  private JavaRuntimeParams() {}

  /**
   * Creates {@code JavaRuntimeParams} from command line arguments.
   *
   * @param args command line arguments
   * @return an instance of {@code JavaRuntimeParams} with parsed values from command line
   */
  public static JavaRuntimeParams parseArgs(String... args) {
    ImmutableList<String> argsList = ImmutableList.copyOf(args);
    argsList = ParameterFactory.expandBooleanParams(argsList, JavaRuntimeParams.class);
    args = argsList.toArray(new String[0]);

    JavaRuntimeParams parameters = new JavaRuntimeParams();
    JCommander jCommander = new JCommander(parameters);
    jCommander.setProgramName("JavaRuntimeParams");
    jCommander.addConverterFactory(new ParameterFactory());
    // Allow duplicate parameters.
    jCommander.setAllowParameterOverwriting(true);
    // Accept unknown options, because we will pass these further.
    jCommander.setAcceptUnknownOptions(true);
    try {
      jCommander.parse(args);
      parameters.initServletEngineClass();
    } catch (ParameterException exception) {
      jCommander.usage();
      throw exception;
    }
    parameters.unknownParams = jCommander.getUnknownOptions();
    return parameters;
  }

  boolean getLogJettyExceptionsToAppLogs() {
    return logJettyExceptionsToAppLogs;
  }

  boolean getEnableCloudCpuProfiler() {
    return enableCloudCpuProfiler;
  }

  boolean getEnableCloudHeapProfiler() {
    return enableCloudHeapProfiler;
  }

  Class<? extends ServletEngineAdapter> getServletEngine() {
    return servletEngineClass;
  }

  private void initServletEngineClass() {
    String servletEngine = "com.google.apphosting.runtime.jetty94.JettyServletEngineAdapter";
    try {
      servletEngineClass = Class.forName(servletEngine).asSubclass(ServletEngineAdapter.class);
    } catch (ClassNotFoundException nfe) {
      throw new ParameterException(
          "No class name with the given name " + servletEngine + " could be found");
    } catch (ClassCastException cce) {
      throw new ParameterException("Not a subtype of " + ServletEngineAdapter.class.getName());
    }
  }

  String getApplicationRoot() {
    return applicationRoot;
  }

  int getPort() {
    return port;
  }

  String getTrustedHost() {
    return trustedHost;
  }

  int getJavaHardDeadlineMs() {
    return javaHardDeadlineMs;
  }

  int getJavaSoftDeadlineMs() {
    return javaSoftDeadlineMs;
  }

  double getApiCallDeadline() {
    return apiCallDeadline;
  }

  double getMaxApiCallDeadline() {
    return maxApiCallDeadline;
  }

  String getApiCallDeadlineMap() {
    return apiCallDeadlineMap;
  }

  String getMaxApiCallDeadlineMap() {
    return maxApiCallDeadlineMap;
  }

  double getOfflineApiCallDeadline() {
    return offlineApiCallDeadline;
  }

  double getMaxOfflineApiCallDeadline() {
    return maxOfflineApiCallDeadline;
  }

  String getOfflineApiCallDeadlineMap() {
    return offlineApiCallDeadlineMap;
  }

  String getMaxOfflineApiCallDeadlineMap() {
    return maxOfflineApiCallDeadlineMap;
  }

  String getEntropyString() {
    return entropyString;
  }

  String getAppengineReleaseName() {
    return appengineReleaseName;
  }

  String getExternalDatacenterName() {
    return externalDatacenterName;
  }

  int getCloneMaxOutstandingApiRpcs() {
    return cloneMaxOutstandingApiRpcs;
  }

  boolean getThreadStopTerminatesClone() {
    return threadStopTerminatesClone;
  }

  int getMaxApiRequestSize() {
    return maxApiRequestSize;
  }

  long getByteCountBeforeFlushing() {
    return byteCountBeforeFlushing;
  }

  int getMaxLogLineSize() {
    return maxLogLineSize;
  }

  int getMaxLogFlushSeconds() {
    return maxLogFlushSeconds;
  }

  boolean getUseCloneControllerForDeadlines() {
    return useCloneControllerForDeadlines;
  }

  boolean getRuntimeHttpCompression() {
    return runtimeHttpCompression;
  }

  long getMaxRuntimeLogPerRequest() {
    return maxRuntimeLogPerRequest;
  }

  boolean getEnableGaeCloudSqlJdbcConnectivity() {
    return enableGaeCloudSqlJdbcConnectivity;
  }

  boolean getDefaultUseGoogleConnectorj() {
    return defaultUseGoogleConnectorj;
  }

  boolean getInterruptThreadsFirstOnSoftDeadline() {
    return interruptThreadsFirstOnSoftDeadline;
  }

  boolean getEnableHotspotPerformanceMetrics() {
    return enableHotspotPerformanceMetrics;
  }

  boolean getUrlfetchDeriveResponseMessage() {
    return urlfetchDeriveResponseMessage;
  }

  boolean getMailFilenamePreventsInlining() {
    return mailFilenamePreventsInlining;
  }

  boolean getMailSupportExtendedAttachmentEncodings() {
    return mailSupportExtendedAttachmentEncodings;
  }

  boolean getForceReadaheadOnCloudsqlSocket() {
    return forceReadaheadOnCloudsqlSocket;
  }

  long getCyclesPerSecond() {
    return cyclesPerSecond;
  }

  boolean getWaitForDaemonRequestThreads() {
    return waitForDaemonRequestThreads;
  }

  boolean getPollForNetwork() {
    return pollForNetwork;
  }

  boolean getDefaultToNativeUrlStreamHandler() {
    return defaultToNativeUrlStreamHandler;
  }

  boolean getForceUrlfetchUrlStreamHandler() {
      return forceUrlfetchUrlStreamHandler;
  }

  boolean getEnableSynchronizedAppLogsWriter() {
    return enableSynchronizedAppLogsWriter;
  }

  boolean getUseEnvVarsFromAppInfo() {
    return useEnvVarsFromAppInfo;
  }

  boolean getUseJettyHttpProxy() {
    return useJettyHttpProxy;
  }

  int getJettyHttpPort() {
    return jettyHttpPort;
  }

  int getJettyRequestHeaderSize() {
    return jettyRequestHeaderSize;
  }

  int getJettyResponseHeaderSize() {
    return jettyResponseHeaderSize;
  }

  String getFixedApplicationPath() {
    return fixedApplicationPath;
  }

  boolean getDisableApiCallLogging() {
    return Boolean.getBoolean("disable_api_call_logging_in_apiproxy") || disableApiCallLogging;
  }

  boolean getLogJsonToVarLog() {
    return logJsonToVarLog;
  }

  boolean getJava8Riptide() {
    return java8Riptide;
  }

  List<String> getUnknownParams() {
    return unknownParams;
  }
}
