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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the Blob class.
 *
 */
@RunWith(JUnit4.class)
public class BlobTest {
  @Test
  public void testEquals() throws Exception {
    Blob blob1 = new Blob(new byte[] {0, 127, -127, 0, 1, -1});
    Blob blob2 = new Blob(new byte[] {0, 127, -127, 0, 1, -1});
    Blob blob3 = new Blob(new byte[] {0, 127, -127, 1, 1, -1});
    Blob blob4 = new Blob(new byte[] {0, 127, -127, 0, 1, 1});

    assertThat(blob2).isEqualTo(blob1);
    assertThat(blob3).isNotEqualTo(blob1);
    assertThat(blob4).isNotEqualTo(blob1);
    assertThat(blob4).isNotEqualTo(blob3);
  }

  @Test
  public void testHashCode() throws Exception {
    Blob blob1 = new Blob(new byte[] {0, 127, -127, 0, 1, -1});
    Blob blob2 = new Blob(new byte[] {0, 127, -127, 0, 1, -1});

    assertThat(blob2.hashCode()).isEqualTo(blob1.hashCode());
  }

  @Test
  public void testToString() throws Exception {
    Blob blob1 = new Blob(new byte[] {0, 127, -127, 0, 1, -1});

    assertThat(blob1.toString()).isEqualTo("<Blob: 6 bytes>");
  }

  @Test
  public void testSerialization() throws Exception {
    Blob blob = new Blob(new byte[] {0, 127, -127, 0, 1, -1});

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(blob);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Blob readBlob = (Blob) iis.readObject();

    assertThat(readBlob).isNotSameInstanceAs(blob);
    assertThat(readBlob).isEqualTo(blob);
  }
}
