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

package com.google.appengine.api.quota;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiStats;

/**
 * Implementation details for the QuotaService.
 *
 *
 */
class QuotaServiceImpl implements QuotaService {

  // This constant is also used in the python version
  // in //depot/google3/apphosting/api/quota.py. Both constants should
  // be kept at identical values.
  private static final double MCYCLES_PER_SECOND = 1200.0;

  private static ApiStats getStats() {
    Environment env = ApiProxy.getCurrentEnvironment();
    if (env == null) {
      return null;
    }
    return ApiStats.get(env);
  }

  @Override
  public boolean supports(DataType type) {
    // Note: we do not do a null check, so any future extensions of this method
    // should make sure that supports(null) == true if getStats() != null

    // Currently, all supported features require the ApiStats to be there
    return getStats() != null;
  }

  public long getApiTimeInMegaCycles() {
    ApiStats stats = getStats();
    return (stats == null) ? 0L : stats.getApiTimeInMegaCycles();
  }

  @Override
  public long getCpuTimeInMegaCycles() {
    ApiStats stats = getStats();
    return (stats == null) ? 0L : stats.getCpuTimeInMegaCycles();
  }

  @Override
  public long convertCpuSecondsToMegacycles(double cpuSeconds) {
    return (long) (cpuSeconds * MCYCLES_PER_SECOND);
  }

  @Override
  public double convertMegacyclesToCpuSeconds(long megaCycles) {
    return megaCycles / MCYCLES_PER_SECOND;
  }

}
