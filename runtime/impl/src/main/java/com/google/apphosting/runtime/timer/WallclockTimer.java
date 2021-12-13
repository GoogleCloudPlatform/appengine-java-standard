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
 * {@code WallclockTimer} measures wallclock time using {@link
 * System.nanoTime()}.
 *
 * On Linux, the JRE implements this in terms of {@code gettimeofday},
 * which has a resolution of at least 10 microseconds.
 *
 */
class WallclockTimer extends AbstractIntervalTimer {
  @Override
  protected long getCurrent() {
    return System.nanoTime();
  }
}
