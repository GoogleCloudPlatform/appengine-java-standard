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

package com.google.appengine.tools.development.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.dev.BlobStorage;
import com.google.appengine.api.blobstore.dev.BlobStorageFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;
import junit.framework.TestCase;

/**
 */
public class LocalBlobstoreServiceTestConfigTest extends TestCase {

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalBlobstoreServiceTestConfig());

  @Override
  public void setUp() throws Exception {
    super.setUp();
    helper.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    NamespaceManager.set(null);
    helper.tearDown();
    super.tearDown();
  }

  // we'll run this test twice to prove that we're not leaking any state across
  // tests
  private void doTest(String key, String namespace) throws IOException {
    // first make sure blobs don't leak across tests
    BlobStorage blobStorage = BlobStorageFactory.getBlobStorage();
    BlobKey blobKey = new BlobKey(key);
    assertFalse(blobStorage.hasBlob(blobKey));

    // next make sure blob infos don't leak across tests
    assertNull(BlobStorageFactory.getBlobInfoStorage().loadBlobInfo(blobKey));

    // now write a blob and verify the write
    OutputStream os = blobStorage.storeBlob(blobKey);
    String blob = "the rain in spain falls mainly on the plain";
    os.write(blob.getBytes(UTF_8));
    os.close();
    InputStream is = blobStorage.fetchBlob(blobKey);
    BufferedReader r = new BufferedReader(new InputStreamReader(is, UTF_8));
    assertEquals(blob, r.readLine());
    r.close();

    // now write a blobinfo and verify the write
    BlobInfo blobInfo = new BlobInfo(blobKey, "String", new Date(), "test", 33);
    BlobStorageFactory.getBlobInfoStorage().saveBlobInfo(blobInfo);
    NamespaceManager.set("");
    assertNotNull(BlobStorageFactory.getBlobInfoStorage().loadBlobInfo(blobKey));
  }

  public void testInsert1() throws IOException {
    doTest("foo", "");
  }

  public void testInsert2() throws IOException {
    doTest("foo", "");
  }

  public void testInsert3() throws IOException {
    doTest("bar", "a-namespace");
  }

  public void testInsert4() throws IOException {
    doTest("bar", "a-namespace");
  }
}
