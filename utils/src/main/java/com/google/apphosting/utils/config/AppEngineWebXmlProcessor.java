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

package com.google.apphosting.utils.config;

import com.google.apphosting.utils.config.AppEngineWebXml.AdminConsolePage;
import com.google.apphosting.utils.config.AppEngineWebXml.ApiConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.AutomaticScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.BasicScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.ClassLoaderConfig;
import com.google.apphosting.utils.config.AppEngineWebXml.CpuUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.CustomMetricUtilization;
import com.google.apphosting.utils.config.AppEngineWebXml.ErrorHandler;
import com.google.apphosting.utils.config.AppEngineWebXml.HealthCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.LivenessCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.ManualScaling;
import com.google.apphosting.utils.config.AppEngineWebXml.Network;
import com.google.apphosting.utils.config.AppEngineWebXml.PrioritySpecifierEntry;
import com.google.apphosting.utils.config.AppEngineWebXml.ReadinessCheck;
import com.google.apphosting.utils.config.AppEngineWebXml.Resources;
import com.google.apphosting.utils.config.AppEngineWebXml.VpcAccessConnector;
import com.google.common.collect.ImmutableSortedSet;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Constructs an {@link AppEngineWebXml} from an xml document corresponding to
 * appengine-web.xsd.
 *
 * TODO: Add a real link to the xsd once it exists and do schema
 * validation.
 *
 */
class AppEngineWebXmlProcessor {

  enum FileType { STATIC, RESOURCE }

  private static final Logger logger = Logger.getLogger(AppEngineWebXmlProcessor.class.getName());
  // Error handling to disallow having both module and service entries.
  private boolean moduleNodeFound;
  private boolean serviceNodeFound;
  private boolean warmupNodeFound;

  /**
   * Construct an {@link AppEngineWebXml} from the xml document
   * identified by the provided {@link InputStream}.
   *
   * @param is The InputStream containing the xml we want to parse and process.
   *
   * @return Object representation of the xml document.
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  public AppEngineWebXml processXml(InputStream is) {
    Element config = getTopLevelNode(is);
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    NodeList nodes = config.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      processSecondLevelNode((Element) node, appEngineWebXml);
    }
    checkScalingConstraints(appEngineWebXml);
    // Do not allow service and module to be defined in the same app.yaml.
    if (serviceNodeFound && moduleNodeFound) {
      throw new AppEngineConfigException(
          "The <service> and <module> elements are conflicting. "
              + "Please remove the deprecated <module> element.");
    }
    // Warmup requests are on by default for Java Standard (not Flex) applications
    // configured via an appengine-web.xml.
    if (!appEngineWebXml.isFlexible() && !warmupNodeFound) {
      appEngineWebXml.setWarmupRequestsEnabled(true);
    }
    return appEngineWebXml;
  }

  /**
   * Given an AppEngineWebXml, ensure it has no more than one of the scaling options available.
   *
   * @throws AppEngineConfigException If there is more than one scaling option selected.
   */
  private static void checkScalingConstraints(AppEngineWebXml appEngineWebXml) {
    int count = appEngineWebXml.getManualScaling().isEmpty() ? 0 : 1;
    count += appEngineWebXml.getBasicScaling().isEmpty() ? 0 : 1;
    count += appEngineWebXml.getAutomaticScaling().isEmpty() ? 0 : 1;
    if (count > 1) {
      throw new AppEngineConfigException(
          "There may be only one of 'automatic-scaling', 'manual-scaling' or " +
          "'basic-scaling' elements.");
    }
  }

  /**
   * Given an InputStream, create a Node corresponding to the top level xml
   * element.
   *
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  Element getTopLevelNode(InputStream is) {
    return XmlUtils.parseXml(is).getDocumentElement();
  }

  private void processSecondLevelNode(Element elt, AppEngineWebXml appEngineWebXml) {
    String elementName = elt.getTagName();
    switch (elementName) {
      case "system-properties":
        processSystemPropertiesNode(elt, appEngineWebXml);
        break;
      case "vm-settings":
      case "beta-settings":
        processBetaSettingsNode(elt, appEngineWebXml);
        break;
      case "vm-health-check":
      case "health-check":
        processHealthCheckNode(elt, appEngineWebXml);
        break;
      case "liveness-check":
        processLivenessCheckNode(elt, appEngineWebXml);
        break;
      case "readiness-check":
        processReadinessCheckNode(elt, appEngineWebXml);
        break;
      case "resources":
        processResourcesNode(elt, appEngineWebXml);
        break;
      case "network":
        processNetworkNode(elt, appEngineWebXml);
        break;
      case "env-variables":
        processEnvironmentVariablesNode(elt, appEngineWebXml);
        break;
      case "build-env-variables":
        processBuildEnvironmentVariablesNode(elt, appEngineWebXml);
        break;
      case "application":
        processApplicationNode(elt, appEngineWebXml);
        break;
      case "entrypoint":
        processEntrypointNode(elt, appEngineWebXml);
        break;
      case "runtime-channel":
        processRuntimeChannelNode(elt, appEngineWebXml);
        break;
      case "runtime":
        processRuntimeNode(elt, appEngineWebXml);
        break;
      case "version":
        processVersionNode(elt, appEngineWebXml);
        break;
      case "source-language":
        logger.warning(
            "The element <source-language> in appengine-web.xml file was ignored.");
        break;
      case "module":
        moduleNodeFound = true;
        processModuleNode(elt, appEngineWebXml);
        break;
      case "service":
        serviceNodeFound = true;
        processServiceNode(elt, appEngineWebXml);
        break;
      case "instance-class":
        processInstanceClassNode(elt, appEngineWebXml);
        break;
      case "automatic-scaling":
        processAutomaticScalingNode(elt, appEngineWebXml);
        break;
      case "manual-scaling":
        processManualScalingNode(elt, appEngineWebXml);
        break;
      case "basic-scaling":
        processBasicScalingNode(elt, appEngineWebXml);
        break;
      case "static-files":
        processFilesetNode(elt, appEngineWebXml, FileType.STATIC);
        break;
      case "resource-files":
        processFilesetNode(elt, appEngineWebXml, FileType.RESOURCE);
        break;
      case "ssl-enabled":
        processSslEnabledNode(elt, appEngineWebXml);
        break;
      case "sessions-enabled":
        processSessionsEnabledNode(elt, appEngineWebXml);
        break;
      case "async-session-persistence":
        processAsyncSessionPersistenceNode(elt, appEngineWebXml);
        break;
      case "user-permissions":
        processPermissionsNode(elt, appEngineWebXml);
        break;
      case "public-root":
        processPublicRootNode(elt, appEngineWebXml);
        break;
      case "inbound-services":
        processInboundServicesNode(elt, appEngineWebXml);
        break;
      case "precompilation-enabled":
        processPrecompilationEnabledNode(elt, appEngineWebXml);
        break;
      case "admin-console":
        processAdminConsoleNode(elt, appEngineWebXml);
        break;
      case "static-error-handlers":
        processErrorHandlerNode(elt, appEngineWebXml);
        break;
      case "warmup-requests-enabled":
        warmupNodeFound = true;
        processWarmupRequestsEnabledNode(elt, appEngineWebXml);
        break;
      case "threadsafe":
        processThreadsafeNode(elt, appEngineWebXml);
        break;
      case "app-engine-apis":
        appEngineWebXml.setAppEngineApis(getBooleanValue(elt));
        break;
      case "auto-id-policy":
        processAutoIdPolicyNode(elt, appEngineWebXml);
        break;
      case "code-lock":
        processCodeLockNode(elt, appEngineWebXml);
        break;
      case "vm":
        processVmNode(elt, appEngineWebXml);
        break;
      case "env":
        processEnvNode(elt, appEngineWebXml);
        break;
      case "api-config":
        processApiConfigNode(elt, appEngineWebXml);
        break;
      case "class-loader-config":
        processClassLoaderConfig(elt, appEngineWebXml);
        break;
      case "url-stream-handler":
        processUrlStreamHandler(elt, appEngineWebXml);
        break;
      case "use-google-connector-j":
        processUseGoogleConnectorJNode(elt, appEngineWebXml);
        break;
      case "pagespeed":
        logger.warning(
            "app_id "
                + appEngineWebXml.getAppId()
                + " has <pagespeed> in appengine-web.xml file, ignored.");
        break;
      case "staging":
        processStagingNode(elt, appEngineWebXml);
        break;
      case "vpc-access-connector":
        processVpcAccessConnector(elt, appEngineWebXml);
        break;
      case "service-account":
        processServiceAccountNode(elt, appEngineWebXml);
        break;
      default:
        throw new AppEngineConfigException("Unrecognized element <" + elementName + ">");
    }
  }

  private void processApplicationNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setAppId(XmlUtils.getText(node));
  }

  private void processEntrypointNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setEntrypoint(XmlUtils.getText(node));
  }

  private void processRuntimeChannelNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setRuntimeChannel(XmlUtils.getText(node));
  }

  private void processPublicRootNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setPublicRoot(XmlUtils.getText(node));
  }

  private void processVersionNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setMajorVersionId(XmlUtils.getText(node));
  }

  private void processRuntimeNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setRuntime(XmlUtils.getText(node));
  }

  private void processModuleNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setModule(XmlUtils.getText(node));
  }

  private void processServiceNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setService(XmlUtils.getText(node));
  }

  private void processInstanceClassNode(Element node, AppEngineWebXml appEngineWebXml) {

    appEngineWebXml.setInstanceClass(XmlUtils.getText(node));
  }

  private void processServiceAccountNode(Element node, AppEngineWebXml appEngineWebXml) {

    appEngineWebXml.setServiceAccount(XmlUtils.getText(node));
  }

  private String getChildNodeText(Element parentNode, String childTag) {
    Element node = XmlUtils.getOptionalChildElement(parentNode, childTag);
    if (node == null) {
      return null;
    }
    String result = XmlUtils.getText(node);
    return result.isEmpty() ? null : result;
  }

  private Integer getChildNodePositiveInteger(Element parentNode, String childTag) {
    Integer result = getChildNodeNonNegativeInteger(parentNode, childTag);
    if (result != null && result == 0) {
      throw new AppEngineConfigException(childTag + " should only contain positive integers.");
    }
    return result;
  }

  private Integer getChildNodeNonNegativeInteger(Element parentNode, String childTag) {
    Integer result = null;
    Element node = XmlUtils.getOptionalChildElement(parentNode, childTag);
    if (node != null) {
      String trimmedText = XmlUtils.getText(node);
      if (!trimmedText.isEmpty()) {
        try {
          result = Integer.parseInt(trimmedText);
        } catch (NumberFormatException ex) {
          throw new AppEngineConfigException(childTag + " should only contain integers.");
        }
        if (result < 0) {
          throw new AppEngineConfigException(
              childTag + " should only contain non-negative integers.");
        }
      }
    }
    return result;
  }

  private Double getChildNodeDouble(Element parentNode, String childTag) {
    Double result = null;
    Element node = XmlUtils.getOptionalChildElement(parentNode, childTag);
    if (node != null) {
      String trimmedText = XmlUtils.getText(node);
      if (!trimmedText.isEmpty()) {
        try {
          result = Double.parseDouble(trimmedText);
        } catch (NumberFormatException ex) {
          throw new AppEngineConfigException(childTag + " should only contain doubles.");
        } catch (NullPointerException ex) {
          // This shouldn't happen because of the check above. However, Keep it here for
          // completeness of catching exceptions.
          throw new AppEngineConfigException(childTag + " should NOT be empty.");
        }
      }
    }
    return result;
  }

  private void processAutomaticScalingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    AutomaticScaling automaticScaling = appEngineWebXml.getAutomaticScaling();
    automaticScaling.setMinPendingLatency(getChildNodeText(settingsNode, "min-pending-latency"));
    automaticScaling.setMaxPendingLatency(getChildNodeText(settingsNode, "max-pending-latency"));
    automaticScaling.setMinIdleInstances(getChildNodeText(settingsNode, "min-idle-instances"));
    automaticScaling.setMaxIdleInstances(getChildNodeText(settingsNode, "max-idle-instances"));
    automaticScaling.setMaxInstances(getChildNodePositiveInteger(settingsNode, "max-instances"));
    automaticScaling.setMinInstances(getChildNodeNonNegativeInteger(settingsNode, "min-instances"));
    automaticScaling.setTargetCpuUtilization(
        getChildNodeDouble(settingsNode, "target-cpu-utilization"));
    automaticScaling.setTargetThroughputUtilization(
        getChildNodeDouble(settingsNode, "target-throughput-utilization"));

    automaticScaling.setMaxConcurrentRequests(
        getChildNodeText(settingsNode, "max-concurrent-requests"));
    automaticScaling.setMinNumInstances(
        getChildNodePositiveInteger(settingsNode, "min-num-instances"));
    automaticScaling.setMaxNumInstances(
        getChildNodePositiveInteger(settingsNode, "max-num-instances"));
    automaticScaling.setCoolDownPeriodSec(
        getChildNodePositiveInteger(settingsNode, "cool-down-period-sec"));
    processCpuUtilizationNode(settingsNode, automaticScaling);
    processCustomMetricsNode(settingsNode, automaticScaling);
    automaticScaling.setTargetNetworkSentBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-sent-bytes-per-sec"));
    automaticScaling.setTargetNetworkSentPacketsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-sent-packets-per-sec"));
    automaticScaling.setTargetNetworkReceivedBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-received-bytes-per-sec"));
    automaticScaling.setTargetNetworkReceivedPacketsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-network-received-packets-per-sec"));
    automaticScaling.setTargetDiskWriteBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-write-bytes-per-sec"));
    automaticScaling.setTargetDiskWriteOpsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-write-ops-per-sec"));
    automaticScaling.setTargetDiskReadBytesPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-read-bytes-per-sec"));
    automaticScaling.setTargetDiskReadOpsPerSec(
        getChildNodePositiveInteger(settingsNode, "target-disk-read-ops-per-sec"));
    automaticScaling.setTargetRequestCountPerSec(
        getChildNodePositiveInteger(settingsNode, "target-request-count-per-sec"));
    automaticScaling.setTargetConcurrentRequests(
        getChildNodePositiveInteger(settingsNode, "target-concurrent-requests"));
  }

  private void processCpuUtilizationNode(Element settingsNode, AutomaticScaling automaticScaling) {
    Element childNode = XmlUtils.getOptionalChildElement(settingsNode, "cpu-utilization");
    if (childNode != null) {
      CpuUtilization cpuUtilization = new CpuUtilization();
      Double targetUtilization = getChildNodeDouble(childNode, "target-utilization");
      if (targetUtilization != null) {
        if (targetUtilization <= 0 || targetUtilization > 1) {
          throw new AppEngineConfigException("target-utilization should be in range (0, 1].");
        }
        cpuUtilization.setTargetUtilization(targetUtilization);
      }

      cpuUtilization.setAggregationWindowLengthSec(
          getChildNodePositiveInteger(childNode, "aggregation-window-length-sec"));
      if (!cpuUtilization.isEmpty()) {
        automaticScaling.setCpuUtilization(cpuUtilization);
      }
    }
  }

  private void processCustomMetricsNode(Element settingsNode, AutomaticScaling automaticScaling) {
    List<CustomMetricUtilization> customMetrics = new ArrayList<>();

    Element node = XmlUtils.getOptionalChildElement(settingsNode, "custom-metrics");
    if (node == null) {
      return;
    }

    for (Element metric : getNodeIterable(node, "custom-metric")) {
      CustomMetricUtilization customMetric = new CustomMetricUtilization();

      final String metricName = getChildNodeText(metric, "metric-name");
      if (metricName == null || metricName.isEmpty()) {
        throw new AppEngineConfigException("metric-name must be defined.");
      }
      customMetric.setMetricName(metricName);

      final String targetType = getChildNodeText(metric, "target-type");
      if (targetType == null || targetType.isEmpty()) {
        throw new AppEngineConfigException("target-type must be defined.");
      }
      customMetric.setTargetType(targetType);

      final Double targetUtilization = getChildNodeDouble(metric, "target-utilization");
      if (targetUtilization != null) {
        if (targetUtilization <= 0) {
          throw new AppEngineConfigException("target-utilization must be positive.");
        }
        customMetric.setTargetUtilization(targetUtilization);
      }

      final Double singleInstanceAssignment =
          getChildNodeDouble(metric, "single-instance-assignment");
      if (singleInstanceAssignment != null) {
        if (singleInstanceAssignment <= 0) {
          throw new AppEngineConfigException("single-instance-assignment must be positive.");
        }
        customMetric.setSingleInstanceAssignment(singleInstanceAssignment);
      }

      String filter = getChildNodeText(metric, "filter");
      if (filter != null && !filter.isEmpty()) {
        customMetric.setFilter(filter);
      }

      customMetrics.add(customMetric);
    }

    automaticScaling.setCustomMetrics(customMetrics);
  }

  private void processManualScalingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    ManualScaling manualScaling = appEngineWebXml.getManualScaling();
    manualScaling.setInstances(getChildNodeText(settingsNode, "instances"));
  }

  private void processBasicScalingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    BasicScaling basicScaling = appEngineWebXml.getBasicScaling();
    basicScaling.setMaxInstances(getChildNodeText(settingsNode, "max-instances"));
    basicScaling.setIdleTimeout(getChildNodeText(settingsNode, "idle-timeout"));
  }

  private void processSslEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSslEnabled(getBooleanValue(node));
  }

  private void processSessionsEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setSessionsEnabled(getBooleanValue(node));
  }

  private void processAsyncSessionPersistenceNode(Element node, AppEngineWebXml appEngineWebXml) {
    boolean enabled = getBooleanAttributeValue(node, "enabled");
    appEngineWebXml.setAsyncSessionPersistence(enabled);
    String queueName = trim(node.getAttribute("queue-name"));
    if (queueName.equals("")) {
      queueName = null;
    }
    appEngineWebXml.setAsyncSessionPersistenceQueueName(queueName);
  }

  private void processPrecompilationEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setPrecompilationEnabled(getBooleanValue(node));
  }

  private void processWarmupRequestsEnabledNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setWarmupRequestsEnabled(getBooleanValue(node));
  }

  private void processThreadsafeNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setThreadsafe(getBooleanValue(node));
  }

  private void processAutoIdPolicyNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setAutoIdPolicy(XmlUtils.getText(node));
  }

  private void processCodeLockNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setCodeLock(getBooleanValue(node));
  }

  private void processVmNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUseVm(getBooleanValue(node));
  }

  private void processEnvNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setEnv(XmlUtils.getText(node));
  }

  private void processFilesetNode(Element node, AppEngineWebXml appEngineWebXml, FileType type) {
    for (Element includeNode : getNodeIterable(node, "include")) {
      String path = trim(includeNode.getAttribute("path"));
      if (path.equals("")) {
        path = null;
      }
      if (type == FileType.STATIC) {
        String expiration = trim(includeNode.getAttribute("expiration"));
        if (expiration.equals("")) {
          expiration = null;
        }
        AppEngineWebXml.StaticFileInclude staticFileInclude =
            appEngineWebXml.includeStaticPattern(path, expiration);

        Map<String, String> httpHeaders = staticFileInclude.getHttpHeaders();
        for (Element httpHeaderNode : getNodeIterable(includeNode, "http-header")) {
          String name = httpHeaderNode.getAttribute("name");
          String value = httpHeaderNode.getAttribute("value");

          if (httpHeaders.containsKey(name)) {
            throw new AppEngineConfigException("Two http-header elements have the same name.");
          }

          httpHeaders.put(name, value);
        }
      } else {
        appEngineWebXml.includeResourcePattern(path);
      }
    }

    for (Element excludeNode : getNodeIterable(node, "exclude")) {
      String path = trim(excludeNode.getAttribute("path"));
      if (type == FileType.STATIC) {
        appEngineWebXml.excludeStaticPattern(path);
      } else {
        appEngineWebXml.excludeResourcePattern(path);
      }
    }
  }

  private Iterable<Element> getNodeIterable(Element node, String filter) {
    return XmlUtils.getChildren(node, filter);
  }

  private void processSystemPropertiesNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element propertyNode : getNodeIterable(node, "property")) {
      String propertyName = trim(propertyNode.getAttribute("name"));
      String propertyValue = trim(propertyNode.getAttribute("value"));
      appEngineWebXml.addSystemProperty(propertyName, propertyValue);
    }
  }

  private void processBetaSettingsNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "setting")) {
      String name = trim(subNode.getAttribute("name"));
      String value = trim(subNode.getAttribute("value"));
      appEngineWebXml.addBetaSetting(name, value);
    }
  }

  private void processHealthCheckNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    HealthCheck healthCheck = new HealthCheck();

    String enableHealthCheck = trim(getChildNodeText(settingsNode, "enable-health-check"));
    if (enableHealthCheck != null && !enableHealthCheck.isEmpty()) {
      healthCheck.setEnableHealthCheck(toBoolean(enableHealthCheck));
    }

    Integer checkIntervalSec = getChildNodePositiveInteger(settingsNode, "check-interval-sec");
    if (checkIntervalSec != null) {
      healthCheck.setCheckIntervalSec(checkIntervalSec);
    }

    Integer timeoutSec = getChildNodePositiveInteger(settingsNode, "timeout-sec");
    if (timeoutSec != null) {
      healthCheck.setTimeoutSec(timeoutSec);
    }

    Integer unhealthyThreshold = getChildNodePositiveInteger(settingsNode, "unhealthy-threshold");
    if (unhealthyThreshold != null) {
      healthCheck.setUnhealthyThreshold(unhealthyThreshold);
    }

    Integer healthyThreshold = getChildNodePositiveInteger(settingsNode, "healthy-threshold");
    if (healthyThreshold != null) {
      healthCheck.setHealthyThreshold(healthyThreshold);
    }

    Integer restartThreshold = getChildNodePositiveInteger(settingsNode, "restart-threshold");
    if (restartThreshold != null) {
      healthCheck.setRestartThreshold(restartThreshold);
    }

    String host = getChildNodeText(settingsNode, "host");
    if (host != null) {
      healthCheck.setHost(host);
    }

    appEngineWebXml.setHealthCheck(healthCheck);
  }

  private void processLivenessCheckNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    LivenessCheck livenessCheck = new LivenessCheck();

    String path = getChildNodeText(settingsNode, "path");
    if (path != null) {
      livenessCheck.setPath(path);
    }

    Integer checkIntervalSec = getChildNodePositiveInteger(settingsNode, "check-interval-sec");
    if (checkIntervalSec != null) {
      livenessCheck.setCheckIntervalSec(checkIntervalSec);
    }

    Integer timeoutSec = getChildNodePositiveInteger(settingsNode, "timeout-sec");
    if (timeoutSec != null) {
      livenessCheck.setTimeoutSec(timeoutSec);
    }

    Integer failureThreshold = getChildNodePositiveInteger(settingsNode, "failure-threshold");
    if (failureThreshold != null) {
      livenessCheck.setFailureThreshold(failureThreshold);
    }

    Integer successThreshold = getChildNodePositiveInteger(settingsNode, "success-threshold");
    if (successThreshold != null) {
      livenessCheck.setSuccessThreshold(successThreshold);
    }

    String host = getChildNodeText(settingsNode, "host");
    if (host != null) {
      livenessCheck.setHost(host);
    }

    Integer initialDelaySec = getChildNodePositiveInteger(settingsNode, "initial-delay-sec");
    if (initialDelaySec != null) {
      livenessCheck.setInitialDelaySec(initialDelaySec);
    }

    appEngineWebXml.setLivenessCheck(livenessCheck);
  }

  private void processReadinessCheckNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    ReadinessCheck readinessCheck = new ReadinessCheck();

    String path = getChildNodeText(settingsNode, "path");
    if (path != null) {
      readinessCheck.setPath(path);
    }

    Integer checkIntervalSec = getChildNodePositiveInteger(settingsNode, "check-interval-sec");
    if (checkIntervalSec != null) {
      readinessCheck.setCheckIntervalSec(checkIntervalSec);
    }

    Integer timeoutSec = getChildNodePositiveInteger(settingsNode, "timeout-sec");
    if (timeoutSec != null) {
      readinessCheck.setTimeoutSec(timeoutSec);
    }

    Integer failureThreshold = getChildNodePositiveInteger(settingsNode, "failure-threshold");
    if (failureThreshold != null) {
      readinessCheck.setFailureThreshold(failureThreshold);
    }

    Integer successThreshold = getChildNodePositiveInteger(settingsNode, "success-threshold");
    if (successThreshold != null) {
      readinessCheck.setSuccessThreshold(successThreshold);
    }

    String host = getChildNodeText(settingsNode, "host");
    if (host != null) {
      readinessCheck.setHost(host);
    }

    Integer appStartTimeoutSec = getChildNodePositiveInteger(settingsNode, "app-start-timeout-sec");
    if (appStartTimeoutSec != null) {
      readinessCheck.setAppStartTimeoutSec(appStartTimeoutSec);
    }

    appEngineWebXml.setReadinessCheck(readinessCheck);
  }

  private void processResourcesNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    Resources resources = appEngineWebXml.getResources();
    Double cpu = getChildNodeDouble(settingsNode, "cpu");
    if (cpu != null) {
      resources.setCpu(cpu);
    }
    Double memory_gb = getChildNodeDouble(settingsNode, "memory-gb");
    if (memory_gb != null) {
      resources.setMemoryGb(memory_gb);
    }
    Integer disk_size_gb = getChildNodePositiveInteger(settingsNode, "disk-size-gb");
    if (disk_size_gb != null) {
      resources.setDiskSizeGb(disk_size_gb);
    }
  }

  private void processNetworkNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    Network network = appEngineWebXml.getNetwork();
    String instance_tag = trim(getChildNodeText(settingsNode, "instance-tag"));
    if (instance_tag != null && !instance_tag.isEmpty()) {
      network.setInstanceTag(instance_tag);
    }
    for (Element subNode : getNodeIterable(settingsNode, "forwarded-port")) {
      String forwardedPort = XmlUtils.getText(subNode);
      network.addForwardedPort(forwardedPort);
    }
    String name = trim(getChildNodeText(settingsNode, "name"));
    if (name != null && !name.isEmpty()) {
      network.setName(name);
    }
    String subnetworkName = trim(getChildNodeText(settingsNode, "subnetwork-name"));
    if (subnetworkName != null && !subnetworkName.isEmpty()) {
      network.setSubnetworkName(subnetworkName);
    }
    String sessionAffinity = trim(getChildNodeText(settingsNode, "session-affinity"));
    if (sessionAffinity != null && !sessionAffinity.isEmpty()) {
      network.setSessionAffinity(toBoolean(sessionAffinity));
    }
  }

  private void processEnvironmentVariablesNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "env-var")) {
      String propertyName = trim(subNode.getAttribute("name"));
      String propertyValue = trim(subNode.getAttribute("value"));
      appEngineWebXml.addEnvironmentVariable(propertyName, propertyValue);
    }
  }

  private void processBuildEnvironmentVariablesNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "build-env-var")) {
      String propertyName = trim(subNode.getAttribute("name"));
      String propertyValue = trim(subNode.getAttribute("value"));
      appEngineWebXml.addBuildEnvironmentVariable(propertyName, propertyValue);
    }
  }

  private void processPermissionsNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "permission")) {
      String className = trim(subNode.getAttribute("class"));
      if (className.equals("")) {
        className = null;
      }
      String name = trim(subNode.getAttribute("name"));
      if (name.equals("")) {
        name = null;
      }
      String actions = trim(subNode.getAttribute("actions"));
      if (actions.equals("")) {
        actions = null;
      }
      appEngineWebXml.addUserPermission(className, name, actions);
    }
  }

  private void processInboundServicesNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "service")) {
      String service = XmlUtils.getText(subNode);
      appEngineWebXml.addInboundService(service);
    }
  }

  private void processAdminConsoleNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "page")) {
      String name = trim(subNode.getAttribute("name"));
      String url = trim(subNode.getAttribute("url"));
      appEngineWebXml.addAdminConsolePage(AdminConsolePage.of(name, url));
    }
  }

  private void processErrorHandlerNode(Element node, AppEngineWebXml appEngineWebXml) {
    for (Element subNode : getNodeIterable(node, "handler")) {
      String file = trim(subNode.getAttribute("file"));
      if (file.equals("")) {
        file = null;
      }
      String errorCode = trim(subNode.getAttribute("error-code"));
      if (errorCode.equals("")) {
        errorCode = null;
      }
      appEngineWebXml.addErrorHandler(ErrorHandler.of(file, errorCode));
    }
  }

  private void processApiConfigNode(Element node, AppEngineWebXml appEngineWebXml) {
    String servlet = trim(node.getAttribute("servlet-class"));
    String url = trim(node.getAttribute("url-pattern"));
    appEngineWebXml.setApiConfig(ApiConfig.of(servlet, url));

    for (Element subNode : getNodeIterable(node, "endpoint-servlet-mapping-id")) {
      String id = XmlUtils.getText(subNode);
      if (!id.isEmpty()) {
        appEngineWebXml.addApiEndpoint(id);
      }
    }
  }
  private void processClassLoaderConfig(Element node, AppEngineWebXml appEngineWebXml) {
    ClassLoaderConfig config = new ClassLoaderConfig();
    appEngineWebXml.setClassLoaderConfig(config);
    for (Element subNode : getNodeIterable(node, "priority-specifier")) {
      processClassPathPrioritySpecifier(subNode, config);
    }
  }

  private void processClassPathPrioritySpecifier(Element node, ClassLoaderConfig config) {
    PrioritySpecifierEntry entry = new PrioritySpecifierEntry();
    entry.setFilename(XmlUtils.getAttributeOrNull(node, "filename"));
    entry.setPriority(XmlUtils.getAttributeOrNull(node, "priority"));
    entry.checkClassLoaderConfig();
    config.add(entry);
  }

  private void processVpcAccessConnector(Element node, AppEngineWebXml appEngineWebXml) {
    String name = getChildNodeText(node, "name");
    if (name == null) {
      throw new AppEngineConfigException(
          "The <vpc-access-connector> element should have a name sub element.");
    }
    VpcAccessConnector.Builder connectorBuilder = VpcAccessConnector.builderFor(name);
    String egressSetting = getChildNodeText(node, "egress-setting");
    if (egressSetting != null) {
      connectorBuilder.setEgressSetting(egressSetting);
    }
    appEngineWebXml.setVpcAccessConnector(connectorBuilder.build());
  }

  private void processStagingNode(Element settingsNode, AppEngineWebXml appEngineWebXml) {
    StagingOptions.Builder builder = StagingOptions.builder();

    String enableJarSplitting = getChildNodeText(settingsNode, "enable-jar-splitting");
    if (enableJarSplitting != null) {
      builder.setSplitJarFiles(Optional.of(toBoolean(enableJarSplitting)));
    }

    String jarSplittingExcludes = getChildNodeText(settingsNode, "jar-splitting-excludes");
    if (jarSplittingExcludes != null) {
      ImmutableSortedSet<String> jarSplittingExcludeSuffixes =
          ImmutableSortedSet.copyOf(jarSplittingExcludes.split(","));
      builder.setSplitJarFilesExcludes(Optional.of(jarSplittingExcludeSuffixes));
    }

    String disableJarJsps = getChildNodeText(settingsNode, "disable-jar-jsps");
    if (disableJarJsps != null) {
      builder.setJarJsps(Optional.of(!toBoolean(disableJarJsps)));
    }

    String enableJarClasses = getChildNodeText(settingsNode, "enable-jar-classes");
    if (enableJarClasses != null) {
      builder.setJarClasses(Optional.of(toBoolean(enableJarClasses)));
    }

    String deleteJsps = getChildNodeText(settingsNode, "delete-jsps");
    if (deleteJsps != null) {
      builder.setDeleteJsps(Optional.of(toBoolean(deleteJsps)));
    }

    String compileEncoding = getChildNodeText(settingsNode, "compile-encoding");
    if (compileEncoding != null) {
      builder.setCompileEncoding(Optional.of(compileEncoding));
    }
    appEngineWebXml.setStagingOptions(builder.build());
  }

  private void processUrlStreamHandler(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUrlStreamHandlerType(XmlUtils.getText(node));
  }

  private boolean getBooleanValue(Element node) {
    return toBoolean(XmlUtils.getText(node));
  }

  private boolean getBooleanAttributeValue(Element node, String attribute) {
    return toBoolean(node.getAttribute(attribute));
  }

  private boolean toBoolean(String value) {
    value = value.trim();
    return (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private String trim(String attribute) {
    return attribute == null ? null : attribute.trim();
  }

  private void processUseGoogleConnectorJNode(Element node, AppEngineWebXml appEngineWebXml) {
    appEngineWebXml.setUseGoogleConnectorJ(getBooleanValue(node));
  }
}
