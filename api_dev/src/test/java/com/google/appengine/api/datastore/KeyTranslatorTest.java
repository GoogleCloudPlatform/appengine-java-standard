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
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Path;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService Key class.
 *
 */
@RunWith(JUnit4.class)
public class KeyTranslatorTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testConvertToPbOneLevelNotPut() throws Exception {
    Key key1 = new Key("foo");
    assertThat(key1.isComplete()).isFalse();

    Reference ref1 = KeyTranslator.convertToPb(key1);
    assertThat(ref1.getPath().getElementCount()).isEqualTo(1);
    assertThat(ref1.getPath().getElementList().get(0).getType()).isEqualTo("foo");
    assertThat(ref1.getPath().getElementList().get(0).hasId()).isFalse();
    assertThat(ref1.getPath().getElementList().get(0).hasName()).isFalse();
  }

  @Test
  public void testConvertToPbOneLevelPut() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    assertThat(key1.isComplete()).isTrue();
    assertThat(key1.getId()).isEqualTo(12345L);

    Reference ref1 = KeyTranslator.convertToPb(key1);
    assertThat(ref1.getPath().getElementCount()).isEqualTo(1);
    assertThat(ref1.getPath().getElementList().get(0).getType()).isEqualTo("foo");
    assertThat(ref1.getPath().getElementList().get(0).hasId()).isTrue();
    assertThat(ref1.getPath().getElementList().get(0).getId()).isEqualTo(12345L);
    assertThat(ref1.getPath().getElementList().get(0).hasName()).isFalse();
  }

  @Test
  public void testConvertToPbOneLevelWithName() throws Exception {
    Key key1 = new Key("foo", "name");
    assertThat(key1.isComplete()).isTrue();

    Reference ref1 = KeyTranslator.convertToPb(key1);
    assertThat(ref1.getPath().getElementCount()).isEqualTo(1);
    assertThat(ref1.getPath().getElementList().get(0).getType()).isEqualTo("foo");
    assertThat(ref1.getPath().getElementList().get(0).hasId()).isFalse();
    assertThat(ref1.getPath().getElementList().get(0).hasName()).isTrue();
    assertThat(ref1.getPath().getElementList().get(0).getName()).isEqualTo("name");
  }

  @Test
  public void testConvertToPbTwoLevelsNotPut() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);

    Key key2 = new Key("bar", key1);
    assertThat(key2.isComplete()).isFalse();

    Reference ref2 = KeyTranslator.convertToPb(key2);
    assertThat(ref2.getPath().getElementCount()).isEqualTo(2);

    assertThat(ref2.getPath().getElementList().get(0).getType()).isEqualTo("foo");
    assertThat(ref2.getPath().getElementList().get(0).hasId()).isTrue();
    assertThat(ref2.getPath().getElementList().get(0).getId()).isEqualTo(12345L);
    assertThat(ref2.getPath().getElementList().get(0).hasName()).isFalse();

    assertThat(ref2.getPath().getElementList().get(1).getType()).isEqualTo("bar");
    assertThat(ref2.getPath().getElementList().get(1).hasId()).isFalse();
    assertThat(ref2.getPath().getElementList().get(1).hasName()).isFalse();
  }

  @Test
  public void testConvertToPbTwoLevelsPut() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);

    Key key2 = new Key("bar", key1);
    key2.simulatePutForTesting(12346L);
    assertThat(key2.isComplete()).isTrue();
    assertThat(key2.getId()).isEqualTo(12346L);

    Reference ref2 = KeyTranslator.convertToPb(key2);
    assertThat(ref2.getPath().getElementCount()).isEqualTo(2);

    assertThat(ref2.getPath().getElementList().get(0).getType()).isEqualTo("foo");
    assertThat(ref2.getPath().getElementList().get(0).hasId()).isTrue();
    assertThat(ref2.getPath().getElementList().get(0).getId()).isEqualTo(12345L);
    assertThat(ref2.getPath().getElementList().get(0).hasName()).isFalse();

    assertThat(ref2.getPath().getElementList().get(1).getType()).isEqualTo("bar");
    assertThat(ref2.getPath().getElementList().get(1).hasId()).isTrue();
    assertThat(ref2.getPath().getElementList().get(1).getId()).isEqualTo(12346L);
    assertThat(ref2.getPath().getElementList().get(1).hasName()).isFalse();
  }

  @Test
  public void testConvertToPbTwoLevelsWithName() throws Exception {
    Key key1 = new Key("foo", "name");

    Key key2 = new Key("bar", key1, "name");
    assertThat(key2.isComplete()).isTrue();
    assertThat(key2.getName()).isEqualTo("name");

    Reference ref2 = KeyTranslator.convertToPb(key2);
    assertThat(ref2.getPath().getElementCount()).isEqualTo(2);

    assertThat(ref2.getPath().getElementList().get(0).getType()).isEqualTo("foo");
    assertThat(ref2.getPath().getElementList().get(0).hasName()).isTrue();
    assertThat(ref2.getPath().getElementList().get(0).getName()).isEqualTo("name");
    assertThat(ref2.getPath().getElementList().get(0).hasId()).isFalse();

    assertThat(ref2.getPath().getElementList().get(1).getType()).isEqualTo("bar");
    assertThat(ref2.getPath().getElementList().get(1).hasName()).isTrue();
    assertThat(ref2.getPath().getElementList().get(1).getName()).isEqualTo("name");
    assertThat(ref2.getPath().getElementList().get(1).hasId()).isFalse();
  }

  @Test
  public void testConvertFromPbNoElements() throws Exception {
    Reference ref = Reference.getDefaultInstance();
    assertThrows(IllegalArgumentException.class, () -> KeyTranslator.createFromPb(ref));
  }

  @Test
  public void testUpdateKey_Id() {
    Key key = new Key("yam");
    AppIdNamespace appIdNamespace = key.getAppIdNamespace();
    Reference.Builder ref = Reference.newBuilder().setApp("my app");
    Path.Builder path = Path.newBuilder();
    path.addElementBuilder().setId(23);
    ref.setPath(path.buildPartial());
    KeyTranslator.updateKey(ref.buildPartial(), key);
    assertThat(key.getAppIdNamespace()).isEqualTo(appIdNamespace);
    assertThat(key.getAppId()).isEqualTo(appIdNamespace.getAppId()); // coverage
    assertThat(key.getId()).isEqualTo(23);
    assertThat(key.getName()).isNull();
  }

  @Test
  public void testUpdateKey_Name() {
    Key key = new Key("yam", "harold");
    AppIdNamespace appIdNamespace = key.getAppIdNamespace();
    Reference.Builder ref = Reference.newBuilder().setApp("my app");
    Path path = Path.getDefaultInstance();
    ref.setPath(path);
    KeyTranslator.updateKey(ref.build(), key);
    assertThat(key.getAppIdNamespace()).isEqualTo(appIdNamespace);
    assertThat(key.getId()).isEqualTo(Key.NOT_ASSIGNED);
    assertThat(key.getName()).isEqualTo("harold");
  }
}
