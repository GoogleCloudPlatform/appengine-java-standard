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

package com.google.appengine.api.datastore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.Math.min;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SlicingIteratorTest {

  @Test
  public void testNoDataZeroOffsetNoLimit() {
    assertNoData(new SlicingIterator<Integer>(Collections.<Integer>emptyIterator(), 0, null));
  }

  @Test
  public void testOneElementZeroOffsetNoLimit() {
    List<Integer> list = Lists.newArrayList(1);
    assertEquals(list, new SlicingIterator<Integer>(list.iterator(), 0, null));
  }

  @Test
  public void testOneElementNoOffsetNoLimit() {
    List<Integer> list = Lists.newArrayList(1);
    assertEquals(list, new SlicingIterator<Integer>(list.iterator(), null, null));
  }

  @Test
  public void testMultipleElementsZeroOffsetNoLimit() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertEquals(list, new SlicingIterator<Integer>(list.iterator(), 0, null));
  }

  @Test
  public void testMultipleElementsNoOffsetNoLimit() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertEquals(list, new SlicingIterator<Integer>(list.iterator(), null, null));
  }

  @Test
  public void testMultipleElementsZeroOffsetZeroLimit() {
    assertNoData(new SlicingIterator<Integer>(Lists.newArrayList(1, 2, 3).iterator(), 0, 0));
  }

  @Test
  public void testMultipleElementsZeroOffsetLimitGreaterThanInputSize() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertEquals(list, new SlicingIterator<Integer>(list.iterator(), 0, list.size() + 1));
  }

  @Test
  public void testMultipleElementsZeroOffsetLimitEqualToInputSize() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertEquals(list, new SlicingIterator<Integer>(list.iterator(), 0, list.size()));
  }

  @Test
  public void testMultipleElementsZeroOffsetLimitSmallerThanInputSize() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertEquals(
        list.subList(0, list.size() - 1),
        new SlicingIterator<Integer>(list.iterator(), 0, list.size() - 1));
    assertThat(iteratorToList(new SlicingIterator<Integer>(list.iterator(), 0, list.size() - 1)))
        .hasSize(list.size() - 1);
    assertEquals(
        list.subList(0, list.size() - 2),
        new SlicingIterator<Integer>(list.iterator(), 0, list.size() - 2));
    assertThat(iteratorToList(new SlicingIterator<Integer>(list.iterator(), 0, list.size() - 2)))
        .hasSize(list.size() - 2);
  }

  @Test
  public void testMultipleElementsOffsetGreaterThanInputSizeNoLimit() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertNoData(new SlicingIterator<Integer>(list.iterator(), list.size() + 1, null));
  }

  @Test
  public void testMultipleElementsOffsetEqualToInputSizeNoLimit() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertNoData(new SlicingIterator<Integer>(list.iterator(), list.size(), null));
  }

  @Test
  public void testMultipleElementsOffsetLessThanInputSizeNoLimit() {
    List<Integer> list = Lists.newArrayList(1, 2, 3);
    assertEquals(
        list.subList(2, list.size()),
        new SlicingIterator<Integer>(list.iterator(), list.size() - 1, null));
    assertEquals(
        list.subList(1, list.size()),
        new SlicingIterator<Integer>(list.iterator(), list.size() - 2, null));
  }

  @Test
  public void testOffsetAndLimit() {
    List<Integer> list = Lists.newArrayList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    assertEquals(
        list.subList(1, list.size()),
        new SlicingIterator<Integer>(list.iterator(), 1, list.size()));
    for (int offset = 0; offset < 10; offset++) {
      for (int limit = 10; limit >= 0; limit--) {
        assertEquals(
            "Failed with offset " + offset + " and limit " + limit,
            list.subList(offset, min(offset + limit, list.size())),
            new SlicingIterator<Integer>(list.iterator(), offset, limit));
      }
    }
  }

  private static void assertEquals(
      String failureMsg, List<Integer> expected, SlicingIterator<Integer> actual) {
    List<Integer> actualList = iteratorToList(actual);
    assertWithMessage(failureMsg).that(actualList).isEqualTo(expected);
  }

  private static void assertEquals(List<Integer> expected, SlicingIterator<Integer> actual) {
    List<Integer> actualList = iteratorToList(actual);
    assertThat(actualList).isEqualTo(expected);
  }

  private static List<Integer> iteratorToList(SlicingIterator<Integer> iterator) {
    List<Integer> list = Lists.newArrayList();
    Iterators.addAll(list, iterator);
    return list;
  }

  private static void assertNoData(Iterator<?> iterator) {
    assertThat(iterator.hasNext()).isFalse();
  }
}
