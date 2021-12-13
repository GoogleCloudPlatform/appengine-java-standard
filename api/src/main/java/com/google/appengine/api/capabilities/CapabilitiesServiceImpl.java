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

package com.google.appengine.api.capabilities;

import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledRequest;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Implementation for {@link CapabilitiesService}.
 *
 */
class CapabilitiesServiceImpl implements CapabilitiesService {
  static final String PACKAGE_NAME = "capability_service";
  static final String METHOD_NAME = "IsEnabled";

  @Override
  public CapabilityState getStatus(Capability capability) {
    if (capability.equals(Capability.DATASTORE_WRITE)) {
      return queryCapabilityService(capability);
    } else {
      return new CapabilityState(capability, CapabilityStatus.ENABLED);
    }
  }

  private static CapabilityState queryCapabilityService(Capability capability) {
    IsEnabledRequest.Builder builder = CapabilityServicePb.IsEnabledRequest.newBuilder();
    builder.setPackage(capability.getPackageName());
    builder.addCapability(capability.getName());
    IsEnabledRequest request = builder.build();
    byte[] responseBytes =
        ApiProxy.makeSyncCall(PACKAGE_NAME, METHOD_NAME, request.toByteArray());

    IsEnabledResponse response;
    try {
      response = CapabilityServicePb.IsEnabledResponse.parseFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ApiProxy.ArgumentException(PACKAGE_NAME, METHOD_NAME);
    }
    CapabilityStatus statusValue;
    switch (response.getSummaryStatus()) {
      case ENABLED:
        statusValue = CapabilityStatus.ENABLED;
        break;
      case DISABLED:
        statusValue = CapabilityStatus.DISABLED;
        break;
      default:
        statusValue = CapabilityStatus.UNKNOWN;
        break;
    }
    return new CapabilityState(capability, statusValue);
  }
}
