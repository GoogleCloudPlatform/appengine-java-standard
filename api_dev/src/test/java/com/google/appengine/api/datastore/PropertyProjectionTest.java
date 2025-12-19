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

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;
import com.google.protobuf.ByteString;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.PropertyValue;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PropertyProjection}.
 *
 */
@RunWith(JUnit4.class)
public class PropertyProjectionTest {

  @Test
  public void testEquality() {
    new EqualsTester()
        .addEqualityGroup(new PropertyProjection("Foo", null))
        .addEqualityGroup(new PropertyProjection("Bar", null))
        .addEqualityGroup(new PropertyProjection("Foo", String.class))
        .addEqualityGroup(new PropertyProjection("Bar", String.class))
        .testEquals();
  }

  @Test
  public void testBadConstructor() {
    assertThrows(NullPointerException.class, () -> new PropertyProjection(null, String.class));

    // invalid type class
    assertThrows(IllegalArgumentException.class, () -> new PropertyProjection("Foo", getClass()));
  }

  @Test
  public void testProjection() {
    Projection projection = new PropertyProjection("Foo", Boolean.class);
    assertThat(projection.getName()).isEqualTo("Foo");
    assertThat(projection.getPropertyName()).isEqualTo("Foo");

    Object value = new RawValue(PropertyValue.newBuilder().setBooleanValue(true).build());
    assertThat(projection.getValue(Collections.singletonMap("Foo", null))).isNull();
    assertThat(projection.getValue(ImmutableMap.of("Foo", value))).isEqualTo(true);
    assertThat(projection.getValue(Collections.singletonMap("Foo", null))).isNull();
    assertThat(
            projection.getValue(ImmutableMap.of("Foo", (Object) new RawValue(PropertyValue.getDefaultInstance()))))
        .isNull();

    assertThrows(IllegalArgumentException.class, () -> projection.getValue(ImmutableMap.of()));

    assertThrows(
        IllegalArgumentException.class,
        () -> projection.getValue(ImmutableMap.of("Foo", (Object) "Hi")));

    assertThrows(
        IllegalArgumentException.class,
        () -> projection.getValue(ImmutableMap.of("Foo", (Object) true)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            projection.getValue(
                ImmutableMap.of(
                    "Foo",
                    (Object)
                        new RawValue(
                            PropertyValue.newBuilder()
                                .setStringValue(ByteString.copyFromUtf8("Hi"))
                                .build()))));
  }
}
