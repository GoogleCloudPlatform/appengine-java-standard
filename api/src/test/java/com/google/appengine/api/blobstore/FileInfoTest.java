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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Date;
import java.util.GregorianCalendar;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the {@link FileInfo} class.
 *
 */
@RunWith(JUnit4.class)
public class FileInfoTest {
  FileInfo infoFull;
  FileInfo infoNoGs;
  Date creationDate;

  @Before
  public void setUp() throws Exception {
    this.creationDate = new Date(
        new GregorianCalendar(2008, 11 - 1, 12, 10, 40, 00).getTimeInMillis() + 20);
    this.infoFull = new FileInfo("image/jpeg", this.creationDate, "file-0.jpg", 5,
                                  "md5-hash", "/gs/bucket_name/some_random_filename1");
    this.infoNoGs = new FileInfo("image/jpeg", this.creationDate, "file-0.jpg", 5,
                                  "md5-hash", null);
  }

  @Test
  public void testConstructorNullContentType() {
    assertThrows(
        NullPointerException.class,
        () -> new FileInfo(null, this.creationDate, "file-0.jpg", 5, "md5-hash", null));
  }

  @Test
  public void testConstructorNullCreation() {
    assertThrows(
        NullPointerException.class,
        () -> new FileInfo("image/jpeg", null, "file-0.jpg", 5, "md5-hash", null));
  }

  @Test
  public void testConstructorNullFilename() {
    assertThrows(
        NullPointerException.class,
        () -> new FileInfo("image/jpeg", this.creationDate, null, 5, "md5-hash", null));
  }

  @Test
  public void testConstructorNullMd5() {
    assertThrows(
        NullPointerException.class,
        () -> new FileInfo("image/jpeg", this.creationDate, "file-0.jpg", 5, null, null));
  }

  @Test
  public void testGetters() {
    assertEquals("image/jpeg", this.infoFull.getContentType());
    assertEquals(this.creationDate, this.infoFull.getCreation());
    assertEquals("file-0.jpg", this.infoFull.getFilename());
    assertEquals(5, this.infoFull.getSize());
    assertEquals("md5-hash", this.infoFull.getMd5Hash());
    assertEquals("/gs/bucket_name/some_random_filename1", this.infoFull.getGsObjectName());
  }

  @Test
  public void testToString() throws Exception {
    assertEquals("<FileInfo: contentType = image/jpeg, creation = " +
                 this.creationDate.toString() +
                 ", filename = file-0.jpg, size = 5, md5Hash = md5-hash, gsObjectName =" +
                 " /gs/bucket_name/some_random_filename1>",
                 this.infoFull.toString());
    assertEquals("<FileInfo: contentType = image/jpeg, creation = " +
                 this.creationDate.toString() + ", filename = file-0.jpg, size = 5, " +
                 "md5Hash = md5-hash>", this.infoNoGs.toString());
  }

  @Test
  public void testEquals() throws Exception {
    assertThat(this.infoNoGs).isNotEqualTo(this.infoFull);

    FileInfo infoFull2 = new FileInfo("image/jpeg", this.creationDate, "file-0.jpg", 5,
                                       "md5-hash", "/gs/bucket_name/some_random_filename1");
    assertEquals(this.infoFull, infoFull2);
  }
}
