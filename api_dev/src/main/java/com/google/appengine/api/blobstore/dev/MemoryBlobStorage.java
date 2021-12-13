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

import com.google.appengine.api.blobstore.BlobKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A version of {@link BlobStorage} that stores all data in memory.
 *
 */
class MemoryBlobStorage implements BlobStorage {
  private final Map<BlobKey, byte[]> blobContents;
  private final BlobInfoStorage blobInfoStorage;

  MemoryBlobStorage(BlobInfoStorage blobInfoStorage) {
    this.blobContents = new HashMap<BlobKey, byte[]>();
    this.blobInfoStorage = blobInfoStorage;
  }

  @Override
  public boolean hasBlob(BlobKey blobKey) {
    return blobContents.containsKey(blobKey);
  }

  @Override
  public OutputStream storeBlob(final BlobKey blobKey) {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        blobContents.put(blobKey, toByteArray());
      }
    };
  }

  @Override
  public InputStream fetchBlob(BlobKey blobKey) throws IOException {
    if (!blobContents.containsKey(blobKey)) {
      throw new FileNotFoundException("Could not find blob: " + blobKey);
    }
    return new ByteArrayInputStream(blobContents.get(blobKey));
  }

  @Override
  public void deleteBlob(BlobKey blobKey) {
    blobContents.remove(blobKey);
    blobInfoStorage.deleteBlobInfo(blobKey);
  }

  public void deleteAllBlobs() {
    blobContents.clear();
    blobInfoStorage.deleteAllBlobInfos();
  }
}
