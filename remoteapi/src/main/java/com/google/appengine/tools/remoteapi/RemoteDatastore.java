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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.io.protocol.ProtocolMessage;
import com.google.storage.onestore.v3.OnestoreEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contains handlers for calling the datastore via the remote API.
 *
 * <p>A note on Reference re-writing:
 *
 * <p>This class needs to handle Keys containing app ids of either the client app or the remote app.
 * Apps that are using the latest version of the Remote API will generate Keys with the remote app
 * id after installing the Remote API. However, older versions of the Remote API will generate Keys
 * with the client app id. Additionally, it's possible that the app could create a Key prior to
 * installing the Remote API and use that.
 *
 * <p>This class makes sure that all Keys in requests sent to the remote app have the remote app id.
 * Keys in responses will also have the remote app id.
 *
 * <p>However, due to implementation details of the Java SDK, Keys from Put operations end up being
 * returned to the user matching the app id of the request. That is, they will match the app id that
 * was on the Entity being Put. As discussed above, this could be either the client app id or the
 * remote app id. TODO: consider updating this.
 */
class RemoteDatastore {
  static final String DATASTORE_SERVICE = "datastore_v3";
  static final String REMOTE_API_SERVICE = "remote_datastore";

  private static final Logger logger = Logger.getLogger(RemoteDatastore.class.getName());

  private final RemoteRpc remoteRpc;
  private final RemoteApiOptions options;
  private final String remoteAppId;

  /** Contains an entry for every query we've ever run. */
  private final Map<Long, QueryState> idToCursor = new ConcurrentHashMap<>();
  // TODO(b/68190107) entries are never removed, which is a memory leak.
  // (But Python has the same problem.)

  /**
   * A counter used to allocate local cursor ids.
   */
  private final AtomicLong nextCursorId = new AtomicLong(1);

  /** Contains an entry for each in-progress transaction. */
  private final Map<Long, TransactionBuilder> idToTransaction = new ConcurrentHashMap<>();

  /**
   * A counter used to allocate transaction ids.
   */
  private final AtomicLong nextTransactionId = new AtomicLong(1);

  RemoteDatastore(String remoteAppId, RemoteRpc remoteRpc, RemoteApiOptions options) {
    this.remoteAppId = remoteAppId;
    this.remoteRpc = remoteRpc;
    this.options = options;
  }

  byte[] handleDatastoreCall(String methodName, byte[] request) {
    // TODO(b/68190109) Perhaps replace with a map of handlers.
    // TODO Support AddActions.
    switch (methodName) {
      case "RunQuery":
        return handleRunQuery(request);
      case "Next":
        return handleNext(request);
      case "BeginTransaction":
        return handleBeginTransaction(request);
      case "Commit":
        return handleCommit(request);
      case "Rollback":
        return handleRollback(request);
      case "Get":
        return handleGet(request);
      case "Put":
        return handlePut(request);
      case "Delete":
        return handleDelete(request);
      default:
        // other datastore call
        return remoteRpc.call(DATASTORE_SERVICE, methodName, "", request);
    }
  }

  private byte[] handleRunQuery(byte[] request) {
    return runQuery(request, nextCursorId.getAndIncrement());
  }

  /**
   * Runs the query and remembers the current position using the given cursor id.
   */
  private byte[] runQuery(byte[] request, long localCursorId) {

    // force query compilation so we get a compiled cursor back
    DatastoreV3Pb.Query query = new DatastoreV3Pb.Query();
    mergeFromBytes(query, request);

    if (rewriteQueryAppIds(query, remoteAppId)) {
      request = query.toByteArray();
    }
    query.setCompile(true);

    // override fetch size; the normal default is too small for the remote API.
    if (!query.hasCount()) {
      query.setCount(options.getDatastoreQueryFetchSize());
    }

    // Force the query not to run in a transaction. Transactions
    // cannot span multiple remote calls, because each call
    // might happen on a different server. The actual transaction
    // (if any) won't happen until commit. Instead we will record
    // the results of the query and verify that they haven't changed
    // at commit time.

    TransactionBuilder tx = null;
    if (query.hasTransaction()) {
      tx = getTransactionBuilder("RunQuery", query.getTransaction());
      query.clearTransaction();
    }

    DatastoreV3Pb.QueryResult result;
    if (tx != null) {
      byte[] resultBytes = remoteRpc.call(REMOTE_API_SERVICE, "TransactionQuery", "",
                                          query.toByteArray());
      result = tx.handleQueryResult(resultBytes);
    } else {
      byte[] resultBytes = remoteRpc.call(DATASTORE_SERVICE, "RunQuery", "", query.toByteArray());

      result = new DatastoreV3Pb.QueryResult();
      mergeFromBytes(result, resultBytes);

      if (tx != null) {
        // Add the preconditions for this query result. The assertion is that
        // none of the entities that we actually downloaded as a result of the
        // query have changed. (This is probably a performance hit for a query
        // that returns many results since we'll be doing a bulk get at commit
        // time.)

        // This consistency check will not detect "phantom" rows. If we wanted
        // that, we would have to change the remote API protocol so that we can
        // re-run all the queries in the transaction at commit time.
        for (OnestoreEntity.EntityProto entity : result.results()) {
          tx.addEntityToCache(entity);
        }
      }
    }

    if (result.isMoreResults() && result.hasCompiledCursor()) {
      // create a query to continue from after the results we already got.
      idToCursor.put(localCursorId, new QueryState(request, result.getCompiledCursor()));
    } else {
      idToCursor.put(localCursorId, QueryState.NO_MORE_RESULTS);
    }
    // replace cursor id with our own cursor, to be used in handleNext() to look up the query.
    result.getMutableCursor().setCursor(localCursorId);

    return result.toByteArray();
  }

  /**
   * Rewrite app ids in the Query pb.
   * @return if any app ids were rewritten
   */
  /* @VisibleForTesting */
  static boolean rewriteQueryAppIds(DatastoreV3Pb.Query query, String remoteAppId) {
    boolean reserialize = false;
    if (!query.getApp().equals(remoteAppId)) {
      reserialize = true;
      query.setApp(remoteAppId);
    }
    if (query.hasAncestor() && !query.getAncestor().getApp().equals(remoteAppId)) {
      reserialize = true;
      query.getAncestor().setApp(remoteAppId);
    }
    for (DatastoreV3Pb.Query.Filter filter : query.filters()) {
      for (OnestoreEntity.Property prop : filter.propertys()) {
        OnestoreEntity.PropertyValue propValue = prop.getMutableValue();
        if (propValue.hasReferenceValue()) {
          OnestoreEntity.PropertyValue.ReferenceValue ref = propValue.getMutableReferenceValue();
          if (!ref.getApp().equals(remoteAppId)) {
            reserialize = true;
            ref.setApp(remoteAppId);
          }
        }
      }
    }
    return reserialize;
  }

  private byte[] handleNext(byte[] request) {
    DatastoreV3Pb.NextRequest nextRequest = new DatastoreV3Pb.NextRequest();
    mergeFromBytes(nextRequest, request);

    long cursorId = nextRequest.getCursor().getCursor();
    QueryState queryState = idToCursor.get(cursorId);
    if (queryState == null) {
      throw new RemoteApiException("local cursor not found", DATASTORE_SERVICE, "Next", null);
    }

    if (!queryState.hasMoreResults()) {
      DatastoreV3Pb.QueryResult result = new DatastoreV3Pb.QueryResult();
      result.setMoreResults(false);
      return result.toByteArray();
    } else {
      return runQuery(queryState.makeNextQuery(nextRequest).toByteArray(), cursorId);
    }
  }

  private byte[] handleBeginTransaction(byte[] request) {
    DatastoreV3Pb.BeginTransactionRequest beginTxnRequest =
        new DatastoreV3Pb.BeginTransactionRequest();
    parseFromBytes(beginTxnRequest, request);

    // Create the transaction builder.
    long txId = nextTransactionId.getAndIncrement();
    idToTransaction.put(txId, new TransactionBuilder(beginTxnRequest.isAllowMultipleEg()));

    // return a Transaction response with the new id
    DatastoreV3Pb.Transaction tx = new DatastoreV3Pb.Transaction();
    tx.setHandle(txId);
    tx.setApp(remoteAppId);
    return tx.toByteArray();
  }

  private byte[] handleCommit(byte[] requestBytes) {
    DatastoreV3Pb.Transaction request = new DatastoreV3Pb.Transaction();
    mergeFromBytes(request, requestBytes);
    request.setApp(remoteAppId);
    TransactionBuilder tx = removeTransactionBuilder("Commit", request);

    // Replay the transaction and do the commit on the server. (Throws an exception
    // if the commit fails.)
    remoteRpc.call(REMOTE_API_SERVICE, "Transaction", "", tx.makeCommitRequest().toByteArray());

    // Return success.
    return new DatastoreV3Pb.CommitResponse().toByteArray();
  }

  private byte[] handleRollback(byte[] requestBytes) {
    DatastoreV3Pb.Transaction request = new DatastoreV3Pb.Transaction();
    mergeFromBytes(request, requestBytes);
    request.setApp(remoteAppId);
    removeTransactionBuilder("Rollback", request);

    return new byte[0]; // this is ApiBasePb.VoidProto.getDefaultInstance().toByteArray();
  }

  private byte[] handleGet(byte[] originalRequestBytes) {
    DatastoreV3Pb.GetRequest rewrittenReq = new DatastoreV3Pb.GetRequest();
    mergeFromBytes(rewrittenReq, originalRequestBytes);

    // Update the Request so that all References have the remoteAppId.
    boolean reserialize = rewriteRequestReferences(rewrittenReq.mutableKeys(), remoteAppId);

    if (rewrittenReq.hasTransaction()) {
      return handleGetWithTransaction(rewrittenReq);
    } else {
      // Send the rpc.
      byte[] requestBytesToSend = reserialize ? rewrittenReq.toByteArray() : originalRequestBytes;
      return remoteRpc.call(DATASTORE_SERVICE, "Get", "", requestBytesToSend);
    }
  }

  private byte[] handlePut(byte[] requestBytes) {
    DatastoreV3Pb.PutRequest request = new DatastoreV3Pb.PutRequest();
    mergeFromBytes(request, requestBytes);
    boolean reserialize = rewritePutAppIds(request, remoteAppId);
    if (request.hasTransaction()) {
      return handlePutForTransaction(request);
    } else {
      if (reserialize) {
        requestBytes = request.toByteArray();
      }
      String suffix = "";
      if (logger.isLoggable(Level.FINE)) {
        // Log the key of the first entity for put calls that happen outside a transaction.
        suffix = describePutRequestForLog(request);
      }
      return remoteRpc.call(DATASTORE_SERVICE, "Put", suffix, requestBytes);
    }
  }

  /* @VisibleForTesting */
  static boolean rewritePutAppIds(DatastoreV3Pb.PutRequest request, String remoteAppId) {
    boolean reserialize = false;
    // rewrite the app on the key of every entity
    for (OnestoreEntity.EntityProto entity : request.mutableEntitys()) {
      if (!entity.getMutableKey().getApp().equals(remoteAppId)) {
        reserialize = true;
        entity.getMutableKey().setApp(remoteAppId);
      }
      // rewrite the app on all reference properties
      for (OnestoreEntity.Property prop : entity.mutablePropertys()) {
        if (prop.hasValue() && prop.getMutableValue().hasReferenceValue()) {
          OnestoreEntity.PropertyValue.ReferenceValue ref =
              prop.getMutableValue().getReferenceValue();
          if (ref.hasApp() && !ref.getApp().equals(remoteAppId)) {
            reserialize = true;
            ref.setApp(remoteAppId);
          }
        }
      }
    }
    return reserialize;
  }

  private byte[] handleDelete(byte[] requestBytes) {
    DatastoreV3Pb.DeleteRequest request = new DatastoreV3Pb.DeleteRequest();
    mergeFromBytes(request, requestBytes);

    boolean reserialize = rewriteRequestReferences(request.mutableKeys(), remoteAppId);
    if (reserialize) {
      // The request was mutated, so we need to reserialize it.
      requestBytes = request.toByteArray();
    }
    if (request.hasTransaction()) {
      return handleDeleteForTransaction(request);
    } else {
      return remoteRpc.call(DATASTORE_SERVICE, "Delete", "", requestBytes);
    }
  }

  /**
   * Replace app ids in the collection of references.
   *
   * @param references The references that may need to be updated.  If so, they will be mutated in
   *                   place.
   * @param remoteAppId The app id that should be present on the references.
   * @return A boolean indicating if any changes were made.
   */
  /* @VisibleForTesting */
  static boolean rewriteRequestReferences(
      Collection<OnestoreEntity.Reference> references, String remoteAppId) {

    boolean reserialize = false;
    for (OnestoreEntity.Reference refToCheck : references) {
      if (!refToCheck.getApp().equals(remoteAppId)) {
        refToCheck.setApp(remoteAppId);
        reserialize = true;
      }
    }
    return reserialize;
  }

  private byte[] handleGetWithTransaction(DatastoreV3Pb.GetRequest rewrittenReq) {
    TransactionBuilder tx = getTransactionBuilder("Get", rewrittenReq.getTransaction());

    // We only send a request for keys that are not already in the cache.  Note that the
    // References have already been rewritten to have the remoteAppId.  Also note that this request
    // does not actually use a transaction.  Instead, all transactional checks will be done at
    // commit time.
    DatastoreV3Pb.GetRequest requestForKeysNotInCache = rewrittenReq.clone();
    requestForKeysNotInCache.clearTransaction();
    requestForKeysNotInCache.clearKey();
    for (OnestoreEntity.Reference key : rewrittenReq.keys()) {
      if (!tx.isCachedEntity(key)) {
        requestForKeysNotInCache.addKey(key);
      }
    }

    // If we need any entities, do the RPC
    Set<OnestoreEntity.Reference> deferredRefs = new HashSet<>();
    if (requestForKeysNotInCache.keySize() > 0) {
      byte[] respBytesFromRemoteApp = remoteRpc.call(
          RemoteDatastore.DATASTORE_SERVICE, "Get", "", requestForKeysNotInCache.toByteArray());

      //  Add new entities to the cache (these have the remote app id.)
      DatastoreV3Pb.GetResponse respFromRemoteApp = new DatastoreV3Pb.GetResponse();
      mergeFromBytes(respFromRemoteApp, respBytesFromRemoteApp);

      for (DatastoreV3Pb.GetResponse.Entity entityResult : respFromRemoteApp.entitys()) {
        if (entityResult.hasEntity()) {
          tx.addEntityToCache(entityResult.getEntity());
        } else {
          tx.addEntityAbsenceToCache(entityResult.getKey());
        }
      }

      // We don't update the cache for deferred Keys, but we'll make sure they flow back out
      // through the returned GetResponse.
      deferredRefs.addAll(respFromRemoteApp.deferreds());
    }

    // The cache is now up to date.  We'll build the response by pulling values from it.
    DatastoreV3Pb.GetResponse mergedResponse = new DatastoreV3Pb.GetResponse();
    mergedResponse.setInOrder(deferredRefs.isEmpty());
    for (OnestoreEntity.Reference key : rewrittenReq.keys()) {
      // Check for deferred keys first, because they were not put in the cache.
      if (deferredRefs.contains(key)) {
        mergedResponse.addDeferred(key);
      } else {
        // Otherwise, it should be in the cache (perhaps as a MISSING entry.)
        OnestoreEntity.EntityProto entity = tx.getCachedEntity(key);
        if (entity == null) {
          mergedResponse.addEntity().setKey(key);
        } else {
          mergedResponse.addEntity().setEntity(entity);
        }
      }
    }

    return mergedResponse.toByteArray();
  }

  byte[] handlePutForTransaction(DatastoreV3Pb.PutRequest request) {
    TransactionBuilder tx = getTransactionBuilder("Put", request.getTransaction());

    // Find the entities for which we need to allocate a new id.
    List<OnestoreEntity.EntityProto> entitiesWithoutIds = new ArrayList<>();
    for (OnestoreEntity.EntityProto entity : request.entitys()) {
      if (requiresId(entity)) {
        entitiesWithoutIds.add(entity);
      }
    }

    // Allocate an id for each entity that needs one.
    if (!entitiesWithoutIds.isEmpty()) {

      DatastoreV3Pb.PutRequest subRequest = new DatastoreV3Pb.PutRequest();
      for (OnestoreEntity.EntityProto entity : entitiesWithoutIds) {
        OnestoreEntity.EntityProto subEntity = subRequest.addEntity();
        subEntity.getKey().mergeFrom(entity.getKey());
        subEntity.getEntityGroup();
      }

      // Gross, but there's no place to hide this attribute in the proto we
      // send over so we just use a separate RPC. If we end up with more
      // txn options we'll need to come up with something else.
      String getIdsRpc = tx.isXG() ? "GetIDsXG" : "GetIDs";
      byte[] subResponseBytes =
          remoteRpc.call(REMOTE_API_SERVICE, getIdsRpc, "", subRequest.toByteArray());
      DatastoreV3Pb.PutResponse subResponse = new DatastoreV3Pb.PutResponse();
      mergeFromBytes(subResponse, subResponseBytes);

      // Add the new id and its entity group to the original entity (still in the request).
      Iterator<OnestoreEntity.EntityProto> it = entitiesWithoutIds.iterator();
      for (OnestoreEntity.Reference newKey : subResponse.keys()) {
        OnestoreEntity.EntityProto entity = it.next();
        entity.getKey().copyFrom(newKey);
        entity.getEntityGroup().addElement().copyFrom(newKey.getPath().getElement(0));
      }
    }

    // Copy all the entities in this put() request into the transaction, to be submitted
    // to the server on commit. Also, create a response that has the key of each entity.
    DatastoreV3Pb.PutResponse response = new DatastoreV3Pb.PutResponse();
    for (OnestoreEntity.EntityProto entityProto : request.entitys()) {
      tx.putEntityOnCommit(entityProto);
      response.addKey().copyFrom(entityProto.getKey());
    }
    return response.toByteArray();
  }

  byte[] handleDeleteForTransaction(DatastoreV3Pb.DeleteRequest request) {
    TransactionBuilder tx = getTransactionBuilder("Delete", request.getTransaction());

    for (OnestoreEntity.Reference key : request.keys()) {
      tx.deleteEntityOnCommit(key);
    }

    DatastoreV3Pb.DeleteResponse response = new DatastoreV3Pb.DeleteResponse();
    return response.toByteArray();
  }

  TransactionBuilder getTransactionBuilder(String methodName, DatastoreV3Pb.Transaction tx) {
    TransactionBuilder result = idToTransaction.get(tx.getHandle());
    if (result == null) {
      throw new RemoteApiException("transaction not found", DATASTORE_SERVICE, methodName, null);
    }
    return result;
  }

  TransactionBuilder removeTransactionBuilder(String methodName,
      DatastoreV3Pb.Transaction tx) {
    TransactionBuilder result = idToTransaction.remove(tx.getHandle());
    if (result == null) {
      throw new RemoteApiException("transaction not found", DATASTORE_SERVICE, methodName, null);
    }
    return result;
  }

  /**
   * Returns true if we need to auto-allocate an id for this entity.
   */
  private boolean requiresId(OnestoreEntity.EntityProto entity) {
    OnestoreEntity.Path path = entity.getKey().getPath();
    OnestoreEntity.Path.Element lastElement = path.elements().get(path.elementSize() - 1);
    return lastElement.getId() == 0 && !lastElement.hasName();
  }

  private static String describePutRequestForLog(DatastoreV3Pb.PutRequest putRequest) {
    int count = putRequest.entitySize();
    if (count <= 0) {
      return "()";
    }
    OnestoreEntity.Reference keyProto = putRequest.getEntity(0).getKey();
    if (count == 1) {
      return "(" + describeKeyForLog(keyProto) + ")";
    } else {
      return "(" + describeKeyForLog(keyProto) + ", ...)";
    }
  }

  private static String describeKeyForLog(OnestoreEntity.Reference keyProto) {
    StringBuilder pathString = new StringBuilder();
    OnestoreEntity.Path path = keyProto.getPath();
    for (OnestoreEntity.Path.Element element : path.elements()) {
      if (pathString.length() > 0) {
        pathString.append(",");
      }
      pathString.append(element.getType() + "/");
      if (element.hasId()) {
        pathString.append(element.getId());
      } else {
        pathString.append(element.getName());
      }
    }
    return "[" + pathString + "]";
  }

  /**
   * The current state of a remote query, allowing us to continue from previous
   * location. (We need to keep this locally because each round trip can be
   * executed on a different instance.)
   */
  private static class QueryState {
    private static final QueryState NO_MORE_RESULTS = new QueryState(null, null);

    private final byte[] query;
    private final DatastoreV3Pb.CompiledCursor cursor;

    /**
     * Creates a QueryState that can continue fetching results from a given cursor.
     * @param query  the query that was previously executed
     * @param cursor  the cursor that was returned after the previous remote call
     */
    QueryState(byte[] query, DatastoreV3Pb.CompiledCursor cursor) {
      this.query = query;
      this.cursor = cursor;
    }

    boolean hasMoreResults() {
      return query != null;
    }

    private DatastoreV3Pb.Query makeNextQuery(DatastoreV3Pb.NextRequest nextRequest) {
      DatastoreV3Pb.Query result = new DatastoreV3Pb.Query();
      mergeFromBytes(result, query);
      result.setOffset(0);
      result.setCompiledCursor(cursor);
      result.setCompile(true);

      if (nextRequest.hasCount()) {
        result.setCount(nextRequest.getCount());
      } else {
        result.clearCount();
      }
      return result;
    }
  }

  private static void parseFromBytes(ProtocolMessage<?> message, byte[] bytes) {
    boolean parsed = message.parseFrom(bytes);
    if (!parsed || !message.isInitialized()) {
      throw new ApiProxy.ApiProxyException("Could not parse protobuf bytes");
    }
  }

  private static void mergeFromBytes(ProtocolMessage<?> message, byte[] bytes) {
    boolean parsed = message.mergeFrom(bytes);
    if (!parsed || !message.isInitialized()) {
      throw new ApiProxy.ApiProxyException("Could not parse protobuf bytes");
    }
  }
}
