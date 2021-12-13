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

package com.google.apphosting.api;

/**
 * {@code DeadlineExceededException} is an unchecked exception thrown
 * whenever a request has exceeded the request deadline (e.g. 60 seconds
 * in the case of a default instance).
 *
 * <p>It will typically be thrown from API methods that did not finish
 * by the deadline.  However, this exception may also be thrown <b>at
 * any time</b>, at any arbitrary point in the execution of your
 * code.</p>
 *
 * An application may catch {@code DeadlineExceededException} to
 * perform cleanup, but it must finish execution shortly afterwards.
 * If the application delays too long, an uncatchable {@code Error} is
 * thrown to force termination of the request with extreme prejudice.
 *
 */
public class DeadlineExceededException extends RuntimeException {
  private static final long serialVersionUID = -6443357055083826003L;

  public DeadlineExceededException() {
    super();
  }

  public DeadlineExceededException(String message) {
    super(message);
  }
}
