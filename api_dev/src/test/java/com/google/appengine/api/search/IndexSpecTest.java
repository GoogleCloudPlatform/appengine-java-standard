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

import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.common.base.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link IndexSpec}.
 *
 */
@RunWith(JUnit4.class)
public class IndexSpecTest {

  @Test
  public void testSetIndexNames() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> IndexSpec.newBuilder().setName(null));
    assertThrows(IllegalArgumentException.class, () -> IndexSpec.newBuilder().setName(""));
    assertThrows(IllegalArgumentException.class, () -> IndexSpec.newBuilder().setName("!reserved"));
    assertThrows(
        IllegalArgumentException.class, () -> IndexSpec.newBuilder().setName("__Entity__"));
    // 3 underscores are OK, not __*__
    assertThat(IndexSpec.newBuilder().setName("___").build().getName()).isEqualTo("___");
    for (int i = 0; i < 33; ++i) {
      String illegalName = String.valueOf((char) i);
      assertThrows(
          IllegalArgumentException.class, () -> IndexSpec.newBuilder().setName(illegalName));
    }
    for (int i = 33; i < 127; ++i) {
      char c = (char) i;
      if (c != '!') {
        String legalName = String.valueOf(c);
        assertThat(IndexSpec.newBuilder().setName(legalName).build().getName())
            .isEqualTo(legalName);
      }
    }
    for (int i = 127; i < 250; ++i) {
      String illegalName = String.valueOf((char) i);
      assertThrows(
          IllegalArgumentException.class, () -> IndexSpec.newBuilder().setName(illegalName));
    }

    String name = Strings.repeat("a", SearchApiLimits.MAXIMUM_INDEX_NAME_LENGTH);
    assertThat(IndexSpec.newBuilder().setName(name).build().getName()).isEqualTo(name);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            IndexSpec.newBuilder()
                .setName(Strings.repeat("a", SearchApiLimits.MAXIMUM_INDEX_NAME_LENGTH + 1)));
  }
}
