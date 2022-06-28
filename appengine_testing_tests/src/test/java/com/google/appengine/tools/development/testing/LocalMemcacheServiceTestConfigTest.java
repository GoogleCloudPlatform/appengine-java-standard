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

import com.google.appengine.api.memcache.dev.LocalMemcacheService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.apphosting.api.ApiProxy;

import junit.framework.TestCase;

/**
 */
public class LocalMemcacheServiceTestConfigTest extends TestCase {

  public void testSetMaxSize() {
    LocalMemcacheServiceTestConfig config = new LocalMemcacheServiceTestConfig();
    LocalServiceTestHelper helper = new LocalServiceTestHelper(config);
    helper.setUp();
    ApiProxyLocal proxy = (ApiProxyLocal) ApiProxy.getDelegate();
    assertEquals(100 * 1024 * 1024, getMaxSizeInBytes());
    proxy.stop();
    config.setMaxSize(10, LocalMemcacheServiceTestConfig.SizeUnit.BYTES);
    helper.setUp();
    assertEquals(10, getMaxSizeInBytes());
    proxy.stop();
    config.setMaxSize(10, LocalMemcacheServiceTestConfig.SizeUnit.KB);
    helper.setUp();
    assertEquals(10 * 1024, getMaxSizeInBytes());
    proxy.stop();
    config.setMaxSize(10, LocalMemcacheServiceTestConfig.SizeUnit.MB);
    helper.setUp();
    assertEquals(10 * 1024 * 1024, getMaxSizeInBytes());
  }

  private long getMaxSizeInBytes() {
    ApiProxyLocal proxy = (ApiProxyLocal) ApiProxy.getDelegate();
    LocalMemcacheService memcacheService =
        (LocalMemcacheService) proxy.getService(LocalMemcacheService.PACKAGE);
    return memcacheService.getMaxSizeInBytes();
  }
}
