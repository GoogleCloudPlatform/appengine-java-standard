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

package com.google.apphosting.runtime.jetty;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.GoogleLogger;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.regex.Pattern;

/**
 * Wrapper for cache-control header value strings. Also includes logic to parse expiration time
 * strings provided in application config files.
 */
public final class CacheControlHeader {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final String DEFAULT_BASE_VALUE = "public, max-age=";
  // Default max age is 10 minutes, per GAE documentation
  private static final String DEFAULT_MAX_AGE = "600";

  private static final ImmutableMap<String, TemporalUnit> EXPIRATION_TIME_UNITS =
      ImmutableMap.of(
          "s", ChronoUnit.SECONDS,
          "m", ChronoUnit.MINUTES,
          "h", ChronoUnit.HOURS,
          "d", ChronoUnit.DAYS);

  private final String value;

  private CacheControlHeader(String value) {
    this.value = value;
  }

  public static CacheControlHeader getDefaultInstance() {
    return new CacheControlHeader(DEFAULT_BASE_VALUE + DEFAULT_MAX_AGE);
  }

  /**
   * Parse formatted expiration time (e.g., "1d 2h 3m") and convert to seconds. If there is no
   * expiration time set, avoid setting max age parameter.
   */
  public static CacheControlHeader fromExpirationTime(String expirationTime) {
    String maxAge = DEFAULT_MAX_AGE;

    if (expirationTime != null) {
      if (expirationTimeIsValid(expirationTime)) {
        Duration totalTime = Duration.ZERO;
        for (String timeString : Splitter.on(" ").split(expirationTime)) {
          String timeUnitShort = Ascii.toLowerCase(timeString.substring(timeString.length() - 1));
          TemporalUnit timeUnit = EXPIRATION_TIME_UNITS.get(timeUnitShort);
          String timeValue = timeString.substring(0, timeString.length() - 1);
          totalTime = totalTime.plus(Long.parseLong(timeValue), timeUnit);
        }
        maxAge = String.valueOf(totalTime.getSeconds());
      } else {
        logger.atWarning().log(
            "Failed to parse expiration time: \"%s\". Using default value instead.",
            expirationTime);
      }
    }

    String output = DEFAULT_BASE_VALUE + maxAge;
    return new CacheControlHeader(output);
  }

  public String getValue() {
    return value;
  }

  /**
   * Validate that expiration time string is a space-delineated collection of expiration tokens (a
   * number followed by a valid unit character).
   */
  private static boolean expirationTimeIsValid(String expirationTime) {
    String expirationTokenPattern = "\\d+[smhd]";
    Pattern pattern =
        Pattern.compile("^" + expirationTokenPattern + "(\\s" + expirationTokenPattern + ")*$");
    return pattern.matcher(expirationTime).matches();
  }
}
