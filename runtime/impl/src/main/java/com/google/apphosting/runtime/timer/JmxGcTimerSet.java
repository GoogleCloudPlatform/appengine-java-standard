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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collection;

/**
 * {@code JmxGcTimerSet} creates timers that measures the amount of
 * time spent in garbage collection while the timer was running.
 *
 */
public class JmxGcTimerSet extends AbstractSharedTimerSet {
  private static final Collection<GarbageCollectorMXBean> GC_MBEANS =
      ManagementFactory.getGarbageCollectorMXBeans();

  @Override
  protected long getCurrentShared() {
    long total = 0;
    for (GarbageCollectorMXBean mxBean : GC_MBEANS) {
      total += mxBean.getCollectionTime();
    }
    return total * 1000000;
  }

  @Override
  protected String getTitle() {
    return "GC";
  }
}
