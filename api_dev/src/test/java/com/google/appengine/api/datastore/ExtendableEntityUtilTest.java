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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests ExtendableEntityUtil methods.
 *
 */
@RunWith(JUnit4.class)
public class ExtendableEntityUtilTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testCoverage() {
    // These routines are heavily tested in the labs. We just need some coverage tests to
    // pass the DatastoreApiExhaustiveUsageTest.
    ExtendableEntityUtil.checkSupportedValue(null, 42L, false, DataTypeUtils.getSupportedTypes());
    Key key1 = ExtendableEntityUtil.createKey(null, "kind1");
    Key key2 = ExtendableEntityUtil.createKey(null, "kind1");
    assertThat(ExtendableEntityUtil.areKeysEqual(key1, key2)).isTrue();
  }
}
