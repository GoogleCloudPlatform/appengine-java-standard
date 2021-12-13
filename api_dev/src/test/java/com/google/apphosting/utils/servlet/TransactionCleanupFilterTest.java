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

package com.google.apphosting.utils.servlet;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransactionCleanupFilterTest {

  private static class MyTransactionCleanupFilter extends TransactionCleanupFilter {

    private final DatastoreService datastoreService;

    private MyTransactionCleanupFilter(DatastoreService datastoreService) {
      this.datastoreService = datastoreService;
    }

    @Override
    DatastoreService getDatastoreService() {
      return datastoreService;
    }
  }

  @Test
  public void testNoAbandonedTransactions() throws ServletException, IOException {
    DatastoreService datastoreMock = mock(DatastoreService.class);
    when(datastoreMock.getActiveTransactions()).thenReturn(ImmutableList.of());
    MyTransactionCleanupFilter filter = new MyTransactionCleanupFilter(datastoreMock);
    filter.init(null);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(null, null, chain);
    verify(chain, atLeastOnce()).doFilter(null, null);
  }

  @Test
  public void testAbandonedTransactions_allRolledBack() throws ServletException, IOException {
    DatastoreService datastoreMock = mock(DatastoreService.class);
    Transaction txn1 = mock(Transaction.class);
    when(txn1.getId()).thenReturn("txn1");
    Transaction txn2 = mock(Transaction.class);
    when(txn2.getId()).thenReturn("txn2");
    when(datastoreMock.getActiveTransactions())
        .thenReturn(Lists.newArrayList(txn1, txn2));
    MyTransactionCleanupFilter filter = new MyTransactionCleanupFilter(datastoreMock);
    filter.init(null);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(null, null, chain);
    verify(chain, atLeastOnce()).doFilter(null, null);
    verify(txn1, atLeastOnce()).rollback();
    verify(txn2, atLeastOnce()).rollback();
  }

  @Test
  public void testAbandonedTransactions_firstRollBackFails() throws ServletException, IOException {
    DatastoreService datastoreMock = mock(DatastoreService.class);
    Transaction txn1 = mock(Transaction.class);
    when(txn1.getId()).thenReturn("txn1");
    // the first txn throws an exception when we rollback
    doThrow(new RuntimeException()).when(txn1).rollback();
    Transaction txn2 = mock(Transaction.class);
    when(txn2.getId()).thenReturn("txn2");
    when(datastoreMock.getActiveTransactions())
        .thenReturn(Lists.newArrayList(txn1, txn2));
    MyTransactionCleanupFilter filter = new MyTransactionCleanupFilter(datastoreMock);
    filter.init(null);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(null, null, chain);
    verify(chain, atLeastOnce()).doFilter(null, null);
    verify(txn1, times(1)).rollback();
    verify(txn2, atLeastOnce()).rollback();
  }
}
