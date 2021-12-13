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
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.Integer.signum;
import static org.junit.Assert.assertThrows;

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService Key class.
 *
 */
@RunWith(JUnit4.class)
public class KeyTest {

  private final LocalServiceTestHelper helper = new LocalServiceTestHelper();

  @Before
  public void setUp() throws Exception {
    helper.setUp();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  @Test
  public void testEquals() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    Key key2 = new Key("foo");
    key2.simulatePutForTesting(12345L);
    Key key3 = new Key("foo");
    key3.simulatePutForTesting(12346L);
    Key key4 = new Key("foo");

    assertThat(key2).isEqualTo(key1);
    assertThat(key3).isNotEqualTo(key1);
    assertThat(key3).isNotEqualTo(key2);
    assertThat(key4).isNotEqualTo(key1);
    assertThat(key4).isNotEqualTo(key3);

    // Keys with name instead of id.
    Key key5 = new Key("foo", "name 1");
    Key key6 = new Key("foo", "name 1");
    Key key7 = new Key("foo", "name 2");

    assertThat(key6).isEqualTo(key5);
    assertThat(key7).isNotEqualTo(key5);
    assertThat(key7).isNotEqualTo(key6);
    assertThat(key1).isNotEqualTo(key5);
    assertThat(key2).isNotEqualTo(key5);
    assertThat(key3).isNotEqualTo(key5);
    assertThat(key4).isNotEqualTo(key5);

    // Testing unassigned ids equality
    Key key8 = new Key("foo");
    assertThat(key4).isNotEqualTo(key8);
    assertThat(key8.equals(key4, false)).isTrue();
    assertThat(key4.equals(key8, false)).isTrue();
    assertThat(key4.equals(key5, false)).isFalse();

    Key key9 = new Key("bar");
    key8.simulatePutForTesting(123L);
    key9.simulatePutForTesting(123L);
    assertThat(key9).isNotEqualTo(key8);
  }

  @Test
  public void testHashCode() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    Key key2 = new Key("foo");
    key2.simulatePutForTesting(12345L);
    Key key3 = new Key("foo");
    key3.simulatePutForTesting(12346L);
    Key key4 = new Key("foo");

    assertThat(key2.hashCode()).isEqualTo(key1.hashCode());
    // These could theoretically fail due to a coincidence. They
    // should be stable from run to run, though.
    assertThat(key3.hashCode()).isNotEqualTo(key1.hashCode());
    assertThat(key3.hashCode()).isNotEqualTo(key2.hashCode());
    assertThat(key4.hashCode()).isNotEqualTo(key1.hashCode());
    assertThat(key4.hashCode()).isNotEqualTo(key3.hashCode());

    // Keys with name instead of id.
    Key key5 = new Key("foo", "name 1");
    Key key6 = new Key("foo", "name 1");
    Key key7 = new Key("foo", "name 2");

    assertThat(key6.hashCode()).isEqualTo(key5.hashCode());
    assertThat(key7.hashCode()).isNotEqualTo(key5.hashCode());
    assertThat(key7.hashCode()).isNotEqualTo(key6.hashCode());
    assertThat(key1.hashCode()).isNotEqualTo(key5.hashCode());
    assertThat(key2.hashCode()).isNotEqualTo(key5.hashCode());
    assertThat(key3.hashCode()).isNotEqualTo(key5.hashCode());
    assertThat(key4.hashCode()).isNotEqualTo(key5.hashCode());
  }

  @Test
  public void testParent() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    Key key2 = new Key("bar", key1);
    key2.simulatePutForTesting(12346L);
    Key key3 = new Key("bar", key1, "name");

    assertThat(key2.getParent()).isEqualTo(key1);
    assertThat(key3.getParent()).isEqualTo(key1);
  }

  @Test
  public void testKind() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    Key key2 = new Key("bar", key1);
    Key key3 = new Key("baz", key1, "name");

    assertThat(key1.getKind()).isEqualTo("foo");
    assertThat(key2.getKind()).isEqualTo("bar");
    assertThat(key3.getKind()).isEqualTo("baz");
  }

  @Test
  public void testToString() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    Key key2 = new Key("bar", key1);
    Key key3 = new Key("baz", key1, "name");

    assertThat(key1.toString()).isEqualTo("foo(12345)");
    assertThat(key2.toString()).isEqualTo("foo(12345)/bar(no-id-yet)");
    assertThat(key3.toString()).isEqualTo("foo(12345)/baz(\"name\")");
  }

  @Test
  public void testSerialization() throws Exception {
    Key key1 = new Key("foo");
    key1.simulatePutForTesting(12345L);
    Key key2 = new Key("bar", key1);
    key2.simulatePutForTesting(67890L);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(key2);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Key readKey = (Key) iis.readObject();

    assertThat(readKey).isNotSameInstanceAs(key2);
    assertThat(readKey).isEqualTo(key2);
  }

  @Test
  public void testSerializationWithParentKey() throws IOException {
    // This unit test was inspired by:
    // http://code.google.com/p/googleappengine/issues/detail?id=2088
    // http://code.google.com/p/googleappengine/issues/detail?id=3060
    Key expectedKey = KeyFactory.createKey(null, "myKind", "name");
    Key actualKey = KeyFactory.stringToKey(KeyFactory.keyToString(expectedKey));
    assertThat(actualKey).isEqualTo(expectedKey);

    Key expectedKeyWithParent = KeyFactory.createKey(expectedKey, "myKind", "name");
    Key actualKeyWithParent = KeyFactory.stringToKey(KeyFactory.keyToString(expectedKeyWithParent));
    assertThat(actualKeyWithParent).isEqualTo(expectedKeyWithParent);

    byte[] expectedBytes = doSerialize(expectedKeyWithParent);
    byte[] actualBytes = doSerialize(actualKeyWithParent);
    assertThat(actualBytes).isEqualTo(expectedBytes);
  }

  @Test
  public void testGetChild_id() {
    Key keyParent = new Key("foo", null, 12345L);
    Key keyChild = keyParent.getChild("bar", 6789L);
    assertThat(new Key("bar", keyParent, 6789L)).isEqualTo(keyChild);
  }

  @Test
  public void testGetChild_name() {
    Key keyParent = new Key("foo", null, 12345L);
    Key keyChild = keyParent.getChild("bar", "child");
    assertThat(new Key("bar", keyParent, "child")).isEqualTo(keyChild);
  }

  @Test
  public void testGetChild_parentMustBeComplete() {
    Key keyParentIncomplete = new Key("foo");
    assertThat(keyParentIncomplete.isComplete()).isFalse();

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class, () -> keyParentIncomplete.getChild("bar", "child"));
    assertThat(e).hasMessageThat().isEqualTo("Cannot get a child of an incomplete key.");

    e =
        assertThrows(
            IllegalStateException.class, () -> keyParentIncomplete.getChild("bar", 12345L));
    assertThat(e).hasMessageThat().isEqualTo("Cannot get a child of an incomplete key.");
  }

  private void doParentMustHaveSameAppId(AppIdNamespace appid1, AppIdNamespace appid2) {
    Key pk1 = new Key("kind", null, 3, null, appid1);
    Key pk2 = new Key("kind", null, 3, null, appid2);
    if (appid1.equals(appid2)) {
      new Key("kind", pk1, 3, null, appid2);
      new Key("kind", pk2, 3, null, appid1);
    } else {
      assertThrows(IllegalArgumentException.class, () -> new Key("kind", pk1, 3, null, appid2));
      assertThrows(IllegalArgumentException.class, () -> new Key("kind", pk2, 3, null, appid1));
    }
  }

  @Test
  public void testParentMustHaveSameAppId() {
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1"),
        AppIdNamespace.parseEncodedAppIdNamespace("app2"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1"),
        AppIdNamespace.parseEncodedAppIdNamespace("app1"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"),
        AppIdNamespace.parseEncodedAppIdNamespace("app2"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"),
        AppIdNamespace.parseEncodedAppIdNamespace("app1"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"),
        AppIdNamespace.parseEncodedAppIdNamespace("app2!namespace"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"),
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"),
        AppIdNamespace.parseEncodedAppIdNamespace("app2!namespace_2"));
    doParentMustHaveSameAppId(
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace"),
        AppIdNamespace.parseEncodedAppIdNamespace("app1!namespace_2"));
  }

  @Test
  public void testNewKeyInheritsParentAppIdNamespaceIfNotSpecified() {
    Key parent =
        new Key(
            "kind",
            null,
            3,
            null,
            AppIdNamespace.parseEncodedAppIdNamespace("an-app-id!a-namespace-name"));
    Key child = new Key("kind", parent, 3, null, null);
    assertThat(child.getAppIdNamespace()).isEqualTo(parent.getAppIdNamespace());

    assertThrows(
        IllegalArgumentException.class,
        () -> new Key("kind", parent, 3, null, AppIdNamespace.parseEncodedAppIdNamespace("app1")));
  }

  private void testCompareToWithSameParentKey(Key parent) {
    AppIdNamespace app1 = AppIdNamespace.parseEncodedAppIdNamespace("app1");
    Key k1 = new Key("k1", parent, Key.NOT_ASSIGNED, null, app1);
    // key is equal to itself
    checkCompare(k1);

    Key k2 = new Key("k1", parent, Key.NOT_ASSIGNED, null, app1);
    // neither key has id or name
    assertThat(k1.compareTo(k2)).isEqualTo(Key.compareToWithIdentityHash(k1, k2));

    k1 = new Key("k1", parent, 1, null, app1);
    k2 = new Key("k1", parent, 1, null, app1);
    // app1.k1.1
    // app1.k1.1
    assertThat(k1.compareTo(k2)).isEqualTo(0);
    assertThat(k2.compareTo(k1)).isEqualTo(0);

    k1 = new Key("k1", parent, 1, null, app1);
    k2 = new Key("k1", parent, 2, null, app1);
    // app1.k1.1
    // app1.k1.2
    checkCompare(k1, k2);

    k1 = new Key("k1", parent, Key.NOT_ASSIGNED, "yar", app1);
    k2 = new Key("k1", parent, Key.NOT_ASSIGNED, "yar", app1);
    // app1.k1.yar
    // app1.k1.yar
    assertThat(k1.compareTo(k2)).isEqualTo(0);
    assertThat(k2.compareTo(k1)).isEqualTo(0);

    k1 = new Key("k1", parent, Key.NOT_ASSIGNED, "yar", app1);
    k2 = new Key("k1", parent, Key.NOT_ASSIGNED, "yara", app1);
    // app1.k1.yar
    // app1.k1.yara
    checkCompare(k1, k2);

    // app1.k1.2
    // app1.k1.yar
    k1 = new Key("k1", parent, 2, null, app1);
    k2 = new Key("k1", parent, Key.NOT_ASSIGNED, "yar", app1);
    checkCompare(k1, k2);

    k1 = new Key("k1", parent, Key.NOT_ASSIGNED, null, app1);
    k2 = new Key("k2", parent, Key.NOT_ASSIGNED, null, app1);
    // app1.k1.?
    // app1.k2.?
    checkCompare(k1, k2);
  }

  @Test
  public void testCompareTo() {
    AppIdNamespace app1 = AppIdNamespace.parseEncodedAppIdNamespace("app1");
    testCompareToWithSameParentKey(null);
    Key p1 = new Key("p1", null, Key.NOT_ASSIGNED, null, app1);
    testCompareToWithSameParentKey(p1);

    Key k1 = new Key("k1", null, Key.NOT_ASSIGNED, null, app1);
    Key k2 = new Key("k1", p1, Key.NOT_ASSIGNED, null, app1);
    // app1.k1.?
    // app1.p1.?.app1.k1.?
    checkCompare(k1, k2);

    Key p2 = new Key("p2", null, Key.NOT_ASSIGNED, null, app1);
    k1 = new Key("k1", p1, Key.NOT_ASSIGNED, null, app1);
    k2 = new Key("k1", p2, Key.NOT_ASSIGNED, null, app1);
    // app1.p1.?.app1.k1.?
    // app1.p2.?.app1.k1.?
    checkCompare(k1, k2);

    k1 = new Key("k1", p1, 5, null, app1);
    k2 = new Key("k1", p2, 4, null, app1);
    // app1.p1.?.app1.k1.5
    // app1.p2.?.app1.k1.4
    checkCompare(k1, k2);

    // app1.k0.4.app1.k1.4
    // app1.k1.5
    p1 = new Key("k0", null, 4, null, app1);
    k1 = new Key("k1", p1, 4, null, app1);
    k2 = new Key("k1", null, 5, null, app1);
    checkCompare(k1, k2);

    // app1.k1.4
    // app1.k0.4.app1.k1.5
    k1 = new Key("k1", null, 4, null, app1);
    k2 = new Key("k1", p1, 5, null, app1);
    checkCompare(k2, k1);

    p1 = new Key("k1", null, 4, null, app1);
    p2 = new Key("k1", p1, 4, null, app1);
    k1 = new Key("k1", p2, 4, null, app1);
    k2 = new Key("k1", p1, 4, null, app1);
    // app1.k1.4.app1.k1.4.app1.k1.4
    // app1.k1.4.app1.k1.4
    checkCompare(k2, k1);
  }

  /**
   * Checks that the given Keys are in ascending order according to Key.compareTo. Each key is
   * compared to itself and each other key, and the result should correspond to their positions in
   * the array.
   */
  private static void checkCompare(Key... keys) {
    for (int i = 0; i < keys.length; i++) {
      for (int j = 0; j < keys.length; j++) {
        assertWithMessage("%s :: %s", keys[i], keys[j])
            .that(signum(keys[i].compareTo(keys[j])))
            .isEqualTo(signum(Integer.compare(i, j)));
      }
    }
  }

  @Test
  public void testGetRootKey() {
    Key key1 = KeyFactory.createKey("foo", 1);
    Key key2 = KeyFactory.createKey(key1, "foo", 2);
    Key key3 = KeyFactory.createKey(key2, "foo", 3);
    assertThat(key1.getRootKey()).isSameInstanceAs(key1);
    assertThat(key2.getRootKey()).isSameInstanceAs(key1);
    assertThat(key3.getRootKey()).isSameInstanceAs(key1);
  }

  private Key doDeserialize(byte[] keyBytes) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream is = new ByteArrayInputStream(keyBytes);
        ObjectInputStream oi = new ObjectInputStream(is)) {
      Object newObj = oi.readObject();
      if (!(newObj instanceof Key)) {
        throw new IOException("Failed to deserialize Key object");
      }
      return (Key) newObj;
    }
  }

  private byte[] doSerialize(Key key) throws IOException {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream oo = new ObjectOutputStream(os)) {
      oo.writeObject(key);
      return os.toByteArray();
    }
  }

  @Test
  public void testLegacyKeyDeSerialization() throws IOException, ClassNotFoundException {
    int i = 0;
    for (Key key : KeyTestData.KEYS) {
      byte[] keyBytes = doSerialize(key);
      byte[] keyBytesLegacy = KeyTestData.KEY_BYTES[i++];
      assertThat(doDeserialize(keyBytes)).isEqualTo(key);
      assertThat(doDeserialize(keyBytesLegacy)).isEqualTo(key);
    }
  }
}
