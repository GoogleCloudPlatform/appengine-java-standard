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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

/**
 * {@code BlobUploadSessionStorage} handles persistence of {@link
 * BlobUploadSession} objects.
 *
 */
public final class BlobUploadSessionStorage {
  static final String SUCCESS_PATH = "success_path";
  static final String MAX_UPLOAD_BYTES_PER_BLOB = "max_bytes_per_blob";
  static final String MAX_UPLOAD_BYTES = "max_bytes_total";
  static final String GOOGLE_STORAGE_BUCKET = "gs_bucket_name";

  private final DatastoreService datastoreService;

  public BlobUploadSessionStorage() {
    this.datastoreService = DatastoreServiceFactory.getDatastoreService();
  }

  public String createSession(BlobUploadSession session) {
    String namespace = NamespaceManager.get();
    Entity entity;
    try {
      NamespaceManager.set("");
      entity = new Entity(ReservedKinds.BLOB_UPLOAD_SESSION_KIND);
    } finally {
      NamespaceManager.set(namespace);
    }
    // N.B.(schwardo): Python stores some other properties here, but
    // they don't appear to be used.  Add them if they're used in the
    // future.
    entity.setProperty(SUCCESS_PATH, session.getSuccessPath());
    if (session.hasMaxUploadSizeBytesPerBlob()) {
      entity.setProperty(MAX_UPLOAD_BYTES_PER_BLOB, session.getMaxUploadSizeBytesPerBlob());
    }
    if (session.hasMaxUploadSizeBytes()) {
      entity.setProperty(MAX_UPLOAD_BYTES, session.getMaxUploadSizeBytes());
    }
    if (session.hasGoogleStorageBucketName()) {
      entity.setProperty(GOOGLE_STORAGE_BUCKET, session.getGoogleStorageBucketName());
    }
    datastoreService.put(entity);

    return KeyFactory.keyToString(entity.getKey());
  }

  public BlobUploadSession loadSession(String sessionId) {
    try {
      return convertFromEntity(datastoreService.get(getKeyForSession(sessionId)));
    } catch (EntityNotFoundException ex) {
      return null;
    }
  }

  public void deleteSession(String sessionId) {
    datastoreService.delete(getKeyForSession(sessionId));
  }

  private BlobUploadSession convertFromEntity(Entity entity) {
    BlobUploadSession session = new BlobUploadSession((String) entity.getProperty(SUCCESS_PATH));
    // The response from the Python implementation can contain a property and have it set to null,
    // so we need to check that the values are not null before setting them.
    if (entity.getProperty(MAX_UPLOAD_BYTES_PER_BLOB) != null) {
      session.setMaxUploadSizeBytesPerBlob((Long) entity.getProperty(MAX_UPLOAD_BYTES_PER_BLOB));
    }
    if (entity.getProperty(MAX_UPLOAD_BYTES) != null) {
      session.setMaxUploadSizeBytes((Long) entity.getProperty(MAX_UPLOAD_BYTES));
    }
    if (entity.getProperty(GOOGLE_STORAGE_BUCKET) != null) {
      session.setGoogleStorageBucketName((String) entity.getProperty(GOOGLE_STORAGE_BUCKET));
    }
    return session;
  }

  private Key getKeyForSession(String sessionId) {
    String namespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      return KeyFactory.stringToKey(sessionId);
    } finally {
      NamespaceManager.set(namespace);
    }
  }
}
