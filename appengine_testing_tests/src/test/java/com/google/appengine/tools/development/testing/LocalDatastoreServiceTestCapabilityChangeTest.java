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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query;
import com.google.apphosting.api.ApiProxy;

import junit.framework.TestCase;


/**
 */
public class LocalDatastoreServiceTestCapabilityChangeTest extends TestCase {

  public void testDatastoreDisabled() {

    LocalServiceTestHelper helper =
        new LocalServiceTestHelper(
            new LocalDatastoreServiceTestConfig(),
            new LocalCapabilitiesServiceTestConfig()
                .setCapabilityStatus(Capability.DATASTORE, CapabilityStatus.DISABLED));
    helper.setUp();

    CapabilitiesService cs = CapabilitiesServiceFactory.getCapabilitiesService();
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    try {
      ds.prepare(new Query("yam")).countEntities(withLimit(10));
      fail();
    } catch (ApiProxy.CapabilityDisabledException e) {
      //success!
    }
    helper.tearDown();

  }

  public void testDatastoreWriteDisabled() {

    LocalServiceTestHelper helper =
        new LocalServiceTestHelper(
            new LocalDatastoreServiceTestConfig(),
            new LocalCapabilitiesServiceTestConfig()
                .setCapabilityStatus(Capability.DATASTORE_WRITE, CapabilityStatus.DISABLED));
    helper.setUp();

    CapabilitiesService cs = CapabilitiesServiceFactory.getCapabilitiesService();
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    try {
      ds.put(new Entity("foo"));
      fail();
    } catch (ApiProxy.CapabilityDisabledException e) {
      //success
    }
    try {
      ds.getIndexes();
    } catch (ApiProxy.CapabilityDisabledException e) {
      fail();
    }
    helper.tearDown();

  }
}
