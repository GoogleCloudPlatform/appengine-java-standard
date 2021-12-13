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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FutureHelperTest {
  @Test
  public void testQuietGet_Success() throws ExecutionException, InterruptedException {
    Future<String> future = happyFuture();
    assertThat(FutureHelper.quietGet(future)).isEqualTo("yar");
  }

  @Test
  public void testQuietGet_Exception_Success()
      throws ExecutionException, InterruptedException, MyException {
    Future<String> future = happyFuture();
    assertThat(FutureHelper.quietGet(future, MyException.class)).isEqualTo("yar");
  }

  @Test
  public void testQuietGet_Interrupted() throws ExecutionException, InterruptedException {
    Future<String> future = interruptedFuture();
    SearchServiceException rte =
        assertThrows(SearchServiceException.class, () -> FutureHelper.quietGet(future));
    assertThat(rte).hasCauseThat().isInstanceOf(InterruptedException.class);
  }

  @Test
  public void testQuietGet_Exception_Interrupted()
      throws ExecutionException, InterruptedException, MyException {
    Future<String> future = interruptedFuture();
    SearchServiceException rte =
        assertThrows(
            SearchServiceException.class, () -> FutureHelper.quietGet(future, MyException.class));
    assertThat(rte).hasCauseThat().isInstanceOf(InterruptedException.class);
  }

  @Test
  public void testQuietGet_RuntimeExecutionException()
      throws ExecutionException, InterruptedException {
    Future<String> future = runtimeExecutionExceptionFuture();
    assertThrows(MyRuntimeException.class, () -> FutureHelper.quietGet(future));
  }

  @Test
  public void testQuietGet_Exception_RuntimeExecutionException()
      throws ExecutionException, InterruptedException, MyException {
    Future<String> future = runtimeExecutionExceptionFuture();
    assertThrows(MyRuntimeException.class, () -> FutureHelper.quietGet(future, MyException.class));
  }

  @Test
  public void testQuietGet_ErrorExecutionException()
      throws ExecutionException, InterruptedException {
    Future<String> future = errorExecutionExceptionFuture();
    assertThrows(MyError.class, () -> FutureHelper.quietGet(future));
  }

  @Test
  public void testQuietGet_Exception_ErrorExecutionException()
      throws ExecutionException, InterruptedException, MyException {
    Future<String> future = errorExecutionExceptionFuture();
    assertThrows(MyError.class, () -> FutureHelper.quietGet(future, MyException.class));
  }

  @Test
  public void testQuietGet_UndeclaredExecutionException()
      throws ExecutionException, InterruptedException {
    Future<String> future = undeclaredExecutionExceptionFuture();
    UndeclaredThrowableException rte =
        assertThrows(UndeclaredThrowableException.class, () -> FutureHelper.quietGet(future));
    assertThat(rte.getCause().getClass()).isEqualTo(MyOtherException.class);
  }

  @Test
  public void testQuietGet_Exception_UndeclaredExecutionException()
      throws ExecutionException, InterruptedException, MyException {
    Future<String> future = undeclaredExecutionExceptionFuture();
    UndeclaredThrowableException rte =
        assertThrows(
            UndeclaredThrowableException.class,
            () -> FutureHelper.quietGet(future, MyException.class));
    assertThat(rte.getCause().getClass()).isEqualTo(MyOtherException.class);
  }

  @Test
  public void testQuietGet_DeclaredException() throws ExecutionException, InterruptedException {
    Future<String> future = immediateFailedFuture(new MyException());
    assertThrows(MyException.class, () -> FutureHelper.quietGet(future, MyException.class));
  }

  private static class MyException extends Exception {
    private static final long serialVersionUID = 1;
  }

  private static class MyOtherException extends Exception {
    private static final long serialVersionUID = 1;
  }

  private static class MyRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1;
  }

  private static class MyError extends Error {
    private static final long serialVersionUID = 1;
  }

  private Future<String> happyFuture() throws ExecutionException, InterruptedException {
    return immediateFuture("yar");
  }

  private Future<String> interruptedFuture() throws ExecutionException, InterruptedException {
    @SuppressWarnings("unchecked")
    Future<String> future = mock(Future.class);
    when(future.get()).thenThrow(new InterruptedException());
    return future;
  }

  private Future<String> runtimeExecutionExceptionFuture()
      throws ExecutionException, InterruptedException {
    return immediateFailedFuture(new MyRuntimeException());
  }

  private Future<String> errorExecutionExceptionFuture()
      throws ExecutionException, InterruptedException {
    return immediateFailedFuture(new MyError());
  }

  private Future<String> undeclaredExecutionExceptionFuture()
      throws ExecutionException, InterruptedException {
    return immediateFailedFuture(new MyOtherException());
  }
}
