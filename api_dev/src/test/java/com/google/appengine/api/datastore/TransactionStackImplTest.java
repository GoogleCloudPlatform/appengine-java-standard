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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransactionStackImplTest {

  private final TransactionStackImpl stack =
      new TransactionStackImpl(new InstanceMemberThreadLocalTransactionStack());

  private void assertEmptyFutures(Transaction txn) {
    assertThat(stack.getFutures(txn)).isEmpty();
  }

  @Test
  public void testLifecycle_1_Txn() {
    Transaction txn = mock(Transaction.class);
    when(txn.getId()).thenReturn("12345");

    stack.push(txn);
    assertThat(stack.getFutures(txn)).isEmpty();
    assertThat(stack.peek()).isSameInstanceAs(txn);
    assertThat(stack.getFutures(txn)).isEmpty();
    assertThat(stack.pop()).isSameInstanceAs(txn);
    assertEmptyFutures(txn);

    // once we've deregistered, everything else should fail
    assertThat(stack.peek(null)).isNull();
    assertThrows(IllegalStateException.class, stack::peek);

    assertThrows(IllegalStateException.class, () -> stack.remove(txn));
  }

  @Test
  public void testLifecycle_Nested_Txns() {
    Transaction txn1 = mock(Transaction.class);
    when(txn1.getId()).thenReturn("12345");
    Transaction txn2 = mock(Transaction.class);
    when(txn2.getId()).thenReturn("123456");
    Transaction txn3 = mock(Transaction.class);
    when(txn3.getId()).thenReturn("1234567");

    stack.push(txn1);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    stack.push(txn2);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertEmptyFutures(txn3);

    stack.push(txn3);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertThat(stack.getFutures(txn3)).isEmpty();

    assertThat(stack.peek()).isSameInstanceAs(txn3);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertThat(stack.getFutures(txn3)).isEmpty();

    assertThat(stack.pop()).isSameInstanceAs(txn3);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertEmptyFutures(txn3);

    assertThat(stack.peek()).isSameInstanceAs(txn2);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertEmptyFutures(txn3);

    assertThat(stack.pop()).isSameInstanceAs(txn2);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    assertThat(stack.peek()).isSameInstanceAs(txn1);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    assertThat(stack.pop()).isSameInstanceAs(txn1);
    assertEmptyFutures(txn1);
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    // once we've deregistered, everything else should fail
    assertThat(stack.peek(null)).isNull();
    assertThrows(IllegalStateException.class, stack::peek);

    assertThrows(IllegalStateException.class, () -> stack.remove(txn1));
    assertThrows(IllegalStateException.class, () -> stack.remove(txn2));
    assertThrows(IllegalStateException.class, () -> stack.remove(txn3));
  }

  @Test
  public void testLifecycle_DeregisterSpecificTxns() {
    Transaction txn1 = mock(Transaction.class);
    when(txn1.getId()).thenReturn("12345");
    Transaction txn2 = mock(Transaction.class);
    when(txn2.getId()).thenReturn("123456");
    Transaction txn3 = mock(Transaction.class);
    when(txn3.getId()).thenReturn("1234567");

    assertEmptyFutures(txn1);
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    stack.push(txn1);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    stack.push(txn2);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertEmptyFutures(txn3);

    stack.push(txn3);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertThat(stack.getFutures(txn3)).isEmpty();

    stack.remove(txn2);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertEmptyFutures(txn2);
    assertThat(stack.getFutures(txn3)).isEmpty();

    assertThrows(IllegalStateException.class, () -> stack.remove(txn2));

    stack.remove(txn3);
    assertThrows(IllegalStateException.class, () -> stack.remove(txn3));

    assertThat(stack.peek()).isSameInstanceAs(txn1);
    assertThat(stack.pop()).isSameInstanceAs(txn1);
    assertThat(stack.peek(null)).isNull();
  }

  @Test
  public void testClearAll() {
    Transaction txn1 = mock(Transaction.class);
    when(txn1.getId()).thenReturn("12345");
    Transaction txn2 = mock(Transaction.class);
    when(txn2.getId()).thenReturn("123456");
    Transaction txn3 = mock(Transaction.class);
    when(txn3.getId()).thenReturn("1234567");

    assertEmptyFutures(txn1);
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    stack.push(txn1);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    stack.push(txn2);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertEmptyFutures(txn3);

    stack.push(txn3);
    assertThat(stack.getFutures(txn1)).isEmpty();
    assertThat(stack.getFutures(txn2)).isEmpty();
    assertThat(stack.getFutures(txn3)).isEmpty();

    stack.clearAll();
    assertEmptyFutures(txn1);
    assertEmptyFutures(txn2);
    assertEmptyFutures(txn3);

    assertThrows(IllegalStateException.class, () -> stack.remove(txn1));

    assertThrows(IllegalStateException.class, () -> stack.remove(txn2));

    assertThrows(IllegalStateException.class, () -> stack.remove(txn3));
  }

  @Test
  public void testPeekOnEmptyStack() {
    assertThat(stack.peek(null)).isNull();
  }

  @Test
  public void testPushNull() {
    assertThrows(NullPointerException.class, () -> stack.push(null));
  }
}
