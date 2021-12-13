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

package com.google.appengine.api.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class FutureWrapperTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  static final class MyFutureWrapper extends FutureWrapper<String, Integer> {
    boolean wrapped = false;
    boolean convertedException = false;
    boolean absorbParentException = false;

    static MyFutureWrapper create(Future<String> parent) {
      return create(parent, false);
    }

    static MyFutureWrapper create(Future<String> parent, boolean absorbParentException) {
      return new MyFutureWrapper(parent, absorbParentException);
    }

    private MyFutureWrapper(Future<String> parent, boolean abosrbParentException) {
      super(parent);
      this.absorbParentException = abosrbParentException;
    }

    @Override
    protected Integer wrap(String key) throws Exception {
      assertThat(wrapped).isFalse();
      wrapped = true;
      return key == null ? null : Integer.valueOf(key);
    }

    @Override
    protected Integer absorbParentException(Throwable cause) throws Throwable {
      if (absorbParentException) {
        assertThat(wrapped).isFalse();
        wrapped = true;
        return 10;
      }
      return super.absorbParentException(cause);
    }

    @Override
    protected Throwable convertException(Throwable cause) {
      assertThat(convertedException).isFalse();
      convertedException = true;
      return cause;
    }
  }

  @Mock private Future<String> parent;
  @Mock private Future<String> inner;
  @Mock private Future<String> inner2;
  @Mock private Future<String> futureThrowingExecutionException;
  @Mock private Future<String> futureThrowingRuntimeException;
  @Mock private Future<Integer> waitingFuture;

  @Test
  public void testAbsorbParentExceptionWithNoException()
      throws InterruptedException, ExecutionException, TimeoutException {
    when(parent.get()).thenReturn("1");
    Future<Integer> wrapper = MyFutureWrapper.create(parent, true);
    assertThat(wrapper.get()).isEqualTo(Integer.valueOf(1));

    when(parent.get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS))).thenReturn("2");
    wrapper = MyFutureWrapper.create(parent, true);
    assertThat(wrapper.get(10, TimeUnit.MILLISECONDS)).isEqualTo(Integer.valueOf(2));
  }

  @Test
  public void testAbsorbParentExceptionWithParentException()
      throws InterruptedException, ExecutionException, TimeoutException {
    when(parent.get()).thenThrow(new ExecutionException(new IllegalArgumentException()));
    Future<Integer> wrapper = MyFutureWrapper.create(parent, true);
    assertThat(wrapper.get()).isEqualTo(Integer.valueOf(10));
    verify(parent).get();

    when(parent.get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenThrow(new ExecutionException(new IllegalArgumentException()));
    wrapper = MyFutureWrapper.create(parent, true);
    assertThat(wrapper.get(10, TimeUnit.MILLISECONDS)).isEqualTo(Integer.valueOf(10));
    verify(parent).get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void testAbsorbParentExceptionWithWrapperException()
      throws InterruptedException, ExecutionException, TimeoutException {
    when(parent.get()).thenReturn("A");
    Future<Integer> wrapper1 = MyFutureWrapper.create(parent, true);
    ExecutionException ex1 = assertThrows(ExecutionException.class, wrapper1::get);
    assertThat(ex1.getCause().getClass()).isEqualTo(NumberFormatException.class);

    when(parent.get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS))).thenReturn("A");
    Future<Integer> wrapper2 = MyFutureWrapper.create(parent, true);
    ExecutionException ex2 =
        assertThrows(ExecutionException.class, () -> wrapper2.get(10, TimeUnit.MILLISECONDS));
    assertThat(ex2.getCause().getClass()).isEqualTo(NumberFormatException.class);
  }

  @Test
  public void testExecutionExceptionIsRuntimeExceptionHandling()
      throws InterruptedException, ExecutionException {
    RuntimeException cause = new RuntimeException("catch me if you can");

    when(futureThrowingExecutionException.get()).thenThrow(new ExecutionException(cause));

    when(futureThrowingRuntimeException.get()).thenThrow(cause);

    MyFutureWrapper futureExecutionExceptionWrapper =
        MyFutureWrapper.create(futureThrowingExecutionException);
    MyFutureWrapper futureRuntimeExceptionWrapper =
        MyFutureWrapper.create(futureThrowingRuntimeException);

    ExecutionException e1 =
        assertThrows(ExecutionException.class, futureExecutionExceptionWrapper::get);
    Throwable fromExecutionException = e1.getCause();

    ExecutionException e2 =
        assertThrows(ExecutionException.class, futureRuntimeExceptionWrapper::get);
    Throwable fromRuntimeException = e2.getCause();

    assertThat(fromExecutionException).isNotNull();
    assertThat(fromRuntimeException).isNotNull();

    assertThat(fromExecutionException).isSameInstanceAs(cause);
    assertThat(fromRuntimeException).isSameInstanceAs(fromExecutionException);
  }

  @Test
  public void testNonNullResultCaching()
      throws ExecutionException, InterruptedException, TimeoutException {
    testResultCaching(3);
  }

  @Test
  public void testNullResultCaching()
      throws ExecutionException, InterruptedException, TimeoutException {
    testResultCaching(null);
  }

  private void testResultCaching(Integer returnValue)
      throws ExecutionException, InterruptedException, TimeoutException {
    when(inner.get()).thenReturn(returnValue == null ? null : returnValue.toString());
    MyFutureWrapper wrapper = MyFutureWrapper.create(inner);
    assertThat(wrapper.wrapped).isFalse();
    assertThat(wrapper.convertedException).isFalse();
    assertThat(wrapper.get()).isEqualTo(returnValue);
    assertThat(wrapper.wrapped).isTrue();
    assertThat(wrapper.convertedException).isFalse();
    assertThat(wrapper.get()).isEqualTo(returnValue);
    assertThat(wrapper.wrapped).isTrue();
    assertThat(wrapper.convertedException).isFalse();
    assertThat(wrapper.get()).isEqualTo(returnValue);
    assertThat(wrapper.wrapped).isTrue();
    assertThat(wrapper.convertedException).isFalse();

    when(inner2.get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(returnValue == null ? null : returnValue.toString());
    wrapper = MyFutureWrapper.create(inner2);
    assertThat(wrapper.wrapped).isFalse();
    assertThat(wrapper.convertedException).isFalse();
    assertThat(wrapper.get(30, TimeUnit.SECONDS)).isEqualTo(returnValue);
    assertThat(wrapper.wrapped).isTrue();
    assertThat(wrapper.convertedException).isFalse();
    assertThat(wrapper.get(30, TimeUnit.SECONDS)).isEqualTo(returnValue);
    assertThat(wrapper.wrapped).isTrue();
    assertThat(wrapper.convertedException).isFalse();
    assertThat(wrapper.get(30, TimeUnit.SECONDS)).isEqualTo(returnValue);
    assertThat(wrapper.wrapped).isTrue();
    assertThat(wrapper.convertedException).isFalse();
  }

  @Test
  public void testExceptionResultCaching()
      throws ExecutionException, InterruptedException, TimeoutException {
    when(inner.get()).thenThrow(new ExecutionException(new RuntimeException("boom")));
    MyFutureWrapper wrapper1 = MyFutureWrapper.create(inner);
    assertThat(wrapper1.wrapped).isFalse();
    assertThat(wrapper1.convertedException).isFalse();
    ExecutionException e1 = assertThrows(ExecutionException.class, wrapper1::get);
    assertThat(e1).hasCauseThat().hasMessageThat().isEqualTo("boom");
    assertThat(wrapper1.wrapped).isFalse();
    assertThat(wrapper1.convertedException).isTrue();
    ExecutionException e2 = assertThrows(ExecutionException.class, wrapper1::get);
    assertThat(e2).hasCauseThat().hasMessageThat().isEqualTo("boom");
    assertThat(wrapper1.wrapped).isFalse();
    assertThat(wrapper1.convertedException).isTrue();
    verify(inner).get();

    when(inner2.get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenThrow(new ExecutionException(new RuntimeException("boom")));
    MyFutureWrapper wrapper2 = MyFutureWrapper.create(inner2);
    assertThat(wrapper2.wrapped).isFalse();
    assertThat(wrapper2.convertedException).isFalse();
    ExecutionException e3 =
        assertThrows(ExecutionException.class, () -> wrapper2.get(30000, TimeUnit.MILLISECONDS));
    assertThat(e3).hasCauseThat().hasMessageThat().isEqualTo("boom");
    assertThat(wrapper2.wrapped).isFalse();
    assertThat(wrapper2.convertedException).isTrue();
    ExecutionException e4 =
        assertThrows(ExecutionException.class, () -> wrapper2.get(30000, TimeUnit.MILLISECONDS));
    assertThat(e4).hasCauseThat().hasMessageThat().isEqualTo("boom");
    assertThat(wrapper2.wrapped).isFalse();
    assertThat(wrapper2.convertedException).isTrue();
    verify(inner2).get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void testInterruptedExceptionResultCaching()
      throws ExecutionException, InterruptedException, TimeoutException {
    Throwable[] throwables = {new InterruptedException("boom"), new TimeoutException("boom")};
    for (Throwable throwMe : throwables) {
      when(inner.get(ArgumentMatchers.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
          .thenThrow(throwMe, throwMe);
      MyFutureWrapper wrapper = MyFutureWrapper.create(inner);
      assertThat(wrapper.wrapped).isFalse();
      assertThat(wrapper.convertedException).isFalse();
      Exception e1 = assertThrows(Exception.class, () -> wrapper.get(30000, TimeUnit.MILLISECONDS));
      assertThat(e1).isSameInstanceAs(throwMe);
      assertThat(wrapper.wrapped).isFalse();
      // We don't wrap or cache InterruptedException
      assertThat(wrapper.convertedException).isFalse();
      Exception e2 = assertThrows(Exception.class, () -> wrapper.get(30000, TimeUnit.MILLISECONDS));
      assertThat(e2).isSameInstanceAs(throwMe);
      assertThat(wrapper.wrapped).isFalse();
      assertThat(wrapper.convertedException).isFalse();
    }

    InterruptedException boom = new InterruptedException("boom");
    when(inner.get()).thenThrow(boom, boom);
    MyFutureWrapper wrapper = MyFutureWrapper.create(inner);
    assertThat(wrapper.wrapped).isFalse();
    assertThat(wrapper.convertedException).isFalse();
    InterruptedException e3 = assertThrows(InterruptedException.class, wrapper::get);
    assertThat(e3).hasMessageThat().isEqualTo("boom");
    assertThat(wrapper.wrapped).isFalse();
    // We don't wrap or cache InterruptedException
    assertThat(wrapper.convertedException).isFalse();
    InterruptedException e4 = assertThrows(InterruptedException.class, wrapper::get);
    assertThat(e4).hasMessageThat().isEqualTo("boom");
    assertThat(wrapper.wrapped).isFalse();
    assertThat(wrapper.convertedException).isFalse();
    verify(inner, times(2)).get();
  }

  @Test
  public void testWaitingBehavior()
      throws ExecutionException, InterruptedException, BrokenBarrierException {
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final CountDownLatch threadRelease = new CountDownLatch(1);
    final CountDownLatch threadDone = new CountDownLatch(1);
    // Anyone who calls get() on this Future will block until threadRelease
    // counts down.
    when(waitingFuture.get())
        .thenAnswer(
            new Answer<Integer>() {
              @Override
              public Integer answer(InvocationOnMock invocation) throws Throwable {
                barrier.await();
                threadRelease.await();
                return 3;
              }
            });
    // An identity wrapper.
    final FutureWrapper<Integer, Integer> wrapper =
        new FutureWrapper<Integer, Integer>(waitingFuture) {
          @Override
          protected Integer wrap(Integer key) throws Exception {
            return key;
          }

          @Override
          protected Throwable convertException(Throwable cause) {
            return cause;
          }
        };
    final AtomicInteger exceptions = new AtomicInteger(0);
    Runnable runnable =
        () -> {
          try {
            // this will block until we release the threadRelease latch.
            wrapper.get();
          } catch (InterruptedException | ExecutionException e) {
            exceptions.incrementAndGet();
          } finally {
            threadDone.countDown();
          }
        };
    new Thread(runnable).start();
    // Wait until the thread has the lock.
    barrier.await();
    // Meanwhile, we'll try to fetch the result of the future with our own
    // timeout multiple times.  We should get a timeout every time because the
    // other thread is still holding the lock.
    for (int i = 0; i < 5; i++) {
      assertThrows(TimeoutException.class, () -> wrapper.get(50, TimeUnit.MILLISECONDS));
    }
    assertThat(exceptions.get()).isEqualTo(0);
    threadRelease.countDown();
    threadDone.await();
    verify(waitingFuture).get();
  }
}
