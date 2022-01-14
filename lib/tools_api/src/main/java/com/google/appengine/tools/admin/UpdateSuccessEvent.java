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
 * Received by an {@link com.google.appengine.tools.admin.UpdateListener}.
 * This event indicates that the operation was 
 * successfully completed.
 *
 */
public class UpdateSuccessEvent {
  private final String details;

  public UpdateSuccessEvent(String details) {
    this.details = details;
  }

  // TODO Add some sort of information in here that indicates
  // what the new version of the application is.

  /** Returns the detailed output from the operation process. */
  public String getDetails() {
    return details;
  }
}
