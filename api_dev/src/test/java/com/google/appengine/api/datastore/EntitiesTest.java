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
 * Unit tests for the DatastoreService Entities class.
 *
 */
@RunWith(JUnit4.class)
public class EntitiesTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testNamespaceKeys() {
    assertThat(Entities.getNamespaceFromNamespaceKey(Entities.createNamespaceKey(""))).isEmpty();
    assertThat(Entities.getNamespaceFromNamespaceKey(Entities.createNamespaceKey("ha")))
        .isEqualTo("ha");
  }

  @Test
  public void testEntityGroupKey() {
    Key k1 = KeyFactory.createKey("base", 22);
    Key k2 = KeyFactory.createKey(k1, "sub", "t");

    Key egK1 = Entities.createEntityGroupKey(k1);
    assertThat(egK1.getParent()).isEqualTo(k1);
    assertThat(Entities.createEntityGroupKey(k2)).isEqualTo(egK1);
  }

  @Test
  public void testGetVersionProperty() {
    Entity e = new Entity("test");
    e.setProperty("__version__", 333);
    assertThat(Entities.getVersionProperty(e)).isEqualTo(333);
    e.setProperty("__version__", (1L << 40));
    assertThat(Entities.getVersionProperty(e)).isEqualTo(1L << 40);
  }
}
