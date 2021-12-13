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

/** Unit tests for the EmbeddedEntity class. */
@RunWith(JUnit4.class)
public class EmbeddedEntityTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testBasic() {
    EmbeddedEntity embEntity1 = new EmbeddedEntity();
    assertThat(embEntity1.getKey()).isNull();
    Key key1 = KeyFactory.createKey("kind", "keyname");
    embEntity1.setKey(key1);
    assertThat(key1).isSameInstanceAs(embEntity1.getKey());
    embEntity1.setProperty("a", 1);
    assertThat(embEntity1.toString())
        .isEqualTo("<EmbeddedEntity [kind(\"keyname\")]:\n\ta = 1\n>\n");

    EmbeddedEntity embEntity2 = new EmbeddedEntity();
    embEntity2.setKey(KeyFactory.createKey("kind", "keyname"));
    embEntity2.setProperty("a", 1);
    assertThat(embEntity2).isEqualTo(embEntity1);
    assertThat(embEntity2.hashCode()).isEqualTo(embEntity1.hashCode());

    embEntity2 = embEntity1.clone();
    assertThat(embEntity2).isNotSameInstanceAs(embEntity1);
    assertThat(embEntity2).isEqualTo(embEntity1);
  }
}
