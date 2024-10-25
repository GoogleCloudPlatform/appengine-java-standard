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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.IMHandle.Scheme;
import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import com.google.datastore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RawValue}.
 *
 */
@RunWith(JUnit4.class)
public class RawValueTest {
  private static final ImmutableList<Class<?>> BASE_TYPES =
      ImmutableList.<Class<?>>of(
          Boolean.class,
          Double.class,
          Long.class,
          String.class,
          Key.class,
          GeoPt.class,
          User.class);
  private final RawValue NULL_VALUE = new RawValue(PropertyValue.getDefaultInstance());

  private void assertTypeMatch(RawValue value, Class<?> validType) {
    value.asType(validType); // Does not throw an exception.
    assertThrows(IllegalArgumentException.class, () -> value.asType(getClass()));
    for (Class<?> clazz : BASE_TYPES) {
      if (clazz != validType) {
        assertThrows(IllegalArgumentException.class, () -> value.asType(clazz));
      }
    }
  }

  private void assertValue(Object expected, RawValue value, Class<?>... otherClasses) {
    assertThat(NULL_VALUE.asType(expected.getClass())).isNull();
    assertThat(value.asType(expected.getClass())).isEqualTo(expected);
    assertThat(value.asStrictType(expected.getClass())).isEqualTo(expected);
    for (Class<?> clazz : otherClasses) {
      assertThat(NULL_VALUE.asType(clazz)).isNull();
      assertThat(value.asType(clazz)).isEqualTo(expected);
      assertThrows(IllegalArgumentException.class, () -> value.asStrictType(clazz));
    }
  }

  @Test
  public void testBoolean() {
    RawValue value = new RawValue(PropertyValue.newBuilder().setBooleanValue(true).build());
    assertTypeMatch(value, Boolean.class);
    assertThat(value.getValue()).isEqualTo(Boolean.TRUE);
    assertValue(true, value);
  }

  @Test
  public void testDouble() {
    RawValue value = new RawValue(PropertyValue.newBuilder().setDoubleValue(3.3).build());
    assertTypeMatch(value, Double.class);
    assertValue(3.3, value, Float.class);
  }

  @Test
  public void testInt64() {
    RawValue value = new RawValue(PropertyValue.newBuilder().setInt64Value(1L).build());
    assertTypeMatch(value, Long.class);
    assertThat(value.getValue()).isEqualTo(1L);
    assertValue(1L, value, Byte.class, Short.class, Integer.class);
    assertValue(new Rating(1), value);
    assertValue(new Date(0), value);
  }

  @Test
  public void testString() {
    RawValue value = new RawValue(PropertyValue.newBuilder().setStringValue("xmpp hi").build());
    assertTypeMatch(value, String.class);
    assertThat(((ByteString) value.getValue()).toByteArray()).isEqualTo("xmpp hi".getBytes(UTF_8));
    assertValue("xmpp hi", value);
    assertValue(new ShortBlob("xmpp hi".getBytes(UTF_8)), value);
    assertValue(new Link("xmpp hi"), value);
    assertValue(new Category("xmpp hi"), value);
    assertValue(new PhoneNumber("xmpp hi"), value);
    assertValue(new PostalAddress("xmpp hi"), value);
    assertValue(new Link("xmpp hi"), value);
    assertValue(new Email("xmpp hi"), value);
    assertValue(new IMHandle(Scheme.xmpp, "hi"), value);
    assertValue(new BlobKey("xmpp hi"), value);
    assertValue(new Blob("xmpp hi".getBytes(UTF_8)), value);
    assertValue(new Text("xmpp hi"), value);
  }

  @Test
  public void testNull() {
    RawValue value = new RawValue(PropertyValue.getDefaultInstance());
    assertThat(value.getValue()).isNull();
    assertThat(value.asType(String.class)).isNull();
    assertThat(value.asStrictType(String.class)).isNull();
  }

  @Test
  public void testGeoPointV1() throws Exception {
    Value.Builder valueV1 = Value.newBuilder();
    valueV1.getGeoPointValueBuilder().setLatitude(1.1).setLongitude(2.2);
    RawValue value = new RawValue(valueV1.build());
    assertThat(value.getValue()).isEqualTo(new GeoPt(1.1f, 2.2f));
  }

  @Test
  public void testNonGeoPointV1() throws Exception {
    Value.Builder valueV1 = Value.newBuilder();
    valueV1.setMeaning(23);
    valueV1.getGeoPointValueBuilder().setLatitude(1.1).setLongitude(2.2);
    RawValue value = new RawValue(valueV1.build());
    assertThat(value.getValue()).isNull();
  }
}
