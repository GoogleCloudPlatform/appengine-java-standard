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

package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId.Location;
import com.google.common.collect.ImmutableBiMap;
import org.jspecify.annotations.Nullable;

class LocationMapper {

  private static final ImmutableBiMap<String, Location> locationByPartitionId =
      ImmutableBiMap.<String, Location>builder()
          .put("b", Location.ASIA_NORTHEAST1)
          .put("d", Location.US_EAST4)
          .put("e", Location.EUROPE_WEST)
          .put("f", Location.AUSTRALIA_SOUTHEAST1)
          .put("g", Location.EUROPE_WEST1)
          .put("h", Location.EUROPE_WEST3)
          .put("p", Location.US_EAST1)
          .put("s", Location.US_CENTRAL)
          .buildOrThrow();

  @Nullable
  static Location getLocation(String partitionId) {
    return locationByPartitionId.get(partitionId);
  }

  @Nullable
  static String getPartitionId(Location location) {
    return locationByPartitionId.inverse().get(location);
  }
}
