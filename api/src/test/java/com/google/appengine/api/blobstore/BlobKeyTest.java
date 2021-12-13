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

package com.google.appengine.api.blobstore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the {@link BlobKey} class.
 *
 */
@RunWith(JUnit4.class)
public class BlobKeyTest {
  @Test
  public void testNullString() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> new BlobKey(null));
  }

  @Test
  public void testEquals() throws Exception {
    BlobKey blob1 = new BlobKey("test1");
    BlobKey blob2 = new BlobKey("test1");
    BlobKey blob3 = new BlobKey("test2");

    assertThat(blob2).isEqualTo(blob1);
    assertThat(blob3).isNotEqualTo(blob1);
  }

  @Test
  public void testHashCode() throws Exception {
    BlobKey blob1 = new BlobKey("test1");
    BlobKey blob2 = new BlobKey("test1");

    assertThat(blob2.hashCode()).isEqualTo(blob1.hashCode());
  }

  @Test
  public void testToString() throws Exception {
    BlobKey blob = new BlobKey("test1");
    assertThat(blob.toString()).isEqualTo("<BlobKey: test1>");
  }

  @Test
  public void testCompareTo() throws Exception {
    BlobKey blob1 = new BlobKey("test1");
    BlobKey blob2 = new BlobKey("test1");
    BlobKey blob3 = new BlobKey("test2");

    assertThat(blob1.compareTo(blob2)).isEqualTo(0);
    assertThat(blob2.compareTo(blob1)).isEqualTo(0);
    assertThat(blob1.compareTo(blob3)).isEqualTo(-1);
    assertThat(blob3.compareTo(blob1)).isEqualTo(1);
  }

  @Test
  public void testSerialization() throws Exception {
    BlobKey blob = new BlobKey("test1");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(blob);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    BlobKey readBlob = (BlobKey) iis.readObject();

    assertThat(readBlob).isNotSameInstanceAs(blob);
    assertThat(readBlob).isEqualTo(blob);
  }
}
