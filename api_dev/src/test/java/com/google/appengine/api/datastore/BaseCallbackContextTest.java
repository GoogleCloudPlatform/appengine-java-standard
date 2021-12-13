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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class BaseCallbackContextTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CurrentTransactionProvider txnProvider;

  @Test
  public void testExecuteCallbacks() {
    BaseCallbackContext<String> context =
        new BaseCallbackContext<String>(txnProvider, Arrays.asList("kind1", "kind2")) {
          @Override
          String getKind(String ele) {
            return ele;
          }
        };

    final AtomicInteger numCallbacksRun = new AtomicInteger(0);
    class MyCallback implements DatastoreCallbacksImpl.Callback {
      final LinkedList<Integer> expected;

      private MyCallback(Integer... elements) {
        this.expected = new LinkedList<>(ImmutableList.copyOf(elements));
      }

      @Override
      public void run(CallbackContext<?> context) {
        assertThat(numCallbacksRun.getAndIncrement()).isEqualTo(expected.removeFirst().intValue());
      }
    }
    DatastoreCallbacksImpl.Callback callback1 = new MyCallback(0);
    DatastoreCallbacksImpl.Callback callback2 = new MyCallback(1);
    DatastoreCallbacksImpl.Callback callback3 = new MyCallback(2, 3);
    SetMultimap<String, DatastoreCallbacksImpl.Callback> callbacksByKind =
        LinkedHashMultimap.create();
    callbacksByKind.put("kind1", callback1);
    callbacksByKind.put("kind1", callback2);
    Collection<DatastoreCallbacksImpl.Callback> noKindCallbacks = Lists.newArrayList(callback3);
    context.executeCallbacks(callbacksByKind, noKindCallbacks);
    assertThat(numCallbacksRun.get()).isEqualTo(4);

    assertThrows(
        IllegalStateException.class,
        () -> context.executeCallbacks(callbacksByKind, noKindCallbacks));
  }

  @Test
  public void testElementsUnmodifiable() {
    List<String> strings = Arrays.asList("a", "b", "c");
    BaseCallbackContext<String> bic =
        new BaseCallbackContext<String>(txnProvider, strings) {
          @Override
          String getKind(String ele) {
            return null;
          }
        };
    assertThrows(UnsupportedOperationException.class, () -> bic.getElements().add("d"));
  }
}
