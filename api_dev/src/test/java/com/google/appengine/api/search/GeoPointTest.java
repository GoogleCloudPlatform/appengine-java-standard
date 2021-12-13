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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.apphosting.api.search.DocumentPb.FieldValue.Geo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@link GeoPoint}.
 *
 */
@RunWith(JUnit4.class)
public class GeoPointTest {

  @Test
  public void testRanges() throws Exception {
    new GeoPoint(0.0, 0.0);
    new GeoPoint(-90.0, 0.0);
    new GeoPoint(-90.0, 180.0);
    new GeoPoint(-90.0, -180.0);
    new GeoPoint(90.0, 0.0);
    new GeoPoint(90.0, 180.0);
    new GeoPoint(90.0, -180.0);

    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(-90.1, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(90.1, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, -180.1));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, 180.1));
  }

  @Test
  public void testCopyToProtocolBuffer() throws Exception {
    GeoPoint geoPoint = new GeoPoint(-33.84, 151.26);
    Geo geoPb = geoPoint.copyToProtocolBuffer();
    assertThat(geoPb.getLat()).isEqualTo(-33.84);
    assertThat(geoPb.getLng()).isEqualTo(151.26);
  }

  @Test
  public void testCopyFromProtocolBuffer() throws Exception {
    Geo.Builder builder = Geo.newBuilder();
    builder.setLat(-33.84);
    builder.setLng(151.26);
    GeoPoint geoPoint = GeoPoint.newGeoPoint(builder.build());
    assertThat(geoPoint.getLatitude()).isEqualTo(-33.84);
    assertThat(geoPoint.getLongitude()).isEqualTo(151.26);
  }

  @Test
  public void testToString() throws Exception {
    GeoPoint geoPoint = new GeoPoint(-33.84, 151.26);
    assertThat(geoPoint.getLatitude()).isEqualTo(-33.84);
    assertThat(geoPoint.getLongitude()).isEqualTo(151.26);
    assertThat(geoPoint.toString())
        .isEqualTo("GeoPoint(latitude=-33.840000, longitude=151.260000)");
  }
}
