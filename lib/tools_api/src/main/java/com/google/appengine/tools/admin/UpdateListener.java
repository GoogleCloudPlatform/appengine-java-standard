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
 * A listener which receives events during a long running operation that
 * involves interaction with the server. Implement
 * this interface to be notified of progress during application update.
 */
public interface UpdateListener {

  /**
   * Called each time some progress is made during the operation. 
   * 
   * @param event a not {@code null} event.
   */
  void onProgress(UpdateProgressEvent event);
  
  /**
   * Called if the operation has completed successfully. 
   *
   * @param event a not {@code null} event.
   */
  void onSuccess(UpdateSuccessEvent event);

  /**
   * Called if the operation has failed. 
   *
   * @param event a not {@code null} event.
   */
  void onFailure(UpdateFailureEvent event);
}
