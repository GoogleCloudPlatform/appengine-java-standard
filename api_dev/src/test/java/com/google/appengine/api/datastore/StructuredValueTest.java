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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link EmbeddedEntity}.
 *
 */
@RunWith(JUnit4.class)
public class StructuredValueTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testKeyEquality() {
    Entity entity1 = new Entity("Foo");
    Entity entity2 = new Entity("Foo");

    // Only tests key, and incomplete keys are special cased for entities
    assertThat(entity2.hashCode()).isEqualTo(entity1.hashCode());
    assertThat(entity2).isNotEqualTo(entity1);

    assertThat(toStructuredValue(entity2)).isEqualTo(toStructuredValue(entity1));
  }

  @Test
  public void testValueEquality() {
    Entity entity1 = new Entity("Foo", "name");
    Entity entity1a = new Entity("Foo", "name");
    Entity entity2 = new Entity("Foo", "name");
    entity1.setProperty("hi", 1);
    entity1a.setProperty("hi", 1);
    entity2.setProperty("hi", 2);

    // Only tests key
    assertThat(entity2.hashCode()).isEqualTo(entity1.hashCode());
    assertThat(entity2).isEqualTo(entity1);

    // Tests all values
    assertThat(toStructuredValue(entity1a).hashCode())
        .isEqualTo(toStructuredValue(entity1).hashCode());
    assertThat(toStructuredValue(entity1a)).isEqualTo(toStructuredValue(entity1));

    assertThat(toStructuredValue(entity2).hashCode())
        .isNotEqualTo(toStructuredValue(entity1).hashCode());
    assertThat(toStructuredValue(entity2)).isNotEqualTo(toStructuredValue(entity1));
  }

  @Test
  public void testValueEquality_Key() {
    Entity entity1 = new Entity("Foo", "name");
    entity1.setProperty("hi", 1);

    EmbeddedEntity noKey1 = new EmbeddedEntity();
    noKey1.setPropertiesFrom(entity1);
    EmbeddedEntity noKey2 = new EmbeddedEntity();
    noKey2.setPropertiesFrom(entity1);
    assertThat(noKey1.hashCode()).isNotEqualTo(toStructuredValue(entity1).hashCode());
    assertThat(noKey1).isNotEqualTo(toStructuredValue(entity1));

    assertThat(noKey2).isEqualTo(noKey1);
  }

  @Test
  public void testValueEquality_Unindexed() {
    Entity entity1 = new Entity("Foo", "name");
    Entity entity2 = new Entity("Foo", "name");
    entity1.setProperty("hi", 1);
    entity2.setUnindexedProperty("hi", 1);

    // Only tests key
    assertThat(entity2.hashCode()).isEqualTo(entity1.hashCode());
    assertThat(entity2).isEqualTo(entity1);

    // Tests all values
    assertThat(toStructuredValue(entity2)).isNotEqualTo(toStructuredValue(entity1));
  }

  @Test
  public void testPreservesUnindexed() {
    Entity entity = new Entity("Foo");
    entity.setProperty("indexed", 1);
    entity.setProperty("implicitly_unindexed", new Text("hi"));
    entity.setUnindexedProperty("unindexed", 1);

    EmbeddedEntity value = toStructuredValue(entity);
    entity = new Entity(value.getKey());
    entity.setPropertiesFrom(value);

    assertThat(entity.isUnindexedProperty("indexed")).isFalse();
    assertThat(entity.isUnindexedProperty("implicitly_unindexed")).isTrue();
    assertThat(entity.isUnindexedProperty("unindexed")).isTrue();
  }

  @Test
  public void testNoKey() {
    EmbeddedEntity value = new EmbeddedEntity();
    value.setProperty("aDateList", Collections.singletonList(new Date()));

    assertThat(value.getKey()).isNull();
    value.setKey(null);
    assertThat(value.getKey()).isNull();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testDefinsiveCopy() {
    Entity entity = new Entity("Foo");
    entity.setProperty("aDateList", Collections.singletonList(new Date()));
    EmbeddedEntity prop = toStructuredValue(entity);
    Entity recovered = toEntity(prop);

    assertThat(prop.getPropertyMap()).isNotSameInstanceAs(entity.getPropertyMap());
    assertThat(prop.getProperty("aDateList")).isNotSameInstanceAs(entity.getProperty("aDateList"));
    assertThat(((List<Date>) prop.getProperty("aDateList")).get(0))
        .isNotSameInstanceAs(((List<Date>) entity.getProperty("aDateList")).get(0));

    assertThat(recovered.getProperty("aDateList"))
        .isNotSameInstanceAs(prop.getProperty("aDateList"));
    assertThat(((List<Date>) recovered.getProperty("aDateList")).get(0))
        .isNotSameInstanceAs(((List<Date>) prop.getProperty("aDateList")).get(0));

    assertThat(recovered).isEqualTo(entity);
  }

  Entity toEntity(EmbeddedEntity value) {
    Entity entity = new Entity(value.getKey());
    entity.setPropertiesFrom(value);
    return entity;
  }

  EmbeddedEntity toStructuredValue(Entity entity) {
    EmbeddedEntity value = new EmbeddedEntity();
    value.setKey(entity.getKey());
    value.setPropertiesFrom(entity);
    return value;
  }
}
