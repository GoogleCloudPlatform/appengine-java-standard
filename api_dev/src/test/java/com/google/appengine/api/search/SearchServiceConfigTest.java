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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SearchServiceConfig}. */
@RunWith(JUnit4.class)
public class SearchServiceConfigTest {

  private static final Double[] INVALID_DEADLINES = new Double[] {-2.0, 0.0};
  private static final Double[] VALID_DEADLINES = new Double[] {2.0, null};
  private static final String INVALID_NAMESPACE = "!Valid";
  private static final String VALID_NAMESPACE = "Valid";

  @Test
  public void testInvalidDeadline() {
    for (Double deadline : INVALID_DEADLINES) {
      IllegalArgumentException e =
          assertThrows(
              IllegalArgumentException.class,
              () -> SearchServiceConfig.newBuilder().setDeadline(deadline));
      assertThat(e).hasMessageThat().containsMatch(".*[Mm]ust be a positive number.*");
    }
  }

  @Test
  public void testValidDeadline() {
    for (Double deadline : VALID_DEADLINES) {
      SearchServiceConfig.newBuilder().setDeadline(deadline);
    }
  }

  @Test
  public void testValidNamespace() {
    SearchServiceConfig.newBuilder().setNamespace(VALID_NAMESPACE);
  }

  @Test
  public void testInvalidNamespace() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SearchServiceConfig.newBuilder().setNamespace(INVALID_NAMESPACE));
  }
}
