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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Test for {@link RemoteDatastore}
 *
 * @author maxr@google.com (Max Ross)
 */
@RunWith(JUnit4.class)
public class RemoteDatastoreTest {

  private static final String CLIENT_APP_ID = "client";
  private static final String TARGET_APP_ID = "target";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private RemoteRpc mockRemoteRpc;

  private RemoteDatastore remoteDatastore;

  private static final RewrittenReferenceHolder REFERENCE_HOLDER_1 =
      new RewrittenReferenceHolder(1);
  private static final RewrittenReferenceHolder REFERENCE_HOLDER_2 =
      new RewrittenReferenceHolder(2);
  private static final RewrittenReferenceHolder REFERENCE_HOLDER_3 =
      new RewrittenReferenceHolder(3);
  private static final RewrittenReferenceHolder REFERENCE_HOLDER_4 =
      new RewrittenReferenceHolder(4);
  private static final RewrittenReferenceHolder REFERENCE_HOLDER_5 =
      new RewrittenReferenceHolder(5);
  private static final RewrittenReferenceHolder REFERENCE_HOLDER_6 =
      new RewrittenReferenceHolder(6);

  /** Wrapper around both formats of Reference and Entity. */
  private static class RewrittenReferenceHolder {
    private final Reference clientReference;
    private final Reference targetReference;
    private final EntityProto clientEntity;
    private final EntityProto targetEntity;

    private RewrittenReferenceHolder(int index) {
      this.clientEntity = createEntityProto(CLIENT_APP_ID, index);
      this.targetEntity = createEntityProto(TARGET_APP_ID, index);
      this.clientReference = clientEntity.getKey();
      this.targetReference = targetEntity.getKey();
    }
  }

  @Before
  public void setUpRemoteDatastore() {
    remoteDatastore = new RemoteDatastore(TARGET_APP_ID, mockRemoteRpc, new RemoteApiOptions());
  }

  @Test
  public void testGetRewritesReferences() throws InvalidProtocolBufferException {
    DatastoreV3Pb.GetRequest.Builder getRequest = DatastoreV3Pb.GetRequest.newBuilder();
    getRequest.addKey(REFERENCE_HOLDER_1.clientReference);
    getRequest.addKey(REFERENCE_HOLDER_2.clientReference);
    getRequest.addKey(REFERENCE_HOLDER_3.clientReference);
    getRequest.addKey(REFERENCE_HOLDER_4.targetReference); // This key already has the target app.
    getRequest.addKey(REFERENCE_HOLDER_5.clientReference);

    DatastoreV3Pb.GetRequest rewrittenGetRequest =
        DatastoreV3Pb.GetRequest.newBuilder()
            .addKey(REFERENCE_HOLDER_1.targetReference)
            .addKey(REFERENCE_HOLDER_2.targetReference)
            .addKey(REFERENCE_HOLDER_3.targetReference)
            .addKey(REFERENCE_HOLDER_4.targetReference)
            .addKey(REFERENCE_HOLDER_5.targetReference)
            .build();

    // A mix of Entities, Missing, and Deferred.
    DatastoreV3Pb.GetResponse.Builder remoteRpcResponse = DatastoreV3Pb.GetResponse.newBuilder();
    remoteRpcResponse.addEntityBuilder().setKey(REFERENCE_HOLDER_1.targetReference);
    remoteRpcResponse.addEntityBuilder().setEntity(REFERENCE_HOLDER_2.targetEntity);
    remoteRpcResponse.addEntityBuilder().setEntity(REFERENCE_HOLDER_3.targetEntity);
    remoteRpcResponse.addDeferred(REFERENCE_HOLDER_4.targetReference);
    remoteRpcResponse.addEntityBuilder().setKey(REFERENCE_HOLDER_5.targetReference);

    expectRemoteRpcGet(rewrittenGetRequest, remoteRpcResponse.build());

    // We should get a serialized version of the remoteRpcResponse.
    DatastoreV3Pb.GetResponse actualResp = invokeGet(getRequest.build());
    assertThat(actualResp).isEqualTo(remoteRpcResponse.build());
  }

  @Test
  public void testGetAndPutInTransaction() throws InvalidProtocolBufferException {
    // Begin a transaction.
    DatastoreV3Pb.Transaction transaction = beginTransaction();

    // Issue a Get request.
    DatastoreV3Pb.GetRequest getRequest1 =
        DatastoreV3Pb.GetRequest.newBuilder()
            .setTransaction(transaction)
            .addKey(REFERENCE_HOLDER_1.clientReference)
            .addKey(REFERENCE_HOLDER_2.clientReference)
            .build();

    DatastoreV3Pb.GetRequest rewrittenGetRequest1 =
        DatastoreV3Pb.GetRequest.newBuilder()
            .addKey(REFERENCE_HOLDER_1.targetReference)
            .addKey(REFERENCE_HOLDER_2.targetReference)
            .build();

    // One found, one missing
    DatastoreV3Pb.GetResponse.Builder remoteRpcGetResponse1 =
        DatastoreV3Pb.GetResponse.newBuilder();
    remoteRpcGetResponse1.addEntityBuilder().setKey(REFERENCE_HOLDER_1.targetReference);
    remoteRpcGetResponse1.addEntityBuilder().setEntity(REFERENCE_HOLDER_2.targetEntity);

    expectRemoteRpcGet(rewrittenGetRequest1, remoteRpcGetResponse1.build());

    // We should get a serialized version of the remoteRpcResponse.
    DatastoreV3Pb.GetResponse actualResp1 = invokeGet(getRequest1);
    assertThat(actualResp1)
        .ignoringFields(DatastoreV3Pb.GetResponse.IN_ORDER_FIELD_NUMBER)
        .isEqualTo(remoteRpcGetResponse1.build());

    // Now do a Put.
    EntityProto.Builder mutatedClientEntity2 = REFERENCE_HOLDER_2.clientEntity.toBuilder();
    EntityProto.Builder mutatedTargetEntity2 = REFERENCE_HOLDER_2.targetEntity.toBuilder();
    addProperty(mutatedClientEntity2, "newprop", 999);
    addProperty(mutatedTargetEntity2, "newprop", 999);

    // One Entity in the request won't have an id yet.
    EntityProto.Builder clientEntityWithNoKey6 = REFERENCE_HOLDER_6.clientEntity.toBuilder();
    EntityProto.Builder targetEntityWithNoKey6 = REFERENCE_HOLDER_6.targetEntity.toBuilder();
    clientEntityWithNoKey6
        .getKeyBuilder()
        .getPathBuilder()
        .getElementBuilder(0)
        .clearName()
        .clearId();
    targetEntityWithNoKey6
        .getKeyBuilder()
        .getPathBuilder()
        .getElementBuilder(0)
        .clearName()
        .clearId();

    DatastoreV3Pb.PutRequest putRequest =
        DatastoreV3Pb.PutRequest.newBuilder()
            .setTransaction(transaction)
            .addEntity(mutatedClientEntity2)
            .addEntity(clientEntityWithNoKey6)
            .build();

    // It will need to send a Remote Rpc to get an id for this entity.
    Reference allocatedKey = expectAllocateIds(targetEntityWithNoKey6.build()).get(0);

    // Returns the target version of the key for the Entity that already had it, and the newly
    // allocated key for the other.
    DatastoreV3Pb.PutResponse actualPutResp = invokePut(putRequest);
    assertThat(actualPutResp.getKeyList())
        .containsExactly(mutatedTargetEntity2.getKey(), allocatedKey)
        .inOrder();

    // Issue another get request.  It should use the cached results from the first.  It should not
    // reflect the change from the Put.
    DatastoreV3Pb.GetRequest.Builder getRequest2 = DatastoreV3Pb.GetRequest.newBuilder();
    getRequest2.setTransaction(transaction);
    getRequest2.addKey(REFERENCE_HOLDER_1.clientReference);
    getRequest2.addKey(REFERENCE_HOLDER_2.clientReference);
    getRequest2.addKey(REFERENCE_HOLDER_3.clientReference);
    getRequest2.addKey(REFERENCE_HOLDER_4.targetReference); // This key already has the target app.
    getRequest2.addKey(REFERENCE_HOLDER_5.clientReference);

    // It doesn't send the request for 1 and 2, because they're already in the cache.
    DatastoreV3Pb.GetRequest rewrittenGetRequest2 =
        DatastoreV3Pb.GetRequest.newBuilder()
            .addKey(REFERENCE_HOLDER_3.targetReference)
            .addKey(REFERENCE_HOLDER_4.targetReference)
            .addKey(REFERENCE_HOLDER_5.targetReference)
            .build();

    // A mix of Entities, Missing, and Deferred.
    DatastoreV3Pb.GetResponse.Builder remoteRpcGetResponse2 =
        DatastoreV3Pb.GetResponse.newBuilder();
    remoteRpcGetResponse2.addEntityBuilder().setEntity(REFERENCE_HOLDER_3.targetEntity);
    remoteRpcGetResponse2.addDeferred(REFERENCE_HOLDER_4.targetReference);
    remoteRpcGetResponse2.addEntityBuilder().setKey(REFERENCE_HOLDER_5.targetReference);

    // Merges both cached data and data from the second GetResponse.
    DatastoreV3Pb.GetResponse.Builder expectedGetResponse2 =
        DatastoreV3Pb.GetResponse.newBuilder()
            .setInOrder(false); // Because there is a deferred result.
    expectedGetResponse2.addEntityBuilder().setKey(REFERENCE_HOLDER_1.targetReference);
    expectedGetResponse2
        .addEntityBuilder()
        .setEntity(REFERENCE_HOLDER_2.targetEntity); // Not from Put.
    expectedGetResponse2.addEntityBuilder().setEntity(REFERENCE_HOLDER_3.targetEntity);
    expectedGetResponse2.addDeferred(REFERENCE_HOLDER_4.targetReference);
    expectedGetResponse2.addEntityBuilder().setKey(REFERENCE_HOLDER_5.targetReference);

    expectRemoteRpcGet(rewrittenGetRequest2, remoteRpcGetResponse2.build());

    DatastoreV3Pb.GetResponse actualGetResp2 = invokeGet(getRequest2.build());
    assertThat(actualGetResp2).ignoringRepeatedFieldOrder().isEqualTo(expectedGetResponse2.build());
  }

  @Test
  public void testQueriesRewriteReferences() {
    OnestoreEntity.PropertyValue.ReferenceValue targetRefValue =
        OnestoreEntity.PropertyValue.ReferenceValue.newBuilder().setApp(TARGET_APP_ID).build();

    DatastoreV3Pb.Query.Builder query = DatastoreV3Pb.Query.newBuilder().setApp(TARGET_APP_ID);
    query
        .addFilterBuilder()
        .addPropertyBuilder()
        .setName("name")
        .setMultiple(false)
        .setValue(OnestoreEntity.PropertyValue.newBuilder().setReferenceValue(targetRefValue));
    assertFalse(RemoteDatastore.rewriteQueryAppIds(query, TARGET_APP_ID));
    OnestoreEntity.PropertyValue.ReferenceValue clientRefValue =
        OnestoreEntity.PropertyValue.ReferenceValue.newBuilder().setApp(CLIENT_APP_ID).build();
    query = DatastoreV3Pb.Query.newBuilder();
    query.setApp(CLIENT_APP_ID);
    query
        .addFilterBuilder()
        .addPropertyBuilder()
        .setName("name")
        .setMultiple(false)
        .setValue(OnestoreEntity.PropertyValue.newBuilder().setReferenceValue(clientRefValue));

    // check that a non-reference property is ignored
    query
        .addFilterBuilder()
        .addPropertyBuilder()
        .setName("name")
        .setMultiple(false)
        .setValue(
            OnestoreEntity.PropertyValue.newBuilder()
                .setStringValue(ByteString.copyFromUtf8("A string")));

    assertTrue(RemoteDatastore.rewriteQueryAppIds(query, TARGET_APP_ID));

    assertEquals(TARGET_APP_ID, query.getApp());
    assertEquals(
        TARGET_APP_ID, query.getFilter(0).getProperty(0).getValue().getReferenceValue().getApp());
    assertWithMessage("string shouldn't be a reference value")
        .that(query.getFilter(1).getProperty(0).getValue().hasReferenceValue())
        .isFalse();
  }

  @Test
  public void rewritesPutAppIds() {
    OnestoreEntity.PropertyValue.ReferenceValue targetRefValue =
        OnestoreEntity.PropertyValue.ReferenceValue.newBuilder()
            .setApp(TARGET_APP_ID)
            .buildPartial();

    DatastoreV3Pb.PutRequest.Builder put = DatastoreV3Pb.PutRequest.newBuilder();
    OnestoreEntity.EntityProto.Builder entity = put.addEntityBuilder();
    entity.getKeyBuilder().setApp(TARGET_APP_ID);
    entity
        .addPropertyBuilder()
        .setName("name")
        .setMultiple(false)
        .setValue(OnestoreEntity.PropertyValue.newBuilder().setReferenceValue(targetRefValue));
    assertFalse(RemoteDatastore.rewritePutAppIds(put, TARGET_APP_ID));

    OnestoreEntity.PropertyValue.ReferenceValue clientRefValue =
        OnestoreEntity.PropertyValue.ReferenceValue.newBuilder()
            .setApp(CLIENT_APP_ID)
            .buildPartial();
    entity = put.addEntityBuilder();
    entity.getKeyBuilder().setApp(CLIENT_APP_ID);
    entity
        .addPropertyBuilder()
        .setName("name")
        .setMultiple(false)
        .setValue(OnestoreEntity.PropertyValue.newBuilder().setReferenceValue(clientRefValue));

    // check that a non-reference property is ignored
    entity
        .addPropertyBuilder()
        .setName("name")
        .setMultiple(false)
        .setValue(
            OnestoreEntity.PropertyValue.newBuilder()
                .setStringValue(ByteString.copyFromUtf8("a string")));

    assertTrue(RemoteDatastore.rewritePutAppIds(put, TARGET_APP_ID));

    assertEquals(TARGET_APP_ID, entity.getKey().getApp());
    assertEquals(TARGET_APP_ID, entity.getProperty(0).getValue().getReferenceValue().getApp());

    assertWithMessage("string shouldn't be a reference value")
        .that(entity.getProperty(1).getValue().hasReferenceValue())
        .isFalse();
  }

  @Test
  public void rewritesAncestorApp() {
    OnestoreEntity.Reference.Builder ancestor =
        OnestoreEntity.Reference.newBuilder()
            .setApp(CLIENT_APP_ID)
            .setPath(
                OnestoreEntity.Path.newBuilder()
                    .addElement(
                        OnestoreEntity.Path.Element.newBuilder().setType("type").setName("name")));
    DatastoreV3Pb.Query.Builder query =
        DatastoreV3Pb.Query.newBuilder().setApp(TARGET_APP_ID).setAncestor(ancestor);
    assertTrue(RemoteDatastore.rewriteQueryAppIds(query, TARGET_APP_ID));
    ancestor.setApp(TARGET_APP_ID);
    query.setAncestor(ancestor);
    assertFalse(RemoteDatastore.rewriteQueryAppIds(query, TARGET_APP_ID));
  }

  private static EntityProto createEntityProto(String appId, int index) {
    OnestoreEntity.Reference.Builder key = OnestoreEntity.Reference.newBuilder().setApp(appId);
    key.getPathBuilder()
        .addElement(
            OnestoreEntity.Path.Element.newBuilder().setType("somekind").setName("name " + index));

    EntityProto.Builder entity = EntityProto.newBuilder().setKey(key);

    // TODO: There are utilities for this, but they are all under
    // apphosting/datastore/testing which we may not want to depend on from here.
    OnestoreEntity.Path group =
        OnestoreEntity.Path.newBuilder().addElement(key.getPath().getElement(0)).build();
    entity.setEntityGroup(group);

    addProperty(entity, "someproperty", index);

    return entity.buildPartial();
  }

  private static void addProperty(EntityProto.Builder entity, String propertyName, int value) {
    OnestoreEntity.Property property =
        OnestoreEntity.Property.newBuilder()
            .setName(propertyName)
            .setMultiple(false)
            .setValue(OnestoreEntity.PropertyValue.newBuilder().setInt64Value(value))
            .build();
    entity.addProperty(property);
  }

  /**
   * Sets mock expectations for allocating new ids for the given Entities.  The RemoteApi
   * accomplishes this by calling a special method: remote_datastore.GetIDs.  Note that it does not
   * actually call allocateIds.
   *
   * @param entities the entities that need ids allocated.
   * @return the References that are mocked to be returned.
   */
  private List<Reference> expectAllocateIds(EntityProto... entities) {
    DatastoreV3Pb.PutRequest.Builder expectedReq = DatastoreV3Pb.PutRequest.newBuilder();
    DatastoreV3Pb.PutResponse.Builder resp = DatastoreV3Pb.PutResponse.newBuilder();

    List<Reference> allocatedKeys = Lists.newLinkedList();
    int idSeed = 444;
    for (OnestoreEntity.EntityProto entity : entities) {
      // This is copied from the impl.  It sends over empty entities.  The key should have a kind,
      // but no id/name yet.
      OnestoreEntity.EntityProto.Builder reqEntity = expectedReq.addEntityBuilder();
      reqEntity.getKeyBuilder().mergeFrom(entity.getKey());
      reqEntity.getEntityGroupBuilder();

      // The response will have an id.
      Reference.Builder respKey = reqEntity.getKeyBuilder().clone();
      respKey.getPathBuilder().getElementBuilder(0).setId(idSeed);
      resp.addKey(respKey);
      allocatedKeys.add(respKey.build());

      idSeed++;
    }

    when(mockRemoteRpc.call(
            eq(RemoteDatastore.REMOTE_API_SERVICE),
            eq("GetIDs"),
            eq(""),
            eq(expectedReq.build().toByteArray())))
        .thenReturn(resp.build().toByteArray());

    return allocatedKeys;
  }

  private DatastoreV3Pb.Transaction beginTransaction() throws InvalidProtocolBufferException {
    DatastoreV3Pb.BeginTransactionRequest beginTxnRequest =
        DatastoreV3Pb.BeginTransactionRequest.newBuilder().setApp(CLIENT_APP_ID).build();

    byte[] txBytes =
        remoteDatastore.handleDatastoreCall("BeginTransaction", beginTxnRequest.toByteArray());
    return DatastoreV3Pb.Transaction.parseFrom(txBytes, ExtensionRegistry.getEmptyRegistry());
  }

  private DatastoreV3Pb.GetResponse invokeGet(DatastoreV3Pb.GetRequest req)
      throws InvalidProtocolBufferException {
    byte[] actualByteResponse = remoteDatastore.handleDatastoreCall("Get", req.toByteArray());

    return DatastoreV3Pb.GetResponse.parseFrom(
        actualByteResponse, ExtensionRegistry.getEmptyRegistry());
  }

  private DatastoreV3Pb.PutResponse invokePut(DatastoreV3Pb.PutRequest req)
      throws InvalidProtocolBufferException {
    byte[] actualByteResponse = remoteDatastore.handleDatastoreCall("Put", req.toByteArray());

    return DatastoreV3Pb.PutResponse.parseFrom(
        actualByteResponse, ExtensionRegistry.getEmptyRegistry());
  }

  private void expectRemoteRpcGet(
      DatastoreV3Pb.GetRequest expectedReq, DatastoreV3Pb.GetResponse resp) {
    when(mockRemoteRpc.call(
            eq(RemoteDatastore.DATASTORE_SERVICE),
            eq("Get"),
            eq(""),
            eq(expectedReq.toByteArray())))
        .thenReturn(resp.toByteArray());
  }
}
