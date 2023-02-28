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

import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Describes options for transactions, passed at transaction creation time.
 *
 * <p>Notes on usage:<br>
 * The recommended way to instantiate a {@code TransactionsOptions} object is to statically import
 * {@link Builder}.* and invoke a static creation method followed by an instance mutator (if
 * needed):
 *
 * <pre>{@code
 * import static com.google.appengine.api.datastore.TransactionOptions.Builder.*;
 *
 * ...
 *
 * datastoreService.beginTransaction(withXG(true));
 * }</pre>
 *
 */
public final class TransactionOptions {
  /** The mode of the transaction. */
  public enum Mode {
    /** Transaction will only be used for reads. */
    READ_ONLY,
    /** Transaction will be used for reads and writes. */
    READ_WRITE
  }
  private boolean xg = false;
  private @Nullable Transaction previousTransaction = null;
  private @Nullable Mode mode = null;
  private TransactionOptions() {}
  TransactionOptions(TransactionOptions original) {
    this.xg = original.xg;
  }
  /**
   * Enable or disable the use of cross-group transactions.
   *
   * @param enable true to cross-group transactions, false to restrict transactions to a single
   *     entity group.
   * @return {@code this} (for chaining)
   */
  public TransactionOptions setXG(boolean enable) {
    this.xg = enable;
    return this;
  }
  /** Return the cross-group transaction setting to default (disabled). */
  public TransactionOptions clearXG() {
    this.xg = false;
    return this;
  }
  /**
   * @return {@code true} if cross-group transactions are allowed, {@code false} if they are not
   *     allowed.
   */
  public boolean isXG() {
    return xg;
  }
  /**
   * Set which previous transaction to retry. Can only be used for READ_WRITE transactions. The
   * previous transaction should have been created with the same {@link TransactionOptions}, with
   * the exception of the {@code previousTransaction} property.
   *
   * <p>A rollback is not required, and should not be performed, prior to retrying the transaction.
   *
   * @param previousTransaction the transaction to retry.
   */
  public TransactionOptions setPreviousTransaction(Transaction previousTransaction) {
    this.previousTransaction = previousTransaction;
    return this;
  }
  /**
   * Return the previous transaction that is being retried, or {@code null} if none was provided.
   */
  public @Nullable Transaction previousTransaction() {
    return previousTransaction;
  }
  /**
   * Set the mode of the transaction.
   *
   * <p>Specifying the mode of the transaction can help to improve throughput, as it provides
   * additional information about the intent (or lack of intent, in the case of read only
   * transaction) to perform a write as part of the transaction.
   */
  public TransactionOptions setTransactionMode(Mode mode) {
    this.mode = mode;
    return this;
  }
  /** Return the mode of the transaction, or {@code null} if none was specified. */
  public @Nullable Mode transactionMode() {
    return mode;
  }
  /**
   * See {@link #setXG}.
   *
   * @deprecated Use {@link setXG(boolean)} instead.
   */
  @Deprecated
  public TransactionOptions multipleEntityGroups(boolean enable) {
    return setXG(enable);
  }
  /**
   * See {@link #clearXG}.
   *
   * @deprecated Use {@link #clearXG()} instead.
   */
  @Deprecated
  public TransactionOptions clearMultipleEntityGroups() {
    return clearXG();
  }
  /**
   * See {@link #isXG}.
   *
   * @deprecated Use {@link #isXG()} instead.
   */
  @Deprecated
  public Boolean allowsMultipleEntityGroups() {
    return isXG();
  }
  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TransactionOptions that = (TransactionOptions) o;
    if (xg != that.xg) {
      return false;
    }
    if (previousTransaction != null
        ? !previousTransaction.equals(that.previousTransaction)
        : that.previousTransaction != null) {
      return false;
    }
    return mode == that.mode;
  }
  @Override
  public int hashCode() {
    int result = (xg ? 1 : 0);
    result = 31 * result + (previousTransaction != null ? previousTransaction.hashCode() : 0);
    result = 31 * result + (mode != null ? mode.hashCode() : 0);
    return result;
  }
  @Override
  public String toString() {
    List<String> result = new ArrayList<>();
    result.add("XG=" + xg);
    result.add("mode=" + mode);
    result.add("previousTransaction=" + previousTransaction);
    return "TransactionOptions" + result;
  }
  /** Contains static creation methods for {@link TransactionOptions}. */
  public static final class Builder {
    /**
     * Create a {@link TransactionOptions} that enables or disables the use of cross-group
     * transactions. Shorthand for <code>TransactionOptions.withDefaults().setXG(...);</code>
     *
     * @param enable true to allow cross-group transactions, false to restrict transactions to a
     *     single entity group.
     * @return {@code this} (for chaining)
     */
    public static TransactionOptions withXG(boolean enable) {
      return withDefaults().setXG(enable);
    }
    /** Shorthand for <code>TransactionOptions.withDefaults().setTransactionMode(...);</code> */
    public static TransactionOptions withTransactionMode(Mode mode) {
      return withDefaults().setTransactionMode(mode);
    }
    /**
     * Shorthand for <code>
     *   TransactionOptions.withTransactionMode(Mode.READ_WRITE).setPreviousTransaction(...);
     * </code>
     */
    public static TransactionOptions withPreviousTransaction(Transaction previousTransaction) {
      return withTransactionMode(Mode.READ_WRITE).setPreviousTransaction(previousTransaction);
    }
    /**
     * See {@link #withXG}.
     *
     * @deprecated Use {@code Builder.withDefaults().setXG(enable)} instead.
     */
    @Deprecated
    public static TransactionOptions allowMultipleEntityGroups(boolean enable) {
      return withDefaults().setXG(enable);
    }
    /**
     * Helper method for creating a {@link TransactionOptions} instance with default values. The
     * defaults is false (disabled) for XG.
     */
    public static TransactionOptions withDefaults() {
      return new TransactionOptions();
    }
    // Only utility methods, no need to instantiate.
    private Builder() {}
  }
}
