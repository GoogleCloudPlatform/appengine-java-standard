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
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.Entity.WrappedValueImpl;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService Entity class.
 *
 * <p>TODO Split PropertyContainer tests out.
 */
@RunWith(JUnit4.class)
public class EntityTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testGetKind() {
    Entity entity = new Entity("foo");
    assertThat(entity.getKind()).isEqualTo("foo");
  }

  @Test
  public void testGetParent() {
    Key parentKey = new Key("foo");
    parentKey.simulatePutForTesting(12345L);

    Entity entity = new Entity("bar", parentKey);
    assertThat(entity.getParent()).isEqualTo(parentKey);
  }

  @Test
  public void testKnownType() {
    Entity entity = new Entity("foo");
    entity.setProperty("aString", "testing");
    entity.setProperty("anInteger", 42);
    entity.setProperty("aLong", 10000000000L);
    entity.setProperty("aDouble", 42.24);
  }

  @Test
  public void testUnknownType() {
    Entity entity = new Entity("foo");
    assertThrows(
        IllegalArgumentException.class,
        () -> entity.setProperty("unknownType", new StringWriter()));
  }

  @Test
  public void testSerialization() throws Exception {
    Entity entity = new Entity("foo");
    entity.setProperty("aString", "testing");
    entity.setProperty("anInteger", 42);
    entity.setProperty("aLong", 10000000000L);
    entity.setProperty("aDouble", 42.24);
    entity.setProperty("aBoolean", true);
    entity.setUnindexedProperty("unindexed", "unindexed");
    entity.setProperty("aText", new Text("text"));
    entity.setProperty("aBlob", new Blob(new byte[] {1, 2, 3}));
    entity.getKey().simulatePutForTesting(12345L);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(entity);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Entity readEntity = (Entity) iis.readObject();

    assertThat(readEntity).isNotSameInstanceAs(entity);
    assertThat(readEntity).isEqualTo(entity); // only tests key equality
    assertThat(readEntity.getPropertyMap()).isEqualTo(entity.getPropertyMap());

    assertThat(readEntity.isUnindexedProperty("aString")).isFalse();
    assertThat(readEntity.isUnindexedProperty("unindexed")).isTrue();

    assertWithMessage("isUnindexed for text")
        .that(readEntity.isUnindexedProperty("aText"))
        .isTrue();
    assertWithMessage("isUnindexed for blob")
        .that(readEntity.isUnindexedProperty("aBlob"))
        .isTrue();
  }

  @Test
  public void testKeyIncompleteness() {
    Entity rootEntity = new Entity("foo");
    assertThat(rootEntity.getKey().isComplete()).isFalse();
    assertThat(rootEntity.getKey().getName()).isNull();
    assertThat(rootEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);

    rootEntity = new Entity(new Entity("foo").getKey());
    assertThat(rootEntity.getKey().isComplete()).isFalse();
    assertThat(rootEntity.getKey().getName()).isNull();
    assertThat(rootEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);

    Entity childEntity = new Entity("foo", rootEntity.getKey());
    assertThat(childEntity.getKey().isComplete()).isFalse();
    assertThat(childEntity.getKey().getName()).isNull();
    assertThat(childEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);

    childEntity = new Entity(new Entity("foo", rootEntity.getKey()).getKey());
    assertThat(childEntity.getKey().isComplete()).isFalse();
    assertThat(childEntity.getKey().getName()).isNull();
    assertThat(childEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);
  }

  @Test
  public void testCompleteKeyEntityConstructors() {
    Entity rootEntity = new Entity("foo", "bar");
    assertThat(rootEntity.getKey().isComplete()).isTrue();
    assertThat(rootEntity.getKey().getName()).isEqualTo("bar");
    assertThat(rootEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);

    Entity childEntity = new Entity("foo", "baz", rootEntity.getKey());
    assertThat(childEntity.getKey().isComplete()).isTrue();
    assertThat(childEntity.getKey().getName()).isEqualTo("baz");
    assertThat(childEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);

    childEntity = new Entity("foo", "bat", null);
    assertThat(childEntity.getKey().isComplete()).isTrue();
    assertThat(childEntity.getKey().getName()).isEqualTo("bat");
    assertThat(childEntity.getKey().getId()).isEqualTo(Key.NOT_ASSIGNED);

    Entity rootIdEntity = new Entity("foo", 42);
    assertThat(rootIdEntity.getKey().isComplete()).isTrue();
    assertThat(rootIdEntity.getKey().getId()).isEqualTo(42);
    assertThat(rootIdEntity.getKey().getName()).isEqualTo(null);

    Entity childIdEntity = new Entity("foo", 54, rootIdEntity.getKey());
    assertThat(childIdEntity.getKey().isComplete()).isTrue();
    assertThat(childIdEntity.getKey().getId()).isEqualTo(54);
    assertThat(childIdEntity.getKey().getName()).isEqualTo(null);

    childIdEntity = new Entity("foo", 69, null);
    assertThat(childIdEntity.getKey().isComplete()).isTrue();
    assertThat(childIdEntity.getKey().getId()).isEqualTo(69);
    assertThat(childIdEntity.getKey().getName()).isEqualTo(null);

    // Next id in sequence is 87.
  }

  @Test
  public void testSetIndexedProperty() {
    Entity entity = new Entity("foo");

    IllegalArgumentException e1 =
        assertThrows(
            IllegalArgumentException.class,
            () -> entity.setIndexedProperty("prop", new Text("text")));
    assertThat(e1).hasMessageThat().contains("is not indexable");

    IllegalArgumentException e2 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                entity.setIndexedProperty("prop", ImmutableList.of(1L, new Blob(new byte[] {1}))));
    assertThat(e2).hasMessageThat().contains("is not indexable");

    entity.setIndexedProperty("prop", new EmbeddedEntity());
    WrappedValueImpl wrappedValue = (WrappedValueImpl) entity.getPropertyMap().get("prop");
    assertThat(wrappedValue.isIndexed()).isTrue();
    assertThat(wrappedValue.getForceIndexedEmbeddedEntity()).isTrue();
  }

  @Test
  public void testUnindexedValue() {
    Entity entity = new Entity("foo");
    entity.setProperty("indexed", "foo");
    entity.setUnindexedProperty("unindexed", "bar");
    entity.setProperty("text", new Text("hello"));
    entity.setProperty("blob", new Blob(new byte[] {1, 2, 3}));
    entity.setProperty("entity", new EmbeddedEntity());

    assertThat(entity.hasProperty("indexed")).isTrue();
    assertThat(entity.getProperty("indexed")).isEqualTo("foo");
    assertThat(entity.isUnindexedProperty("indexed")).isFalse();

    assertWithMessage("isUnindexed for text").that(entity.isUnindexedProperty("text")).isTrue();
    assertWithMessage("isUnindexed for blob").that(entity.isUnindexedProperty("blob")).isTrue();
    assertWithMessage("isUnindexed for embedded entity")
        .that(entity.isUnindexedProperty("entity"))
        .isTrue();

    assertThat(entity.hasProperty("unindexed")).isTrue();
    assertThat(entity.getProperty("unindexed")).isEqualTo("bar");
    assertThat(entity.isUnindexedProperty("unindexed")).isTrue();

    assertThat(entity.getProperties())
        .containsExactly(
            "indexed", "foo",
            "unindexed", "bar",
            "text", new Text("hello"),
            "blob", new Blob(new byte[] {1, 2, 3}),
            "entity", new EmbeddedEntity());
    assertThat(entity.getPropertyMap())
        .containsExactly(
            "indexed", "foo",
            "unindexed", new Entity.UnindexedValue("bar"),
            "text", new Text("hello"),
            "blob", new Blob(new byte[] {1, 2, 3}),
            "entity", new EmbeddedEntity());

    EntityProto proto = EntityTranslator.convertToPb(entity);
    assertThat(proto.propertySize()).isEqualTo(1);
    assertThat(proto.rawPropertySize()).isEqualTo(4);
  }

  @Test
  public void testUnindexableValueInIndexedList() {
    Entity entity = new Entity("foo");
    entity.setProperty(
        "prop",
        ImmutableList.of(
            "string", new EmbeddedEntity(), new Text("text"), new Blob(new byte[] {1, 2, 3})));
    assertThat(entity.isUnindexedProperty("prop")).isFalse();
    EntityProto proto = EntityTranslator.convertToPb(entity);
    assertThat(proto.propertySize()).isEqualTo(1);
    assertThat(proto.rawPropertySize()).isEqualTo(3);
    Entity restoredEntity = EntityTranslator.createFromPb(proto);
    assertThat(restoredEntity.getPropertyMap()).isEqualTo(entity.getPropertyMap());
    assertThat(restoredEntity.isUnindexedProperty("prop")).isFalse();
  }

  @Test
  public void testUnindexedAfterIndexedAndBack() {
    Entity entity = new Entity("foo");

    entity.setProperty("foo", "bar");
    assertThat(entity.getProperties()).containsExactly("foo", "bar");
    assertThat(entity.getPropertyMap()).containsExactly("foo", "bar");
    assertThat(entity.isUnindexedProperty("foo")).isFalse();

    entity.setUnindexedProperty("foo", "bar");
    assertThat(entity.getProperties()).containsExactly("foo", "bar");
    assertThat(entity.getPropertyMap()).containsExactly("foo", new Entity.UnindexedValue("bar"));
    assertThat(entity.isUnindexedProperty("foo")).isTrue();

    entity.setProperty("foo", "bar");
    assertThat(entity.getProperties()).containsExactly("foo", "bar");
    assertThat(entity.getPropertyMap()).containsExactly("foo", "bar");
    assertThat(entity.isUnindexedProperty("foo")).isFalse();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testClone() {
    Entity entity = new Entity("foo", "name");
    entity.setProperty("indexed", "testing");
    entity.setUnindexedProperty("unindexed", 42);
    entity.setProperty("indexed_list", Lists.newArrayList(1, 2, 3, new Date()));
    entity.setUnindexedProperty("unindexed_list", Lists.newArrayList("a", "b", "c", new Date()));
    Date indexedDate = new Date(33);
    entity.setProperty("indexed_date", indexedDate);

    Date unindexedDate = new Date(34);
    entity.setProperty("unindexed_date", unindexedDate);

    Entity cloned = entity.clone();

    // Verify different object
    assertThat(cloned).isNotSameInstanceAs(entity);

    // Verify values are equal
    assertThat(cloned).isEqualTo(entity);
    assertThat(cloned.getPropertyMap()).isEqualTo(entity.getPropertyMap());

    // Verify the lists are different objects
    assertThat(cloned.getProperty("indexed_list"))
        .isNotSameInstanceAs(entity.getProperty("indexed_list"));
    assertThat(cloned.getProperty("unindexed_list"))
        .isNotSameInstanceAs(entity.getProperty("unindexed_list"));

    // Verify the dates in the lists are different objects
    assertThat(((List<Object>) cloned.getProperty("indexed_list")).get(3))
        .isNotSameInstanceAs(((List<Object>) entity.getProperty("indexed_list")).get(3));
    assertThat(((List<Object>) cloned.getProperty("unindexed_list")).get(3))
        .isNotSameInstanceAs(((List<Object>) entity.getProperty("unindexed_list")).get(3));

    // Verify the dates are different objects
    assertThat(cloned.getProperty("indexed_date"))
        .isNotSameInstanceAs(entity.getProperty("indexed_date"));
    assertThat(cloned.getProperty("inindexed_date"))
        .isNotSameInstanceAs(entity.getProperty("unindexed_date"));
  }

  @Test
  public void testCloneIncompleteKeyEquality() {
    Entity entity1 = new Entity("foo");
    Entity entity2 = new Entity("foo");
    assertThat(entity2).isNotEqualTo(entity1);

    // NOTE: Seems like these should not be equal, but they are.
    assertThat(entity1.clone()).isEqualTo(entity1);
  }

  @Test
  public void testWrappedValue() {
    WrappedValueImpl firstString = new WrappedValueImpl("string", true, true);
    WrappedValueImpl secondString = new WrappedValueImpl("string", true, false);
    WrappedValueImpl thirdString = new WrappedValueImpl("string", false, false);
    WrappedValueImpl firstInt = new WrappedValueImpl(1, true, true);
    WrappedValueImpl firstNull = new WrappedValueImpl(null, true, false);

    new EqualsTester()
        .addEqualityGroup(firstString)
        .addEqualityGroup(firstInt)
        .addEqualityGroup(firstNull)
        .testEquals();

    assertThat(secondString.hashCode()).isNotEqualTo(firstString.hashCode());
    assertThat(thirdString.hashCode()).isNotEqualTo(secondString.hashCode());
    assertThat(firstInt.hashCode()).isNotEqualTo(firstString.hashCode());
    assertThat(firstNull.hashCode()).isNotEqualTo(firstString.hashCode());

    assertThat(firstString.getValue()).isEqualTo("string");
    assertThat(firstInt.getValue()).isEqualTo(1);
    assertThat(firstNull.getValue()).isEqualTo(null);

    assertThat(firstString.isIndexed()).isTrue();
    assertThat(thirdString.isIndexed()).isFalse();

    assertThat(firstString.getForceIndexedEmbeddedEntity()).isTrue();
    assertThat(secondString.getForceIndexedEmbeddedEntity()).isFalse();

    assertThrows(IllegalArgumentException.class, () -> new WrappedValueImpl("string", false, true));
  }

  @Test
  public void testCollectionChanges() {
    // Collections can be changed after being set as property value. Such behavior is not desirable
    // but we need to live with it. This test verifies that the validation of values performed
    // in setProperty and variants is repeated when serializing to proto.

    List<Object> listValue = Lists.newArrayList();
    Entity entity = new Entity("foo");
    entity.setProperty("list", listValue);

    listValue.add(1);
    EntityTranslator.convertToPb(entity);

    listValue.clear();
    listValue.add(new Entity("foo")); // Entity is not a supported value type.
    assertThrows(UnsupportedOperationException.class, () -> EntityTranslator.convertToPb(entity));

    listValue.clear();
    listValue.add(new Text("text")); // Unindexable but we used setProperty().
    EntityTranslator.convertToPb(entity);

    listValue.clear();
    listValue.add(new EmbeddedEntity()); // Unindexed because we used setProperty().
    EntityTranslator.convertToPb(entity);

    listValue.clear();
    entity.setIndexedProperty("list", listValue);

    listValue.clear();
    listValue.add(new Blob(new byte[] {1, 2})); // Unindexable and we used setIndexedProperty().
    assertThrows(UnsupportedOperationException.class, () -> EntityTranslator.convertToPb(entity));

    listValue.clear();
    listValue.add(new EmbeddedEntity()); // Acceptable for setIndexedProperty();
    EntityTranslator.convertToPb(entity);
  }
}
