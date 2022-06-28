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

import com.google.appengine.api.datastore.dev.HighRepJobPolicy;
import com.google.apphosting.api.ApiProxy;
import junit.framework.TestCase;
import org.mockito.Mockito;

/**
 */
public class LocalDatastoreServiceTestConfigTest extends TestCase {
  public void testHighRepConfig() {
    LocalDatastoreServiceTestConfig config = new LocalDatastoreServiceTestConfig();
    // If you set an alternate policy class you can't set either of the default
    // high-rep properties.
    config.setAlternateHighRepJobPolicyClass(HighRepJobPolicy.class);
    try {
      config.setDefaultHighRepJobPolicyRandomSeed(33);
      fail();
    } catch (IllegalArgumentException e) {
      // good
    }
    try {
      config.setDefaultHighRepJobPolicyUnappliedJobPercentage(66);
      fail();
    } catch (IllegalArgumentException e) {
      // good
    }

    config = new LocalDatastoreServiceTestConfig();
    // If you set a random seed you can't set an alternate policy class
    config.setDefaultHighRepJobPolicyRandomSeed(33);
    try {
      config.setAlternateHighRepJobPolicyClass(HighRepJobPolicy.class);
      fail();
    } catch (IllegalArgumentException e) {
      // good
    }

    config = new LocalDatastoreServiceTestConfig();
    // If you set an unapplied job percentage you can't set an alternate policy
    // class
    config.setDefaultHighRepJobPolicyUnappliedJobPercentage(66);
    try {
      config.setAlternateHighRepJobPolicyClass(HighRepJobPolicy.class);
      fail();
    } catch (IllegalArgumentException e) {
      // good
    }

    // setting a random seed an an unapplied job percentage is fine
    new LocalDatastoreServiceTestConfig().setDefaultHighRepJobPolicyRandomSeed(33).
        setDefaultHighRepJobPolicyUnappliedJobPercentage(66);
  }

  public void testCustomDelegate() {
    LocalServiceTestHelper helper = new LocalServiceTestHelper(
        new LocalDatastoreServiceTestConfig());
    helper.setUp();
    ApiProxy.setDelegate(Mockito.mock(ApiProxy.Delegate.class));
    // the test is that we don't get a ClassCastException when the helper
    // gets torn down.
    helper.tearDown();
  }
}
