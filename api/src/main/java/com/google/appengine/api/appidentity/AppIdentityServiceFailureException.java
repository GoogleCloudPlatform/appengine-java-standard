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

package com.google.appengine.api.appidentity;

import org.jspecify.annotations.Nullable;

/**
 * {@link AppIdentityServiceFailureException} is thrown when any unknown error occurs while
 * communicating with the App Identity service.
 */
public final class AppIdentityServiceFailureException extends RuntimeException {
  private static final long serialVersionUID = -6195565425867241842L;

  /**
   * Creates an exception with the supplied message.
   *
   * @param message A message describing the reason for the exception.
   */
  public AppIdentityServiceFailureException(@Nullable String message) {
    super(message);
  }
}
