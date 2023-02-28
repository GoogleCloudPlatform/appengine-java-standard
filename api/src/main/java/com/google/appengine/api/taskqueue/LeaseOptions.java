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

package com.google.appengine.api.taskqueue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Contains various options for lease requests following the builder pattern. Calls to {@link
 * LeaseOptions} methods may be chained to specify multiple options in the one {@link LeaseOptions}
 * object.
 *
 * <p>Notes on usage:<br>
 * The recommended way to instantiate a {@link LeaseOptions} object is to statically import {@link
 * Builder}.* and invoke a static creation method followed by instance mutators:
 *
 * <pre>{@code
 * import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.*;
 *
 * ...
 * tasks = pullQueue.leaseTasks(withLeasePeriod(2, TimeUnit.HOURS).countLimit(1000));
 * }</pre>
 *
 */
public final class LeaseOptions {

  private Long lease;
  private TimeUnit unit;
  private Long countLimit;
  private Double deadlineInSeconds;
  private boolean groupByTag;
  private byte[] tag;

  private LeaseOptions() {
    this.lease = null;
    this.unit = null;
    this.countLimit = null;
    this.deadlineInSeconds = null;
    this.groupByTag = false;
    this.tag = null;
  }

  /** A copy constructor for {@link LeaseOptions}. */
  public LeaseOptions(LeaseOptions options) {
    lease = options.lease;
    unit = options.unit;
    countLimit = options.countLimit;
    deadlineInSeconds = options.deadlineInSeconds;
    groupByTag = options.groupByTag;
    tag = options.tag;

  }

  /** Returns the lease period for lease requests. May be {@code null} if not specified. */
  Long getLease() {
    return lease;
  }

  /** Returns the lease period unit for lease requests. May be {@code null} if not specified. */
  TimeUnit getUnit() {
    return unit;
  }

  /** Returns the count limit for lease requests. May be {@code null} if not specified. */
  Long getCountLimit() {
    return countLimit;
  }

  /** Returns the deadline for lease requests. May be {@code null} if not specified. */
  Double getDeadlineInSeconds() {
    return deadlineInSeconds;
  }

  /** Returns if leased tasks should be grouped by tag */
  boolean getGroupByTag() {
    return groupByTag;
  }

  /** Returns the task tag for lease requests. */
  byte[] getTag() {
    return tag;
  }

  /**
   * Sets the lease period for lease requests. Must be positive.
   *
   * @throws IllegalArgumentException
   */
  public LeaseOptions leasePeriod(long lease, TimeUnit unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Unit for lease period must not be null");
    }
    if (lease <= 0) {
      throw new IllegalArgumentException(
          "Lease period must be greater than 0, got " + lease + " " + unit);
    }
    this.lease = lease;
    this.unit = unit;
    return this;
  }

  /**
   * Sets the count limit for lease requests. Must be positive.
   *
   * @throws IllegalArgumentException
   */
  public LeaseOptions countLimit(long countLimit) {
    if (countLimit <= 0) {
      throw new IllegalArgumentException("Number of tasks to lease must be greater than 0");
    }
    this.countLimit = countLimit;
    return this;
  }

  /**
   * Sets the deadline for lease requests. Must be positive.
   *
   * @throws IllegalArgumentException
   */
  public LeaseOptions deadlineInSeconds(@Nullable Double deadlineInSeconds) {
    if (deadlineInSeconds != null && deadlineInSeconds <= 0.0) {
      throw new IllegalArgumentException("Deadline must be > 0, got " + deadlineInSeconds);
    }
    this.deadlineInSeconds = deadlineInSeconds;
    return this;
  }

  /**
   * Indicates that all tasks being leased must have the same tag. Redundant if tag is specified.
   *
   * @throws IllegalArgumentException
   */
  public LeaseOptions groupByTag() {
    this.groupByTag = true;
    return this;
  }

  /**
   * Sets the tag for lease requests. Must not be null.
   *
   * @throws IllegalArgumentException
   */
  public LeaseOptions tag(byte[] tag) {
    if (tag == null) {
      throw new IllegalArgumentException("Tag must not be null");
    }
    this.groupByTag = true;
    this.tag = tag;
    return this;
  }

  /**
   * Sets the tag for lease requests. Must not be null.
   *
   * @throws IllegalArgumentException
   */
  public LeaseOptions tag(String tag) {
    if (tag == null) {
      throw new IllegalArgumentException("Tag must not be null");
    }
    this.groupByTag = true;
    this.tag = tag.getBytes();
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lease == null) ? 0 : lease.hashCode());
    result = prime * result + ((unit == null) ? 0 : unit.hashCode());
    result = prime * result + ((countLimit == null) ? 0 : countLimit.hashCode());
    result = prime * result + ((deadlineInSeconds == null) ? 0 : deadlineInSeconds.hashCode());
    result = prime * result + (groupByTag ? 1 : 0);
    result = prime * result + ((tag == null) ? 0 : Arrays.hashCode(tag));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    LeaseOptions other = (LeaseOptions) obj;
    if (lease == null) {
      if (other.lease != null) {
        return false;
      }
    } else if (!lease.equals(other.lease)) {
      return false;
    }
    if (unit == null) {
      if (other.unit != null) {
        return false;
      }
    } else if (!unit.equals(other.unit)) {
      return false;
    }
    if (countLimit == null) {
      if (other.countLimit != null) {
        return false;
      }
    } else if (!countLimit.equals(other.countLimit)) {
      return false;
    }
    if (deadlineInSeconds == null) {
      if (other.deadlineInSeconds != null) {
        return false;
      }
    } else if (!deadlineInSeconds.equals(other.deadlineInSeconds)) {
      return false;
    }
    if (groupByTag != other.groupByTag) {
      return false;
    }
    if (!Arrays.equals(tag, other.tag)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "LeaseOptions[lease="
        + lease
        + " "
        + unit
        + ",countLimit="
        + countLimit
        + ",deadlineInSeconds="
        + deadlineInSeconds
        + ",groupByTag="
        + groupByTag
        + "]";
  }

  /** Provides static creation methods for {@link LeaseOptions}. */
  public static final class Builder {
    /**
     * Returns default {@link LeaseOptions} and calls {@link LeaseOptions#leasePeriod(long,
     * TimeUnit)}.
     */
    public static LeaseOptions withLeasePeriod(long lease, TimeUnit unit) {
      return withDefaults().leasePeriod(lease, unit);
    }

    /** Returns default {@link LeaseOptions} and calls {@link LeaseOptions#countLimit(long)}. */
    public static LeaseOptions withCountLimit(long countLimit) {
      return withDefaults().countLimit(countLimit);
    }

    /**
     * Returns default {@link LeaseOptions} and calls {@link
     * LeaseOptions#deadlineInSeconds(Double)}.
     */
    public static LeaseOptions withDeadlineInSeconds(@Nullable Double deadlineInSeconds) {
      return withDefaults().deadlineInSeconds(deadlineInSeconds);
    }

    /** Returns default {@link LeaseOptions} and calls {@link LeaseOptions#tag(byte[])}. */
    public static LeaseOptions withTag(byte[] tag) {
      return withDefaults().tag(tag);
    }

    /** Returns default {@link LeaseOptions} and calls {@link LeaseOptions#tag(String)}. */
    public static LeaseOptions withTag(String tag) {
      return withDefaults().tag(tag);
    }

    /** Returns default {@link LeaseOptions} with default values. */
    private static LeaseOptions withDefaults() {
      return new LeaseOptions();
    }

    private Builder() {}
  }
}
