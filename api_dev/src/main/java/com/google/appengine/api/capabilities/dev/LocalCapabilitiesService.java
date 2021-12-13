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

package com.google.appengine.api.capabilities.dev;

import com.google.appengine.api.capabilities.CapabilitiesPb.CapabilityConfig;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledRequest;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse.SummaryStatus;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalCapabilitiesEnvironment;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Java stub implementation of the capabilities api backend.
 *
 */
@AutoService(LocalRpcService.class)
public class LocalCapabilitiesService extends AbstractLocalRpcService {

  /** Name mapping between the 2 enums: CapabilityStatus and SummaryStatus */
  static final ImmutableMap<String, CapabilityStatus> CAPABILITY_STATUS_TO_SUMMARY_STATUS =
      new ImmutableMap.Builder<String, CapabilityStatus>()
          .put("DEFAULT", CapabilityStatus.ENABLED)
          .put(CapabilityStatus.ENABLED.name(), CapabilityStatus.ENABLED)
          .put(CapabilityStatus.DISABLED.name(), CapabilityStatus.DISABLED)
          .put(CapabilityStatus.UNKNOWN.name(), CapabilityStatus.UNKNOWN)
          .put(
              CapabilityStatus.SCHEDULED_MAINTENANCE.name(), CapabilityStatus.SCHEDULED_MAINTENANCE)
          // TODO handle FUTURE date value in the next CL
          .put(
              CapabilityStatus.SCHEDULED_MAINTENANCE.name() + ".future",
              CapabilityStatus.SCHEDULED_MAINTENANCE)
          .build();

  static final ImmutableMap<CapabilityStatus, CapabilityConfig.Status>
      CAPABILITY_STATUS_TO_CAPABILITY_CONFIG_STATUS =
          new ImmutableMap.Builder<CapabilityStatus, CapabilityConfig.Status>()
              .put(CapabilityStatus.ENABLED, CapabilityConfig.Status.ENABLED)
              .put(CapabilityStatus.SCHEDULED_MAINTENANCE, CapabilityConfig.Status.SCHEDULED)
              .put(CapabilityStatus.DISABLED, CapabilityConfig.Status.DISABLED)
              .put(CapabilityStatus.UNKNOWN, CapabilityConfig.Status.UNKNOWN)
              .build();

  private LocalCapabilitiesEnvironment localCapabilitiesEnvironment = null;

  /**
   * The package name for this service.
   */
  public static final String PACKAGE = "capability_service";


  @Override
  public String getPackage() {
    return PACKAGE;
  }

  /** @throws IllegalArgumentException If a property key is not known */
  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    // cache the localCapabilitiesEnvironment
    this.localCapabilitiesEnvironment = context.getLocalCapabilitiesEnvironment();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (!entry.getKey().startsWith(LocalCapabilitiesEnvironment.KEY_PREFIX)) {
        continue;
      }
      String value = entry.getValue();
      CapabilityStatus status = CAPABILITY_STATUS_TO_SUMMARY_STATUS
          .get(value);
      if (status == null) {
        throw new IllegalArgumentException("Capability Status: " + value + " is not known");
      }

      setCapabilitiesStatusJavaStub(entry.getKey(), status);
    }
  }

  public void setCapabilitiesStatusJavaStub(String service, CapabilityStatus status) {
    // TODO what about        status = SummaryStatus.SCHEDULED_FUTURE;
    // and  long timeUntilScheduled?
    localCapabilitiesEnvironment.setCapabilitiesStatus(service, status);
  }

  @Override
  public void start() {

  }

  @Override
  public void stop() {

  }

  /**
   * Implementation of RPC IsEnabled. Everything is enabled in the local dev.
   *
   * @param status  RPC status
   * @param request {@link IsEnabledRequest}
   * @return {@link IsEnabledResponse}
   */
  public IsEnabledResponse isEnabled(Status status, IsEnabledRequest request) {
    IsEnabledResponse.Builder builder = IsEnabledResponse.newBuilder();
    String packageName = request.getPackage();
    String capability = request.getCapability(0);
    builder.setSummaryStatus(getStatus(packageName, capability));
    builder.setTimeUntilScheduled(0);
    return builder.build();
  }

  /**
   * Determine the status for a given capability
   *
   * @param packageName name of the package associated with this capability
   * @param capability  the name associated with this capability (often "*")
   * @return the test mode status for this capability
   */
  protected SummaryStatus getStatus(String packageName, String capability) {
    CapabilityStatus status = localCapabilitiesEnvironment
        .getStatusFromCapabilityName(packageName, capability);
    if (status == CapabilityStatus.SCHEDULED_MAINTENANCE) {
      return SummaryStatus.SCHEDULED_NOW;
    } else {
      return SummaryStatus.valueOf(status.name());
    }
  }

  /**
   * @return the current LocalCapabilitiesEnvironment
   */
  public LocalCapabilitiesEnvironment getLocalCapabilitiesEnvironment() {
    return localCapabilitiesEnvironment;
  }
  /*
  * Calculate a unique key based on a package name and capability name
  * @param packageName name of the package associated with this capability
  * @param capability  the name associated with this capability (often "*")
  * @return a unique key used to store the given SummaryStatus in a map
  */
  public static String geCapabilityPropertyKey(String packageName, String capability) {
    // delegate to the LocalCapabilitiesEnvironment implementation
    return LocalCapabilitiesEnvironment.geCapabilityPropertyKey(packageName, capability);
  }

}
