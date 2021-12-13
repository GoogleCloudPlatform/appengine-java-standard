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

import static com.google.common.io.BaseEncoding.base64Url;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService Key class.
 *
 */
@RunWith(JUnit4.class)
public class KeyFactoryTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testOneLevel() {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);

    Key key2 = KeyFactory.stringToKey(KeyFactory.keyToString(key1));
    assertThat(key2).isEqualTo(key1);
  }

  @Test
  public void testTwoLevels() {
    Key key1 = new Key("foo", "name");
    Key key2 = new Key("bar", key1);
    key2.simulatePutForTesting(12346L);

    Key key3 = KeyFactory.stringToKey(KeyFactory.keyToString(key2));
    assertThat(key3).isEqualTo(key2);
    assertThat(key3.getParent()).isEqualTo(key1);
  }

  @Test
  public void testCreateKey_id() {
    assertThat(KeyFactory.createKey("foo", 12345L)).isEqualTo(new Key("foo", null, 12345L));
    assertThat(KeyFactory.createKey("foo", -12345L)).isEqualTo(new Key("foo", null, -12345L));
  }

  @Test
  public void testCreateKey_name() {
    assertThat(KeyFactory.createKey("foo", "name")).isEqualTo(new Key("foo", null, "name"));
    assertThat(KeyFactory.createKey("foo", " ")).isEqualTo(new Key("foo", null, " "));
  }

  @Test
  public void testCreateKeyString_id() {
    assertThat(KeyFactory.createKeyString("foo", 12345L))
        .isEqualTo(KeyFactory.keyToString(new Key("foo", null, 12345L)));
    assertThat(KeyFactory.createKeyString("foo", -12345L))
        .isEqualTo(KeyFactory.keyToString(new Key("foo", null, -12345L)));
  }

  @Test
  public void testCreateKeyString_name() {
    assertThat(KeyFactory.createKeyString("foo", "name"))
        .isEqualTo(KeyFactory.keyToString(new Key("foo", null, "name")));
    assertThat(KeyFactory.createKeyString("foo", " "))
        .isEqualTo(KeyFactory.keyToString(new Key("foo", null, " ")));
  }

  @Test
  public void testCreateKey_cannotCreateIncompleteKey() {
    IllegalArgumentException e1 =
        assertThrows(IllegalArgumentException.class, () -> KeyFactory.createKey("foo", 0L));
    assertThat(e1).hasMessageThat().isEqualTo("id cannot be zero");

    IllegalArgumentException e2 =
        assertThrows(IllegalArgumentException.class, () -> KeyFactory.createKey("foo", null));
    assertThat(e2).hasMessageThat().isEqualTo("name cannot be null or empty");

    IllegalArgumentException e3 =
        assertThrows(IllegalArgumentException.class, () -> KeyFactory.createKey("foo", ""));
    assertThat(e3).hasMessageThat().isEqualTo("name cannot be null or empty");
  }

  @Test
  public void testRandomBase64EncodedStringDoesNotParse() {
    // ok not really random, more like arbitrary
    byte[] randomBytes = getClass().getName().getBytes();
    String encoded = base64Url().omitPadding().encode(randomBytes);
    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> KeyFactory.stringToKey(encoded));
    assertThat(iae).hasMessageThat().isEqualTo("Could not parse Reference");
  }

  @Test
  public void testBuilder() {
    KeyFactory.Builder builder = new KeyFactory.Builder("foo", "bar");
    Key fooBarKey = new Key("foo", "bar");
    assertThat(builder.getKey()).isEqualTo(fooBarKey);
    assertThat(builder.getKey().getParent()).isNull();
    assertThat(builder.getString()).isEqualTo(KeyFactory.keyToString(fooBarKey));

    builder.addChild("yam", 23);

    Key yamKey = new Key("yam", fooBarKey, 23);
    assertThat(builder.getKey()).isEqualTo(yamKey);
    assertThat(builder.getKey().getParent()).isEqualTo(fooBarKey);
    assertThat(builder.getString()).isEqualTo(KeyFactory.keyToString(yamKey));

    // should we do one more?  ok we'll do one more
    builder.addChild("bam", "jam");

    Key bamKey = new Key("bam", yamKey, "jam");
    assertThat(builder.getKey()).isEqualTo(bamKey);
    assertThat(builder.getKey().getParent()).isEqualTo(yamKey);
    assertThat(builder.getString()).isEqualTo(KeyFactory.keyToString(bamKey));
  }

  @Test
  public void testNameBeginsWithDigits() {
    Key key1 = KeyFactory.createKey("foo", "123");
    Key key2 = KeyFactory.createKey("foo", 123);

    assertThat(key2).isNotEqualTo(key1);
    assertThat(key2.toString()).isNotEqualTo(key1.toString());
    assertThat(KeyFactory.keyToString(key2)).isNotEqualTo(KeyFactory.keyToString(key1));
  }
}
