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

package com.google.appengine.api.search.checkers;

import com.google.apphosting.api.search.DocumentPb;
import com.google.common.base.Preconditions;

/**
 * Provides checks for {@link com.google.appengine.api.search.GeoPoint}.
 *
 */
public class GeoPointChecker {

  public GeoPointChecker() {
  }

  /**
   * Checks whether a {@link com.google.appengine.api.search.GeoPoint} latitude is valid. The value
   * must be between -90.0 and 90.0 degrees.
   *
   * @param latitude the latitude to check
   * @return the checked latitude
   * @throws IllegalArgumentException if the latitude is out of range
   */
  public static double checkLatitude(double latitude) {
    Preconditions.checkArgument(
        SearchApiLimits.MAXIMUM_NEGATIVE_LATITUDE <= latitude
            && latitude <= SearchApiLimits.MAXIMUM_POSITIVE_LATITUDE,
        "latitude %s must be between %s and %s",
        latitude,
        SearchApiLimits.MAXIMUM_NEGATIVE_LATITUDE,
        SearchApiLimits.MAXIMUM_POSITIVE_LATITUDE);
    return latitude;
  }

  /**
   * Checks whether a {@link com.google.appengine.api.search.GeoPoint} longitude is valid. The value
   * must be between -180.0 and 180.0 degrees.
   *
   * @param longitude the longitude to check
   * @return the checked longitude
   * @throws IllegalArgumentException if the longitude is out of range
   */
  public static double checkLongitude(double longitude) {
    Preconditions.checkArgument(
        SearchApiLimits.MAXIMUM_NEGATIVE_LONGITUDE <= longitude
            && longitude <= SearchApiLimits.MAXIMUM_POSITIVE_LONGITUDE,
        "longitude %s must be between %s and %s",
        longitude,
        SearchApiLimits.MAXIMUM_NEGATIVE_LONGITUDE,
        SearchApiLimits.MAXIMUM_POSITIVE_LONGITUDE);
    return longitude;
  }

  public static DocumentPb.FieldValue.Geo checkValid(DocumentPb.FieldValue.Geo geoPb) {
    checkLatitude(geoPb.getLat());
    checkLongitude(geoPb.getLng());
    return geoPb;
  }
}
