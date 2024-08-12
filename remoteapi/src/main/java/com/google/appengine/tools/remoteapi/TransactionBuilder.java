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

package com.google.appengine.tools.remoteapi;

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.utils.remoteapi.RemoteApiPb;
import com.google.io.protocol.ProtocolMessage;
import com.google.protobuf.ByteString;
// <internal24>
import com.google.storage.onestore.v3.OnestoreEntity;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * An in-progress transaction that will be sent via the remote API on commit.
 */
class TransactionBuilder {

  /**
   * A map containing a copy of each entity that we retrieved from the
   * datastore during this transaction. On commit, we will assert
   * that these entities haven't changed. If the value is null, the
   * datastore didn't return any entity for the given key, and we will
   * assert that the entity doesn't exist at commit time.
   */
  private final Map<ByteString, OnestoreEntity.EntityProto> getCache =
      new HashMap<ByteString, OnestoreEntity.EntityProto>();

  /**
   * A map from an entity's key to the entity that should be saved when
   * this transaction commits. If the value is null, the entity should
   * be deleted.
   */
  private final Map<ByteString, OnestoreEntity.EntityProto> updates =
      new HashMap<ByteString, OnestoreEntity.EntityProto>();

  private final boolean isXG;

  TransactionBuilder(boolean isXG) {
    this.isXG = isXG;
  }

  public boolean isXG() {
    return isXG;
  }

  /**
   * Returns true if we've cached the presence or absence of this entity.
   */
  public boolean isCachedEntity(OnestoreEntity.Reference key) {
    return getCache.containsKey(key.toByteString());
  }

  /**
   * Saves the original value of an entity (as returned by the datastore)
   * to the local cache.
   */
  public void addEntityToCache(OnestoreEntity.EntityProto entityPb) {
    ByteString key = entityPb.getKey().toByteString();
    if (getCache.containsKey(key)) {
      throw new IllegalStateException("shouldn't load the same entity twice within a transaction");
    }
    getCache.put(key, entityPb);
  }

  /**
   * Caches the absence of an entity (according to the datastore).
   */
  public void addEntityAbsenceToCache(OnestoreEntity.Reference key) {
    ByteString keyBytes = key.toByteString();
    if (getCache.containsKey(keyBytes)) {
      throw new IllegalStateException("shouldn't load the same entity twice within a transaction");
    }
    getCache.put(keyBytes, (OnestoreEntity.EntityProto) null);
  }

  /**
   * Returns a cached entity, or null if the entity's absence was cached.
   */
  @Nullable
  public OnestoreEntity.EntityProto getCachedEntity(OnestoreEntity.Reference key) {
    ByteString keyBytes = key.toByteString();
    if (!getCache.containsKey(keyBytes)) {
      throw new IllegalStateException("entity's status unexpectedly not in cache");
    }
    return getCache.get(keyBytes);
  }

  /**
   * Update transaction with result from a TransactionQuery call.
   */
  public DatastoreV3Pb.QueryResult handleQueryResult(byte[] resultBytes) {
    RemoteApiPb.TransactionQueryResult result = new RemoteApiPb.TransactionQueryResult();
    boolean parsed = result.mergeFrom(resultBytes);
    if (!parsed || !result.isInitialized()) {
      throw new IllegalArgumentException("Could not parse TransactionQueryResult");
    }

    // Record the entity_group version in the transaction's preconditions if it's new
    // (don't overwrite an old version, as that could mask a concurrency error)
    if (isCachedEntity(result.getEntityGroupKey())) {
      OnestoreEntity.EntityProto cached = getCachedEntity(result.getEntityGroupKey());
      if (!((result.hasEntityGroup() && result.getEntityGroup().equals(cached))
            || (!result.hasEntityGroup() && cached == null))) {
        throw new ConcurrentModificationException("Transaction precondition failed.");
      }
    } else if (result.hasEntityGroup()) {
      addEntityToCache(result.getEntityGroup());
    } else {
      addEntityAbsenceToCache(result.getEntityGroupKey());
    }
    return result.getResult();
  }

  public void putEntityOnCommit(OnestoreEntity.EntityProto entity) {
    updates.put(entity.getKey().toByteString(), entity);
  }

  public void deleteEntityOnCommit(OnestoreEntity.Reference key) {
    updates.put(key.toByteString(), null);
  }

  /**
   * Creates a request to perform this transaction on the server.
   */
  public RemoteApiPb.TransactionRequest makeCommitRequest() {
    RemoteApiPb.TransactionRequest result = new RemoteApiPb.TransactionRequest();
    result.setAllowMultipleEg(isXG);
    for (Map.Entry<ByteString, OnestoreEntity.EntityProto> entry : getCache.entrySet()) {
      if (entry.getValue() == null) {
        result.addPrecondition(makeEntityNotFoundPrecondition(entry.getKey()));
      } else {
        result.addPrecondition(makeEqualEntityPrecondition(entry.getValue()));
      }
    }
    for (Map.Entry<ByteString, OnestoreEntity.EntityProto> entry : updates.entrySet()) {
      OnestoreEntity.EntityProto entityPb = entry.getValue();
      if (entityPb == null) {
        ProtocolMessage<?> newKey = result.getMutableDeletes().addKey();
        boolean parsed = newKey.mergeFrom(entry.getKey().toByteArray());
        if (!parsed || !newKey.isInitialized()) {
          throw new IllegalStateException("Could not parse serialized key");
        }
      } else {
        result.getMutablePuts().addEntity(entityPb);
      }
    }
    return result;
  }

  // === end of public methods ===

  private static RemoteApiPb.TransactionRequest.Precondition makeEntityNotFoundPrecondition(
      ByteString key) {
    OnestoreEntity.Reference ref = new OnestoreEntity.Reference();
    boolean parsed = ref.mergeFrom(key.toByteArray());
    if (!parsed || !ref.isInitialized()) {
      throw new IllegalArgumentException("Could not parse Reference");
    }

    RemoteApiPb.TransactionRequest.Precondition result =
        new RemoteApiPb.TransactionRequest.Precondition();
    result.setKey(ref);
    return result;
  }

  private static RemoteApiPb.TransactionRequest.Precondition makeEqualEntityPrecondition(
      OnestoreEntity.EntityProto entityPb) {
    RemoteApiPb.TransactionRequest.Precondition result =
        new RemoteApiPb.TransactionRequest.Precondition();
    result.setKey(entityPb.getKey());
    result.setHashAsBytes(computeSha1(entityPb));
    return result;
  }

  // <internal25>
  private static byte[] computeSha1(OnestoreEntity.EntityProto entity) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("can't compute sha1 hash");
    }
    byte[] entityBytes = entity.toByteArray();
    md.update(entityBytes, 0, entityBytes.length);
    return md.digest();
  }
}
