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

package com.google.common.time;

import com.google.common.annotations.GwtIncompatible;
import com.google.j2objc.annotations.J2ObjCIncompatible;
import java.time.Duration;

/**
 * An object which accepts requests to put the current thread to sleep.
 *
 */
@GwtIncompatible
@J2ObjCIncompatible
public interface Sleeper {

  /**
   * A sleeper that uses {@link Thread#sleep(long, int)}.
   *
   * <p>The returned implementation is immutable and {@link Serializable}.
   */
  static Sleeper defaultSleeper() {
    return Sleepers.DefaultSleeper.INSTANCE;
  }

  /**
   * A sleeper that <b>never</b> sleeps. This may be useful for unit tests.
   *
   * <p><b>Note:</b> {@link #sleep(Duration)} will still throw an {@link IllegalArgumentException}
   * if passed a negative duration.
   *
   * <p>The returned implementation is immutable and {@link Serializable}.
   */
  static Sleeper noOpSleeper() {
    return Sleepers.NoOpSleeper.INSTANCE;
  }

  /**
   * Requests to put the current thread to sleep for the given duration.
   *
   * @param duration the duration to sleep.
   * @throws InterruptedException if the current thread is interrupted
   */
  void sleep(Duration duration) throws InterruptedException;
}
