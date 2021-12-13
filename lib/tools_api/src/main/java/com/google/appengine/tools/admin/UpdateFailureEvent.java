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

package com.google.appengine.tools.admin;

/**
 * Received by an {@link UpdateListener}.  This event indicates that an
 * operation failed.
 *
 */
public class UpdateFailureEvent {

  private String failureMessage;
  private Throwable cause;
  private final String details;

  public UpdateFailureEvent(Throwable cause, String failureMessage, String details) {
    this.failureMessage = failureMessage;
    this.cause = cause;
    this.details = details;
  }

  /**
   * Returns the failure message for the operation.
   *
   * @return a not {@code null} message.
   */
  public String getFailureMessage() {
    return failureMessage;
  }

  /**
   * Returns the cause, if any, for the operation failure.
   *
   * @return a {@link Throwable}, or {@code null}.
   */
  public Throwable getCause() {
    return cause;
  }

  /**
   * Returns the detailed output from the operation process.
   */
  public String getDetails() {
    return details;
  }
}
