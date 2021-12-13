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
 * {@code CommittedButStillApplyingException} is thrown when the write or transaction was committed,
 * but some entities or index rows may not have been fully updated. Those updates should
 * automatically be applied soon. You can roll them forward immediately by reading one of the
 * entities inside a transaction.
 *
 */
public class CommittedButStillApplyingException extends RuntimeException {
  private static final long serialVersionUID = -4443173074154542216L;

  public CommittedButStillApplyingException(String message) {
    super(message);
  }

  public CommittedButStillApplyingException(String message, Throwable cause) {
    super(message, cause);
  }
}
