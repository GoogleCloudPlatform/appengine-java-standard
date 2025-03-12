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

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXml.AdminConsolePage;
import com.google.apphosting.utils.config.AppEngineWebXml.ApiConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.AutomaticScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.CpuUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.CustomMetricUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.ErrorHandler;
import com.google.apphosting.utils.config.AppEngineWebXml.HealthCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.VpcAccessConnector;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.StagingOptions;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXml.SecurityConstraint;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link AppYamlTranslator}.
 *
 */
public class AppYamlTranslatorTest extends TestCase {
  private AppEngineWebXml appEngineWebXml;
  private WebXml webXml;
  private BackendsXml backendsXml;
  private Set<String> staticFiles;
  private ApiConfig apiConfig;

  @Override
  public void setUp() throws Exception {
    appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.setAppId("app1");
    appEngineWebXml.setMajorVersionId("ver1");
    appEngineWebXml.setPrecompilationEnabled(false);

    webXml = new WebXml();

    backendsXml = new BackendsXml();

    staticFiles = new HashSet<String>();
  }

  private void addWelcomeFiles() {
    // Just add the default welcome files.
    webXml.addWelcomeFile("index.html");
    webXml.addWelcomeFile("index.jsp");
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    appEngineWebXml = null;
    webXml = null;
  }

  public void testJava6Runtime() {
    AppYamlTranslator translator = createTranslator("java");
    String yaml =
        "application: 'app1'\n"
            + "runtime: java\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testGoogleRuntime() {
    appEngineWebXml.setRuntime("google");
    AppYamlTranslator translator = createTranslator("google");
    String yaml =
        "application: 'app1'\n"
            + "runtime: google\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testGoogleLegacyRuntime() {
    appEngineWebXml.setRuntime("googlelegacy");
    AppYamlTranslator translator = createTranslator("googlelegacy");
    String yaml =
        "application: 'app1'\n"
            + "runtime: googlelegacy\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testJava11Runtime() {
    appEngineWebXml.setRuntime("java11");
    AppYamlTranslator translator = createTranslator("java11");
    String yaml =
        "application: 'app1'\n"
            + "runtime: java11\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testJava17Runtime() {
    appEngineWebXml.setRuntime("java17");
    AppYamlTranslator translator = createTranslator("java17");
    String yaml =
        "application: 'app1'\n"
            + "runtime: java17\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testThreadsafeAndNoAPIVersionJava17() {
    appEngineWebXml.setThreadsafe(true);
    appEngineWebXml.setRuntime("java17");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java17\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoServletsNoFiles() {
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoVersion() {
    appEngineWebXml.setMajorVersionId(null);
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testRuntime() {
    appEngineWebXml.setRuntime("foo-bar");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: foo-bar\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testAutomaticServer_Minimal() {
    appEngineWebXml.setService("stan");
    appEngineWebXml.setInstanceClass("F8");
    AutomaticScaling automaticScaling = appEngineWebXml.getAutomaticScaling();
    automaticScaling.setMinNumInstances(1);
    automaticScaling.setMaxNumInstances(2);
    automaticScaling.setTargetNetworkSentBytesPerSec(16777216);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "service: 'stan'\n"
            + "instance_class: F8\n"
            + "automatic_scaling:\n"
            + "  min_num_instances: 1\n"
            + "  max_num_instances: 2\n"
            + "  target_network_sent_bytes_per_sec: 16777216\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testAutomaticServer_Full() {
    appEngineWebXml.setService("stan");
    appEngineWebXml.setInstanceClass("F8");
    AutomaticScaling automaticScaling = appEngineWebXml.getAutomaticScaling();
    automaticScaling.setMinPendingLatency("automatic");
    automaticScaling.setMaxPendingLatency("123.2s");
    automaticScaling.setMinIdleInstances("automatic");
    automaticScaling.setMaxIdleInstances("123");
    automaticScaling.setMaxConcurrentRequests("20");

    automaticScaling.setMinNumInstances(2);
    automaticScaling.setMaxNumInstances(7);
    automaticScaling.setCoolDownPeriodSec(11);
    CpuUtilization cpuUtilization = new CpuUtilization();
    cpuUtilization.setTargetUtilization(0.7);
    cpuUtilization.setAggregationWindowLengthSec(18);
    automaticScaling.setCpuUtilization(cpuUtilization);

    automaticScaling.setTargetNetworkSentBytesPerSec(16777216);
    automaticScaling.setTargetNetworkSentPacketsPerSec(1200);
    automaticScaling.setTargetNetworkReceivedBytesPerSec(33554432);
    automaticScaling.setTargetNetworkReceivedPacketsPerSec(1000);
    automaticScaling.setTargetDiskWriteBytesPerSec(4194304);
    automaticScaling.setTargetDiskWriteOpsPerSec(100);
    automaticScaling.setTargetDiskReadBytesPerSec(8388608);
    automaticScaling.setTargetDiskReadOpsPerSec(500);
    automaticScaling.setTargetRequestCountPerSec(80);
    automaticScaling.setTargetConcurrentRequests(12);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "service: 'stan'\n"
            + "instance_class: F8\n"
            + "automatic_scaling:\n"
            + "  min_pending_latency: automatic\n"
            + "  max_pending_latency: 123.2s\n"
            + "  min_idle_instances: automatic\n"
            + "  max_idle_instances: 123\n"
            + "  max_concurrent_requests: 20\n"
            + "  min_num_instances: 2\n"
            + "  max_num_instances: 7\n"
            + "  cool_down_period_sec: 11\n"
            + "  cpu_utilization:\n"
            + "    target_utilization: 0.7\n"
            + "    aggregation_window_length_sec: 18\n"
            + "  target_network_sent_bytes_per_sec: 16777216\n"
            + "  target_network_sent_packets_per_sec: 1200\n"
            + "  target_network_received_bytes_per_sec: 33554432\n"
            + "  target_network_received_packets_per_sec: 1000\n"
            + "  target_disk_write_bytes_per_sec: 4194304\n"
            + "  target_disk_write_ops_per_sec: 100\n"
            + "  target_disk_read_bytes_per_sec: 8388608\n"
            + "  target_disk_read_ops_per_sec: 500\n"
            + "  target_request_count_per_sec: 80\n"
            + "  target_concurrent_requests: 12\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testAutomaticScalingCloneSchedulerSettings() {
    appEngineWebXml.setService("stan");
    appEngineWebXml.setInstanceClass("F8");
    AutomaticScaling automaticScaling = appEngineWebXml.getAutomaticScaling();
    automaticScaling.setMinInstances(1);
    automaticScaling.setMaxInstances(2);
    automaticScaling.setTargetCpuUtilization(3.12);
    automaticScaling.setTargetThroughputUtilization(43.12);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "service: 'stan'\n"
            + "instance_class: F8\n"
            + "automatic_scaling:\n"
            + "  min_instances: 1\n"
            + "  max_instances: 2\n"
            + "  target_cpu_utilization: 3.12\n"
            + "  target_throughput_utilization: 43.12\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testAutomaticScalingCustomMetrics() {
    appEngineWebXml.setEnv("flex");
    AutomaticScaling automaticScaling = appEngineWebXml.getAutomaticScaling();
    automaticScaling.setMinInstances(1);
    automaticScaling.setMaxInstances(2);

    List<CustomMetricUtilization> customMetrics = new ArrayList<>();
    CustomMetricUtilization customMetric = new CustomMetricUtilization();
    customMetric.setMetricName("foo/metric/name");
    customMetric.setTargetType("GAUGE");
    customMetric.setTargetUtilization(10.0);
    customMetric.setFilter("metric.foo != bar");
    customMetrics.add(customMetric);
    customMetric = new CustomMetricUtilization();
    customMetric.setMetricName("bar/metric/name");
    customMetric.setTargetType("DELTA_PER_SECOND");
    customMetric.setSingleInstanceAssignment(20.0);
    customMetrics.add(customMetric);
    automaticScaling.setCustomMetrics(customMetrics);

    String yaml =
        "application: 'app1'\n"
            + "runtime: java\n"
            + "env: flex\n"
            + "version: 'ver1'\n"
            + "automatic_scaling:\n"
            + "  min_instances: 1\n"
            + "  max_instances: 2\n"
            + "  custom_metrics:\n"
            + "    - metric_name: 'foo/metric/name'\n"
            + "      target_type: 'GAUGE'\n"
            + "      target_utilization: 10.0\n"
            + "      filter: 'metric.foo != bar'\n"
            + "    - metric_name: 'bar/metric/name'\n"
            + "      target_type: 'DELTA_PER_SECOND'\n"
            + "      single_instance_assignment: 20.0\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    AppYamlTranslator translator = createTranslator();
    assertEquals(yaml, translator.getYaml());
  }

  public void testManualServer() {
    appEngineWebXml.setService("stan");
    appEngineWebXml.setInstanceClass("B8");
    appEngineWebXml.getManualScaling().setInstances("15");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "service: 'stan'\n"
            + "instance_class: B8\n"
            + "manual_scaling:\n"
            + "  instances: 15\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testBasicServer() {
    appEngineWebXml.setService("stan");
    appEngineWebXml.setInstanceClass("B8");
    appEngineWebXml.getBasicScaling().setMaxInstances("13");
    appEngineWebXml.getBasicScaling().setIdleTimeout("15m");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "service: 'stan'\n"
            + "instance_class: B8\n"
            + "basic_scaling:\n"
            + "  max_instances: 13\n"
            + "  idle_timeout: 15m\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoServletsWithStaticFile() {
    staticFiles.add("__static__/static-file.txt");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoServletsWithStaticFileAndPublicRoot() {
    appEngineWebXml.setPublicRoot("/public");
    staticFiles.add("__static__/public/static-file.txt");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__/public\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoServletsWithWelcomeFile() {
    staticFiles.add("__static__/index.html");
    staticFiles.add("__static__/index.jsp");
    addWelcomeFiles();
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/.*/)\n"
            + "  static_files: __static__\\1index.jsp\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/)\n"
            + "  static_files: __static__\\1index.jsp\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoServletsWithWelcomeFileAndPublicRoot() {
    appEngineWebXml.setPublicRoot("/public");
    staticFiles.add("__static__/public/index.html");
    addWelcomeFiles();
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*/)\n"
            + "  static_files: __static__/public\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/)\n"
            + "  static_files: __static__/public\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__/public\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNoApiVersion() {
    AppYamlTranslator translator = createTranslator(null, "java8");
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: 'none'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testServlets() {
    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/servlet2/*", null);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet2/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testServletEmptyMapping() {
    webXml.setServletVersion("3.1");
    webXml.addServletPattern("", null);
    webXml.addServletPattern("/servlet2/*", null);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet2/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    ;
    assertEquals(yaml, translator.getYaml());
  }

  public void testMatchAllServlet() {
    webXml.addServletPattern("/*", null);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    // No wildcard entries since fallthrough gets set.
    assertEquals(yaml, translator.getYaml());
  }

  public void testServletsWithStaticFile() {
    staticFiles.add("__static__/static-file.txt");

    appEngineWebXml.setSslEnabled(false);
    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/servlet2/*", null);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /servlet2/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testServletsWithOverlappingAuth() {
    staticFiles.add("__static__/index.html");
    staticFiles.add("__static__/other-file.html");
    staticFiles.add("__static__/admin/index.html");
    staticFiles.add("__static__/admin/other-file.html");

    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/servlet2/*", null);
    webXml.addServletPattern("/admin/servlet3", null);
    webXml.addServletPattern("/admin/servlet4/*", null);
    addWelcomeFiles();

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/admin/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ADMIN);
    }
    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ANY_USER);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/admin/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: (/admin)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/admin/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: (/admin/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /admin/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/servlet4\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /admin/servlet4/.*\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/servlet3\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /servlet2/.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testServletsWithOverlappingNoneAuth() {
    staticFiles.add("__static__/index.html");
    staticFiles.add("__static__/other-file.html");
    staticFiles.add("__static__/noauth/index.html");
    staticFiles.add("__static__/noauth/other-file.html");

    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/servlet2/*", null);
    webXml.addServletPattern("/noauth/servlet3", null);
    webXml.addServletPattern("/noauth/servlet4/*", null);
    addWelcomeFiles();

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/noauth/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.NONE);
    }
    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ANY_USER);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/noauth/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: (/noauth)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/noauth/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: (/noauth/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /noauth/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /noauth/servlet4\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /noauth/.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /noauth/servlet4/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /noauth/servlet3\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /servlet2/.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testInboundServices() {
    appEngineWebXml.addInboundService("mail");
    appEngineWebXml.addInboundService("xmpp_message");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "inbound_services:\n"
            + "- mail\n"
            + "- xmpp_message\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testOverrideExpirationForSubset() {
    staticFiles.add("__static__/foo/Foo.html");
    staticFiles.add("__static__/foo/Foo.nocache.js");
    staticFiles.add("__static__/foo/F00001.cache.js");
    staticFiles.add("__static__/foo/F00002.cache.png");
    staticFiles.add("__static__/foo/F00003.cache.html");

    appEngineWebXml.includeStaticPattern("**.cache.**", "7d");
    appEngineWebXml.includeStaticPattern("**", null);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*\\.cache\\..*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "  expiration: 7d\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testMultipleJspsWithOverlappingAuth() {
    webXml.addServletPattern("/foo.jsp", null);
    webXml.addServletPattern("/bar.jsp", null);
    webXml.addServletPattern("/baz.jsp", null);
    webXml.addServletPattern("/admin/foo.jsp", null);
    webXml.addServletPattern("/admin/bar.jsp", null);
    webXml.addServletPattern("/admin/baz.jsp", null);

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/admin/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ADMIN);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /admin/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /admin/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/.*\\.jsp\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testSimpleAuth() {
    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/admin/servlet2", null);

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/admin/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ADMIN);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /admin/.*/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /admin/\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/servlet2\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testSimpleSsl() {
    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/secure/servlet2", null);

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/secure/*");
      constraint.setTransportGuarantee(SecurityConstraint.TransportGuarantee.INTEGRAL);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /secure/.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /secure/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n"
            + "- url: /secure/servlet2\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: always\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testSslDisabled() {
    appEngineWebXml.setSslEnabled(false);
    webXml.addServletPattern("/servlet1", null);
    webXml.addServletPattern("/servlet2/*", null);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /servlet2/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: never\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testPrecompilationEnabledStandardEnvironment() {
    appEngineWebXml.setPrecompilationEnabled(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "derived_file_type:\n"
            + "- java_precompiled\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testPrecompilationEnabledVmEnvironment() {
    appEngineWebXml.setPrecompilationEnabled(true);
    appEngineWebXml.setUseVm(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testPrecompilationEnabledFlexEnvironment() {
    appEngineWebXml.setPrecompilationEnabled(true);
    appEngineWebXml.setEnv("flex");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java\n"
            + "env: flex\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testThreadsafe() {
    appEngineWebXml.setThreadsafe(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "threadsafe: True\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testLegacyAPisOnJava11() {
    appEngineWebXml.setAppEngineApis(true);
    appEngineWebXml.setRuntime("java11");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java11\n"
            + "version: 'ver1'\n"
            + "app_engine_apis: True\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testLegacyAPisOnJava8() {
    appEngineWebXml.setAppEngineApis(true);
    appEngineWebXml.setRuntime("java8");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testThreadsafeAndNoAPIVersionGoogleLegacy() {
    appEngineWebXml.setThreadsafe(true);
    appEngineWebXml.setRuntime("googlelegacy");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: googlelegacy\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testLegacyAutoIdPolicy() {
    appEngineWebXml.setAutoIdPolicy("legacy");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: legacy\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testEntrypoint() {
    appEngineWebXml.setEntrypoint("/some entrypoint");
    appEngineWebXml.setRuntime("java11");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java11\n"
            + "entrypoint: '/some entrypoint'\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testRuntimeChannel() {
    appEngineWebXml.setRuntimeChannel("canary");
    appEngineWebXml.setRuntime("java11");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java11\n"
            + "runtime_channel: canary\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testDefaultAutoIdPolicy() {
    appEngineWebXml.setAutoIdPolicy("default");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testUnspecifiedAutoIdPolicy() {
    assertNull(appEngineWebXml.getAutoIdPolicy());

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testCodeLock() {
    appEngineWebXml.setCodeLock(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "code_lock: True\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testEnv() {
    appEngineWebXml.setEnv("flexible");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java\n"
            + "env: flexible\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testEnvFlex() {
    appEngineWebXml.setEnv("flex");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java\n"
            + "env: flex\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testEnv2() {
    appEngineWebXml.setEnv("2");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java\n"
            + "env: 2\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testValidEnv() {
    appEngineWebXml.setEnv("2");
    assertTrue(appEngineWebXml.isFlexible());
    appEngineWebXml.setEnv("flex");
    assertTrue(appEngineWebXml.isFlexible());
    appEngineWebXml.setEnv("flexible");
    assertTrue(appEngineWebXml.isFlexible());
    appEngineWebXml.setEnv("standard");
    assertFalse(appEngineWebXml.isFlexible());
  }

  public void testEnvStd() {
    appEngineWebXml.setEnv("standard");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testVmEnabled() {
    appEngineWebXml.setUseVm(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testVmDisabled() {
    appEngineWebXml.setUseVm(false);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testBetaSettings() {
    appEngineWebXml.setUseVm(true);
    appEngineWebXml.addBetaSetting("machine_type", "n1-standard-1");
    appEngineWebXml.addBetaSetting("region", "region-foo");
    appEngineWebXml.addBetaSetting("zone", "zone-foo");
    appEngineWebXml.addBetaSetting("image", "canary");
    appEngineWebXml.addBetaSetting("use_deployment_manager", "true");
    appEngineWebXml.addBetaSetting("no_appserver_affinity", "true");
    appEngineWebXml.addBetaSetting("health_check_enabled", "on");
    appEngineWebXml.addBetaSetting("health_check_restart_timeout", "600");
    appEngineWebXml.addBetaSetting(
        "root_setup_command", "/bin/bash {app_name}/setup.sh 'arg 1' arg");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "beta_settings:\n"
            + "  'machine_type': 'n1-standard-1'\n"
            + "  'region': 'region-foo'\n"
            + "  'zone': 'zone-foo'\n"
            + "  'image': 'canary'\n"
            + "  'use_deployment_manager': 'true'\n"
            + "  'no_appserver_affinity': 'true'\n"
            + "  'health_check_enabled': 'on'\n"
            + "  'health_check_restart_timeout': '600'\n"
            + "  'root_setup_command': '/bin/bash {app_name}/setup.sh ''arg 1'' arg'\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testBetaSettingsExistingImage() {
    appEngineWebXml.setUseVm(true);
    appEngineWebXml.addBetaSetting("image", "4.3.2");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "beta_settings:\n"
            + "  'image': '4.3.2'\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testBetaSettings_standardEnvironment() {
    appEngineWebXml.setUseVm(false);
    appEngineWebXml.addBetaSetting("use_endpoints_api_management", "true");
    appEngineWebXml.addBetaSetting("snapshot_enabled", "true");
    appEngineWebXml.addBetaSetting("snapshot_reusable", "true");
    appEngineWebXml.addBetaSetting("snapshot_trigger", "true");
    appEngineWebXml.addBetaSetting("endpoints_swagger_spec_file", "swagger.yaml");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "beta_settings:\n"
            + "  'use_endpoints_api_management': 'true'\n"
            + "  'snapshot_enabled': 'true'\n"
            + "  'snapshot_reusable': 'true'\n"
            + "  'snapshot_trigger': 'true'\n"
            + "  'endpoints_swagger_spec_file': 'swagger.yaml'\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testHealthCheck_vmDisabled() {
    HealthCheck healthCheck = new HealthCheck();
    healthCheck.setEnableHealthCheck(true);
    healthCheck.setCheckIntervalSec(5);
    healthCheck.setTimeoutSec(6);
    healthCheck.setUnhealthyThreshold(7);
    healthCheck.setHealthyThreshold(8);
    healthCheck.setRestartThreshold(9);
    healthCheck.setHost("test.com");
    appEngineWebXml.setHealthCheck(healthCheck);

    AppYamlTranslator translator = createTranslator();

    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    assertEquals(yaml, translator.getYaml());
  }

  public void testHealthCheck_vmEnabled_healthCheckNotSet() {
    HealthCheck healthCheck = new HealthCheck();
    healthCheck.setCheckIntervalSec(5);
    healthCheck.setTimeoutSec(6);
    healthCheck.setUnhealthyThreshold(7);
    healthCheck.setHealthyThreshold(8);
    healthCheck.setRestartThreshold(9);
    healthCheck.setHost("test.com");

    appEngineWebXml.setHealthCheck(healthCheck);
    appEngineWebXml.setUseVm(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "health_check:\n"
            + "  enable_health_check: True\n"
            + "  check_interval_sec: 5\n"
            + "  timeout_sec: 6\n"
            + "  unhealthy_threshold: 7\n"
            + "  healthy_threshold: 8\n"
            + "  restart_threshold: 9\n"
            + "  host: test.com\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testHealthCheck_vmEnabled_healthCheckSetTrue() {
    HealthCheck healthCheck = new HealthCheck();
    healthCheck.setEnableHealthCheck(true);
    healthCheck.setCheckIntervalSec(5);
    healthCheck.setTimeoutSec(6);
    healthCheck.setUnhealthyThreshold(7);
    healthCheck.setHealthyThreshold(8);
    healthCheck.setRestartThreshold(9);
    healthCheck.setHost("test.com");

    appEngineWebXml.setHealthCheck(healthCheck);
    appEngineWebXml.setUseVm(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "health_check:\n"
            + "  enable_health_check: True\n"
            + "  check_interval_sec: 5\n"
            + "  timeout_sec: 6\n"
            + "  unhealthy_threshold: 7\n"
            + "  healthy_threshold: 8\n"
            + "  restart_threshold: 9\n"
            + "  host: test.com\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testHealthCheck_vmEnabled_healthCheckSetFalse() {
    HealthCheck healthCheck = new HealthCheck();
    healthCheck.setEnableHealthCheck(false);
    healthCheck.setCheckIntervalSec(5);
    healthCheck.setTimeoutSec(6);
    healthCheck.setUnhealthyThreshold(7);
    healthCheck.setHealthyThreshold(8);
    healthCheck.setRestartThreshold(9);
    healthCheck.setHost("test.com");
    appEngineWebXml.setHealthCheck(healthCheck);

    appEngineWebXml.setUseVm(true);

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "health_check:\n"
            + "  enable_health_check: False\n"
            + "  check_interval_sec: 5\n"
            + "  timeout_sec: 6\n"
            + "  unhealthy_threshold: 7\n"
            + "  healthy_threshold: 8\n"
            + "  restart_threshold: 9\n"
            + "  host: test.com\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testResourcesNotSet() {
    AppYamlTranslator translator = createTranslator();

    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    assertEquals(yaml, translator.getYaml());
  }

  public void testResourcesCpu() {
    appEngineWebXml.getResources().setCpu(3.7);
    appEngineWebXml.setUseVm(true);
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "resources:\n"
            + "  cpu: 3.7\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    assertEquals(yaml, translator.getYaml());
  }

  public void testNetworkNotSet() {
    AppYamlTranslator translator = createTranslator();

    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    assertEquals(yaml, translator.getYaml());
  }

  public void testNetworkFull() {
    appEngineWebXml.setUseVm(true);
    appEngineWebXml.getNetwork().setInstanceTag("mytag");
    appEngineWebXml.getNetwork().addForwardedPort("myport");
    appEngineWebXml.getNetwork().addForwardedPort("myport2:myport3");
    appEngineWebXml.getNetwork().setName("mynetwork");
    appEngineWebXml.getNetwork().setSubnetworkName("mysubnetwork");
    appEngineWebXml.getNetwork().setSessionAffinity(true);
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "network:\n"
            + "  instance_tag: mytag\n"
            + "  forwarded_ports:\n"
            + "  - myport\n"
            + "  - myport2:myport3\n"
            + "  name: mynetwork\n"
            + "  subnetwork_name: mysubnetwork\n"
            + "  session_affinity: True\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    assertEquals(yaml, translator.getYaml());
  }

  /* The staging-section does not generate any app.yaml contents */
  public void testStagingIgnored() {
    StagingOptions stagingOptions =
        StagingOptions.builder()
            .setSplitJarFiles(Optional.of(true))
            .setCompileEncoding(Optional.of("UTF-16"))
            .build();
    appEngineWebXml.setStagingOptions(stagingOptions);
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";

    assertEquals(yaml, translator.getYaml());
  }

  public void testVmEnvVariables() {
    appEngineWebXml.setUseVm(true);
    appEngineWebXml.addEnvironmentVariable("DEFAULT_ENCODING", "UTF-8");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "vm: True\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "env_variables:\n"
            + "  'DEFAULT_ENCODING': 'UTF-8'\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: .*\\.jsp\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testEnvVariablesforAppEngineStandardjava8() {
    appEngineWebXml.addEnvironmentVariable("DEFAULT_ENCODING", "UTF-8");
    appEngineWebXml.addEnvironmentVariable("foo", "bar");

    AppYamlTranslator translator = createTranslator();
    String result = translator.getYaml();
    // Check for presence of the environment variables.
    assertTrue(result.contains("  'DEFAULT_ENCODING': 'UTF-8'\n"));
    assertTrue(result.contains("  'foo': 'bar'\n"));
  }

  public void testEnvVariablesforAppEngineStandardJava8() {
    appEngineWebXml.addEnvironmentVariable("DEFAULT_ENCODING", "UTF-8");
    appEngineWebXml.addEnvironmentVariable("foo", "bar");
    appEngineWebXml.setRuntime("java8");

    AppYamlTranslator translator = createTranslator();
    String result = translator.getYaml();
    // Check for presence of the environment variables.
    assertTrue(result.contains("  'DEFAULT_ENCODING': 'UTF-8'\n"));
    assertTrue(result.contains("  'foo': 'bar'\n"));
  }

  public void testServiceAccount() {
    appEngineWebXml.setServiceAccount("foobar");
    appEngineWebXml.setRuntime("java8");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "service_account: foobar\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testVpcAccessConnector() {
    appEngineWebXml.setVpcAccessConnector(VpcAccessConnector.builderFor("barf").build());
    appEngineWebXml.setRuntime("java8");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "vpc_access_connector:\n"
            + "  name: barf\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testVpcAccessConnectorEgressSettings() {
    appEngineWebXml.setVpcAccessConnector(
        VpcAccessConnector.builderFor("barf").setEgressSetting("all-traffic").build());
    appEngineWebXml.setRuntime("java8");
    AppYamlTranslator translator = createTranslator();
    assertThat(translator.getYaml()).contains(
        "vpc_access_connector:\n"
            + "  name: barf\n"
            + "  egress_setting: all-traffic\n");
  }

  public void testAdminConsolePages() {
    appEngineWebXml.addAdminConsolePage(AdminConsolePage.of("foo", "/bar"));
    appEngineWebXml.addAdminConsolePage(AdminConsolePage.of("baz", "/bax"));

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "admin_console:\n"
            + "  pages:\n"
            + "  - name: foo\n"
            + "    url: /bar\n"
            + "  - name: baz\n"
            + "    url: /bax\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testErrorHandlers() {
    staticFiles.add("__static__/ohno.html");
    staticFiles.add("__static__/timeout.html");
    appEngineWebXml.addErrorHandler(ErrorHandler.of("ohno.html", null));
    appEngineWebXml.addErrorHandler(ErrorHandler.of("/timeout.html", "timeout"));

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "error_handlers:\n"
            + "- file: __static__/ohno.html\n"
            + "- file: __static__/timeout.html\n"
            + "  error_code: timeout\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testNonExistentErrorHandlers() {
    appEngineWebXml.addErrorHandler(ErrorHandler.of("unknown.html", null));

    AppYamlTranslator translator = createTranslator();
    try {
      translator.getYaml();
      fail("Expected a AppEngineConfigException");
    } catch (AppEngineConfigException ex) {
      // expected
    }
  }

  public void testAuthWithFallthrough() {
    webXml.setFallThroughToRuntime(true);

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/admin/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ADMIN);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /admin\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/.*\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testTooManyHandlersEnablesFallthrough() {
    staticFiles.add("__static__/index.html");
    staticFiles.add("__static__/other-file.html");
    staticFiles.add("__static__/admin/index.html");
    staticFiles.add("__static__/admin/other-file.html");
    addWelcomeFiles();

    // This should force it to use fallthrough handlers.
    for (int i = 0; i < 150; i++) {
      webXml.addServletPattern("/servlet" + i, null);
    }

    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/admin/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ADMIN);
    }
    {
      SecurityConstraint constraint = webXml.addSecurityConstraint();
      constraint.addUrlPattern("/*");
      constraint.setRequiredRole(SecurityConstraint.RequiredRole.ANY_USER);
    }

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/admin/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: (/admin)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/admin/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/.*/)\n"
            + "  static_files: __static__\\1index.html\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: (/admin/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: (/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: required\n"
            + "  secure: optional\n"
            + "- url: /admin\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /admin/.*\n"
            + "  script: unused\n"
            + "  login: admin\n"
            + "  secure: optional\n"
            + "- url: /.*\n"
            + "  script: unused\n"
            + "  login: required\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testHttpHeaders() {
    staticFiles.add("__static__/my-static-files/foo.txt");

    AppEngineWebXml.StaticFileInclude staticFileInclude =
        appEngineWebXml.includeStaticPattern("/my-static-files/*", null);
    Map<String, String> httpHeaders = staticFileInclude.getHttpHeaders();
    httpHeaders.put("foo", "1");
    httpHeaders.put("bar", "barf");

    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: (/my-static-files/.*)\n"
            + "  static_files: __static__\\1\n"
            + "  upload: __NOT_USED__\n"
            + "  require_matching_file: True\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "  http_headers:\n"
            // Yaml library emitting headers is OS dependent so eol is different on Windows.
            + "    foo: 1" +  System.getProperty("line.separator")
            + "    bar: barf" +  System.getProperty("line.separator")
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());

  }

  public void testBackends() {
    backendsXml.addBackend(
        new BackendsXml.Entry(
            "foo", null, "B1", 1, EnumSet.of(BackendsXml.Option.FAIL_FAST), null));
    backendsXml.addBackend(
        new BackendsXml.Entry(
            "bar",
            5,
            "B8",
            null,
            EnumSet.of(BackendsXml.Option.PUBLIC, BackendsXml.Option.DYNAMIC),
            null));
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "backends:\n"
            + "- name: foo\n"
            + "  class: B1\n"
            + "  max_concurrent_requests: 1\n"
            + "  options: failfast\n"
            + "- name: bar\n"
            + "  instances: 5\n"
            + "  class: B8\n"
            + "  options: dynamic, public\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testApiConfig() {
    webXml.addServletPattern("/servlet1", "endpoint-1");
    webXml.addServletPattern("/servlet2", "endpoint-2");
    appEngineWebXml.setApiConfig(ApiConfig.of("fake.ConfigServlet", "/my/api"));
    appEngineWebXml.addApiEndpoint("endpoint-1");
    appEngineWebXml.addApiEndpoint("endpoint-2");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_config:\n"
            + "  url: /my/api\n"
            + "  script: unused\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /servlet2\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "  api_endpoint: True\n"
            + "- url: /servlet1\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "  api_endpoint: True\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testApiServingExplicitGlobForEndpoint() {
    webXml.addServletPattern("/_ah/spi/*", "my-api-endpoint");
    AppYamlTranslator translator = createTranslator();
    String yaml =
        "application: 'app1'\n"
            + "runtime: java8\n"
            + "version: 'ver1'\n"
            + "auto_id_policy: default\n"
            + "api_version: '1.0'\n"
            + "handlers:\n"
            + "- url: /\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /.*/\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n"
            + "- url: /_ah/spi/.*\n"
            + "  script: unused\n"
            + "  login: optional\n"
            + "  secure: optional\n";
    assertEquals(yaml, translator.getYaml());
  }

  public void testBuildEnvVariablesforAppEngineStandard() {
    appEngineWebXml.addBuildEnvironmentVariable("USE_GRAAL_VM", "true");
    appEngineWebXml.addBuildEnvironmentVariable("foo", "bar");

    AppYamlTranslator translator = createTranslator();
    String result = translator.getYaml();
    // Check for presence of the build environment variables.
    assertThat(result).contains("  'USE_GRAAL_VM': 'true'\n");
    assertThat(result).contains("  'foo': 'bar'\n");
  }

  private AppYamlTranslator createTranslator() {
    return createTranslator("1.0", null);
  }

  private AppYamlTranslator createTranslator(String runtime) {
    return createTranslator("1.0", runtime);
  }

  private AppYamlTranslator createTranslator(String apiVersion, String runtime) {
    return new AppYamlTranslator(
        appEngineWebXml, webXml, backendsXml, apiVersion, staticFiles, apiConfig, runtime);
  }
}
