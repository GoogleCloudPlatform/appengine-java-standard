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
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@code FileBlobStorage} provides durable persistence of blobs by storing blob content directly to
 * disk.
 *
 */
class FileBlobStorage implements BlobStorage {
  private final File rootDirectory;
  private final BlobInfoStorage blobInfoStorage;

  FileBlobStorage(File rootDirectory, BlobInfoStorage blobInfoStorage) {
    this.rootDirectory = rootDirectory;
    this.blobInfoStorage = blobInfoStorage;
  }

  @Override
  public boolean hasBlob(final BlobKey blobKey) {

    return getFileForBlob(blobKey).exists();
  }

  @Override
  public OutputStream storeBlob(final BlobKey blobKey) throws IOException {
    return new FileOutputStream(getFileForBlob(blobKey));
  }

  @Override
  public InputStream fetchBlob(final BlobKey blobKey) throws IOException {
    return new FileInputStream(getFileForBlob(blobKey));
  }

  @Override
  public void deleteBlob(final BlobKey blobKey) throws IOException {
    // Make sure the blob exists before deleting it. This way an unknown
    // blobkey that is compatible with the filename will not cause the
    // info storage and the directory to get out of sync. For example,
    // a valid key of XYZ has a compatible filename of /XYZ and XYZ/
    // (the former is only compatible as we add a directory prefix).
    if (blobInfoStorage.loadBlobInfo(blobKey) == null
        && blobInfoStorage.loadGsFileInfo(blobKey) == null) {
      throw new RuntimeException("Unknown blobkey: " + blobKey);
    }
    File file = getFileForBlob(blobKey);
    if (!file.delete()) {
      throw new IOException("Could not delete: " + file);
    }
    blobInfoStorage.deleteBlobInfo(blobKey);
  }

  @VisibleForTesting
  File getFileForBlob(BlobKey blobKey) {
    // Blobkeys should never have the separator character in them (since
    // keys are web-safe base64 and as of this writing, no platforms that
    // I know of have the separator char in that list we are good). Doing
    // this to prevent an attack where a made up blob key accesses files
    // outside the blobstore directory.
    if (blobKey.getKeyString().contains(File.separator)) {
      throw new RuntimeException("illegal blobKey: " + blobKey.getKeyString());
    }
    File file = new File(rootDirectory, blobKey.getKeyString());
    // One last sanity check on filenames. Doing this for Windows and a key
    // name like "d:XYZ" which would get missed by the check above.
    if (!file.getAbsoluteFile().getParent().equals(rootDirectory.getAbsolutePath())) {
      throw new RuntimeException("illegal blobKey: " + blobKey.getKeyString());
    }
    return file;
  }
}
