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
 * {@code TimerSet} is an abstraction for creating one or more
 * {@link Timer} instances that are interdependent in some way.
 *
 * <p>For example, {@link AbstractSharedTimerSet} is a
 * {@link TimerSet} that maintains a single global counter and
 * attributes its change to all of the {@link Timer} instances in the
 * set that are currently running.
 *
 */
public interface TimerSet {
  /**
   * Create a {@link Timer} that becomes a part of this {@link TimerSet}.
   */
  Timer createTimer();

  /**
   * Returns the number of {@link Timer} instances in this set that
   * are currently active (i.e. {@link Timer#start()} has been called
   * since the last {@link Timer#stop()} call.
   */
  int getActiveCount();
}
