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

package com.google.appengine.api.memcache;

import java.util.Date;
import java.util.Map;

/**
 * Expiration specifications on {@link MemcacheService#putAll(Map , Expiration)}
 * and {@link MemcacheService#put(Object, Object, Expiration)} operations.
 * <p>
 * {@code Expiration} has resolution to one second, although a milliseconds
 * factory constructor is provided for convenience.
 *
 */
public final class Expiration {

  private boolean isOffset;
  private long millis;

  /**
   * Creates an expiration at specific date/time.
   *
   * @param expirationTime date/time after which an item must be discarded
   *    from the cache.
   * @return a new {@code Expiration} object representing the given
   *    {@link Date}.
   */
  public static Expiration onDate(Date expirationTime) {
    return new Expiration(expirationTime.getTime(), false);
  }

  /**
   * Creates an {@code Expiration} for some number of milliseconds into the
   * future.
   *
   * @param milliDelay amount of time, in milliseconds, after which an item
   *    must be discarded from the cache.
   * @return a new {@code Expiration} representing the requested time.
   */
  public static Expiration byDeltaMillis(int milliDelay) {
    return new Expiration(milliDelay, true);
  }

  /**
   * Creates an {@code Expiration} for some number of
   * seconds in the future.
   * @param secondsDelay number of seconds after which an item must be
   *   discarded.
   * @return a new {@code Expiration} for the requested time.
   */
  public static Expiration byDeltaSeconds(int secondsDelay) {
    return new Expiration(secondsDelay * 1000L, true);
  }

  private Expiration(long millis, boolean isOffset) {
    this.millis = millis;
    this.isOffset = isOffset;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Expiration) {
      return ((Expiration) obj).millis == millis &&
          ((Expiration) obj).isOffset == isOffset;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return (int) millis;
  }

  /**
   * Fetches the expiration date, in milliseconds-since-epoch.
   *
   * @return timestamp of expiration, even if constructed via a delta
   *     rather than fixed date.
   */
  public long getMillisecondsValue() {
    if (isOffset) {
      return System.currentTimeMillis() + millis;
    } else {
      return millis;
    }
  }

  /**
   * Fetches the expiration date, in seconds-since-epoch.
   *
   * @return timestamp of expiration, even if constructed via a delta
   *     rather than fixed date.
   */
  public int getSecondsValue() {
    return (int) (getMillisecondsValue() / 1000);
  }
}
