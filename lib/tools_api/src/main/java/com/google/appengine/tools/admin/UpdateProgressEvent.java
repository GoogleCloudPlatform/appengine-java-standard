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
 * Received by an {@link com.google.appengine.tools.admin.UpdateListener}
 * periodically during an operation to indicate progress.
 *
 */
public class UpdateProgressEvent {

  private String message;
  private int percentageComplete;
  private Thread updateThread;
  
  public UpdateProgressEvent(Thread updateThread, String message, int percentageComplete) {
    this.message = message;
    this.percentageComplete = percentageComplete;
    this.updateThread = updateThread;
  }
  
  /**
   * Cancels the operation. A 
   * {@link com.google.appengine.tools.admin.AppAdmin#rollback rollback} is 
   * implicitly issued. 
   */
  public void cancel() {
    updateThread.interrupt();
  }
  
  /**
   * Retrieves the current status message.
   * 
   * @return a not {@code null} status message.
   */
  public String getMessage() {
    return message;
  }

  /**
   * Retrieves the current percentage complete.
   * 
   * @return a number inclusively between 0 and 100.
   */
  public int getPercentageComplete() {
    return percentageComplete;
  }
}
