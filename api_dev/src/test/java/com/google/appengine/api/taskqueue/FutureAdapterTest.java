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

package com.google.appengine.api.taskqueue;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FutureAdapterTest {

  static final class MyFutureAdapter extends FutureAdapter<String, Integer> {
    boolean wrapped = false;

    MyFutureAdapter(Future<String> parent) {
      super(parent);
    }

    @Override
    protected Integer wrap(String key) throws Exception {
      assertThat(wrapped).isFalse();
      wrapped = true;
      return key == null ? null : Integer.valueOf(key);
    }
  }

  @Test
  public void testWithNoException() throws Exception {
    Future<String> parent = immediateFuture("1");
    Future<Integer> wrapper = new MyFutureAdapter(parent);
    assertThat(wrapper.get()).isEqualTo(Integer.valueOf(1));
  }

  @Test
  public void testWithException() throws Exception {
    Future<String> parent = immediateFuture("A");
    Future<Integer> wrapper = new MyFutureAdapter(parent);
    ExecutionException ex = assertThrows(ExecutionException.class, wrapper::get);
    assertThat(ex.getCause().getClass()).isEqualTo(NumberFormatException.class);
  }
}
