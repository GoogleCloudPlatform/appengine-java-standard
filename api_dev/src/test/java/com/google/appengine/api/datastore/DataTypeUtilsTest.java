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

import com.google.appengine.api.users.User;
import com.google.common.collect.ImmutableList;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService DataTypeUtils class.
 *
 */
@RunWith(JUnit4.class)
public class DataTypeUtilsTest {
  @Test
  public void testSupported() {
    assertType(true, Boolean.class);
    assertType(true, Byte.class);
    assertType(true, Short.class);
    assertType(true, Integer.class);
    assertType(true, Long.class);
    assertType(true, String.class);
    assertType(true, User.class);
    assertType(true, Key.class);
    assertType(true, Blob.class);
    assertType(true, Text.class);
    assertType(true, Date.class);
    assertType(true, Link.class);
    assertType(true, ShortBlob.class);
    assertType(true, GeoPt.class);
    assertType(true, PhoneNumber.class);
    assertType(true, PostalAddress.class);
    assertType(true, Email.class);
    assertType(true, IMHandle.class);
    assertType(true, EmbeddedEntity.class);
  }

  @Test
  public void testUnsupported() {
    assertType(false, StringBuffer.class);
    assertType(false, StringWriter.class);
    assertType(false, Random.class);
    assertType(false, Timestamp.class);

    @SuppressWarnings("ComparableType")
    class MyComparable implements Comparable<MyComparable> {
      @Override
      public int compareTo(MyComparable that) {
        return 0;
      }
    }
    assertType(false, MyComparable.class);
    assertType(false, Byte[].class);
    assertType(false, byte[].class);
  }

  @Test
  public void testCheckSupportedValue() {
    DataTypeUtils.checkSupportedValue(null);
    DataTypeUtils.checkSupportedValue(42);
    DataTypeUtils.checkSupportedValue(42L);

    assertThrows(
        IllegalArgumentException.class,
        () -> DataTypeUtils.checkSupportedValue(new StringBuilder()));

    DataTypeUtils.checkSupportedValue("This is a small string.");

    String dataString = makeString(DataTypeUtils.MAX_STRING_PROPERTY_LENGTH);
    DataTypeUtils.checkSupportedValue(dataString);

    assertThrows(
        IllegalArgumentException.class, () -> DataTypeUtils.checkSupportedValue(dataString + "x"));

    DataTypeUtils.checkSupportedValue(new Link("This is a small link."));
    Link link = new Link(makeString(DataTypeUtils.MAX_LINK_PROPERTY_LENGTH));
    DataTypeUtils.checkSupportedValue(link);

    assertThrows(
        IllegalArgumentException.class,
        () -> DataTypeUtils.checkSupportedValue(new Link(link + "x")));

    DataTypeUtils.checkSupportedValue(new ShortBlob("this is a short byte array".getBytes(UTF_8)));

    byte[] byteArray = makeString(DataTypeUtils.MAX_SHORT_BLOB_PROPERTY_LENGTH).getBytes(UTF_8);
    DataTypeUtils.checkSupportedValue(new ShortBlob(byteArray));

    byte[] byteArray2 =
        makeString(DataTypeUtils.MAX_SHORT_BLOB_PROPERTY_LENGTH + 1).getBytes(UTF_8);
    assertThrows(
        IllegalArgumentException.class,
        () -> DataTypeUtils.checkSupportedValue(new ShortBlob(byteArray2)));

    IllegalArgumentException e1 =
        assertThrows(
            IllegalArgumentException.class,
            () -> DataTypeUtils.checkSupportedValue("name", new Text("text"), true, false, true));
    assertThat(e1).hasMessageThat().contains("is not indexable");

    IllegalArgumentException e2 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                DataTypeUtils.checkSupportedValue(
                    "name", new Blob(new byte[] {1}), true, false, true));
    assertThat(e2).hasMessageThat().contains("is not indexable");

    IllegalArgumentException e3 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                DataTypeUtils.checkSupportedValue(
                    "name", ImmutableList.of(1, new Text("text")), true, false, true));
    assertThat(e3).hasMessageThat().contains("is not indexable");
  }

  private String makeString(int length) {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < length; i++) {
      buffer.append('x');
    }
    return buffer.toString();
  }

  private void assertType(boolean expected, Class<?> clazz) {
    assertThat(DataTypeUtils.isSupportedType(clazz)).isEqualTo(expected);
    assertThat(DataTypeUtils.getSupportedTypes().contains(clazz)).isEqualTo(expected);
  }
}
