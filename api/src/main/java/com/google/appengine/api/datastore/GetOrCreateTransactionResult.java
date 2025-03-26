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

import org.jspecify.annotations.Nullable;

/**
 * Helper class used to encapsulate the result of a call to {@link
 * BaseAsyncDatastoreServiceImpl#getOrCreateTransaction()}.
 */
final class GetOrCreateTransactionResult {

  private final boolean isNew;
  private final Transaction txn;

  GetOrCreateTransactionResult(boolean isNew, @Nullable Transaction txn) {
    this.isNew = isNew;
    this.txn = txn;
  }

  /**
   * Returns {@code true} if the Transaction was created and should therefore be closed before the
   * end of the operation, {@code false} otherwise.
   */
  public boolean isNew() {
    return isNew;
  }

  /** Returns the Transaction to use. Can be {@code null}. */
  public Transaction getTransaction() {
    return txn;
  }
}
