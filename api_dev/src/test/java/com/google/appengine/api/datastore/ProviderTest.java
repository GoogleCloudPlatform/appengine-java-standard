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

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withDefaults;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link IDatastoreServiceFactoryProvider}.
 *
 */
@RunWith(JUnit4.class)
public class ProviderTest {
  @Test
  public void testRawProvider() throws Exception {
    IDatastoreServiceFactoryProvider provider = new IDatastoreServiceFactoryProvider();
    IDatastoreServiceFactory factory = provider.getFactoryInstance();
    assertThat(factory).isInstanceOf(DatastoreServiceFactoryImpl.class);

    {
      AsyncDatastoreService service = factory.getAsyncDatastoreService(withDefaults());
      assertThat(service).isInstanceOf(AsyncDatastoreServiceImpl.class);
    }

    {
      DatastoreService service = factory.getDatastoreService(withDefaults());
      assertThat(service).isInstanceOf(DatastoreServiceImpl.class);
    }
  }
}
