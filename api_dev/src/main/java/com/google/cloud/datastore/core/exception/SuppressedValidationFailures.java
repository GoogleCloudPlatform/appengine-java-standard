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
import com.google.common.collect.ImmutableSet;
import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps track of {@link ValidationException validation failures} that would have occurred if strict
 * validation had been enforced.
 */
public class SuppressedValidationFailures {
  private final Set<ProblemCode> failures = EnumSet.noneOf(ProblemCode.class);

  /** Adds a {@link ProblemCode problem} to the found failures. */
  public void add(ProblemCode problemCode) {
    failures.add(problemCode);
  }

  /** Adds a list of {@link ProblemCode problem}s to the found failures. */
  public void addAll(Set<ProblemCode> problemCodes) {
    failures.addAll(problemCodes);
  }

  public ImmutableSet<ProblemCode> failures() {
    return ImmutableSet.copyOf(failures);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof SuppressedValidationFailures)) {
      return false;
    }
    return failures.equals(((SuppressedValidationFailures) other).failures);
  }

  @Override
  public int hashCode() {
    return failures.hashCode();
  }

  @Override
  public String toString() {
    return String.format("SuppressedValidationFailures[%s]", failures);
  }
}
