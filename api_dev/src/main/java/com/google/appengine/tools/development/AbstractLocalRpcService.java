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

package com.google.appengine.tools.development;

import java.util.Map;

/**
 * An abstract implementation of {@link LocalRpcService} which runs no
 * setup logic and provides no deadline hints.
 *
 */
public abstract class AbstractLocalRpcService implements LocalRpcService {

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  public Double getDefaultDeadline(boolean isOfflineRequest) {
    return null;
  }

  @Override
  public Double getMaximumDeadline(boolean isOfflineRequest) {
    return null;
  }

  @Override
  public Integer getMaxApiRequestSize() {
    return null;
  }
}
