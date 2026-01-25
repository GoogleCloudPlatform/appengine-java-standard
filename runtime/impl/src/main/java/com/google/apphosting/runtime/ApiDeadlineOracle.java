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

package com.google.apphosting.runtime;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code ApiDeadlineOracle} determines the appropriate deadline for API calls based on the
 * user-specified deadline, the per-package maximum and default deadlines, and the fallthrough
 * maximum and default deadlines.
 *
 * <p>This class is also used to track shared buffer counts and sizes as they can also be specified
 * on a per-package and online/offline basis.
 */
public class ApiDeadlineOracle {
  private final DeadlineMap deadlineMap;
  // TODO: Rename this class to something less deadline-specific.
  private ApiDeadlineOracle(DeadlineMap deadlineMap) {
    this.deadlineMap = deadlineMap;
  }

  public double getDeadline(String packageName, boolean isOffline, Number userDeadline) {
    if (isOffline) {
      double deadline;
      if (userDeadline == null) {
        deadline =
            ApiDeadlineMap.OFFLINE_DEFAULT_DEADLINE_MAP.getOrDefault(
                packageName, 5.0); // Default offline deadline
      } else {
        deadline = userDeadline.doubleValue();
      }
      return Math.min(
          deadline,
          ApiDeadlineMap.OFFLINE_MAX_DEADLINE_MAP.getOrDefault(
              packageName, 10.0)); // Default offline max deadline
    }
    return deadlineMap.getDeadline(packageName, userDeadline);
  }

  public void addPackageDefaultDeadline(String packageName, double defaultDeadline) {
    deadlineMap.addDefaultDeadline(packageName, defaultDeadline);
  }

  public void addPackageMaxDeadline(String packageName, double maxDeadline) {
    deadlineMap.addMaxDeadline(packageName, maxDeadline);
  }

  public void addPackageMinContentSizeForBuffer(String packageName, long minContentSizeForBuffer) {
    deadlineMap.addMinContentSizeForBuffer(packageName, minContentSizeForBuffer);
  }

  public void addPackageMaxRequestSize(String packageName, long maxRequestSize) {
    deadlineMap.addMaxRequestSize(packageName, maxRequestSize);
  }

  /** Build an ApiDeadlineOracle. */
  public static class Builder {
    private DeadlineMap deadlineMap;

    /** Initializes the default deadline map using standard hardcoded values. */
    @CanIgnoreReturnValue
    public Builder initDeadlineMap() {
      deadlineMap =
          new DeadlineMap(
              10.0, ApiDeadlineMap.DEFAULT_DEADLINE_MAP, 10.0, ApiDeadlineMap.MAX_DEADLINE_MAP);
      return this;
    }

    public Builder initDeadlineMap(DeadlineMap deadlineMap) {
      this.deadlineMap = deadlineMap;
      return this;
    }

    public ApiDeadlineOracle build() {
      if (deadlineMap == null) {
        throw new IllegalStateException("All deadline maps must be initialized.");
      }
      return new ApiDeadlineOracle(deadlineMap);
    }
  }

  /** Deadlines for one instance type (offline or online). */
  public static class DeadlineMap {
    private final double defaultDeadline;
    private final Map<String, Double> defaultDeadlineMap;
    private final double maxDeadline;
    private final Map<String, Double> maxDeadlineMap;
    private final Map<String, Long> minContentSizeForBufferMap;
    private final Map<String, Long> maxRequestSizeMap;

    public DeadlineMap(
        double defaultDeadline,
        Map<String, Double> defaultDeadlineMap,
        double maxDeadline,
        Map<String, Double> maxDeadlineMap) {
      this.defaultDeadline = defaultDeadline;
      this.defaultDeadlineMap = new HashMap<>(defaultDeadlineMap);
      this.maxDeadline = maxDeadline;
      this.maxDeadlineMap = new HashMap<>(maxDeadlineMap);
      this.minContentSizeForBufferMap = new HashMap<String, Long>();
      this.maxRequestSizeMap = new HashMap<String, Long>();
    }

    private double getDeadline(String pkg, Number userDeadline) {
      double deadline;
      if (userDeadline == null) {
        // If the user didn't provide one, default it.
        deadline = getDoubleValue(pkg, defaultDeadlineMap, defaultDeadline);
      } else {
        deadline = userDeadline.doubleValue();
      }
      // Now cap it at the maximum deadline.
      return Math.min(deadline, getDoubleValue(pkg, maxDeadlineMap, maxDeadline));
    }

    /**
     * Adds new deadlines for the specified package.  If the package was
     * already known (either from a previous {@code addPackage} call or
     * from the string passed into the constructor, these values will
     * override it.
     */
    private void addDefaultDeadline(String packageName, double defaultDeadline) {
      defaultDeadlineMap.put(packageName, defaultDeadline);
    }

    private void addMaxDeadline(String packageName, double maxDeadline) {
      maxDeadlineMap.put(packageName, maxDeadline);
    }

    private void addMinContentSizeForBuffer(String packageName, long minContentSizeForBuffer) {
      minContentSizeForBufferMap.put(packageName, minContentSizeForBuffer);
    }

    private void addMaxRequestSize(String packageName, long maxRequestSize) {
      maxRequestSizeMap.put(packageName, maxRequestSize);
    }

    private double getDoubleValue(String packageName, Map<String, Double> map, double fallthrough) {
      Double value = map.get(packageName);
      if (value == null) {
        value = fallthrough;
      }
      return value;
    }
  }
}
