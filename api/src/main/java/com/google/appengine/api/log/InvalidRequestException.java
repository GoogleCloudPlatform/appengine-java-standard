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

package com.google.appengine.api.log;

/**
 * Log errors caused by an invalid request due either to an out of range option
 * or an unsupported combination of options.  The exception detail will
 * typically provide more specific information about the error.
 *
 */
public class InvalidRequestException extends RuntimeException {
  private static final long serialVersionUID = -3509853815395203365L;

  public InvalidRequestException(String detail) {
    super(detail);
  }
}
