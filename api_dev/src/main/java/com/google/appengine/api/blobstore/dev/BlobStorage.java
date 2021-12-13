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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@code BlobStorage} provide an abstraction over the storage of blob
 * content.  For storing blob metadata, see {@link BlobInfoStorage}.
 *
 */
public interface BlobStorage {
  /**
   * @return true if content is found for the specified blob.
   */
  boolean hasBlob(BlobKey blobKey);

  /**
   * Store the contents of the specified blob.  The contents should be
   * written to the returned {@link OutputStream}, and the blob
   * content may not appear until that stream is closed.
   */
  OutputStream storeBlob(BlobKey blobKey) throws IOException;

  /**
   * Fetch the contents of the specified blob.
   */
  InputStream fetchBlob(BlobKey blobKey) throws IOException;

  /**
   * Remove both the content and the metadata for the specified blob.
   */
  void deleteBlob(BlobKey blobKey) throws IOException;
}
