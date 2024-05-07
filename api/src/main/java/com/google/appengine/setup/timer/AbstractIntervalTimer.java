/*
 * Copyright 2022 Google LLC
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

package com.google.appengine.setup.timer;

/**
 * {@code AbstractIntervalTimer} is common base class for {@link
 * Timer} implementations that base measure the change in some value
 * between the point where the timer is started and the point where
 * the timer is stopped.
 * <p/>
 * <p>This class is thread-safe.
 */
abstract public class AbstractIntervalTimer implements Timer {
    protected boolean running = false;
    protected long startTime = 0L;
    protected long cumulativeTime = 0L;

    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("already running");
        }

        startTime = getCurrent();
        running = true;
    }

    public synchronized void stop() {
        if (!running) {
            throw new IllegalStateException("not running");
        }

        update(getCurrent());
        running = false;
    }

    public synchronized void update() {
        update(getCurrent());
    }

    public long getNanoseconds() {
        double ratio = getRatio();
        synchronized (this) {
            if (running) {
                return cumulativeTime + ((long) ((getCurrent() - startTime) * ratio));
            } else {
                return cumulativeTime;
            }
        }
    }

    /**
     * The fraction of the change in the underlying counter which will
     * be attributed to this timer.  By default, 100% of it.
     */
    protected double getRatio() {
        return 1.0;
    }

    protected void update(long currentValue) {
        synchronized (this) {
            long increment = (long) ((currentValue - startTime) * getRatio());
            cumulativeTime += increment;
            startTime = currentValue;
        }
    }

    abstract protected long getCurrent();
}
