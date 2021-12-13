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

import static org.junit.Assert.fail;

import com.google.appengine.api.blobstore.BlobKey;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileBlobStorageTest {
  @Test
  public void relativeRoot() {
    FileBlobStorage fileBlobStorage = new FileBlobStorage(
        new File("local/root"), new BlobInfoStorage());
    BlobKey blobKey = new BlobKey("blobkey");
    // TODO confirm the value of the file (although for now as my main concern
    // is to make sure this doesn't throw this is already useful).
    fileBlobStorage.getFileForBlob(blobKey);
  }

  @Test
  public void disallowFileSeparator() {
    FileBlobStorage fileBlobStorage = new FileBlobStorage(
        new File("/tmp/foo"), new BlobInfoStorage());
    BlobKey blobKey = new BlobKey("blob" + File.separatorChar + "key");
    try {
      fileBlobStorage.getFileForBlob(blobKey);
      fail("Exception expected.");
    } catch (RuntimeException ex) {
      // Expected.
    }
  }
}
