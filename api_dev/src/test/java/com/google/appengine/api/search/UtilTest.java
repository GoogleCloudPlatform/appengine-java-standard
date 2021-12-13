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

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link Util}.
 *
 */
@RunWith(JUnit4.class)
public class UtilTest {

  @Test
  public void testIterableToString() {
    List<String> items = ImmutableList.of(
        "1", "2", "3", "4", "5");
    assertThat(Util.iterableToString(items, 1000)).isEqualTo("[1, 2, 3, 4, 5]");
    assertThat(Util.iterableToString(items, 6)).isEqualTo("[1, 2, 3, 4, 5]");
    assertThat(Util.iterableToString(items, 5)).isEqualTo("[1, 2, 3, 4, 5]");
    assertThat(Util.iterableToString(items, 4)).isEqualTo("[1, 2, ..., 4, 5]");
    assertThat(Util.iterableToString(items, 3)).isEqualTo("[1, 2, ..., 5]");
    assertThat(Util.iterableToString(items, 2)).isEqualTo("[1, ..., 5]");
    assertThat(Util.iterableToString(items, 1)).isEqualTo("[1, ...]");
    assertThat(Util.iterableToString(items, 0)).isEqualTo("[1, 2, 3, 4, 5]");
    assertThat(Util.iterableToString(items, -1000)).isEqualTo("[1, 2, 3, 4, 5]");
  }
}
