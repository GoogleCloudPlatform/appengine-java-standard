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

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Error.ErrorCode;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Test the functionality of {@link DatastoreApiHelper}.
 *
 */
@RunWith(JUnit4.class)
public class DatastoreApiHelperTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ApiProxy.Delegate<ApiProxy.Environment> delegate;
  TransactionStack txnStack;
  protected ImplicitTransactionManagementPolicy txnMgmtPolicy;
  DatastoreServiceConfig datastoreServiceConfig;

  @Before
  public void setUp() {
    ApiProxy.setDelegate(delegate);

    txnStack = new TransactionStackImpl(new InstanceMemberThreadLocalTransactionStack());
    txnMgmtPolicy = ImplicitTransactionManagementPolicy.AUTO;
    datastoreServiceConfig =
        withImplicitTransactionManagementPolicy(txnMgmtPolicy)
            .readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
  }

  @After
  public void tearDown() {
    ApiProxy.setDelegate(null);
  }

  private <E extends RuntimeException> void expectMakeAsyncCall(
      byte[] request, Future<byte[]> response) {
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(DatastoreApiHelper.DATASTORE_V3_PACKAGE),
            eq("Commit"),
            eq(request),
            notNull()))
        .thenReturn(response);
  }

  private <E extends RuntimeException> void assertMakeAsyncCallThrows(
      DatastoreV3Pb.Error.ErrorCode errorCode, Class<E> clazz)
      throws InterruptedException, ExecutionException {
    DatastoreV3Pb.Query.Builder queryProto = DatastoreV3Pb.Query.newBuilder();
    Future<byte[]> future =
        immediateFailedFuture(new ApiProxy.ApplicationException(errorCode.getNumber()));
    expectMakeAsyncCall(queryProto.buildPartial().toByteArray(), future);
    Future<DatastoreV3Pb.QueryResult> result =
        DatastoreApiHelper.makeAsyncCall(
            new ApiProxy.ApiConfig(), Method.Commit, queryProto, DatastoreV3Pb.QueryResult.newBuilder());
    RuntimeException rte =
        assertThrows(RuntimeException.class, () -> FutureHelper.quietGet(result));
    assertThat(rte.getClass()).isEqualTo(clazz);
  }

  @Test
  public void testBadRequest() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(ErrorCode.BAD_REQUEST, IllegalArgumentException.class);
  }

  @Test
  public void testConcurrentTransaction() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(
        ErrorCode.CONCURRENT_TRANSACTION, ConcurrentModificationException.class);
  }

  @Test
  public void testNeedIndex() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(ErrorCode.NEED_INDEX, DatastoreNeedIndexException.class);
  }

  @Test
  public void testTimeOut() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(ErrorCode.TIMEOUT, DatastoreTimeoutException.class);
  }

  @Test
  public void testBigtableError() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(ErrorCode.BIGTABLE_ERROR, DatastoreTimeoutException.class);
  }

  @Test
  public void testCommittedButStillApplying() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(
        ErrorCode.COMMITTED_BUT_STILL_APPLYING, CommittedButStillApplyingException.class);
  }

  @Test
  public void testInternalError() throws InterruptedException, ExecutionException {
    assertMakeAsyncCallThrows(ErrorCode.INTERNAL_ERROR, DatastoreFailureException.class);
  }

  @Test
  public void testV1Exceptions() throws Exception {
    assertV1Exception(Code.INVALID_ARGUMENT, "", IllegalArgumentException.class);
    assertV1Exception(Code.ABORTED, "", ConcurrentModificationException.class);
    assertV1Exception(Code.FAILED_PRECONDITION, "", DatastoreNeedIndexException.class);
    assertV1Exception(
        Code.FAILED_PRECONDITION,
        "The Cloud Datastore API is not enabled for the project",
        DatastoreFailureException.class);
    assertV1Exception(Code.DEADLINE_EXCEEDED, "", DatastoreTimeoutException.class);
    assertV1Exception(Code.PERMISSION_DENIED, "", IllegalArgumentException.class);
    assertV1Exception(Code.UNAVAILABLE, "", ApiProxy.RPCFailedException.class);
    assertV1Exception(Code.RESOURCE_EXHAUSTED, "", ApiProxy.OverQuotaException.class);
    assertV1Exception(Code.INTERNAL, "", DatastoreFailureException.class);
    assertV1Exception(Code.CANCELLED, "", DatastoreFailureException.class);
  }

  private static void assertV1Exception(Code code, String message, Class<?> expectedClass) {
    Throwable cause = new Throwable();
    RuntimeException exception = DatastoreApiHelper.createV1Exception(code, message, cause);
    assertThat(exception).isInstanceOf(expectedClass);
    assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
  }

  @Test
  public void testAppIdOverride() {
    // In the absence of any overrides, it gets the app id from the environment.
    String actualAppId = ApiProxy.getCurrentEnvironment().getAppId();
    assertThat(actualAppId).isEqualTo(DatastoreApiHelper.getCurrentAppId());

    // Setup an override in the environment attributes, and verify it comes through.
    String overriddenAppId = "someOverrideAppId";
    ApiProxy.getCurrentEnvironment()
        .getAttributes()
        .put(DatastoreApiHelper.APP_ID_OVERRIDE_KEY, overriddenAppId);
    assertThat(DatastoreApiHelper.getCurrentAppId()).isEqualTo(overriddenAppId);

    // Remove the override, and the actual app id is reflected again.
    ApiProxy.getCurrentEnvironment().getAttributes().remove(DatastoreApiHelper.APP_ID_OVERRIDE_KEY);
    assertThat(actualAppId).isEqualTo(DatastoreApiHelper.getCurrentAppId());
  }

  private Future<DatastoreV3Pb.Transaction> makeTestCall(byte[] response) {
    DatastoreV3Pb.GetRequest.Builder request = DatastoreV3Pb.GetRequest.newBuilder();
    expectMakeAsyncCall(request.buildPartial().toByteArray(), immediateFuture(response));

    return DatastoreApiHelper.makeAsyncCall(
        new ApiProxy.ApiConfig(), Method.Commit, request, DatastoreV3Pb.Transaction.newBuilder());
  }

  @Test
  public void testAsyncCall_ValidResponse() throws InterruptedException, ExecutionException {
    DatastoreV3Pb.Transaction.Builder response = DatastoreV3Pb.Transaction.newBuilder();
    response.setHandle(23).setApp("foo");
    assertThat(makeTestCall(response.buildPartial().toByteArray()).get()).isEqualTo(response.build());
  }

  @Test
  public void testAsyncCall_InvalidResponse() throws InterruptedException {
    byte[] response = {1, 2, 3};
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> makeTestCall(response).get());
    assertThat(e).hasCauseThat().isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  public void testAsyncCall_EmptyResponse() throws InterruptedException {
    // NOTE: For protos with no required fields, an empty response is valid.
    byte[] response = {};
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> makeTestCall(response).get());
    assertThat(e).hasCauseThat().isInstanceOf(InvalidProtocolBufferException.class);
  }
}
