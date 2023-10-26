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

package com.google.appengine.tools.admin;

import com.esotericsoftware.yamlbeans.YamlConfig;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlWriter;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXml.AdminConsolePage;
import com.google.apphosting.utils.config.AppEngineWebXml.ApiConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.CpuUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.CustomMetricUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.ErrorHandler;
import com.google.apphosting.utils.config.AppEngineWebXml.HealthCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.LivenessCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.Network;
import com.google.apphosting.utils.config.AppEngineWebXml.ReadinessCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.Resources;
import com.google.apphosting.utils.config.AppEngineWebXml.VpcAccessConnector;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXml.SecurityConstraint;
import com.google.apphosting.utils.glob.ConflictResolver;
import com.google.apphosting.utils.glob.Glob;
import com.google.apphosting.utils.glob.GlobFactory;
import com.google.apphosting.utils.glob.GlobIntersector;
import com.google.apphosting.utils.glob.LongestPatternConflictResolver;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates {@code app.yaml} files suitable for uploading as part of
 * a Google App Engine application.
 *
 */
public class AppYamlTranslator {
  private static final String NO_API_VERSION = "none";

  private static final ConflictResolver RESOLVER =
      new LongestPatternConflictResolver();

  private static final String DYNAMIC_PROPERTY = "dynamic";
  private static final String STATIC_PROPERTY = "static";
  private static final String WELCOME_FILES = "welcome";
  private static final String TRANSPORT_GUARANTEE_PROPERTY = "transportGuarantee";
  private static final String REQUIRED_ROLE_PROPERTY = "requiredRole";
  private static final String EXPIRATION_PROPERTY = "expiration";
  private static final String HTTP_HEADERS_PROPERTY = "http_headers";
  private static final String API_ENDPOINT_REGEX = "/_ah/spi/*";

  private static final String[] PROPERTIES = new String[] {
    DYNAMIC_PROPERTY,
    STATIC_PROPERTY,
    WELCOME_FILES,
    TRANSPORT_GUARANTEE_PROPERTY,
    REQUIRED_ROLE_PROPERTY,
    EXPIRATION_PROPERTY,
  };

  // This should be kept in sync with MAX_URL_MAPS in //apphosting/api/appinfo.py.
  private static final int MAX_HANDLERS = 100;

  private final AppEngineWebXml appEngineWebXml;
  private final WebXml webXml;
  private final BackendsXml backendsXml;
  private final String apiVersion;
  private final Set<String> staticFiles;
  private final String runtime;

  public AppYamlTranslator(
      AppEngineWebXml appEngineWebXml,
      WebXml webXml,
      BackendsXml backendsXml,
      String apiVersion,
      Set<String> staticFiles,
      ApiConfig apiConfig,
      String runtime) {
    this.appEngineWebXml = appEngineWebXml;
    this.webXml = webXml;
    this.backendsXml = backendsXml;
    this.apiVersion = apiVersion;
    this.staticFiles = staticFiles;
    // apiConfig not used.
    if (runtime != null) {
      this.runtime = runtime;
    } else {
      this.runtime = appEngineWebXml.getRuntime();
    }
    if (appEngineWebXml.getUseVm() && appEngineWebXml.isFlexible()) {
      throw new AppEngineConfigException("Cannot define both <vm> and <env> entries.");
    }
  }

  public String getYaml() {
    StringBuilder builder = new StringBuilder();
    translateAppEngineWebXml(builder);
    // No need to process the api_version field for Java11 or above.
    if (!appEngineWebXml.isJava11OrAbove()) {
      translateApiVersion(builder);
    }
    translateWebXml(builder);
    return builder.toString();
  }

  private void appendIfNotNull(StringBuilder builder, String tag, Object value) {
    if (value != null) {
      builder.append(tag);
      builder.append(value);
      builder.append("\n");
    }
  }

  private void appendIfNotZero(StringBuilder builder, String tag, double value) {
    if (value != 0) {
      builder.append(tag);
      builder.append(value);
      builder.append("\n");
    }
  }

  private void translateAppEngineWebXml(StringBuilder builder) {
    if (appEngineWebXml.getAppId() != null) {
      builder.append("application: '" + appEngineWebXml.getAppId() + "'\n");
    }
    builder.append("runtime: " + runtime + "\n");
    if (appEngineWebXml.getUseVm()) {
      builder.append("vm: True\n");
    }

    if (appEngineWebXml.isFlexible()) {
      builder.append("env: " + appEngineWebXml.getEnv() + "\n");
    }

    if (appEngineWebXml.getEntrypoint() != null) {
      builder.append("entrypoint: '" + appEngineWebXml.getEntrypoint() + "'\n");
    }

    if (appEngineWebXml.getRuntimeChannel() != null) {
      builder.append("runtime_channel: " + appEngineWebXml.getRuntimeChannel() + "\n");
    }
    if (appEngineWebXml.getMajorVersionId() != null) {
      builder.append("version: '" + appEngineWebXml.getMajorVersionId() + "'\n");
    }

    if (appEngineWebXml.getService() != null) {
      builder.append("service: '" + appEngineWebXml.getService() + "'\n");
    } else if (appEngineWebXml.getModule() != null) {
      builder.append("module: '" + appEngineWebXml.getModule() + "'\n");
    }

    if (appEngineWebXml.getInstanceClass() != null) {
      builder.append("instance_class: " + appEngineWebXml.getInstanceClass() + "\n");
    }

    if (!appEngineWebXml.getAutomaticScaling().isEmpty()) {
      builder.append("automatic_scaling:\n");
      AppEngineWebXml.AutomaticScaling settings = appEngineWebXml.getAutomaticScaling();
      appendIfNotNull(builder, "  min_pending_latency: ", settings.getMinPendingLatency());
      appendIfNotNull(builder, "  max_pending_latency: ", settings.getMaxPendingLatency());
      appendIfNotNull(builder, "  min_idle_instances: ", settings.getMinIdleInstances());
      appendIfNotNull(builder, "  max_idle_instances: ", settings.getMaxIdleInstances());
      appendIfNotNull(builder, "  max_concurrent_requests: ", settings.getMaxConcurrentRequests());
      appendIfNotNull(builder, "  min_num_instances: ", settings.getMinNumInstances());
      appendIfNotNull(builder, "  max_num_instances: ", settings.getMaxNumInstances());
      appendIfNotNull(builder, "  cool_down_period_sec: ", settings.getCoolDownPeriodSec());
      // GAE Standard new clone scheduler:
      // I know, having min_num_instances and min_instances is confusing. b/70626925.
      appendIfNotNull(builder, "  min_instances: ", settings.getMinInstances());
      appendIfNotNull(builder, "  max_instances: ", settings.getMaxInstances());
      appendIfNotNull(builder, "  target_cpu_utilization: ", settings.getTargetCpuUtilization());
      appendIfNotNull(
          builder, "  target_throughput_utilization: ", settings.getTargetThroughputUtilization());

      CpuUtilization cpuUtil = settings.getCpuUtilization();
      if (cpuUtil != null
          && (cpuUtil.getTargetUtilization() != null
              || cpuUtil.getAggregationWindowLengthSec() != null)) {
        builder.append("  cpu_utilization:\n");
        appendIfNotNull(builder, "    target_utilization: ", cpuUtil.getTargetUtilization());
        appendIfNotNull(
            builder, "    aggregation_window_length_sec: ",
            cpuUtil.getAggregationWindowLengthSec());
      }

      appendIfNotNull(
          builder, "  target_network_sent_bytes_per_sec: ",
          settings.getTargetNetworkSentBytesPerSec());
      appendIfNotNull(
          builder, "  target_network_sent_packets_per_sec: ",
          settings.getTargetNetworkSentPacketsPerSec());
      appendIfNotNull(
          builder, "  target_network_received_bytes_per_sec: ",
          settings.getTargetNetworkReceivedBytesPerSec());
      appendIfNotNull(
          builder, "  target_network_received_packets_per_sec: ",
          settings.getTargetNetworkReceivedPacketsPerSec());
      appendIfNotNull(
          builder, "  target_disk_write_bytes_per_sec: ",
          settings.getTargetDiskWriteBytesPerSec());
      appendIfNotNull(
          builder, "  target_disk_write_ops_per_sec: ",
          settings.getTargetDiskWriteOpsPerSec());
      appendIfNotNull(
          builder, "  target_disk_read_bytes_per_sec: ",
          settings.getTargetDiskReadBytesPerSec());
      appendIfNotNull(
          builder, "  target_disk_read_ops_per_sec: ",
          settings.getTargetDiskReadOpsPerSec());
      appendIfNotNull(
          builder, "  target_request_count_per_sec: ",
          settings.getTargetRequestCountPerSec());
      appendIfNotNull(
          builder, "  target_concurrent_requests: ",
          settings.getTargetConcurrentRequests());

      if (!settings.getCustomMetrics().isEmpty()) {
        if (!appEngineWebXml.isFlexible()) {
          throw new AppEngineConfigException("custom-metrics is only available in the AppEngine "
                                             + "Flexible environment.");
        }
        builder.append("  custom_metrics:\n");
        for (CustomMetricUtilization metric : settings.getCustomMetrics()) {
          builder.append("    - metric_name: '" + metric.getMetricName() + "'\n");
          builder.append("      target_type: '" + metric.getTargetType() + "'\n");
          appendIfNotNull(builder, "      target_utilization: ", metric.getTargetUtilization());
          appendIfNotNull(builder,
              "      single_instance_assignment: ",
              metric.getSingleInstanceAssignment());
          if (metric.getFilter() != null) {
            builder.append("      filter: '" + metric.getFilter() + "'\n");
          }
        }
      }
    }

    if (!appEngineWebXml.getManualScaling().isEmpty()) {
      builder.append("manual_scaling:\n");
      AppEngineWebXml.ManualScaling settings = appEngineWebXml.getManualScaling();
      builder.append("  instances: " + settings.getInstances() + "\n");
    }

    if (!appEngineWebXml.getBasicScaling().isEmpty()) {
      builder.append("basic_scaling:\n");
      AppEngineWebXml.BasicScaling settings = appEngineWebXml.getBasicScaling();
      builder.append("  max_instances: " + settings.getMaxInstances() + "\n");
      appendIfNotNull(builder, "  idle_timeout: ", settings.getIdleTimeout());
    }

    Collection<String> services = appEngineWebXml.getInboundServices();
    if (!services.isEmpty()) {
      builder.append("inbound_services:\n");
      for (String service : services) {
        builder.append("- " + service + "\n");
      }
    }

    // Precompilation is only for the Standard environment.
    if (appEngineWebXml.getPrecompilationEnabled()
        && !appEngineWebXml.getUseVm()
        && !appEngineWebXml.isFlexible()
        && !appEngineWebXml.isJava11OrAbove()) {
      builder.append("derived_file_type:\n");
      builder.append("- java_precompiled\n");

    }

    if (appEngineWebXml.getThreadsafe() && !appEngineWebXml.isJava11OrAbove()) {
      builder.append("threadsafe: True\n");
    }

    if (appEngineWebXml.getAppEngineApis() && appEngineWebXml.isJava11OrAbove()) {
      builder.append("app_engine_apis: True\n");
    }

    if (appEngineWebXml.getThreadsafeValueProvided() && appEngineWebXml.isJava11OrAbove()) {
      System.out.println(
          "Warning: The "
              + appEngineWebXml.getRuntime()
              + " runtime does not use the <threadsafe> element"
              + " in appengine-web.xml anymore");
      System.out.println(
          "Instead, you can use the <max-concurrent-requests> element in <automatic-scaling>.");
    }

    if (appEngineWebXml.getAutoIdPolicy() != null) {
      builder.append("auto_id_policy: " + appEngineWebXml.getAutoIdPolicy() + "\n");
    } else {
      // NOTE: The YAML parsing and validation done in the admin console must
      // set the value for unspecified auto_id_policy to 'legacy' in order to achieve
      // the desired behavior for previous SDK versions. But the desired value for
      // unspecified auto_id_policy in current and future SDK versions is 'default'.
      // Therefore in new SDK versions we intercept unspecified auto_id_policy here.
      builder.append("auto_id_policy: default\n");
    }

    if (appEngineWebXml.getCodeLock()) {
      builder.append("code_lock: True\n");
    }

    if (appEngineWebXml.getVpcAccessConnector() != null) {
      VpcAccessConnector connector = appEngineWebXml.getVpcAccessConnector();
      builder.append("vpc_access_connector:\n");
      builder.append("  name: " + connector.getName() + "\n");
      if (connector.getEgressSetting().isPresent()) {
        builder.append("  egress_setting: " + connector.getEgressSetting().get() + "\n");
      }
    }

    if (appEngineWebXml.getServiceAccount() != null) {
      builder.append("service_account: " + appEngineWebXml.getServiceAccount() + "\n");
    }

    List<AdminConsolePage> adminConsolePages = appEngineWebXml.getAdminConsolePages();
    if (!adminConsolePages.isEmpty()) {
      builder.append("admin_console:\n");
      builder.append("  pages:\n");
      for (AdminConsolePage page : adminConsolePages) {
        builder.append("  - name: " + page.getName() + "\n");
        builder.append("    url: " + page.getUrl() + "\n");
      }
    }

    List<ErrorHandler> errorHandlers = appEngineWebXml.getErrorHandlers();
    if (!errorHandlers.isEmpty()) {
      builder.append("error_handlers:\n");
      for (ErrorHandler handler : errorHandlers) {
        String fileName = handler.getFile();
        if (!fileName.startsWith("/")) {
          fileName = "/" + fileName;
        }
        // TODO: Consider whether we should be adding the
        // public root to this path.
        if (!staticFiles.contains("__static__" + fileName)) {
          throw new AppEngineConfigException("No static file found for error handler: "
              + fileName + ", out of " + staticFiles);
        }
        // error_handlers doesn't want a leading slash here.
        builder.append("- file: __static__" + fileName + "\n");
        if (handler.getErrorCode() != null) {
          builder.append("  error_code: " + handler.getErrorCode() + "\n");
        }
        String mimeType = webXml.getMimeTypeForPath(handler.getFile());
        if (mimeType != null) {
          builder.append("  mime_type: " + mimeType + "\n");
        }
      }
    }

    if (backendsXml != null) {
      builder.append(backendsXml.toYaml());
    }

    // Only one api config, it is a singleton, and multiple APIs are served
    // from subpaths within the namespace.
    // TODO: Verify script: is required.
    ApiConfig apiConfig = appEngineWebXml.getApiConfig();
    if (apiConfig != null) {
      builder.append("api_config:\n");
      builder.append("  url: " + apiConfig.getUrl() + "\n");
      builder.append("  script: unused\n");
    }

    // For beta-settings, we allow anything and defer to the later app.yaml processing
    // to detect invalid values.
    appendBetaSettings(appEngineWebXml.getBetaSettings(), builder);
    appendEnvVariables(appEngineWebXml.getEnvironmentVariables(), builder);
    appendBuildEnvVariables(appEngineWebXml.getBuildEnvironmentVariables(), builder);
    if (appEngineWebXml.getUseVm() || appEngineWebXml.isFlexible()) {

      if (appEngineWebXml.getHealthCheck() != null) {
        appendHealthCheck(appEngineWebXml.getHealthCheck(), builder);
      }
      if (appEngineWebXml.getLivenessCheck() != null) {
        appendLivenessCheck(appEngineWebXml.getLivenessCheck(), builder);
      }
      if (appEngineWebXml.getReadinessCheck() != null) {
        appendReadinessCheck(appEngineWebXml.getReadinessCheck(), builder);
      }

      appendResources(appEngineWebXml.getResources(), builder);
      appendNetwork(appEngineWebXml.getNetwork(), builder);
    }
  }

  /**
   * Appends the given environment variables as YAML to the given StringBuilder.
   *
   * @param envVariables The env variables map to append as YAML.
   * @param builder The StringBuilder to append to.
   */
  private void appendEnvVariables(Map<String, String> envVariables, StringBuilder builder) {
    if (envVariables.size() > 0) {
      builder.append("env_variables:\n");
      for (Map.Entry<String, String> envVariable : envVariables.entrySet()) {
        String k = envVariable.getKey();
        String v = envVariable.getValue();
        builder.append("  ").append(yamlQuote(k)).append(": ").append(yamlQuote(v)).append("\n");
      }
    }
  }

  /**
   * Appends the given build environment variables as YAML to the given StringBuilder.
   *
   * @param buildEnvVariables The build env variables map to append as YAML.
   * @param builder The StringBuilder to append to.
   */
  private void appendBuildEnvVariables(
      Map<String, String> buildEnvVariables, StringBuilder builder) {
    if (buildEnvVariables.size() > 0) {
      builder.append("build_env_variables:\n");
      for (Map.Entry<String, String> buildEnvVariable : buildEnvVariables.entrySet()) {
        String k = buildEnvVariable.getKey();
        String v = buildEnvVariable.getValue();
        builder.append("  ").append(yamlQuote(k)).append(": ").append(yamlQuote(v)).append("\n");
      }
    }
  }

  /**
   * Appends the given Beta Settings as YAML to the given StringBuilder.
   *
   * @param betaSettings The beta settings map to append as YAML.
   * @param builder The StringBuilder to append to.
   */
  private void appendBetaSettings(Map<String, String> betaSettings, StringBuilder builder) {
    if (betaSettings != null && !betaSettings.isEmpty()) {
      builder.append("beta_settings:\n");
      for (Map.Entry<String, String> setting : betaSettings.entrySet()) {
        builder.append(
            "  " + yamlQuote(setting.getKey()) + ": " + yamlQuote(setting.getValue()) + "\n");
      }
    }
  }

  private void appendHealthCheck(HealthCheck healthCheck, StringBuilder builder) {
    builder.append("health_check:\n");
    if (healthCheck.getEnableHealthCheck()) {
      builder.append("  enable_health_check: True\n");
    } else {
      builder.append("  enable_health_check: False\n");
    }

    appendIfNotNull(builder, "  check_interval_sec: ", healthCheck.getCheckIntervalSec());
    appendIfNotNull(builder, "  timeout_sec: ", healthCheck.getTimeoutSec());
    appendIfNotNull(builder, "  unhealthy_threshold: ", healthCheck.getUnhealthyThreshold());
    appendIfNotNull(builder, "  healthy_threshold: ", healthCheck.getHealthyThreshold());
    appendIfNotNull(builder, "  restart_threshold: ", healthCheck.getRestartThreshold());
    appendIfNotNull(builder, "  host: ", healthCheck.getHost());
  }

  private void appendLivenessCheck(LivenessCheck livenessCheck, StringBuilder builder) {
    builder.append("liveness_check:\n");

    appendIfNotNull(builder, "  path: ", livenessCheck.getPath());
    appendIfNotNull(builder, "  check_interval_sec: ", livenessCheck.getCheckIntervalSec());
    appendIfNotNull(builder, "  timeout_sec: ", livenessCheck.getTimeoutSec());
    appendIfNotNull(builder, "  failure_threshold: ", livenessCheck.getFailureThreshold());
    appendIfNotNull(builder, "  success_threshold: ", livenessCheck.getSuccessThreshold());
    appendIfNotNull(builder, "  host: ", livenessCheck.getHost());
    appendIfNotNull(builder, "  initial_delay_sec: ", livenessCheck.getInitialDelaySec());
  }

  private void appendReadinessCheck(ReadinessCheck readinessCheck, StringBuilder builder) {
    builder.append("readiness_check:\n");

    appendIfNotNull(builder, "  path: ", readinessCheck.getPath());
    appendIfNotNull(builder, "  check_interval_sec: ", readinessCheck.getCheckIntervalSec());
    appendIfNotNull(builder, "  timeout_sec: ", readinessCheck.getTimeoutSec());
    appendIfNotNull(builder, "  failure_threshold: ", readinessCheck.getFailureThreshold());
    appendIfNotNull(builder, "  success_threshold: ", readinessCheck.getSuccessThreshold());
    appendIfNotNull(builder, "  host: ", readinessCheck.getHost());
    appendIfNotNull(builder, "  app_start_timeout_sec: ", readinessCheck.getAppStartTimeoutSec());
  }

  private void appendResources(Resources resources, StringBuilder builder) {
    if (!resources.isEmpty()) {
      builder.append("resources:\n");
      appendIfNotZero(builder, "  cpu: ", resources.getCpu());
      appendIfNotZero(builder, "  memory_gb: ", resources.getMemoryGb());
      appendIfNotZero(builder, "  disk_size_gb: ", resources.getDiskSizeGb());
    }
  }

  private void appendNetwork(Network network, StringBuilder builder) {
    if (!network.isEmpty()) {
      builder.append("network:\n");
      appendIfNotNull(builder, "  instance_tag: ", network.getInstanceTag());
      if (!network.getForwardedPorts().isEmpty()) {
        builder.append("  forwarded_ports:\n");
        for (String forwardedPort : network.getForwardedPorts()) {
          builder.append("  - " + forwardedPort + "\n");
        }
      }
      appendIfNotNull(builder, "  name: ", network.getName());
      appendIfNotNull(builder, "  subnetwork_name: ", network.getSubnetworkName());
      if (network.getSessionAffinity()) {
        builder.append("  session_affinity: True\n");
      } else {
        builder.append("  session_affinity: False\n");
      }
    }
  }

  /**
   * Appends the given collection to the StringBuilder as YAML, indenting each emitted line by
   * numIndentSpaces.
   */
  private static void appendObjectAsYaml(
      StringBuilder builder, Object collection, int numIndentSpaces) {
    StringBuilder prefixBuilder = new StringBuilder();
    for (int i = 0; i < numIndentSpaces; ++i) {
      prefixBuilder.append(' ');
    }
    final String indentPrefix = prefixBuilder.toString();

    StringWriter stringWriter = new StringWriter();
    YamlConfig yamlConfig = new YamlConfig();
    yamlConfig.writeConfig.setIndentSize(2);
    yamlConfig.writeConfig.setWriteRootTags(false);

    YamlWriter writer = new YamlWriter(stringWriter, yamlConfig);
    try {
      writer.write(collection);
      writer.close();
    } catch (YamlException e) {
      throw new AppEngineConfigException("Unable to generate YAML.", e);
    }

    // Add the requested number of spaces since we may be emitting children of a parent element.
    for (String line : stringWriter.toString().split("\n")) {
      builder.append(indentPrefix);
      builder.append(line);
      builder.append("\n");
    }
  }

  /**
   * Surrounds the provided string with single quotes, escaping any single
   * quotes in the string by replacing them with ''.
   */
  private String yamlQuote(String str) {
    return "'" + str.replace("'", "''") + "'";
  }

  private void translateApiVersion(StringBuilder builder) {
    if (apiVersion == null) {
      builder.append("api_version: '" + NO_API_VERSION + "'\n");
    } else {
      builder.append("api_version: '" + apiVersion + "'\n");
    }
  }

  private void translateWebXml(StringBuilder builder) {
    builder.append("handlers:\n");

    AbstractHandlerGenerator staticGenerator = null;
    if (staticFiles.isEmpty()) {
      // Don't bother emitting any static handlers if we have no static files.
      staticGenerator = new EmptyHandlerGenerator();
    } else {
      staticGenerator = new StaticHandlerGenerator(appEngineWebXml.getPublicRoot());
    }

    DynamicHandlerGenerator dynamicGenerator =
        new DynamicHandlerGenerator(webXml.getFallThroughToRuntime());
    if (staticGenerator.size() + dynamicGenerator.size() > MAX_HANDLERS) {
      // Are we going to generate too many handler entries?  If so,
      // try again with fallthrough set.  This will prevent individual
      // servlet/filter entries unless they are required for
      // authentication or SSL purposes.
      dynamicGenerator = new DynamicHandlerGenerator(true);
    }

    // Static handlers have require_matching_file first so try them first.
    staticGenerator.translate(builder);
    dynamicGenerator.translate(builder);
  }

  class StaticHandlerGenerator extends AbstractHandlerGenerator {
    private final String root;

    public StaticHandlerGenerator(String root) {
      this.root = root;
    }

    @Override
    protected Map<String, Object> getWelcomeProperties() {
      // As an optimization, only include the filenames for which we
      // know there is at least one static file on the filesystem.
      List<String> staticWelcomeFiles = new ArrayList<String>();
      for (String welcomeFile : webXml.getWelcomeFiles()) {
        for (String staticFile : staticFiles) {
          if (staticFile.endsWith("/" + welcomeFile)) {
            staticWelcomeFiles.add(welcomeFile);
            break;
          }
        }
      }
      return Collections.<String,Object>singletonMap(WELCOME_FILES, staticWelcomeFiles);
    }

    @Override
    protected void addPatterns(GlobIntersector intersector) {
      List<AppEngineWebXml.StaticFileInclude> includes = appEngineWebXml.getStaticFileIncludes();
      if (includes.isEmpty()) {
        intersector.addGlob(GlobFactory.createGlob("/*", STATIC_PROPERTY, true));
      } else {
        for (AppEngineWebXml.StaticFileInclude include : includes) {
          String pattern = include.getPattern().replaceAll("\\*\\*", "*");
          if (!pattern.startsWith("/")) {
            pattern = "/" + pattern;
          }
          Map<String, Object> props = new HashMap<String, Object>();
          props.put(STATIC_PROPERTY, true);
          if (include.getExpiration() != null) {
            props.put(EXPIRATION_PROPERTY, include.getExpiration());
          }
          // Add http_headers.
          // include.getHttpHeaders shouldn't return null, but we check anyway,
          // in case some future change makes this no longer true.
          if (include.getHttpHeaders() != null) {
            props.put(HTTP_HEADERS_PROPERTY, include.getHttpHeaders());
          }

          intersector.addGlob(GlobFactory.createGlob(pattern, props));
        }
      }
    }

    @Override
    public void translateGlob(StringBuilder builder, Glob glob) {
      String regex = glob.getRegularExpression().pattern();
      if (!root.isEmpty()) {
        if (regex.startsWith(root)){
          regex = regex.substring(root.length(), regex.length());
        }
      }
      @SuppressWarnings("unchecked")
      List<String> welcomeFiles =
          (List<String>) glob.getProperty(WELCOME_FILES, RESOLVER);
      if (welcomeFiles != null) {
        for (String welcomeFile : welcomeFiles) {
          builder.append("- url: (" + regex + ")\n");
          builder.append("  static_files: __static__" + root + "\\1" + welcomeFile + "\n");
          builder.append("  upload: __NOT_USED__\n");
          builder.append("  require_matching_file: True\n");
          translateHandlerOptions(builder, glob);
          translateAdditionalStaticOptions(builder, glob);
        }
      } else {
        Boolean isStatic = (Boolean) glob.getProperty(STATIC_PROPERTY, RESOLVER);
        if (isStatic != null && isStatic.booleanValue()) {
          builder.append("- url: (" + regex + ")\n");
          builder.append("  static_files: __static__" + root + "\\1\n");
          builder.append("  upload: __NOT_USED__\n");
          builder.append("  require_matching_file: True\n");
          translateHandlerOptions(builder, glob);
          translateAdditionalStaticOptions(builder, glob);
        }
      }
    }

    private void translateAdditionalStaticOptions(StringBuilder builder, Glob glob)
        throws AppEngineConfigException {
      String expiration = (String) glob.getProperty(EXPIRATION_PROPERTY, RESOLVER);
      if (expiration != null) {
        builder.append("  expiration: " + expiration + "\n");
      }

      @SuppressWarnings("unchecked")
      Map<String, String> httpHeaders =
          (Map<String, String>) glob.getProperty(HTTP_HEADERS_PROPERTY, RESOLVER);
      if (httpHeaders != null && !httpHeaders.isEmpty()) {
        builder.append("  http_headers:\n");
        appendObjectAsYaml(builder, httpHeaders, 4);
      }
    }
  }

  /**
   * According to the example In section 12.2.2 of Servlet Spec 3.0 , /baz/* should also match /baz,
   * so add an additional glob for that.
   */
  private static void extendMeaningOfTrailingStar(
      GlobIntersector intersector, String pattern, String property, Object value) {
    if (pattern.endsWith("/*") && pattern.length() > 2) {
      intersector.addGlob(
          GlobFactory.createGlob(pattern.substring(0, pattern.length() - 2), property, value));
    }
  }

  class DynamicHandlerGenerator extends AbstractHandlerGenerator {
    private final List<String> patterns;
    private boolean fallthrough;
    private boolean hasJsps;

    DynamicHandlerGenerator(boolean alwaysFallthrough) {
      fallthrough = alwaysFallthrough;
      patterns = new ArrayList<String>();
      for (String servletPattern : webXml.getServletPatterns()) {
        if (servletPattern.equals("/") || servletPattern.equals("/*")) {
          // The special servlet URL pattern "/" actually serves as the
          // default servlet, which means that it matches all requests
          // that aren't matched by other servlet patterns.
          fallthrough = true;
        } else if (servletPattern.equals(API_ENDPOINT_REGEX)) {
          // The special servlet URL pattern "/_ah/spi/*" serves the
          // Apiserving backend.  Admin console looks for this pattern
          // explicitly so it must not be collapsed by GlobIntersector.
          hasApiEndpoint = true;
        } else if (servletPattern.endsWith(".jsp")) {
          hasJsps = true;
        } else {
          if (servletPattern.equals("")) { // New in Servlet 3.x, we map it to /
            servletPattern = "/";
          }
          patterns.add(servletPattern);
        }
      }
    }

    @Override
    protected Map<String, Object> getWelcomeProperties() {
      if (fallthrough) {
        // Don't bother adding handlers for dynamic welcome files,
        // we're going to pass all requests through to the runtime
        // anyway.
        return null;
      } else {
        return Collections.<String,Object>singletonMap(DYNAMIC_PROPERTY, true);
      }
    }

    @Override
    protected void addPatterns(GlobIntersector intersector) {
      if (fallthrough) {
        intersector.addGlob(GlobFactory.createGlob(
            "/*",
            DYNAMIC_PROPERTY, true));
      } else {
        for (String servletPattern : patterns) {
          intersector.addGlob(GlobFactory.createGlob(
              servletPattern,
              DYNAMIC_PROPERTY, true));
          extendMeaningOfTrailingStar(intersector, servletPattern, DYNAMIC_PROPERTY, true);
        }
        if (hasJsps) {
          // Just add a single rule for any JSPs so users can define more
          // than 100.  The extra load for serving 404's for mistyped
          // jsp requests is trivial.
          intersector.addGlob(GlobFactory.createGlob(
              "*.jsp",
              DYNAMIC_PROPERTY, true));
        } else if (appEngineWebXml.getUseVm() || appEngineWebXml.isFlexible()) {
          // The VM Runtime handles jsp files on the VM.
          intersector.addGlob(GlobFactory.createGlob("*.jsp", DYNAMIC_PROPERTY, true));
        }
        intersector.addGlob(GlobFactory.createGlob(
            "/_ah/*",
            DYNAMIC_PROPERTY, true));
      }
    }

    @Override
    public void translateGlob(StringBuilder builder, Glob glob) {
      String regex = glob.getRegularExpression().pattern();

      Boolean isDynamic = (Boolean) glob.getProperty(DYNAMIC_PROPERTY, RESOLVER);
      if (isDynamic != null && isDynamic.booleanValue()) {
        builder.append("- url: " + regex + "\n");
        builder.append("  script: unused\n");
        translateHandlerOptions(builder, glob);
      }
    }
  }

  /**
   * An {@code AbstractHandlerGenerator} that returns no globs.
   */
  class EmptyHandlerGenerator extends AbstractHandlerGenerator {
    @Override
    protected void addPatterns(GlobIntersector intersector) {
    }

    @Override
    protected void translateGlob(StringBuilder builder, Glob glob) {
    }

    @Override
    protected Map<String, Object> getWelcomeProperties() {
      return Collections.emptyMap();
    }
  }

  abstract class AbstractHandlerGenerator {
    private List<Glob> globs = null;
    protected boolean hasApiEndpoint;

    public int size() {
      return getGlobPatterns().size();
    }

    public void translate(StringBuilder builder) {
      for (Glob glob : getGlobPatterns()) {
        translateGlob(builder, glob);
      }
    }

    abstract protected void addPatterns(GlobIntersector intersector);
    abstract protected void translateGlob(StringBuilder builder, Glob glob);

    /**
     * @returns a map of welcome properties to apply to the welcome
     * file entries, or {@code null} if no welcome file entries are
     * necessary.
     */
    abstract protected Map<String, Object> getWelcomeProperties();

    protected List<Glob> getGlobPatterns() {
      if (globs == null) {
        GlobIntersector intersector = new GlobIntersector();
        addPatterns(intersector);
        addSecurityConstraints(intersector);
        addWelcomeFiles(intersector);

        globs = intersector.getIntersection();
        removeNearDuplicates(globs);
        if (hasApiEndpoint) {
          // Add /_ah/spi/* handler back in explicitly after intersection and duplicate
          // removal, otherwise it will be subsumed by /_ah/* above.  Admin console
          // must see this explicitly as a signal that the app is serving an API
          // endpoint.  See http://go/swarmSignal2
          globs.add(GlobFactory.createGlob(API_ENDPOINT_REGEX, DYNAMIC_PROPERTY, true));
        }
      }
      return globs;
    }

    protected void addWelcomeFiles(GlobIntersector intersector) {
      Map<String, Object> welcomeProperties = getWelcomeProperties();
      if (welcomeProperties != null) {
        // N.B.: Unfortunately we need to do both / and /*/
        // rather than just */ here so any /* patterns interact
        // correctly.  If I think too hard about this my brain explodes
        // so I've just left it this way for now.
        intersector.addGlob(GlobFactory.createGlob("/", welcomeProperties));
        intersector.addGlob(GlobFactory.createGlob("/*/", welcomeProperties));
      }
    }

    protected void addSecurityConstraints(GlobIntersector intersector) {
      for (SecurityConstraint constraint : webXml.getSecurityConstraints()) {
        for (String pattern : constraint.getUrlPatterns()) {
          intersector.addGlob(GlobFactory.createGlob(
              pattern,
              TRANSPORT_GUARANTEE_PROPERTY,
              constraint.getTransportGuarantee()));
          extendMeaningOfTrailingStar(intersector, pattern, TRANSPORT_GUARANTEE_PROPERTY,
              constraint.getTransportGuarantee());
          intersector.addGlob(GlobFactory.createGlob(
              pattern,
              REQUIRED_ROLE_PROPERTY,
              constraint.getRequiredRole()));
          extendMeaningOfTrailingStar(
              intersector, pattern, REQUIRED_ROLE_PROPERTY, constraint.getRequiredRole());
        }
      }
    }

    protected void translateHandlerOptions(StringBuilder builder, Glob glob) {
      SecurityConstraint.RequiredRole requiredRole =
          (SecurityConstraint.RequiredRole) glob.getProperty(REQUIRED_ROLE_PROPERTY, RESOLVER);
      if (requiredRole == null) {
        requiredRole = SecurityConstraint.RequiredRole.NONE;
      }
      switch (requiredRole) {
        case NONE:
          builder.append("  login: optional\n");
          break;
        case ANY_USER:
          builder.append("  login: required\n");
          break;
        case ADMIN:
          builder.append("  login: admin\n");
          break;
      }

      SecurityConstraint.TransportGuarantee transportGuarantee =
          (SecurityConstraint.TransportGuarantee) glob.getProperty(TRANSPORT_GUARANTEE_PROPERTY,
                                                                   RESOLVER);
      if (transportGuarantee == null) {
        transportGuarantee = SecurityConstraint.TransportGuarantee.NONE;
      }
      switch (transportGuarantee) {
        case NONE:
          if (appEngineWebXml.getSslEnabled()) {
            builder.append("  secure: optional\n");
          } else {
            builder.append("  secure: never\n");
          }
          break;
        case INTEGRAL:
        case CONFIDENTIAL:
          if (!appEngineWebXml.getSslEnabled()) {
            throw new AppEngineConfigException(
                "SSL must be enabled in appengine-web.xml to use transport-guarantee");
          }
          builder.append("  secure: always\n");
          break;
      }

      String pattern = glob.getRegularExpression().pattern();
      String id = webXml.getHandlerIdForPattern(pattern);
      if (id != null) {
        if (appEngineWebXml.isApiEndpoint(id)) {
          builder.append("  api_endpoint: True\n");
        }
      }
    }

    private void removeNearDuplicates(List<Glob> globs) {
      // For each entry...
      for (int i = 0; i < globs.size(); i++) {
        Glob topGlob = globs.get(i);
        // Find the following entry that completely subsumes it...
        for (int j = i + 1; j < globs.size(); j++) {
          Glob bottomGlob = globs.get(j);
          if (bottomGlob.matchesAll(topGlob)) {
            // If the properties match, the top entry can be removed.
            if (propertiesMatch(topGlob, bottomGlob)) {
              globs.remove(i);
              i--;
            }
            // If not, the first entry is important so check the next.
            break;
          }
        }
      }
    }

    private boolean propertiesMatch(Glob glob1, Glob glob2) {
      for (String property : PROPERTIES) {
        Object value1 = glob1.getProperty(property, RESOLVER);
        Object value2 = glob2.getProperty(property, RESOLVER);
        if (value1 != value2 && (value1 == null || !value1.equals(value2))) {
          return false;
        }
      }
      return true;
    }
  }
}
