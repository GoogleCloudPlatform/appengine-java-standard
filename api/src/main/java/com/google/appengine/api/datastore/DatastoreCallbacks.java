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

/**
 * Internal interface describing the callback operations we support.
 *
 */
interface DatastoreCallbacks {

  /**
   * Runs all PrePut callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePrePutCallbacks(PutContext context);

  /**
   * Runs all PostPut callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePostPutCallbacks(PutContext context);

  /**
   * Runs all PreDelete callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePreDeleteCallbacks(DeleteContext context);

  /**
   * Runs all PostDelete callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePostDeleteCallbacks(DeleteContext context);

  /**
   * Runs all PreGet callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePreGetCallbacks(PreGetContext context);

  /**
   * Runs all PostLoad callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePostLoadCallbacks(PostLoadContext context);

  /**
   * Runs all PreQuery callbacks for the given context.
   *
   * @param context The callback context
   */
  void executePreQueryCallbacks(PreQueryContext context);

  /** Class that provides a no-op implementation of the {@code DatastoreCallbacks} interface. */
  static class NoOpDatastoreCallbacks implements DatastoreCallbacks {
    static final DatastoreCallbacks INSTANCE = new NoOpDatastoreCallbacks();

    @Override
    public void executePrePutCallbacks(PutContext context) {}

    @Override
    public void executePostPutCallbacks(PutContext context) {}

    @Override
    public void executePreDeleteCallbacks(DeleteContext context) {}

    @Override
    public void executePostDeleteCallbacks(DeleteContext context) {}

    @Override
    public void executePreGetCallbacks(PreGetContext context) {}

    @Override
    public void executePostLoadCallbacks(PostLoadContext context) {}

    @Override
    public void executePreQueryCallbacks(PreQueryContext context) {}
  }
}
