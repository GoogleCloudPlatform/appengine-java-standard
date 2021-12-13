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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.datastore.logs.ProblemCode;

/**
 * A container for a {@link ProblemCode}, potentially with other useful information; consult e.g.
 * <code>EntityRefProblem</code>.
 */
public class Problem {
  protected final ProblemCode problemCode;

  protected Problem(ProblemCode problemCode) {
    this.problemCode = checkNotNull(problemCode);
  }

  public ProblemCode getProblemCode() {
    return problemCode;
  }

  public static Problem create(ProblemCode problemCode) {
    return new Problem(problemCode);
  }
}
