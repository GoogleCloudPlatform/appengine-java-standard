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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.google.datastore.v1.BeginTransactionRequest;
import com.google.datastore.v1.BeginTransactionResponse;
import com.google.datastore.v1.client.Datastore;
import com.google.datastore.v1.client.DatastoreException;
import com.google.rpc.Code;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test for {@link CloudDatastoreV1ClientImpl}. */
@RunWith(JUnit4.class)
public class CloudDatastoreV1ClientImplTest {

  private static final String APP_ID = "s~project-id";

  @Mock private Datastore datastore;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void after() {
    DatastoreServiceGlobalConfig.clear();
  }

  @Test
  public void testCreate() {
    DatastoreServiceGlobalConfig.setConfig(
        DatastoreServiceGlobalConfig.builder()
            .appId(APP_ID)
            // Stub out call to the GCE metadata server.
            .emulatorHost("dummy-value-to-stub-out-credentials")
            .build());

    CloudDatastoreV1ClientImpl client1 =
        CloudDatastoreV1ClientImpl.create(DatastoreServiceConfig.Builder.withDefaults());
    CloudDatastoreV1ClientImpl client2 =
        CloudDatastoreV1ClientImpl.create(DatastoreServiceConfig.Builder.withDefaults());
    assertThat(client1.datastore).isSameInstanceAs(client2.datastore);

    CloudDatastoreV1ClientImpl deadline1client1 =
        CloudDatastoreV1ClientImpl.create(DatastoreServiceConfig.Builder.withDeadline(1.0));
    CloudDatastoreV1ClientImpl deadline1client2 =
        CloudDatastoreV1ClientImpl.create(DatastoreServiceConfig.Builder.withDeadline(1.0));
    assertThat(deadline1client1.datastore).isNotSameInstanceAs(client1.datastore);
    assertThat(deadline1client1.datastore).isSameInstanceAs(deadline1client2.datastore);

    CloudDatastoreV1ClientImpl deadline2client1 =
        CloudDatastoreV1ClientImpl.create(DatastoreServiceConfig.Builder.withDeadline(2.0));
    CloudDatastoreV1ClientImpl deadline2client2 =
        CloudDatastoreV1ClientImpl.create(DatastoreServiceConfig.Builder.withDeadline(2.0));
    assertThat(deadline2client1.datastore).isNotSameInstanceAs(client1.datastore);
    assertThat(deadline2client1.datastore).isNotSameInstanceAs(deadline1client1.datastore);
    assertThat(deadline2client1.datastore).isSameInstanceAs(deadline2client2.datastore);
  }

  @Test
  public void testStackTrace() throws Throwable {
    BeginTransactionRequest req = BeginTransactionRequest.getDefaultInstance();

    when(datastore.beginTransaction(req))
        .thenThrow(
            new DatastoreException("beginTransaction", Code.INVALID_ARGUMENT, "message", null));

    CloudDatastoreV1ClientImpl client = newClient(datastore);
    Future<BeginTransactionResponse> future = makeBeginTransactionCallForTest(client, req);

    try {
      future.get();
      fail("expected exception");
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      // The test helper method used to call beginTransaction() appears in the error message
      // (even though it is no longer on the stack).
      assertThat(e).hasMessageThat().contains("makeBeginTransactionCallForTest");
      // The underlying cause is also present.
      assertThat(e).hasCauseThat().hasCauseThat().isInstanceOf(DatastoreException.class);
    }
  }

  @Test
  public void testRetries() throws Exception {
    BeginTransactionRequest req = BeginTransactionRequest.getDefaultInstance();

    CloudDatastoreV1ClientImpl client = new CloudDatastoreV1ClientImpl(datastore, 2);

    when(datastore.beginTransaction(req))
        .thenThrow(retryableException())
        .thenThrow(retryableException())
        .thenReturn(BeginTransactionResponse.getDefaultInstance());

    client.beginTransaction(req).get();
  }

  @Test
  public void testRetries_MaxRetriesExceeded() throws Exception {
    BeginTransactionRequest req = BeginTransactionRequest.getDefaultInstance();

    CloudDatastoreV1ClientImpl client = new CloudDatastoreV1ClientImpl(datastore, 2);

    DatastoreException lastException = retryableException();
    when(datastore.beginTransaction(req))
        .thenThrow(retryableException())
        .thenThrow(retryableException())
        .thenThrow(lastException);

    try {
      client.beginTransaction(req).get();
      fail("expected exception");
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().hasCauseThat().isEqualTo(lastException);
    }
  }

  @Test
  public void testRetries_NonRetryable() throws Exception {
    BeginTransactionRequest req = BeginTransactionRequest.getDefaultInstance();

    CloudDatastoreV1ClientImpl client = new CloudDatastoreV1ClientImpl(datastore, 2);

    DatastoreException lastException = nonRetryableException();
    when(datastore.beginTransaction(req)).thenThrow(lastException);

    try {
      client.beginTransaction(req).get();
      fail("expected exception");
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().hasCauseThat().isEqualTo(lastException);
    }
  }

  @Test
  public void testAsyncStackTraceCaptureEnabled() throws Exception {
    runAsyncStackTraceCaptureTest(true);
  }

  @Test
  public void testAsyncStackTraceCaptureDisabled() throws Exception {
    runAsyncStackTraceCaptureTest(false);
  }

  private void runAsyncStackTraceCaptureTest(boolean asyncStackTraceCaptureEnabled)
      throws Exception {
    DatastoreServiceGlobalConfig.setConfig(
        DatastoreServiceGlobalConfig.builder()
            .appId(APP_ID)
            // Stub out call to the GCE metadata server.
            .emulatorHost("dummy-value-to-stub-out-credentials")
            .asyncStackTraceCaptureEnabled(asyncStackTraceCaptureEnabled)
            .build());

    BeginTransactionRequest req = BeginTransactionRequest.getDefaultInstance();

    CloudDatastoreV1ClientImpl client = new CloudDatastoreV1ClientImpl(datastore, 1);

    DatastoreException lastException = nonRetryableException();
    when(datastore.beginTransaction(req)).thenThrow(lastException);

    try {
      client.beginTransaction(req).get();
      fail("expected exception");
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().hasCauseThat().isEqualTo(lastException);

      if (asyncStackTraceCaptureEnabled) {
        assertThat(e).hasMessageThat().contains("stack trace when async call was initiated");
      } else {
        assertThat(e).hasMessageThat().contains("(stack trace capture for async call is disabled)");
      }
    }
  }

  // A helper method whose name we'll look for in exception messages.
  private Future<BeginTransactionResponse> makeBeginTransactionCallForTest(
      CloudDatastoreV1Client client, BeginTransactionRequest req) {
    return client.beginTransaction(req);
  }

  private static DatastoreException retryableException() {
    return new DatastoreException(
        "beginTransaction", Code.UNAVAILABLE, "message", new ConnectException());
  }

  private static DatastoreException nonRetryableException() {
    return new DatastoreException(
        "beginTransaction", Code.DEADLINE_EXCEEDED, "message", new SocketTimeoutException());
  }

  private static CloudDatastoreV1ClientImpl newClient(Datastore datastore) {
    return new CloudDatastoreV1ClientImpl(
        datastore, DatastoreServiceGlobalConfig.DEFAULT_MAX_RETRIES);
  }
}
