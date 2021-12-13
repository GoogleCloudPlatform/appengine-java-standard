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

package com.google.apphosting.runtime.timer;

/**
 * {@code Timer} is a base interface for various timer implementations
 * provided in this package.  Each of them can be started and stopped,
 * and can return the total elapsed time, however each implementation
 * will interpret elapsed time differently (e.g. wall time, user time,
 * system time, etc.).
 *
 * This was inspired by and loosely based on //google3/base/timer.h.
 *
 * @see TimerFactory
 *
 */
public interface Timer {
  /**
   * Start the timer running.
   *
   * @throws IllegalStateException If it was already running.
   */
  public void start();

  /**
   * Stop the timer.
   *
   * @throws IllegalStateException If it was not running.
   */
  public void stop();

  /**
   * Get the number of nanoseconds that elapsed between when the
   * {@code Timer} was started and when it was stopped.  Note that the
   * return value has nanosecond precision but not necessarily
   * nanosecond accuracy.
   */
  public long getNanoseconds();

  /**
   * Update any internal state.  For example, some {@link Timer}
   * implementations may take snapshots of data that may be
   * unavailable in the future.  Clients should call this method
   * periodically to qensure that any state is being updated.  For an
   * example, see {@link JmxThreadGroupCpuTimer#update}.
   */
  public void update();
}
