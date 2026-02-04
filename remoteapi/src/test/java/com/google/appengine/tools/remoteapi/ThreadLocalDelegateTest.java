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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 */
@RunWith(JUnit4.class)
public class ThreadLocalDelegateTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Delegate<Environment> global;
  @Mock private Delegate<Environment> local;

  @Test
  public void testSetDelegateForThread()
      throws ExecutionException, TimeoutException, InterruptedException {
    when(local.getRequestThreads(null)).thenReturn(ImmutableList.of());
    when(global.getRequestThreads(null)).thenReturn(Lists.<Thread>newArrayList(null, null));

    ApiProxy.setDelegate(new ThreadLocalDelegate<>(global, local));

    Delegate<?> delegate = ApiProxy.getDelegate();

    assertThat(delegate.getRequestThreads(null)).isEmpty();

    Executors.newSingleThreadExecutor()
        .submit(() -> {
            Delegate<?> delegate1 = ApiProxy.getDelegate();
            assertThat(delegate1.getRequestThreads(null)).hasSize(2);
    })
        .get(1, SECONDS);

    verify(local).getRequestThreads(null);
    verify(global).getRequestThreads(null);
  }
}
