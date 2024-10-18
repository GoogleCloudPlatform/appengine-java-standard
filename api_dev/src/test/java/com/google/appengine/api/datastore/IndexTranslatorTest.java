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

import com.google.appengine.api.datastore.Index.Property;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.common.collect.ImmutableList;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link IndexTranslator} class.
 *
 */
@RunWith(JUnit4.class)
public class IndexTranslatorTest {

  private static final Property PROPERTY_1 = new Property("p1", SortDirection.ASCENDING);
  private static final Property PROPERTY_2 = new Property("p2", SortDirection.DESCENDING);
  private static final Property PROPERTY_3 = new Property("p2", null);
  private static final Index INDEX_1 = new Index(0, "Comment", true, ImmutableList.<Property>of());
  private static final Index INDEX_2 =
      new Index(0, "Blog", false, ImmutableList.of(PROPERTY_1, PROPERTY_2));
  private static final Index INDEX_3 = new Index(10, "Bla", false, ImmutableList.of(PROPERTY_2));
  private static final Index INDEX_4 = new Index(0, "Quux", false, ImmutableList.of(PROPERTY_3));

  @Test
  public void testConversion() {
    OnestoreEntity.Index pb = IndexTranslator.convertToPb(INDEX_1);
    assertThat(IndexTranslator.convertFromPb(pb)).isEqualTo(INDEX_1);
    pb = IndexTranslator.convertToPb(INDEX_2);
    assertThat(IndexTranslator.convertFromPb(pb)).isEqualTo(INDEX_2);
    pb = IndexTranslator.convertToPb(INDEX_3);
    OnestoreEntity.CompositeIndex.Builder ci = OnestoreEntity.CompositeIndex.newBuilder();
    ci.setId(10).setDefinition(pb);
    assertThat(IndexTranslator.convertFromPb(ci.build())).isEqualTo(INDEX_3);
    pb = IndexTranslator.convertToPb(INDEX_4);
    assertThat(IndexTranslator.convertFromPb(pb)).isEqualTo(INDEX_4);
  }

  /**
   * Ensures that an Index with a GEOSPATIAL mode can be converted (even though for now we're
   * ignoring the mode).
   */
  // TODO add support for Mode, so that it can be surfaced to the app
  @Test
  public void testIgnoreGeoMode() {
    OnestoreEntity.CompositeIndex.Builder ci = OnestoreEntity.CompositeIndex.newBuilder();
    ci.setAppId("foo");
    ci.setId(1);
    ci.setState(OnestoreEntity.CompositeIndex.State.WRITE_ONLY);
    OnestoreEntity.Index.Builder indexPb =
        OnestoreEntity.Index.newBuilder().setEntityType("Mountain").setAncestor(false);
    indexPb.addProperty(
         OnestoreEntity.Index.Property.newBuilder()
            .setName("location")
            .setMode(OnestoreEntity.Index.Property.Mode.GEOSPATIAL));
    ci.setDefinition(indexPb);
    Index index = IndexTranslator.convertFromPb(ci.build());
    assertThat(index.getProperties()).hasSize(1);
    Property property = index.getProperties().get(0);
    assertThat(property.getName()).isEqualTo("location");
    assertThat(property.getDirection()).isNull();
  }
}
