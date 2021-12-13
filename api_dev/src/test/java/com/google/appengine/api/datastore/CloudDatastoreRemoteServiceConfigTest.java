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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId;
import com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId.Location;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CloudDatastoreRemoteServiceConfig}. Some of it is tested via {@link
 * DatastoreServiceGlobalConfig} instead.
 */
@RunWith(JUnit4.class)
public class CloudDatastoreRemoteServiceConfigTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void appId_NullLocation() {
    thrown.expect(NullPointerException.class);
    AppId.create(null, "project-id");
  }

  @Test
  public void appId_NullProjectId() {
    thrown.expect(NullPointerException.class);
    AppId.create(Location.US_CENTRAL, null);
  }

  @Test
  public void location_FromString() {
    assertThat(Location.fromString("us-central")).isEqualTo(Location.US_CENTRAL);
    assertThat(Location.fromString("us_central")).isEqualTo(Location.US_CENTRAL);
    assertThat(Location.fromString("US-CENTRAL")).isEqualTo(Location.US_CENTRAL);
    assertThat(Location.fromString("US_CENTRAL")).isEqualTo(Location.US_CENTRAL);
  }

  @Test
  public void location_FromString_UnknownLocation() {
    thrown.expect(IllegalArgumentException.class);
    Location.fromString("unknown-location");
  }

  @Test
  public void location_FromString_NullLocation() {
    thrown.expect(NullPointerException.class);
    Location.fromString(null);
  }
}
