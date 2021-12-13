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

import static com.google.appengine.api.datastore.dev.DefaultHighRepJobPolicy.UNAPPLIED_JOB_PERCENTAGE_PROPERTY;
import static com.google.appengine.api.datastore.dev.LocalDatastoreService.AUTO_ID_ALLOCATION_POLICY_PROPERTY;
import static com.google.appengine.api.datastore.dev.LocalDatastoreService.AutoIdAllocationPolicy.SCATTERED;
import static com.google.appengine.api.datastore.dev.LocalDatastoreService.HIGH_REP_JOB_POLICY_CLASS_PROPERTY;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

/**
 * Manage {@link DevAppServer} local datastore service properties.
 *
 * This permits applying defaults specific to the local datastore run in the
 * {@link DevAppServer} or in {@link DevAppServer} integration tests. These
 * defaults do not apply to the datastore service unit test config.
 *
 */
public class DevAppServerDatastorePropertyHelper {

  private DevAppServerDatastorePropertyHelper() {
  }

  private static final DevAppServerDatastorePropertyHelper HELPER =
      new DevAppServerDatastorePropertyHelper();

  private class DatastoreProperty {
    private final String property;
    private final String defaultValue;

    DatastoreProperty(String property, String value) {
      this.property = property;
      this.defaultValue = value;
    }

    boolean isConfigured(Map<String, String> properties) {
      return properties.get(property) != null;
    }

    void maybeApplyDefault(Map<String, String> properties) {
      if (!isConfigured(properties)) {
        properties.put(property, defaultValue);
      }
    }
  }

  private final List<DatastoreProperty> defaultDatastoreProperties =
      new ImmutableList.Builder<DatastoreProperty>()

      // The default consistency model for the DevAppServer local datastore
      // is HRD-like consistency with approx. 10% unapplied jobs.
      .add(new DatastoreProperty(UNAPPLIED_JOB_PERCENTAGE_PROPERTY, "10") {
        @Override
        boolean isConfigured(Map<String, String> properties) {
          return properties.get(UNAPPLIED_JOB_PERCENTAGE_PROPERTY) != null ||
              properties.get(HIGH_REP_JOB_POLICY_CLASS_PROPERTY) != null;
        }
      })

      // The DevAppServer local datastore defaults to scattered ids.
      .add(new DatastoreProperty(AUTO_ID_ALLOCATION_POLICY_PROPERTY, SCATTERED.toString()))
      .build();

  /**
   * Apply DevAppServer local datastore service property defaults where
   * properties are not already otherwise configured.
   */
  public static void setDefaultProperties(Map<String, String> serviceProperties) {
    checkNotNull(serviceProperties, "serviceProperties cannot be null");
    for (DatastoreProperty property : HELPER.defaultDatastoreProperties) {
      property.maybeApplyDefault(serviceProperties);
    }
  }
}
