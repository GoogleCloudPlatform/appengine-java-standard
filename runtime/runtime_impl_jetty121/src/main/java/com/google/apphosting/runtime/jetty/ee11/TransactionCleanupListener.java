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

package com.google.apphosting.runtime.jetty.ee11;

import com.google.common.flogger.GoogleLogger;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Logger;
import org.eclipse.jetty.ee11.webapp.WebAppContext;
import org.eclipse.jetty.server.Request;

/**
 * {@code TransactionCleanupListener} looks for datastore transactions that are still active when
 * request processing is finished. The filter attempts to roll back any transactions that are found,
 * and swallows any exceptions that are thrown while trying to perform rollbacks. This ensures that
 * any problems we encounter while trying to perform rollbacks do not have any impact on the result
 * returned the user.
 */
public class TransactionCleanupListener implements RequestListener {

  // TODO: this implementation uses reflection so that the datasource instance
  // of the application classloader is accessed.  This is the approach currently used
  // in Flex, but should ultimately be replaced by a mechanism that places a class within
  // the applications classloader.

  // TODO: this implementation assumes only a single thread services the
  // request. Once async handling is implemented, this listener will need to be modified
  // to collect active transactions on every dispatch to the context for the request
  // and to test and rollback any incompleted transactions on completion.

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private Object contextDatastoreService;
  private Method getActiveTransactions;
  private Method transactionRollback;
  private Method transactionGetId;

  public TransactionCleanupListener(ClassLoader loader) {
    // Reflection used for reasons listed above.
    try {
      Class<?> factory =
          loader.loadClass("com.google.appengine.api.datastore.DatastoreServiceFactory");
      contextDatastoreService = factory.getMethod("getDatastoreService").invoke(null);
      if (contextDatastoreService != null) {
        getActiveTransactions =
            contextDatastoreService.getClass().getMethod("getActiveTransactions");
        getActiveTransactions.setAccessible(true);

        Class<?> transaction = loader.loadClass("com.google.appengine.api.datastore.Transaction");
        transactionRollback = transaction.getMethod("rollback");
        transactionGetId = transaction.getMethod("getId");
      }
    } catch (Exception ex) {
      logger.atInfo().log("No datastore service found in webapp");
      logger.atFine().withCause(ex).log("No context datastore service");
    }
  }

  @Override
  public void requestReceived(WebAppContext context, Request request)
      throws IOException, ServletException {}

  @Override
  public void requestComplete(WebAppContext context, Request request) {
    if (transactionGetId == null) {
      // No datastore service found in webapp
      return;
    }
    try {
      // Reflection used for reasons listed above.
      Object txns = getActiveTransactions.invoke(contextDatastoreService);

      if (txns instanceof Collection) {
        for (Object tx : (Collection) txns) {
          Object id = transactionGetId.invoke(tx);
          try {
            // User the original TCFilter log, as c.g.ah.r.j9 logs are filter only logs are
            // filtered out by NullSandboxLogHandler. This keeps the behaviour identical.
            Logger.getLogger("com.google.apphosting.util.servlet.TransactionCleanupFilter")
                .warning(
                    "Request completed without committing or rolling back transaction "
                        + id
                        + ".  Transaction will be rolled back.");
            transactionRollback.invoke(tx);
          } catch (InvocationTargetException ex) {
            logger.atWarning().withCause(ex.getTargetException()).log(
                "Failed to rollback abandoned transaction %s", id);
          } catch (Exception ex) {
            logger.atWarning().withCause(ex).log("Failed to rollback abandoned transaction %s", id);
          }
        }
      }
    } catch (Exception ex) {
      logger.atWarning().withCause(ex).log("Failed to rollback abandoned transaction");
    }
  }
}
