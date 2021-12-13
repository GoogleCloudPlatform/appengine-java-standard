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

import com.google.appengine.api.blobstore.dev.LocalBlobstoreService;
import com.google.appengine.tools.development.ApiProxyLocal;

/**
 * Config for accessing the local blobstore service in tests.
 * Default behavior is to configure the local blobstore to only store data
 * in-memory and not write anything to disk.  {@link #tearDown()} wipes out
 * all in-memory state so that the blobstore is empty at the end of every test.
 *
 */
public final class LocalBlobstoreServiceTestConfig implements LocalServiceTestConfig {

  private Boolean noStorage = true;

  private String backingStoreLocation;

  public boolean isNoStorage() {
    return noStorage;
  }

  /**
   * True to put the blobstore into "memory-only" mode.
   * @param noStorage
   * @return {@code this} (for chaining)
   */
  public LocalBlobstoreServiceTestConfig setNoStorage(boolean noStorage) {
    this.noStorage = noStorage;
    return this;
  }

  public String getBackingStoreLocation() {
    return backingStoreLocation;
  }

  /**
   * Sets the location for on-disk storage.
   * @param backingStoreLocation
   * @return {@code this} (for chaining)
   */
  public LocalBlobstoreServiceTestConfig setBackingStoreLocation(String backingStoreLocation) {
    this.backingStoreLocation = backingStoreLocation;
    return this;
  }

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.setProperty(LocalBlobstoreService.NO_STORAGE_PROPERTY, noStorage.toString());
    if (backingStoreLocation != null) {
      proxy.setProperty(LocalBlobstoreService.BACKING_STORE_PROPERTY, backingStoreLocation);
    }
    // force the service to initialize
    getLocalBlobstoreService();
  }

  @Override
  public void tearDown() {
    // if the service is storing blobs in memory this will clear everything out
    // if the service is storing blobs on-disk this has no effect
    getLocalBlobstoreService().stop();
  }

  public static LocalBlobstoreService getLocalBlobstoreService() {
    return (LocalBlobstoreService) LocalServiceTestHelper.getLocalService(
        LocalBlobstoreService.PACKAGE);
  }
}
