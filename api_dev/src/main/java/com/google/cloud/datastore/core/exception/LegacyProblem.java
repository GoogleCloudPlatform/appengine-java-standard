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

package com.google.cloud.datastore.core.exception;

import com.google.cloud.datastore.logs.ProblemCode;

/**
 * For use with "legacy" problems, i.e. ones specified via a string constant error message and not a
 * {@link ProblemCode}. <b>Do not add uses</b>; instead, specify a new problem code and an error
 * message to ErrorMessages so that we can do API-specific translation.
 */
public class LegacyProblem extends Problem {

  protected LegacyProblem(String message) {
    super(ProblemCode.LEGACY);
  }

  public static LegacyProblem create(String message) {
    return new LegacyProblem(message);
  }
}
