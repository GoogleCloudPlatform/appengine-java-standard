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

package com.google.appengine.api.oauth;

/**
 * {@code InvalidOAuthParametersException} is thrown when a request is a
 * malformed OAuth request (for example, it omits a required parameter or
 * contains an invalid signature).
 *
 */
public class InvalidOAuthParametersException extends OAuthRequestException {
  private static final long serialVersionUID = -2964880034535072636L;

  public InvalidOAuthParametersException(String message) {
    super(message);
  }
}
