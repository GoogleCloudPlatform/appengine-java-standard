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

import java.util.ConcurrentModificationException;

/**
 * Describes the various policies the datastore can follow for implicit transaction management. When
 * deciding which policy to use, keep the following in mind: The datastore will automatically retry
 * operations that fail due to concurrent updates to the same entity group if the operation is not
 * part of a transaction. The datastore will not retry operations that fail due to concurrent
 * updates to the same entity group if the operation is part of a transaction, and will instead
 * immediately throw a {@link ConcurrentModificationException}. If your application needs to perform
 * any sort of intelligent merging when concurrent attempts are made to update the same entity group
 * you probably want {@link #AUTO}, otherwise {@link #NONE} is probably acceptable.
 *
 * <p>See {@link DatastoreService} for a list of operations that perform implicit transaction
 * management.
 *
 */
public enum ImplicitTransactionManagementPolicy {

  /** If a current transaction exists, use it, otherwise execute without a transaction. */
  NONE,

  /**
   * If a current transaction exists, use it, otherwise create one. The transaction will be
   * committed before the method returns if the datastore operation completes successfully and
   * rolled back if the datastore operation does not complete successfully. No matter the type or
   * quantity of entities provided, only one transaction will be created. This means that if you
   * pass entities that belong to multiple entity groups into one of these methods and you have this
   * policy enabled, you will receive an exception because transactions do not function across
   * entity groups.
   */
  AUTO,
}
