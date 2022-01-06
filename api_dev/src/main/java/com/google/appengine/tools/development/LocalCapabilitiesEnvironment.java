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

package com.google.appengine.tools.development;

import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 */
public class LocalCapabilitiesEnvironment {

  /**
   * delimiter added to get a unique key from a package name and a capability name
   */
  private static final String KEY_DELIMITER = ".";

  /**
   * prefix used to detect which properties are relevant to the Capability Service. All property
   * keys not starting with this prefix will be ignored in the initialization on the service.
   */
  public static final String KEY_PREFIX = "capability.status.";

  // see <internal>
  // &q=register_methods.cc&l=62
  public static final ImmutableSet<String> DATASTORE_WRITE_RPCS = new ImmutableSet.Builder<String>()
      .add("Delete")
      .add("Put")
      .add("Touch")
      .add("Commit")
      .add("CreateIndex")
      .add("UpdateIndex")
      .add("DeleteIndex")
      .add("AddActions")
      .add("AllocateIds")
      .build();

  // The services that are supported by the Capabilities API. See
  // https://cloud.google.com/appengine/docs/standard/java/capabilities/#Java_Supported_capabilities
  public static final ImmutableList<Capability> DEFAULT_ENABLED_SERVICES =
      ImmutableList.of(
          Capability.BLOBSTORE,
          Capability.DATASTORE,
          Capability.DATASTORE_WRITE,
          Capability.IMAGES,
          Capability.MAIL,
          Capability.MEMCACHE,
          Capability.TASKQUEUE,
          Capability.URL_FETCH,
          Capability.XMPP);

  /**
   * a map of capability to status. The key to the map is the concatenation of the prefix, the
   * package name, delimiter and capability name. the corresponding value holds the SummaryStatus
   * desired for the capability
   */
  Map<String, CapabilityStatus> capabilitiesStatus = Collections
      .synchronizedMap(new HashMap<String, CapabilityStatus>());

  /**
   * initialize a LocalCapabilitiesEnvironment with all the properties that
   * start with the correct KEY_PREFIX
   * @param properties setting up some capability states
   */
  public LocalCapabilitiesEnvironment(Properties properties) {
    for (Capability capability : DEFAULT_ENABLED_SERVICES) {
      capabilitiesStatus.put(
          geCapabilityPropertyKey(capability.getPackageName(), capability.getName()),
          CapabilityStatus.ENABLED);
    }

    for (String capabilityName : properties.stringPropertyNames()) {
      if (!capabilityName.startsWith(KEY_PREFIX)) {
        continue;
      }
      String status = properties.getProperty(capabilityName);
      CapabilityStatus s = CapabilityStatus.valueOf(status);
      capabilitiesStatus.put(capabilityName, s);

    }
  }

  /*
  * Calculate a unique key based on a package name and capability name
  * @param packageName name of the package associated with this capability
  * @param capability  the name associated with this capability (often "*")
  * @return a unique key used to store the given SummaryStatus in a map
  */
  public static String geCapabilityPropertyKey(String packageName, String capability) {
    return KEY_PREFIX + packageName + KEY_DELIMITER + capability;
  }

  /**
   * modify the capability status based on the capability property name property names that do not
   * start with  "capability.status" are ignored
   *
   * @param capabilityName property name with prefix as "capability.status.memcache"
   * @param status         required status for the given capability
   */
  public void setCapabilitiesStatus(String capabilityName, CapabilityStatus status) {
    if (!capabilityName.startsWith(KEY_PREFIX)) {
      return;
    }

    if (status == null) {
      throw new IllegalArgumentException("Capability Status: " + " is not known");
    }
    // TODO what about        status = SummaryStatus.SCHEDULED_FUTURE;
    // and  long timeUntilScheduled?

    capabilitiesStatus.put(capabilityName, status);
  }

  /**
   * @param capabilityName name fo the capability (for ex "datastore_v3")
   * @param methodName     RPC method name (for example "Get")
   * @return the capability status for the given method
   */
  public CapabilityStatus getStatusFromMethodName(String capabilityName, String methodName) {
    CapabilityStatus status;
    if (capabilityName.equals("datastore_v3")) {
      status = capabilitiesStatus.get(geCapabilityPropertyKey(capabilityName, "write"));
      if ((status != null) && (!status.equals(CapabilityStatus.ENABLED))) {
        if (DATASTORE_WRITE_RPCS.contains(methodName)) {
          return status;
        }
      }
    }
    status = capabilitiesStatus.get(geCapabilityPropertyKey(capabilityName, "*"));
    if (status != null) {
      return status;
    } else {
      return CapabilityStatus.ENABLED;
    }
  }

  /**
   * @param packageName package Name name of the capability (for ex "datastore_v3")
   * @param capabilityName name of the capability (for example, "write" or "*")
   * @return the capability status for the given capability
   */
  public CapabilityStatus getStatusFromCapabilityName(String packageName, String capabilityName) {
    CapabilityStatus status =
        capabilitiesStatus.get(geCapabilityPropertyKey(packageName, capabilityName));
    return status != null ? status : CapabilityStatus.UNKNOWN;
  }
}
