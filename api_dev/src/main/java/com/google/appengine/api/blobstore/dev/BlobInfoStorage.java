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

import static com.google.appengine.api.blobstore.dev.LocalBlobstoreService.GOOGLE_STORAGE_KEY_PREFIX;
import static com.google.appengine.api.blobstore.dev.ReservedKinds.GOOGLE_STORAGE_FILE_KIND;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;

/**
 * {@code BlobInfoStorage} provides persistence of blob metadata (in
 * the form of {@link BlobInfo} objects).  It uses {@link
 * DatastoreService} as its persistence mechanism.
 *
 */
public final class BlobInfoStorage {
  private final BlobInfoFactory blobInfoFactory;
  private final DatastoreService datastoreService;

  public BlobInfoStorage() {
    this.blobInfoFactory = new BlobInfoFactory();
    this.datastoreService = DatastoreServiceFactory.getDatastoreService();
  }

  /**
   * Load blob metadata for {@code blobKey}.  Returns
   * {@code null} if no matching blob is found.
   */
  public BlobInfo loadBlobInfo(BlobKey blobKey) {
    return blobInfoFactory.loadBlobInfo(blobKey);
  }

  /**
   * Load Google Storage file metadata for a {@code blobKey}. Returns
   * {@code null} if no matching file is found.
   */
  public BlobInfo loadGsFileInfo(BlobKey blobKey) {
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      Key key = KeyFactory.createKey(null, GOOGLE_STORAGE_FILE_KIND,
          blobKey.getKeyString());
      try {
        Entity entity = datastoreService.get(key);
        return new BlobInfoFactory().createBlobInfo(entity);
      } catch (EntityNotFoundException ex) {
        return null;
      }
    } finally {
      NamespaceManager.set(namespace);
    }
  }

  /**
   * Save the metadata in {@code blobInfo}.
   */
  public void saveBlobInfo(BlobInfo blobInfo) {
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      Entity entity = new Entity(BlobInfoFactory.KIND, blobInfo.getBlobKey().getKeyString());
      entity.setProperty(BlobInfoFactory.CONTENT_TYPE, blobInfo.getContentType());
      entity.setProperty(BlobInfoFactory.CREATION, blobInfo.getCreation());
      entity.setProperty(BlobInfoFactory.FILENAME, blobInfo.getFilename());
      entity.setProperty(BlobInfoFactory.SIZE, blobInfo.getSize());
      entity.setProperty(BlobInfoFactory.MD5_HASH, blobInfo.getMd5Hash());
      entity.setProperty(BlobInfoFactory.GS_OBJECT_NAME, blobInfo.getGsObjectName());
      datastoreService.put(entity);
    } finally {
      NamespaceManager.set(namespace);
    }
  }

  /**
   * Delete the metadata associated with {@code blobKey}.
   */
  public void deleteBlobInfo(BlobKey blobKey) {
    datastoreService.delete(getMetadataKeyForBlobKey(blobKey));
  }

  protected Key getMetadataKeyForBlobKey(BlobKey blobKey) {
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      if (blobKey.getKeyString().startsWith(GOOGLE_STORAGE_KEY_PREFIX)) {
        return KeyFactory.createKey(GOOGLE_STORAGE_FILE_KIND, blobKey.getKeyString());
      } else {
        return KeyFactory.createKey(BlobInfoFactory.KIND, blobKey.getKeyString());
      }
    } finally {
      NamespaceManager.set(namespace);
    }
  }

  void deleteAllBlobInfos() {
    String namespace = NamespaceManager.get();
    Query q;
    try {
      NamespaceManager.set("");
      q = new Query(BlobInfoFactory.KIND);
    } finally {
      NamespaceManager.set(namespace);
    }
    for (Entity e : datastoreService.prepare(q).asIterable()) {
      datastoreService.delete(e.getKey());
    }
  }
}
