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

import java.io.Serializable;

/**
 * Contains various options for a task's retry strategy. Calls to {@link RetryOptions} methods may
 * be chained to specify multiple options in the one {@link RetryOptions} object.
 *
 * <p>Notes on usage:<br>
 * The recommended way to instantiate a {@link RetryOptions} object is to statically import {@link
 * Builder}.* and invoke a static creation method followed by an instance mutator (if needed):
 *
 * <pre>{@code
 * import static com.google.appengine.api.taskqueue.RetryOptions.Builder.*;
 *
 * ...
 * RetryOptions retry = withTaskRetryLimit(10).taskAgeLimitSeconds("4d")
 *     .minBackoffSeconds(120).maxBackoffSeconds(3600).maxDoublings(5);
 * {@link QueueFactory#getDefaultQueue()}.add(retryOptions(retry));
 * }</pre>
 *
 */
public final class RetryOptions implements Serializable {
  private static final long serialVersionUID = -5434793768244571551L;
  private Integer taskRetryLimit;
  private Long taskAgeLimitSeconds;
  private Double minBackoffSeconds;
  private Double maxBackoffSeconds;
  private Integer maxDoublings;

  private RetryOptions() {}

  public RetryOptions(RetryOptions options) {
    taskRetryLimit = options.taskRetryLimit;
    taskAgeLimitSeconds = options.taskAgeLimitSeconds;
    minBackoffSeconds = options.minBackoffSeconds;
    maxBackoffSeconds = options.maxBackoffSeconds;
    maxDoublings = options.maxDoublings;
  }

  /**
   * Sets the number of retries allowed before a task can fail permanently. If both taskRetryLimit
   * and taskAgeLimitSeconds are specified, then both limits must be exceeded before a task can fail
   * permanently.
   */
  public RetryOptions taskRetryLimit(int taskRetryLimit) {
    if (taskRetryLimit < 0) {
      throw new IllegalArgumentException("taskRetryLimit must not be negative.");
    }
    this.taskRetryLimit = taskRetryLimit;
    return this;
  }

  Integer getTaskRetryLimit() {
    return taskRetryLimit;
  }

  /**
   * Sets the maximum age from the first attempt to execute a task after which any new task failure
   * can be permanent. If both taskRetryLimit and taskAgeLimitSeconds are specified, then both
   * limits must be exceeded before a task can fail permanently.
   *
   * @param taskAgeLimitSeconds The age limit in seconds. Must not be negative.
   * @return the RetryOptions object for chaining.
   */
  public RetryOptions taskAgeLimitSeconds(long taskAgeLimitSeconds) {
    if (taskAgeLimitSeconds < 0) {
      throw new IllegalArgumentException("taskAgeLimitSeconds must not be negative.");
    }
    this.taskAgeLimitSeconds = taskAgeLimitSeconds;
    return this;
  }

  Long getTaskAgeLimitSeconds() {
    return taskAgeLimitSeconds;
  }

  /**
   * Sets the minimum retry backoff interval, in seconds.
   *
   * @param minBackoffSeconds Seconds value for the minimum backoff interval.
   * @return the RetryOptions object for chaining.
   */
  public RetryOptions minBackoffSeconds(double minBackoffSeconds) {
    if (minBackoffSeconds < 0) {
      throw new IllegalArgumentException("minBackoffSeconds must not be negative.");
    }
    this.minBackoffSeconds = minBackoffSeconds;
    return this;
  }

  Double getMinBackoffSeconds() {
    return minBackoffSeconds;
  }

  /**
   * Sets the maximum retry backoff interval, in seconds.
   *
   * @param maxBackoffSeconds Seconds value for the maximum backoff interval.
   * @return the RetryOptions object for chaining.
   */
  public RetryOptions maxBackoffSeconds(double maxBackoffSeconds) {
    if (maxBackoffSeconds < 0) {
      throw new IllegalArgumentException("maxBackoffSeconds must not be negative.");
    }
    this.maxBackoffSeconds = maxBackoffSeconds;
    return this;
  }

  Double getMaxBackoffSeconds() {
    return maxBackoffSeconds;
  }

  /**
   * Sets the maximum times the retry backoff interval should double before rising linearly to the
   * maximum.
   *
   * @param maxDoublings The number of allowed doublings. Must not be negative.
   * @return the RetryOptions object for chaining.
   */
  public RetryOptions maxDoublings(int maxDoublings) {
    if (maxDoublings < 0) {
      throw new IllegalArgumentException("maxDoublings must not be negative.");
    }
    this.maxDoublings = maxDoublings;
    return this;
  }

  Integer getMaxDoublings() {
    return maxDoublings;
  }

  @Override
  public int hashCode() {
    final int prime = 37;
    int result = 1;
    result = prime * result + ((taskRetryLimit == null) ? 0 : taskRetryLimit.hashCode());
    result = prime * result + ((taskAgeLimitSeconds == null) ? 0 : taskAgeLimitSeconds.hashCode());
    result = prime * result + ((minBackoffSeconds == null) ? 0 : minBackoffSeconds.hashCode());
    result = prime * result + ((maxBackoffSeconds == null) ? 0 : maxBackoffSeconds.hashCode());
    result = prime * result + ((maxDoublings == null) ? 0 : maxDoublings.hashCode());
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
    RetryOptions other = (RetryOptions) obj;
    if (taskRetryLimit == null) {
      if (other.taskRetryLimit != null) {
        return false;
      }
    } else if (!taskRetryLimit.equals(other.taskRetryLimit)) {
      return false;
    }
    if (taskAgeLimitSeconds == null) {
      if (other.taskAgeLimitSeconds != null) {
        return false;
      }
    } else if (!taskAgeLimitSeconds.equals(other.taskAgeLimitSeconds)) {
      return false;
    }
    if (minBackoffSeconds == null) {
      if (other.minBackoffSeconds != null) {
        return false;
      }
    } else if (!minBackoffSeconds.equals(other.minBackoffSeconds)) {
      return false;
    }
    if (maxBackoffSeconds == null) {
      if (other.maxBackoffSeconds != null) {
        return false;
      }
    } else if (!maxBackoffSeconds.equals(other.maxBackoffSeconds)) {
      return false;
    }
    if (maxDoublings == null) {
      if (other.maxDoublings != null) {
        return false;
      }
    } else if (!maxDoublings.equals(other.maxDoublings)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "RetryOptions[taskRetryLimit="
        + taskRetryLimit
        + ", taskAgeLimitSeconds="
        + taskAgeLimitSeconds
        + ", minBackoffSeconds="
        + minBackoffSeconds
        + ", maxBackoffSeconds="
        + maxBackoffSeconds
        + ", maxDoublings="
        + maxDoublings
        + "]";
  }

  /** Provides static creation methods for {@link RetryOptions}. */
  public static final class Builder {
    /** Returns default {@link RetryOptions} and calls {@link RetryOptions#taskRetryLimit(int)}. */
    public static RetryOptions withTaskRetryLimit(int taskRetryLimit) {
      return withDefaults().taskRetryLimit(taskRetryLimit);
    }

    /**
     * Returns default {@link RetryOptions} and calls {@link
     * RetryOptions#taskAgeLimitSeconds(long)}.
     */
    public static RetryOptions withTaskAgeLimitSeconds(long taskAgeLimitSeconds) {
      return withDefaults().taskAgeLimitSeconds(taskAgeLimitSeconds);
    }

    /**
     * Returns default {@link RetryOptions} and calls {@link
     * RetryOptions#minBackoffSeconds(double)}.
     */
    public static RetryOptions withMinBackoffSeconds(double minBackoffSeconds) {
      return withDefaults().minBackoffSeconds(minBackoffSeconds);
    }

    /**
     * Returns default {@link RetryOptions} and calls {@link
     * RetryOptions#maxBackoffSeconds(double)}.
     */
    public static RetryOptions withMaxBackoffSeconds(double maxBackoffSeconds) {
      return withDefaults().maxBackoffSeconds(maxBackoffSeconds);
    }

    /** Returns default {@link RetryOptions} and calls {@link RetryOptions#maxDoublings(int)}. */
    public static RetryOptions withMaxDoublings(int maxDoublings) {
      return withDefaults().maxDoublings(maxDoublings);
    }

    /** Returns default {@link RetryOptions}. */
    public static RetryOptions withDefaults() {
      return new RetryOptions();
    }

    private Builder() {}
  }
}
