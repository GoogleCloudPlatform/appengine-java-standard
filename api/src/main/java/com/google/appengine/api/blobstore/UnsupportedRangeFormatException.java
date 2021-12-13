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

package com.google.appengine.api.blobstore;

/**
 * {@code UnsupportedRangeFormatException} is an unchecked exception that is thrown
 * when an valid but unsupported Range header format is provided.
 *
 */
public class UnsupportedRangeFormatException extends RangeFormatException {
  private static final long serialVersionUID = -2678138419307671864L;

  public UnsupportedRangeFormatException(String message) {
    super(message);
  }

  public UnsupportedRangeFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
