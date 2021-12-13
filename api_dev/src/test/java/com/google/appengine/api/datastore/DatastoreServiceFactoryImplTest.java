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

package com.google.appengine.api.datastore;

import com.google.apphosting.api.ApiProxy;
import junit.framework.TestCase;

/** Base class for tests of the {@link DatastoreServiceFactoryImpl}. */
public class DatastoreServiceFactoryImplTest extends TestCase {

  private DatastoreServiceConfig config;
  private DatastoreServiceFactoryImpl factory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    config = DatastoreServiceConfig.Builder.withDefaults();
    factory = new DatastoreServiceFactoryImpl();
  }

  @Override
  protected void tearDown() throws Exception {
    DatastoreServiceGlobalConfig.clear();

    super.tearDown();
  }

  public void testApiProxy() {
    // Uses API proxy by default.
    AsyncDatastoreService service = factory.getAsyncDatastoreService(config);

    assertTrue(service instanceof AsyncDatastoreServiceImpl);

    // Should not have resulted in any changes to the ApiProxy.
    assertNull(ApiProxy.getDelegate());
    assertNull(ApiProxy.getEnvironmentFactory());
    assertNull(ApiProxy.getCurrentEnvironment());
  }

  public void testNonApiProxy() {
    DatastoreServiceGlobalConfig.setConfig(nonApiProxyConfig());
    AsyncDatastoreService service = factory.getAsyncDatastoreService(config);
    assertTrue(service instanceof AsyncCloudDatastoreV1ServiceImpl);
  }

  private static DatastoreServiceGlobalConfig nonApiProxyConfig() {
    return DatastoreServiceGlobalConfig.builder()
        .appId("s~project-id")
        .emulatorHost("dummy-value-to-stub-out-credentials")
        .build();
  }
}
