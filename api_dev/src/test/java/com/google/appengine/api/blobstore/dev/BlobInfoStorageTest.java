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

package com.google.appengine.api.blobstore.dev;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link BlobInfoStorage}.
 *
 */
@RunWith(JUnit4.class)
public class BlobInfoStorageTest {

  private final LocalServiceTestHelper helper = new LocalServiceTestHelper();
  private BlobInfoStorage blobInfoStorage;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    blobInfoStorage = new BlobInfoStorage();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
    blobInfoStorage = null;
  }

  @Test
  public void testRoundTrip() {
    BlobKey blobKey = new BlobKey("foo");
    BlobInfo expected = new BlobInfo(blobKey, "content-type", new Date(), "filename", 42L);
    blobInfoStorage.saveBlobInfo(expected);

    BlobInfo actual = blobInfoStorage.loadBlobInfo(blobKey);
    assertThat(actual).isEqualTo(expected);

    blobInfoStorage.deleteBlobInfo(blobKey);
  }

  @Test
  public void testRoundTripWithGsObjectName() {
    BlobKey blobKey = new BlobKey("foo");
    BlobInfo expected =
        new BlobInfo(
            blobKey, "content-type", new Date(), "filename", 42L, "md5hash", "/bucket/obj");
    blobInfoStorage.saveBlobInfo(expected);

    BlobInfo actual = blobInfoStorage.loadBlobInfo(blobKey);
    assertThat(actual).isEqualTo(expected);

    blobInfoStorage.deleteBlobInfo(blobKey);
  }
}
