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

import static java.util.Objects.requireNonNull;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import java.util.Date;
import java.util.Iterator;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code BlobInfoFactory} provides a trivial interface for retrieving
 * {@link BlobInfo} metadata.
 *
 * <p>BlobInfo metadata is stored in read-only {@code __BlobInfo__}
 * entities in the datastore.  This class provides an easy way to
 * access these entities.  For more complex queries, you can use the
 * datastore directly.
 *
 */
public class BlobInfoFactory {
  public static final String KIND = "__BlobInfo__";
  public static final String CONTENT_TYPE = "content_type";
  public static final String CREATION = "creation";
  public static final String FILENAME = "filename";
  public static final String SIZE = "size";
  public static final String MD5_HASH = "md5_hash";
  public static final String GS_OBJECT_NAME = "gs_object_name";

  private final DatastoreService datastoreService;

  /**
   * Creates a {@code BlobInfoFactory} that uses the default
   * implementation of {@link DatastoreService}.
   */
  public BlobInfoFactory() {
    this(DatastoreServiceFactory.getDatastoreService());
  }

  /**
   * Creates a {@code BlobInfoFactory} with the specified
   * implementation of {@link DatastoreService}.
   */
  public BlobInfoFactory(DatastoreService datastoreService) {
    this.datastoreService = datastoreService;
  }

  /**
   * Loads the {@link BlobInfo} metadata for {@code blobKey}.  Returns
   * {@code null} if no matching blob is found.
   */
  public @Nullable BlobInfo loadBlobInfo(BlobKey blobKey) {
    try {
      return createBlobInfo(datastoreService.get(getMetadataKeyForBlobKey(blobKey)));
    } catch (EntityNotFoundException ex) {
      return null;
    }
  }

  /**
   * Queries for {@link BlobInfo} instances, beginning with the {@link
   * BlobKey} that appears first in lexicographic order.
   */
  public Iterator<BlobInfo> queryBlobInfos() {
    return queryBlobInfosAfter(null);
  }

  /**
   * Queries for {@link BlobInfo} instances, beginning at the blob
   * following {@code previousBlob} in lexicographic order.  If {@code
   * previousBlob} is null, the first blob will be returned.
   *
   * <p>This is useful for displaying discrete pages of blobs.
   */
  public Iterator<BlobInfo> queryBlobInfosAfter(@Nullable BlobKey previousBlob) {
    String origNamespace = NamespaceManager.get();
    Query query;
    try {
      NamespaceManager.set("");
      query = new Query(KIND, null);
    } finally {
      NamespaceManager.set(origNamespace);
    }

    if (previousBlob != null) {
      query.setFilter(new Query.FilterPredicate(
          Entity.KEY_RESERVED_PROPERTY,
          Query.FilterOperator.GREATER_THAN,
          getMetadataKeyForBlobKey(previousBlob)));
    }

    final Iterator<Entity> parent = datastoreService.prepare(query).asIterator();

    return new Iterator<BlobInfo>() {
      @Override
      public boolean hasNext() {
        return parent.hasNext();
      }

      @Override
      public BlobInfo next() {
        return createBlobInfo(parent.next());
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Creates a {@link BlobInfo} by extracting content from the
   * specified {@link Entity}.
   */
  public BlobInfo createBlobInfo(Entity entity) {
    // Recently created __BlobInfo__ entities will have a md5 hash property,
    // while older blobs will not. Ensure we construct the BlobInfo object
    // accordingly. Similarly, only __BlobInfo__ entities stored in Google
    // Cloud Storage have a gs_object_name.
    // TODO (slangley): Remove this check once b/3483289 is fixed.
    String md5Hash = BlobInfo.DEFAULT_MD5_HASH;
    if (entity.hasProperty(MD5_HASH)) {
        md5Hash = (String) entity.getProperty(MD5_HASH);
    }
    String gsObjectName = BlobInfo.DEFAULT_GS_OBJECT_NAME;
    if (entity.hasProperty(GS_OBJECT_NAME)) {
        gsObjectName = (String) entity.getProperty(GS_OBJECT_NAME);
    }
    return new BlobInfo(
        new BlobKey(requireNonNull(entity.getKey().getName())),
        (String) entity.getProperty(CONTENT_TYPE),
        (Date) entity.getProperty(CREATION),
        (String) entity.getProperty(FILENAME),
        (Long) entity.getProperty(SIZE),
        md5Hash,
        gsObjectName);
  }

  private Key getMetadataKeyForBlobKey(BlobKey blobKey) {
    String origNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      return KeyFactory.createKey(null, KIND, blobKey.getKeyString());
    } finally {
      NamespaceManager.set(origNamespace);
    }
  }
}
