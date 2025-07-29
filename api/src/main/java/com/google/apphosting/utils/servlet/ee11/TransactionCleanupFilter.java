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

package com.google.apphosting.utils.servlet.ee11;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Transaction;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A servlet {@link Filter} that looks for datastore transactions that are
 * still active when request processing is finished.  The filter attempts
 * to roll back any transactions that are found, and swallows any exceptions
 * that are thrown while trying to perform roll backs.  This ensures that
 * any problems we encounter while trying to perform roll backs do not have any
 * impact on the result returned the user.
 *
 */
public class TransactionCleanupFilter implements Filter {

  private static final Logger logger = Logger.getLogger(TransactionCleanupFilter.class.getName());

  private DatastoreService datastoreService;

  @Override
  public void init(FilterConfig filterConfig) {
    datastoreService = getDatastoreService();
  }
  
  @Override
  public void destroy() {
    datastoreService = null;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } finally {
      handleAbandonedTxns(datastoreService.getActiveTransactions());
    }
  }

  private void handleAbandonedTxns(Collection<Transaction> txns) {
    // TODO: In the dev appserver, capture a stack trace whenever a
    // transaction is started so we can print it here.
    for (Transaction txn : txns) {
      String txnId;
      try {
        // getId() can throw if the beginTransaction() call failed. The rollback() call cleans up
        // thread local state (even if it also throws), so it's imperative we actually make the
        // call. See http://b/26878109 for details.
        txnId = txn.getId();
      } catch (Exception e) {
        txnId = "[unknown]";
      }
      logger.warning("Request completed without committing or rolling back transaction with id "
          + txnId + ".  Transaction will be rolled back.");

      try {
        txn.rollback();
      } catch (Exception e) {
        // We swallow exceptions so that there is no risk of our cleanup
        // impacting the actual result of the request.
        logger.log(Level.SEVERE, "Swallowing an exception we received while trying to rollback "
            + "abandoned transaction.", e);
      }
    }
  }

  // @VisibleForTesting
  DatastoreService getDatastoreService() {
    // Active transactions are ultimately stored in a thread local, so any instance of the
    // DatastoreService is sufficient to access them. Transactions that are active in other threads
    // are not cleaned up by this filter.
    return DatastoreServiceFactory.getDatastoreService();
  }
}
