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

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.search.dev.LocalSearchService;
import com.google.appengine.tools.development.ApiProxyLocal;

/**
 * Config for accessing the local text search service in tests.
 *
 */
public class LocalSearchServiceTestConfig implements LocalServiceTestConfig {
  private boolean persistent = Boolean.FALSE;
  private String storageDirectory = null;

  /**
   * Whether search service data will be persisted.
   */
  public boolean isPersistent() {
    return persistent;
  }

  /**
   * True to persist search service data, otherwise all data will be stored in memory only.
   * @param persistent
   * @return {@code this} (for chaining)
   */
  public LocalSearchServiceTestConfig setPersistent(boolean persistent) {
    this.persistent = persistent;
    return this;
  }

  /**
   * Sets the directory to use to persist search service data, when persistence is enabled.
   * @param storageDirectory the directory for storing data
   * @return {@code this} (for chaining)
   */
  public LocalSearchServiceTestConfig setStorageDirectory(String storageDirectory) {
    this.storageDirectory = storageDirectory;
    return this;
  }

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.setProperty(LocalSearchService.USE_RAM_DIRECTORY, Boolean.toString(!persistent));
    if (storageDirectory != null) {
      proxy.setProperty(LocalSearchService.USE_DIRECTORY, storageDirectory);
    }
  }

  @Override
  public void tearDown() {
    // Nothing to do here.
  }

  public static LocalSearchService getLocalSearchService() {
    return (LocalSearchService) LocalServiceTestHelper.getLocalService(LocalSearchService.PACKAGE);
  }
}
