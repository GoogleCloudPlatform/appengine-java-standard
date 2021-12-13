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

import java.util.HashSet;
import java.util.Set;

/**
 * {@code AbstractSharedTimerSet} tracks a single counter that is
 * shared by all of the {@link Timer} instances that are part of the
 * set.
 *
 * <p>Each {@link Timer} returned by {@link #createTimer()} becomes
 * part of the set that created it, and starting or stopping that
 * timer will increment or decrement the rate at which all other
 * active timers are advancing.
 *
 */
public abstract class AbstractSharedTimerSet implements TimerSet {
  // All access to startedTimers must be synchronized on
  // startedTimers.  This lock should be acquired *before* acquiring
  // any locks on timers that are contained within the set.
  private final Set<SharedTimer> startedTimers = new HashSet<>();

  @Override
  public Timer createTimer() {
    return new SharedTimer();
  }

  @Override
  public int getActiveCount() {
    synchronized (startedTimers) {
      return startedTimers.size();
    }
  }

  /**
   * Returns the current value of a counter that is shared by all
   * timers created by calling {@link #createTimer()} on this
   * instance.  The rate of change of this counter will be evenly
   * divided among started {@link Timer} instances.
   */
  protected abstract long getCurrentShared();

  /**
   * Implementations should provide a short, readable string to be
   * included in the {@code toString()} output of the timers that they
   * create (e.g. "hotspot").
   */
  protected abstract String getTitle();

  /**
   * Call {@link AbstractIntervalTimer#update(long)} on each of the
   * currently-started timers using the supplied value.  This must be
   * called whenever any timer is started or stopped, as this changes
   * the rate at which all timers apply their values.  By updating all
   * started timers before starting new timers and before stopping
   * existing timers, we ensure that all changes to the underlying
   * counter are divided by the appropriate number of running timers.
   */
  private void updateStartedTimers(long current) {
    synchronized (startedTimers) {
      for (SharedTimer timer : startedTimers) {
        timer.update(current);
      }
    }
  }

  private class SharedTimer extends AbstractIntervalTimer {
    /**
     * {@inheritDoc}
     *
     * Note that calling this method will update all other
     * {@link Timer} instances in the same {@link TimerSet}.
     */
    @Override
    public void start() {
      synchronized (startedTimers) {
        synchronized (this) {
          // Does not call super.start() because our logic needs to happen in the middle.
          if (running) {
            throw new IllegalStateException("already running");
          }

          long current = getCurrent();
          // We need to call update before we add ourselves so the count
          // is correct (all time since the last update was done with
          // the current number of timers).
          updateStartedTimers(current);
          startedTimers.add(this);
          startTime = current;
          running = true;
        }
      }
    }

    /**
     * {@inheritDoc}
     *
     * Note that calling this method will update all other
     * {@link Timer} instances in the same {@link TimerSet}.
     */
    @Override
    public void stop() {
      synchronized (startedTimers) {
        synchronized (this) {
          // Does not call super.stop() because our logic needs to happen in the middle.
          if (!running) {
            throw new IllegalStateException("not running");
          }
          // We need to call update before removing ourselves from the
          // list so the count is correct (all time since the last
          // update was done with the current number of timers).
          //
          // We're still on startedTimers at this point, so this will update us as well.
          updateStartedTimers(getCurrent());
          startedTimers.remove(this);
          // We were updated above, so all that's left is to set this flag.
          running = false;
        }
      }
    }

    @Override
    protected long getCurrent() {
      return getCurrentShared();
    }

    @Override
    protected double getRatio() {
      synchronized (startedTimers) {
        return 1.0 / startedTimers.size();
      }
    }

    @Override
    public String toString() {
      return String.format("%.3f %s", getNanoseconds() / 1000000000.0, getTitle());
    }
  }
}
