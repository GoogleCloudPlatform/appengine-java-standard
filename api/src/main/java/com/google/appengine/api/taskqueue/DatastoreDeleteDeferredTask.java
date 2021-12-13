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

package com.google.appengine.api.taskqueue;

import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Key;

/**
 * A {@link DeferredTask} implementation that deletes the entities uniquely identified by the
 * provided {@link Key Keys} when it runs.
 *
 */
// TODO: Currently only used by our session management code. Consider
// making this part of the public api.
class DatastoreDeleteDeferredTask implements DeferredTask {
  private static final long serialVersionUID = 4972781397046493305L;

  private final Key deleteMe;

  public DatastoreDeleteDeferredTask(Key deleteMe) {
    if (deleteMe == null) {
      throw new NullPointerException("deleteMe cannot be null");
    }
    this.deleteMe = deleteMe;
  }

  @Override
  public void run() {
    DatastoreServiceFactory.getDatastoreService().delete(deleteMe);
  }
}
