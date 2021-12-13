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

import java.util.List;

/**
 * Describes the context in which a callback runs. The context has access to the current transaction
 * (if any), the element that the callback is operating on (eg the Entity being put or the Key being
 * deleted), as well as all elements being operated on in the operation that triggered the
 * callback..
 *
 * @param <T> the type of element that the callback is acting on.
 */
public interface CallbackContext<T> {
  /**
   * Returns an unmodifiable view of the elements involved in the operation that triggered the
   * callback..
   */
  List<T> getElements();

  /** Returns the current transaction, or {@code null} if there is no current transaction. */
  Transaction getCurrentTransaction();

  /**
   * Returns the index in the result of {@link #getElements()} of the element for which the callback
   * has been invoked.
   */
  int getCurrentIndex();

  /**
   * Returns the element for which the callback has been invoked. Shortcut for {@code
   * getElements().getCurrentIndex()}.
   */
  T getCurrentElement();
}
