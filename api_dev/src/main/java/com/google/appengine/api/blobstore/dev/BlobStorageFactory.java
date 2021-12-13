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

import java.io.File;

/**
 * {@code BlobStorageFactory} provides some helper methods for
 * instantiating implementations of {@link BlobStorage} and {@link
 * BlobInfoStorage}.
 *
 */
public final class BlobStorageFactory {
  private static final BlobInfoStorage blobInfoStorage = new BlobInfoStorage();
  private static BlobStorage blobStorage;

  public static BlobInfoStorage getBlobInfoStorage() {
    return blobInfoStorage;
  }

  public static BlobStorage getBlobStorage() {
    if (blobStorage == null) {
      throw new IllegalStateException("Must call one of set*BlobStorage() first.");
    }
    return blobStorage;
  }

  static void setFileBlobStorage(File blobRoot) {
    blobStorage = new FileBlobStorage(blobRoot, blobInfoStorage);
  }

  static void setMemoryBlobStorage() {
    blobStorage = new MemoryBlobStorage(blobInfoStorage);
  }
}
