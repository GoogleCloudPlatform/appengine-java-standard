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
import com.google.common.testing.EqualsTester;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KeyRangeTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testConstructor_BadInput() {
    assertThrows(IllegalArgumentException.class, () -> new KeyRange(null, null, 1, 2));

    assertThrows(IllegalArgumentException.class, () -> new KeyRange(null, "", 1, 2));

    assertThrows(IllegalArgumentException.class, () -> new KeyRange(null, "yam", 0, 2));

    assertThrows(IllegalArgumentException.class, () -> new KeyRange(null, "yam", 1, 0));

    assertThrows(
        IllegalArgumentException.class,
        () -> new KeyRange(new Entity("yam").getKey(), "yam", 1, 1));
  }

  @Test
  public void testConstructor_NoParent() {
    KeyRange range = new KeyRange(null, "k1", 1, 5);

    assertThat(range.getKind()).isEqualTo("k1");
    assertThat(range.getParent()).isEqualTo(null);

    assertThat(range.getStart().getId()).isEqualTo(1);
    assertThat(range.getStart().getKind()).isEqualTo("k1");
    assertThat(range.getStart().getParent()).isNull();

    assertThat(range.getEnd().getId()).isEqualTo(5);
    assertThat(range.getEnd().getKind()).isEqualTo("k1");
    assertThat(range.getEnd().getParent()).isNull();
  }

  @Test
  public void testConstructor_Parent() {
    Key parent = KeyFactory.createKey("parent kind", 33);
    KeyRange range = new KeyRange(parent, "k1", 1, 5);

    assertThat(range.getKind()).isEqualTo("k1");
    assertThat(range.getParent()).isEqualTo(parent);

    assertThat(range.getStart().getId()).isEqualTo(1);
    assertThat(range.getStart().getKind()).isEqualTo("k1");
    assertThat(range.getStart().getParent()).isEqualTo(parent);

    assertThat(range.getEnd().getId()).isEqualTo(5);
    assertThat(range.getEnd().getKind()).isEqualTo("k1");
    assertThat(range.getEnd().getParent()).isEqualTo(parent);
  }

  @Test
  public void testIterator_NoParent() {
    KeyRange range = new KeyRange(null, "k1", 1, 3);
    Iterator<Key> iter = range.iterator();
    assertThat(iter.hasNext()).isTrue();
    Key k = iter.next();
    assertThat(k.getId()).isEqualTo(1);
    assertThat(k.getKind()).isEqualTo("k1");
    assertThat(k.getParent()).isNull();

    assertThat(iter.hasNext()).isTrue();
    k = iter.next();
    assertThat(k.getId()).isEqualTo(2);
    assertThat(k.getKind()).isEqualTo("k1");
    assertThat(k.getParent()).isNull();

    assertThat(iter.hasNext()).isTrue();
    k = iter.next();
    assertThat(k.getId()).isEqualTo(3);
    assertThat(k.getKind()).isEqualTo("k1");
    assertThat(k.getParent()).isNull();

    assertThat(iter.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iter::next);

    assertThrows(UnsupportedOperationException.class, iter::remove);
  }

  @Test
  public void testIterator_Parent() {
    Key parent = KeyFactory.createKey("parent kind", 33);
    KeyRange range = new KeyRange(parent, "k1", 1, 3);
    Iterator<Key> iter = range.iterator();
    assertThat(iter.hasNext()).isTrue();
    Key k = iter.next();
    assertThat(k.getId()).isEqualTo(1);
    assertThat(k.getKind()).isEqualTo("k1");
    assertThat(k.getParent()).isEqualTo(parent);

    assertThat(iter.hasNext()).isTrue();
    k = iter.next();
    assertThat(k.getId()).isEqualTo(2);
    assertThat(k.getKind()).isEqualTo("k1");
    assertThat(k.getParent()).isEqualTo(parent);

    assertThat(iter.hasNext()).isTrue();
    k = iter.next();
    assertThat(k.getId()).isEqualTo(3);
    assertThat(k.getKind()).isEqualTo("k1");
    assertThat(k.getParent()).isEqualTo(parent);

    assertThat(iter.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iter::next);

    assertThrows(UnsupportedOperationException.class, iter::remove);
  }

  @Test
  public void testKeyRangeWithNullAppIdNamespace() {
    // This can happen when we deserialize KeyRange that was
    // created and serialized before we added AppIdNamespace to KeyRange
    KeyRange keyRange = new KeyRange(null, "k", 5, 10, null);
    int currentId = 5;
    for (Key key : keyRange) {
      assertThat(key.getKind()).isEqualTo("k");
      assertThat(key.getParent()).isNull();
      assertThat(DatastoreApiHelper.getCurrentAppId()).isEqualTo(key.getAppId());
      assertThat(key.getId()).isEqualTo(currentId++);
    }
    assertThat(currentId).isEqualTo(11);
    Key parent = KeyFactory.createKey("p", 1);
    keyRange = new KeyRange(parent, "k", 5, 10, null);
    currentId = 5;
    for (Key key : keyRange) {
      assertThat(key.getKind()).isEqualTo("k");
      assertThat(key.getParent()).isEqualTo(parent);
      assertThat(DatastoreApiHelper.getCurrentAppId()).isEqualTo(key.getAppId());
      assertThat(key.getId()).isEqualTo(currentId++);
    }
    assertThat(currentId).isEqualTo(11);
  }

  @Test
  public void testGetSize() {
    KeyRange range = new KeyRange(null, "yar", 1, 1);
    assertThat(range.getSize()).isEqualTo(1);
    range = new KeyRange(null, "yar", 1, 2);
    assertThat(range.getSize()).isEqualTo(2);
  }

  @Test
  public void testEquals() {
    KeyRange range = new KeyRange(null, "foo", 1, 2);
    KeyRange range2 = new KeyRange(KeyFactory.createKey("x", "name"), "foo", 1, 2);
    new EqualsTester()
        .addEqualityGroup(range, new KeyRange(null, "foo", 1, 2))
        .addEqualityGroup(new KeyRange(null, "foo", 2, 2))
        .addEqualityGroup(new KeyRange(null, "foo", 1, 3))
        .addEqualityGroup(new KeyRange(null, "foo2", 1, 2))
        .addEqualityGroup(range2, new KeyRange(KeyFactory.createKey("x", "name"), "foo", 1, 2))
        .testEquals();
  }
}
