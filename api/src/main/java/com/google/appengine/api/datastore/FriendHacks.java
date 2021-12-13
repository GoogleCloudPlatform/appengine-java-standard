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

/**
 * Provides limited access to unlaunched (package-private) methods and classes from outside this
 * package. This class is not bundled with the API jar. As features launch, code should be updated
 * to use the public interfaces instead of the contents of this class.
 */
public class FriendHacks {

  /**
   * Create an {@link com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId}
   * from an app ID string.
   *
   * @throws IllegalArgumentException if {@code appIdString} cannot be parsed or it does not
   *     correspond to a known {@link
   *     com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId.Location}
   */
  public static com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId
      appIdFromString(String appIdString) {
    String[] parts = appIdString.split("~");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid app ID string: " + appIdString);
    }
    com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId.Location location =
        LocationMapper.getLocation(parts[0]);
    if (location == null) {
      throw new IllegalArgumentException("Unknown location: " + parts[0]);
    }
    return com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId.create(
        location, parts[1]);
  }
}
