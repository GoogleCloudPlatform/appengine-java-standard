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

package com.google.appengine.api.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ImmutableCopyTest {
  @Test
  public void empty() {
    List<String> input = new ArrayList<>();
    List<String> empty = ImmutableCopy.list(new ArrayList<>());
    assertThat(empty).isEmpty();
    assertThat(empty).isEqualTo(input);
    assertThat(empty).isNotSameInstanceAs(input);
  }

  @Test
  public void emptyCollectionsAreTheSameObject() {
    assertThat(ImmutableCopy.list(new ArrayList<Object>()))
        .isSameInstanceAs(ImmutableCopy.list(new HashSet<Object>()));
  }

  @Test
  public void singleElement() {
    List<String> input = Arrays.asList("foo");
    List<String> single = ImmutableCopy.list(input);
    assertThat(single).isEqualTo(input);
    try {
      single.set(0, "foo");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void multiElement() {
    List<String> input = Arrays.asList("foo", "bar", "baz");
    List<String> copy = ImmutableCopy.list(input);
    assertThat(copy).isEqualTo(input);
    try {
      copy.set(1, "bar");
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void guava() {
    List<ImmutableList<String>> inputs =
        ImmutableList.of(
            ImmutableList.of(), ImmutableList.of("foo"), ImmutableList.of("foo", "bar", "baz"));
    for (List<String> input : inputs) {
      List<String> copy = ImmutableCopy.list(input);
      assertThat(copy).isEqualTo(input);
      assertThat(copy).isNotSameInstanceAs(input);
    }
  }
}
