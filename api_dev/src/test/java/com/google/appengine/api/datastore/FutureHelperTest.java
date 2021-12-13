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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for FutureHelper */
@RunWith(JUnit4.class)
public class FutureHelperTest {
  @Mock Future<Void> inner1;

  @Mock Future<Void> inner2;

  @Mock Future<Void> inner3;

  List<Future<Void>> futures;

  Future<Void> multiFuture;

  @Mock Environment mockEnv;

  @Before
  public void beforeFutureHelperTest() {
    MockitoAnnotations.initMocks(this);
    futures = Arrays.asList(inner1, inner2, inner3);

    multiFuture =
        new FutureHelper.MultiFuture<Void, Void>(futures) {
          @Override
          public Void get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
          }

          @Override
          public Void get(long timeout, TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
          }
        };
    ApiProxy.setEnvironmentForCurrentThread(mockEnv);
  }

  @Test
  public void testQuietGet_Success() throws Exception {
    Future<String> future = happyFuture();
    assertThat(FutureHelper.quietGet(future)).isEqualTo("yar");
  }

  @Test
  public void testQuietGet_Exception_Success() throws Exception {
    Future<String> future = happyFuture();
    assertThat(FutureHelper.quietGet(future, MyException.class)).isEqualTo("yar");
  }

  @Test
  public void testQuietGet_Interrupted() throws Exception {
    Future<String> future = interruptedFuture();
    DatastoreFailureException rte =
        assertThrows(DatastoreFailureException.class, () -> FutureHelper.quietGet(future));
    assertThat(rte.getCause().getClass()).isEqualTo(InterruptedException.class);
  }

  @Test
  public void testQuietGet_Exception_Interrupted_BeforeRequestTimeout() throws Exception {
    Future<String> future = interruptedFuture();
    when(mockEnv.getRemainingMillis()).thenReturn(10L); // Has time left.
    DatastoreFailureException rte =
        assertThrows(
            DatastoreFailureException.class,
            () -> FutureHelper.quietGet(future, MyException.class));
    assertThat(rte).hasMessageThat().isEqualTo("The thread has been interrupted");
    assertThat(rte.getCause().getClass()).isEqualTo(InterruptedException.class);
  }

  @Test
  public void testQuietGet_Exception_Interrupted_AfterRequestTimeout() throws Exception {
    Future<String> future = interruptedFuture();
    when(mockEnv.getRemainingMillis()).thenReturn(0L); // No time left.
    DatastoreFailureException rte =
        assertThrows(
            DatastoreFailureException.class,
            () -> FutureHelper.quietGet(future, MyException.class));
    assertThat(rte)
        .hasMessageThat()
        .isEqualTo("The thread has been interrupted and the request has timed out");
    assertThat(rte.getCause().getClass()).isEqualTo(InterruptedException.class);
  }

  @Test
  public void testQuietGet_RuntimeExecutionException() throws Exception {
    Future<String> future = runtimeExecutionExceptionFuture();
    assertThrows(MyRuntimeException.class, () -> FutureHelper.quietGet(future));
  }

  @Test
  public void testQuietGet_Exception_RuntimeExecutionException() throws Exception {
    Future<String> future = runtimeExecutionExceptionFuture();
    assertThrows(MyRuntimeException.class, () -> FutureHelper.quietGet(future, MyException.class));
  }

  @Test
  public void testQuietGet_ErrorExecutionException() throws Exception {
    Future<String> future = errorExecutionExceptionFuture();
    assertThrows(MyError.class, () -> FutureHelper.quietGet(future));
  }

  @Test
  public void testQuietGet_Exception_ErrorExecutionException() throws Exception {
    Future<String> future = errorExecutionExceptionFuture();
    assertThrows(MyError.class, () -> FutureHelper.quietGet(future, MyException.class));
  }

  @Test
  public void testQuietGet_UndeclaredExecutionException() throws Exception {
    Future<String> future = undeclaredExecutionExceptionFuture();
    UndeclaredThrowableException rte =
        assertThrows(UndeclaredThrowableException.class, () -> FutureHelper.quietGet(future));
    assertThat(rte.getCause().getClass()).isEqualTo(MyOtherException.class);
  }

  @Test
  public void testQuietGet_Exception_UndeclaredExecutionException() throws Exception {
    Future<String> future = undeclaredExecutionExceptionFuture();
    UndeclaredThrowableException rte =
        assertThrows(
            UndeclaredThrowableException.class,
            () -> FutureHelper.quietGet(future, MyException.class));
    assertThat(rte.getCause().getClass()).isEqualTo(MyOtherException.class);
  }

  @Test
  public void testQuietGet_DeclaredException() throws Exception {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenThrow(new ExecutionException(new MyException()));
    assertThrows(MyException.class, () -> FutureHelper.quietGet(future, MyException.class));
  }

  private static class MyException extends Exception {}

  private static class MyOtherException extends Exception {}

  private static class MyRuntimeException extends RuntimeException {}

  private static class MyError extends Error {}

  private Future<String> happyFuture() throws Exception {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenReturn("yar");
    return future;
  }

  private Future<String> interruptedFuture() throws Exception {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenThrow(new InterruptedException());
    return future;
  }

  private Future<String> runtimeExecutionExceptionFuture() throws Exception {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenThrow(new ExecutionException(new MyRuntimeException()));
    return future;
  }

  private Future<String> errorExecutionExceptionFuture() throws Exception {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenThrow(new ExecutionException(new MyError()));
    return future;
  }

  private Future<String> undeclaredExecutionExceptionFuture() throws Exception {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenThrow(new ExecutionException(new MyOtherException()));
    return future;
  }

  @Test
  public void testGetClearsFutureCallbacks() throws Exception {
    when(inner1.get()).thenReturn(null);
    when(inner2.get(1000, TimeUnit.MILLISECONDS)).thenReturn(null);
    Transaction txn = mock(Transaction.class);
    when(txn.getId()).thenReturn("1234");
    TransactionStack stack = new TransactionStackImpl();
    stack.push(txn);
    stack.addFuture(txn, inner1);
    stack.addFuture(txn, inner2);
    stack.addFuture(txn, inner3);
    assertThat(stack.getFutures(txn)).hasSize(3);
    Future<Void> outer = new FutureHelper.TxnAwareFuture<Void>(inner1, txn, stack);
    outer.get();
    assertThat(stack.getFutures(txn)).hasSize(2);
    Iterator<Future<?>> iter = stack.getFutures(txn).iterator();
    assertThat(iter.next()).isSameInstanceAs(inner2);
    assertThat(iter.next()).isSameInstanceAs(inner3);

    outer = new FutureHelper.TxnAwareFuture<Void>(inner2, txn, stack);
    outer.get(1000, TimeUnit.MILLISECONDS);
    assertThat(stack.getFutures(txn)).hasSize(1);
    iter = stack.getFutures(txn).iterator();
    assertThat(iter.next()).isSameInstanceAs(inner3);
  }

  @Test
  public void testAggregateFuture_cancel() {
    when(inner1.cancel(true)).thenReturn(Boolean.TRUE);
    when(inner2.cancel(true)).thenReturn(Boolean.TRUE);
    when(inner3.cancel(true)).thenReturn(Boolean.TRUE);
    assertThat(multiFuture.cancel(true)).isTrue();

    when(inner1.cancel(false)).thenReturn(Boolean.TRUE);
    when(inner2.cancel(false)).thenReturn(Boolean.FALSE);
    when(inner3.cancel(false)).thenReturn(Boolean.TRUE);
    assertThat(multiFuture.cancel(false)).isFalse();

    when(inner1.cancel(true)).thenReturn(Boolean.FALSE);
    when(inner2.cancel(true)).thenReturn(Boolean.FALSE);
    when(inner3.cancel(true)).thenReturn(Boolean.FALSE);
    assertThat(multiFuture.cancel(true)).isFalse();
  }

  @Test
  public void testAggregateFuture_isCancelled() {
    when(inner1.isCancelled()).thenReturn(Boolean.TRUE);
    when(inner2.isCancelled()).thenReturn(Boolean.TRUE);
    when(inner3.isCancelled()).thenReturn(Boolean.TRUE);
    assertThat(multiFuture.isCancelled()).isTrue();

    when(inner1.isCancelled()).thenReturn(Boolean.TRUE);
    when(inner2.isCancelled()).thenReturn(Boolean.FALSE);
    when(inner3.isCancelled()).thenReturn(Boolean.TRUE);
    assertThat(multiFuture.isCancelled()).isFalse();

    when(inner1.isCancelled()).thenReturn(Boolean.FALSE);
    when(inner2.isCancelled()).thenReturn(Boolean.FALSE);
    when(inner3.isCancelled()).thenReturn(Boolean.FALSE);
    assertThat(multiFuture.isCancelled()).isFalse();
  }

  @Test
  public void testAggregateFuture_isDone() {
    when(inner1.isDone()).thenReturn(Boolean.TRUE);
    when(inner2.isDone()).thenReturn(Boolean.TRUE);
    when(inner3.isDone()).thenReturn(Boolean.TRUE);
    assertThat(multiFuture.isDone()).isTrue();

    when(inner1.isDone()).thenReturn(Boolean.TRUE);
    when(inner2.isDone()).thenReturn(Boolean.FALSE);
    when(inner3.isDone()).thenReturn(Boolean.TRUE);
    assertThat(multiFuture.isDone()).isFalse();

    when(inner1.isDone()).thenReturn(Boolean.FALSE);
    when(inner2.isDone()).thenReturn(Boolean.FALSE);
    when(inner3.isDone()).thenReturn(Boolean.FALSE);
    assertThat(multiFuture.isDone()).isFalse();
  }
}
