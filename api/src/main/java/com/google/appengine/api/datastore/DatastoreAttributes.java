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
 * Attributes of a datastore.
 *
 */
public final class DatastoreAttributes {
  /**
   * Indicates the type of datastore being used. Currently always returns HIGH_REPLICATION.
   *
   */
  public enum DatastoreType {
    UNKNOWN,
    MASTER_SLAVE,
    HIGH_REPLICATION,
  }

  private final DatastoreType datastoreType;

  DatastoreAttributes() {
    datastoreType = DatastoreType.HIGH_REPLICATION;
  }

  /**
   * Gets the datastore type.
   *
   * <p>Only guaranteed to return something other than {@link DatastoreType#UNKNOWN} when running in
   * production and querying the current app.
   */
  public DatastoreType getDatastoreType() {
    return datastoreType;
  }
}
