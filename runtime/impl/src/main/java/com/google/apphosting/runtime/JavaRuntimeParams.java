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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Command line parameters for Java runtime, and its dependencies. */
@Parameters(separators = "=")
final class JavaRuntimeParams {

  private Class<? extends ServletEngineAdapter> servletEngineClass;

  @Parameter(
      description = "Specification used for connecting back to the appserver.",
      names = {"--trusted_host"})
  private String trustedHost = "";



  @Parameter(
      description = "If true, exceptions logged by Jetty also go to app logs.",
      names = {"--log_jetty_exceptions_to_app_logs"},
      arity = 1)
  private boolean logJettyExceptionsToAppLogs = true;

  @Parameter(
      description = "The maximum number of simultaneous APIHost RPCs.",
      names = {"--clone_max_outstanding_api_rpcs"})
  private int cloneMaxOutstandingApiRpcs = 100;

  @Parameter(
      description = "Always terminate the clone when Thread.stop() is used.",
      names = {"--thread_stop_terminates_clone"},
      arity = 1)
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
      names = {"--max_log_flush_seconds"})
  private int maxLogFlushSeconds = 60;

  @Parameter(
      description =
          "The maximum allowed size in bytes of the Runtime Log "
              + "per request, returned in the UPResponse.",
      names = {"--max_runtime_log_per_request"})
  private long maxRuntimeLogPerRequest = 3000L * 1024L;

  @Parameter(
      description = "Whether to enable exporting of hotspot performance metrics.",
      names = {"--enable_hotspot_performance_metrics"},
      arity = 1)
  private boolean enableHotspotPerformanceMetrics = false;

  @Parameter(
      description = "Enables Java Cloud Profiler CPU usage agent in the process.",
      names = {"--enable_cloud_cpu_profiler"},
      arity = 1)
  private boolean enableCloudCpuProfiler = false;

  @Parameter(
      description = "Enables Java Cloud Profiler heap usage agent in the process.",
      names = {"--enable_cloud_heap_profiler"},
      arity = 1)
  private boolean enableCloudHeapProfiler = false;

  @Parameter(
      description = "Allows URLFetch to generate response messages based on HTTP return codes.",
      names = {"--urlfetch_derive_response_message"},
      arity = 1)
  private boolean urlfetchDeriveResponseMessage = true;



  @Parameter(
      description = "Poll for network connectivity before running application code.",
      names = {"--poll_for_network"},
      arity = 1)
  private boolean pollForNetwork = false;

  @Parameter(
      description =
          "Force url-stream-handler to 'urlfetch' irrespective of the contents "
              + "of the appengine-web.xml descriptor.",
      names = {"--force_urlfetch_url_stream_handler"},
      arity = 1)
  private boolean forceUrlfetchUrlStreamHandler = false;

  @Parameter(
      description =
          "Fixed path to use for the application root directory, irrespective of "
              + "the application id and version. Ignored if empty.",
      names = {"--fixed_application_path"})
  private String fixedApplicationPath = null;

  @Parameter(
      description = "Jetty HTTP Port number to use for http access to the runtime.",
      names = {"--jetty_http_port"})
  private int jettyHttpPort = 8080;



  @Parameter(
      description = "Disable API call logging in the runtime.",
      names = {"--disable_api_call_logging"},
      arity = 1)
  private boolean disableApiCallLogging = false;

  private List<String> unknownParams;

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
    String servletEngine;

    if (Boolean.getBoolean("appengine.use.EE8")
        || Boolean.getBoolean("appengine.use.EE10")
        || Boolean.getBoolean("appengine.use.EE11")) {
      servletEngine = "com.google.apphosting.runtime.jetty.JettyServletEngineAdapter";
    } else {
      servletEngine = "com.google.apphosting.runtime.jetty9.JettyServletEngineAdapter";
    }
    try {
      servletEngineClass = Class.forName(servletEngine).asSubclass(ServletEngineAdapter.class);
    } catch (ClassNotFoundException nfe) {
      throw new ParameterException(
          "No class name with the given name " + servletEngine + " could be found", nfe);
    } catch (ClassCastException cce) {
      throw new ParameterException("Not a subtype of " + ServletEngineAdapter.class.getName(), cce);
    }
  }

  String getTrustedHost() {
    return trustedHost;
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

  long getMaxRuntimeLogPerRequest() {
    return maxRuntimeLogPerRequest;
  }

  boolean getEnableHotspotPerformanceMetrics() {
    return enableHotspotPerformanceMetrics;
  }

  boolean getUrlfetchDeriveResponseMessage() {
    return urlfetchDeriveResponseMessage;
  }



  boolean getPollForNetwork() {
    return pollForNetwork;
  }

  boolean getForceUrlfetchUrlStreamHandler() {
    return forceUrlfetchUrlStreamHandler;
  }

  int getJettyHttpPort() {
    return jettyHttpPort;
  }



  String getFixedApplicationPath() {
    return fixedApplicationPath;
  }

  boolean getDisableApiCallLogging() {
    return Boolean.getBoolean("disable_api_call_logging_in_apiproxy") || disableApiCallLogging;
  }

  List<String> getUnknownParams() {
    return unknownParams;
  }
}
