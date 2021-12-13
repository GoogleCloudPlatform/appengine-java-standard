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

import com.google.appengine.api.search.checkers.GeoPointChecker;
import com.google.apphosting.api.search.DocumentPb.FieldValue.Geo;

/**
 * Represents a point on the Earth's surface, in latitude and longitude
 * coordinates.
 *
 */
public class GeoPoint {
  private final double latitude;
  private final double longitude;

  /**
   * Constructs a {@link GeoPoint} from a given latitude and longitude.
   *
   * @param latitude the angle between the equatorial plane and a line
   * that passes through the GeoPoint
   * @param longitude is the angle east or west from a reference meridian
   * to another meridian that passes through the GeoPoint
   */
  public GeoPoint(double latitude, double longitude) {
    this.latitude = GeoPointChecker.checkLatitude(latitude);
    this.longitude = GeoPointChecker.checkLongitude(longitude);
  }

  /**
   * @return the angle between the equatorial plan and a line that passes
   * through the GeoPoint
   */
  public double getLatitude() {
    return latitude;
  }

  /**
   * @return the angle east or west from a reference meridian
   * to another meridian that passes through the GeoPoint
   */
  public double getLongitude() {
    return longitude;
  }

  /**
   * Copies a {@link GeoPoint} object into a {@link Geo}
   * protocol buffer.
   *
   * @return the Geo protocol buffer copy of this GeoPoint object
   */
  Geo copyToProtocolBuffer() {
    Geo geoPb = Geo.newBuilder()
        .setLat(getLatitude())
        .setLng(getLongitude())
        .build();
    return geoPb;
  }

  /**
   * @return a {@link GeoPoint} constructed from the proto buf Geo
   */
  static GeoPoint newGeoPoint(Geo geoPb) {
    return new GeoPoint(geoPb.getLat(), geoPb.getLng());
  }

  @Override
  public String toString() {
    return String.format("GeoPoint(latitude=%f, longitude=%f)",
        getLatitude(),
        getLongitude());
  }
}
