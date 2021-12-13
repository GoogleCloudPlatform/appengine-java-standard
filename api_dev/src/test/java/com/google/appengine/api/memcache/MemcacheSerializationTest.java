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

package com.google.appengine.api.memcache;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.memcache.MemcacheSerialization.Flag;
import com.google.appengine.api.testing.SerializationTestBase;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the MemcacheSerialization class.
 *
 */
@RunWith(JUnit4.class)
public class MemcacheSerializationTest extends SerializationTestBase {

  /**
   * A non-serializable class to use to test errors.
   */
  public static class NotSerializable {
    public NotSerializable() {
    }
  }

  @Test
  public void testNull() throws ClassNotFoundException, IOException {

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(null);
    assertThat(vaf.flags).isEqualTo(Flag.OBJECT);
    assertThat(vaf.value).hasLength(0);

    Object deserialized = MemcacheSerialization.deserialize(vaf.value, vaf.flags.ordinal());
    assertThat(deserialized).isEqualTo(null);

    byte[] asKey = MemcacheSerialization.makePbKey(null);
    assertThat(asKey).hasLength(0);
  }

  @Test
  public void testBytes() throws ClassNotFoundException, IOException {
    MemcacheSerialization.ValueAndFlags vaf;
    byte[] zeroBytes = new byte[0];
    byte[] someBytes = { 1, 2, 3, 4, 5, 6 };
    byte[] someBytesWithNull = { 65, 66, 67, 0, 68, 69, 70 };
    byte[] bigKeyBytes = Strings.repeat("x", 300).getBytes(UTF_8);

    byte[] asKey;
    vaf = MemcacheSerialization.serialize(zeroBytes);
    assertThat(vaf.flags).isEqualTo(Flag.BYTES);
    assertThat(vaf.value).hasLength(0);

    Object deserialized = MemcacheSerialization.deserialize(vaf.value, vaf.flags.ordinal());
    assertWithMessage("Zero byte array deserialized as wrong type")
        .that(deserialized)
        .isInstanceOf(byte[].class);
    assertThat(((byte[]) deserialized)).hasLength(0);

    asKey = MemcacheSerialization.makePbKey(zeroBytes);
    assertThat(asKey).hasLength(0);

    vaf = MemcacheSerialization.serialize(someBytes);
    assertThat(vaf.flags).isEqualTo(Flag.BYTES);
    assertWithMessage("Multi-byte array wasn't serialized pass-through")
        .that(vaf.value)
        .isEqualTo(someBytes);

    deserialized = MemcacheSerialization.deserialize(vaf.value, vaf.flags.ordinal());
    assertWithMessage("Multi-byte array deserialized as wrong type")
        .that(deserialized)
        .isInstanceOf(byte[].class);
    assertWithMessage("Multi-byte array didn't survive serialization & deserialization")
        .that((byte[]) deserialized)
        .isEqualTo(someBytes);

     // Bytes keys are generally passed through unchanged
     asKey = MemcacheSerialization.makePbKey(someBytes);
    assertThat(asKey).hasLength(someBytes.length);
    assertThat(asKey).isEqualTo(someBytes);

    // Bytes keys with embedded nulls however are SHA1 encoded
    asKey = MemcacheSerialization.makePbKey(someBytesWithNull);
    assertThat(asKey).hasLength(28); // 20 bytes + 8 bytes of base64 overhead

    // Bytes keys that are too long are also SHA1 encoded
    asKey = MemcacheSerialization.makePbKey(bigKeyBytes);
    assertThat(asKey).hasLength(28); // 20 bytes + 8 bytes of base64 overhead
  }

  @Test
  public void testString() throws Exception {
    String string = "a short text string";
    byte[] bytes = string.getBytes(UTF_8);

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(string);
    assertThat(vaf.flags).isEqualTo(Flag.UTF8);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes, Flag.UTF8.ordinal());
    assertThat(deserialized).isEqualTo(string);

    byte[] asKey = MemcacheSerialization.makePbKey(string);
    assertThat(asKey).hasLength(2 + string.length());
    assertThat(Arrays.copyOfRange(asKey, 1, asKey.length - 1)).isEqualTo(string.getBytes(UTF_8));
  }

  @Test
  public void testBigKeyString() throws Exception {
    String string = Strings.repeat("x", 300); // longer than max server key size
    byte[] bytes = string.getBytes(UTF_8);

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(string);
    assertThat(vaf.flags).isEqualTo(Flag.UTF8);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes, Flag.UTF8.ordinal());
    assertThat(deserialized).isEqualTo(string);

    byte[] asKey = MemcacheSerialization.makePbKey(string);
    assertThat(asKey).hasLength(28); // 20 bytes + 8 bytes of base64 overhead
  }

  @Test
  public void testInteger() throws Exception {
    Integer asInt = 12345;
    byte[] bytes = Integer.toString(asInt).getBytes(UTF_8);

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(asInt);
    assertThat(vaf.flags).isEqualTo(Flag.INTEGER);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes,
        Flag.INTEGER.ordinal());
    assertThat(deserialized).isInstanceOf(Integer.class);
    assertThat(deserialized).isEqualTo(asInt);

    byte[] asKey = MemcacheSerialization.makePbKey(asInt);
    assertThat(asKey).isEqualTo("java.lang.Integer:12345".getBytes(UTF_8));
  }

  @Test
  public void testLong() throws Exception {
    Long asLong = 12345L;
    byte[] bytes = Long.toString(asLong).getBytes(UTF_8);

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(asLong);
    assertThat(vaf.flags).isEqualTo(Flag.LONG);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes, Flag.LONG.ordinal());
    assertThat(deserialized).isInstanceOf(Long.class);
    assertThat(deserialized).isEqualTo(asLong);

    byte[] asKey = MemcacheSerialization.makePbKey(asLong);
    assertThat(asKey).isEqualTo("java.lang.Long:12345".getBytes(UTF_8));
  }

  @Test
  public void testBoolean() throws Exception {
    MemcacheSerialization.ValueAndFlags vaf;
    Boolean bTrue = true;
    byte[] bytesTrue = "1".getBytes(UTF_8);
    Boolean bFalse = false;
    byte[] bytesFalse = "0".getBytes(UTF_8);

    vaf = MemcacheSerialization.serialize(bTrue);
    assertThat(vaf.flags).isEqualTo(Flag.BOOLEAN);
    assertThat(Arrays.equals(bytesTrue, vaf.value)).isTrue();
    vaf = MemcacheSerialization.serialize(bFalse);
    assertThat(vaf.flags).isEqualTo(Flag.BOOLEAN);
    assertThat(Arrays.equals(bytesFalse, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytesTrue, Flag.BOOLEAN.ordinal());
    assertThat(deserialized).isInstanceOf(Boolean.class);
    assertThat((Boolean) deserialized).isTrue();
    deserialized = MemcacheSerialization.deserialize(bytesFalse, Flag.BOOLEAN.ordinal());
    assertThat(deserialized).isInstanceOf(Boolean.class);
    assertThat((Boolean) deserialized).isFalse();

    byte[] asKey = MemcacheSerialization.makePbKey(bTrue);
    assertThat(asKey).isEqualTo("true".getBytes(UTF_8));
    asKey = MemcacheSerialization.makePbKey(bFalse);
    assertThat(asKey).isEqualTo("false".getBytes(UTF_8));
  }

  @Test
  public void testByte() throws Exception {
    Byte asByte = (byte) 123;
    byte[] bytes = Byte.toString(asByte).getBytes(UTF_8);

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(asByte);
    assertThat(vaf.flags).isEqualTo(Flag.BYTE);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes, Flag.BYTE.ordinal());
    assertThat(deserialized).isInstanceOf(Byte.class);
    assertThat(deserialized).isEqualTo(asByte);

    byte[] asKey = MemcacheSerialization.makePbKey(asByte);
    assertThat(asKey).isEqualTo("java.lang.Byte:123".getBytes(UTF_8));
  }

  @Test
  public void testShort() throws Exception {
    Short asShort = (short) 12345;
    byte[] bytes = Short.toString(asShort).getBytes(UTF_8);

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(asShort);
    assertThat(vaf.flags).isEqualTo(Flag.SHORT);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes, Flag.SHORT.ordinal());
    assertThat(deserialized).isInstanceOf(Short.class);
    assertThat(deserialized).isEqualTo(asShort);

    byte[] asKey = MemcacheSerialization.makePbKey(asShort);
    assertThat(asKey).isEqualTo("java.lang.Short:12345".getBytes(UTF_8));
  }

  @Test
  public void testObject() throws Exception {
    List<Integer> list = new ArrayList<>();
    list.add(31415);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream objOut = new ObjectOutputStream(baos)) {
      objOut.writeObject(list);
    }
    byte[] bytes = baos.toByteArray();

    MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize(list);
    assertThat(vaf.flags).isEqualTo(Flag.OBJECT);
    assertThat(Arrays.equals(bytes, vaf.value)).isTrue();

    Object deserialized = MemcacheSerialization.deserialize(bytes, Flag.OBJECT.ordinal());
    assertThat(deserialized).isInstanceOf(List.class);
    assertThat(deserialized).isEqualTo(list);

    byte[] asKey = MemcacheSerialization.makePbKey(list);
    assertThat(asKey).hasLength(28); // 20 bytes + 8 bytes of base64 overhead
  }

  @Test
  public void testNotSerializable() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> MemcacheSerialization.serialize(new NotSerializable()));
  }

  @Override
  protected List<Serializable> getCanonicalObjects() {
    return Lists.newArrayList(
        new InvalidValueException("yar"),
        MemcacheSerialization.Flag.INTEGER,
        new MemcacheServiceException("yar"),
        MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return MemcacheService.class;
  }

  /**
   * Instructions for generating new golden files are in the BUILD file in this
   * directory.
   */
  public static void main(String[] args) throws IOException {
    MemcacheSerializationTest st = new MemcacheSerializationTest();
    st.writeCanonicalObjects();
  }
}
