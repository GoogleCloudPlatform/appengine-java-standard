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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withOffset;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.PreparedMultiQuery.EntitySource;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO Test projection + multiquery here
@RunWith(JUnit4.class)
public class PreparedMultiQueryTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  QueryRunner queryRunner =
      new QueryRunnerV3(DatastoreServiceConfig.Builder.withDefaults(), new ApiProxy.ApiConfig());

  @Test
  public void testNextResult_NoData() {
    PriorityQueue<EntitySource> sources = new PriorityQueue<>();
    assertThat(PreparedMultiQuery.nextResult(sources)).isNull();
  }

  @Test
  public void testEntitySourceConstruction() {
    assertThrows(
        IllegalArgumentException.class, () -> newEntitySource(Collections.<Entity>emptyIterator()));
  }

  @Test
  public void testNextResult_OneSource() {
    PriorityQueue<EntitySource> sources = new PriorityQueue<>();
    List<Entity> entities = buildHeap(sources, Lists.newArrayList(23L));
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(0));
    assertThat(sources).isEmpty();
    assertThat(PreparedMultiQuery.nextResult(sources)).isNull();

    entities = buildHeap(sources, Lists.newArrayList(23L), Lists.newArrayList(22L));
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(1));
    assertThat(sources).hasSize(1);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(0));
    assertThat(sources).isEmpty();
    assertThat(PreparedMultiQuery.nextResult(sources)).isNull();
  }

  @Test
  public void testNextResult_TwoSources() {
    PriorityQueue<EntitySource> sources = new PriorityQueue<>();
    List<Entity> entities =
        buildHeap(sources, Lists.newArrayList(23L, 24L), Lists.newArrayList(22L, 25L));

    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(2));
    assertThat(sources).hasSize(2);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(0));
    assertThat(sources).hasSize(2);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(1));
    assertThat(sources).hasSize(1);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(3));
    assertThat(sources).isEmpty();
    assertThat(PreparedMultiQuery.nextResult(sources)).isNull();
  }

  @Test
  public void testNextResult_ThreeSources() {
    PriorityQueue<EntitySource> sources = new PriorityQueue<>();

    List<Entity> entities =
        buildHeap(
            sources,
            Lists.newArrayList(23L, 25L),
            Lists.newArrayList(22L, 24L),
            Lists.newArrayList(21L, 26L, 27L));

    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(4));
    assertThat(sources).hasSize(3);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(2));
    assertThat(sources).hasSize(3);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(0));
    assertThat(sources).hasSize(3);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(3));
    assertThat(sources).hasSize(2);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(1));
    assertThat(sources).hasSize(1);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(5));
    assertThat(sources).hasSize(1);
    assertThat(PreparedMultiQuery.nextResult(sources)).isEqualTo(entities.get(6));
    assertThat(sources).isEmpty();
    assertThat(PreparedMultiQuery.nextResult(sources)).isNull();
  }

  @Test
  public void testAsSingleEntity() {
    FetchOptions fetchOptions = withLimit(2).chunkSize(Integer.MAX_VALUE);

    // 2 prepared queries that both return null
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Collections.<Entity>emptyIterator());
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Collections.<Entity>emptyIterator());
    PreparedMultiQuery pmq1 = preparedMultiQueryFrom(pqMock1, pqMock2);

    assertThat(pmq1.asSingleEntity()).isNull();
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());

    reset(pqMock1);
    reset(pqMock2);

    Entity e1 = new Entity(KeyFactory.createKey("yam", 3));
    Entity e2 = new Entity(KeyFactory.createKey("yam", 4));
    // 2 prepared queries, the first returns an entity and the second returns
    // null
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Collections.<Entity>emptyIterator());
    PreparedMultiQuery pmq2 = preparedMultiQueryFrom(pqMock1, pqMock2);
    assertThat(pmq2.asSingleEntity()).isSameInstanceAs(e1);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());

    reset(pqMock1);
    reset(pqMock2);

    // 2 prepared queries, the first returns null and the second returns an
    // entity
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Collections.<Entity>emptyIterator());
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    PreparedMultiQuery pmq3 = preparedMultiQueryFrom(pqMock1, pqMock2);
    assertThat(pmq3.asSingleEntity()).isSameInstanceAs(e1);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());

    reset(pqMock1);
    reset(pqMock2);

    // 2 prepared queries, both return the same entity
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    PreparedMultiQuery pmq4 = preparedMultiQueryFrom(pqMock1, pqMock2);
    assertThat(pmq4.asSingleEntity()).isSameInstanceAs(e1);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());

    reset(pqMock1);
    reset(pqMock2);

    // 2 prepared queries, each returns a different entity
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e2));
    PreparedMultiQuery pmq5 = preparedMultiQueryFrom(pqMock1, pqMock2);
    assertThrows(PreparedQuery.TooManyResultsException.class, pmq5::asSingleEntity);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());

    reset(pqMock1);
    reset(pqMock2);

    // 3 prepared queries, all return the same entity
    final PreparedQuery pqMock3 = mock(PreparedQuery.class);
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock3.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    PreparedMultiQuery pmq6 = preparedMultiQueryFrom(pqMock1, pqMock2, pqMock3);
    assertThat(pmq6.asSingleEntity()).isSameInstanceAs(e1);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
    verify(pqMock3).asIterator(any());

    reset(pqMock1);
    reset(pqMock2);
    reset(pqMock3);

    // 3 prepared queries, the first two return the same entity but the last
    // returns a different entity
    when(pqMock1.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock2.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e1));
    when(pqMock3.asIterator(eq(fetchOptions))).thenReturn(Iterators.forArray(e2));
    PreparedMultiQuery pmq7 = preparedMultiQueryFrom(pqMock1, pqMock2, pqMock3);
    assertThrows(PreparedQuery.TooManyResultsException.class, pmq7::asSingleEntity);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
    verify(pqMock3).asIterator(any());
  }

  @Test
  public void testNoLimit() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withChunkSize(Integer.MAX_VALUE);
    when(pqMock1.asIterator(options)).thenReturn(makeEntityIterator(4, 1));
    when(pqMock2.asIterator(options)).thenReturn(makeEntityIterator(3, 4));
    assertThat(pmq.asList(options)).hasSize(6);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testNoLimit_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withChunkSize(Integer.MAX_VALUE);
    when(pqMock1.asIterable(options)).thenReturn(makeEntityIterable(4, 1));
    when(pqMock2.asIterable(options)).thenReturn(makeEntityIterable(3, 4));
    assertThat(pmq.countEntities(options)).isEqualTo(6);
    verify(pqMock1).asIterable(any());
    verify(pqMock2).asIterable(any());
  }

  @Test
  public void testOverLimit() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count returns limit even if it gets more results
    FetchOptions options = withLimit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(50);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(25, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeEntityIterator(50, 40));
    assertThat(pmq.asList(options)).hasSize(50);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testOverLimit_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count returns limit even if it gets more results
    FetchOptions options = withLimit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(50);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(25, 1));
    when(pqMock2.asIterable(expected)).thenReturn(makeEntityIterable(50, 40));
    assertThat(pmq.countEntities(options)).isEqualTo(50);
    verify(pqMock1).asIterable(any());
    verify(pqMock2).asIterable(any());
  }

  @Test
  public void testOverLimitInOne() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count doesn't call count on pqMock2 after getting
    // max results from pqMock1
    FetchOptions options = withLimit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(50);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(60, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeTripwireEntityIterator());
    assertThat(pmq.asList(options)).hasSize(50);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testOverLimitInOne_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count doesn't call count on pqMock2 after getting
    // max results from pqMock1
    FetchOptions options = withLimit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(50);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(60, 1));
    assertThat(pmq.countEntities(options)).isEqualTo(50);
    verify(pqMock1).asIterable(any());
    verifyNoMoreInteractions(pqMock2);
  }

  @Test
  public void testOffset() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withOffset(25);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(50, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeEntityIterator(50, 16));
    assertThat(pmq.asList(options)).hasSize(50 + 15 - 25);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testOffset_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withOffset(25);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(50, 1));
    when(pqMock2.asIterable(expected)).thenReturn(makeEntityIterable(50, 16));
    assertThat(pmq.countEntities(options)).isEqualTo(50 + 15 - 25);
    verify(pqMock1).asIterable(any());
    verify(pqMock2).asIterable(any());
  }

  @Test
  public void testOffsetAndUnderLimit() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withChunkSize(Integer.MAX_VALUE).offset(25).limit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(75);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(40, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeEntityIterator(40, 31));
    assertThat(pmq.asList(options)).hasSize(40 + 30 - 25);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testOffsetAndUnderLimit_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withChunkSize(Integer.MAX_VALUE).offset(25).limit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(75);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(40, 1));
    when(pqMock2.asIterable(expected)).thenReturn(makeEntityIterable(40, 31));
    assertThat(pmq.countEntities(options)).isEqualTo(40 + 30 - 25);
    verify(pqMock1).asIterable(any());
    verify(pqMock2).asIterable(any());
  }

  @Test
  public void testMaxOffsetAndLimit() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withOffset(Integer.MAX_VALUE).limit(Integer.MAX_VALUE);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(50, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeEntityIterator(50, 26));
    assertThat(pmq.asList(options)).isEmpty();
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testMaxOffsetAndLimit_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    FetchOptions options = withOffset(Integer.MAX_VALUE).limit(Integer.MAX_VALUE);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(50, 1));
    when(pqMock2.asIterable(expected)).thenReturn(makeEntityIterable(50, 26));
    assertThat(pmq.countEntities(options)).isEqualTo(0);
    verify(pqMock1).asIterable(any());
    verify(pqMock2).asIterable(any());
  }

  @Test
  public void testOffsetAndOverLimit() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count returns limit even if it gets more results
    FetchOptions options = withOffset(25).limit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(75);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(40, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeEntityIterator(60, 31));
    assertThat(pmq.asList(options)).hasSize(50);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testOffsetAndOverLimit_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count returns limit even if it gets more results
    FetchOptions options = withOffset(25).limit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(75);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(40, 1));
    when(pqMock2.asIterable(expected)).thenReturn(makeEntityIterable(60, 31));
    assertThat(pmq.countEntities(options)).isEqualTo(50);
    verify(pqMock1).asIterable(any());
    verify(pqMock2).asIterable(any());
  }

  @Test
  public void testOffsetAndOverLimitInOne() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count doesn't call count on pqMock2 after getting
    // max results from pqMock1
    FetchOptions options = withOffset(25).limit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(75);
    when(pqMock1.asIterator(expected)).thenReturn(makeEntityIterator(80, 1));
    when(pqMock2.asIterator(expected)).thenReturn(makeTripwireEntityIterator());
    assertThat(pmq.asList(options)).hasSize(50);
    verify(pqMock1).asIterator(any());
    verify(pqMock2).asIterator(any());
  }

  @Test
  public void testOffsetAndOverLimitInOne_Count() {
    final PreparedQuery pqMock1 = mock(PreparedQuery.class);
    final PreparedQuery pqMock2 = mock(PreparedQuery.class);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqMock1, pqMock2);

    // checking to make sure count doesn't call count on pqMock2 after getting
    // max results from pqMock1
    FetchOptions options = withOffset(25).limit(50);
    FetchOptions expected = withChunkSize(Integer.MAX_VALUE).limit(75);
    when(pqMock1.asIterable(expected)).thenReturn(makeEntityIterable(80, 1));
    assertThat(pmq.countEntities(options)).isEqualTo(50);
    verify(pqMock1).asIterable(any());
  }

  @SuppressWarnings("unused")
  @Test
  public void testConstructor() {
    // keys-only queries can only be sorted by key
    Query query1 = new Query("k").setKeysOnly().addSort("not key");
    assertThrows(
        IllegalArgumentException.class,
        () -> new PreparedMultiQuery(query1, makeMultiQuery(query1, 1, 2), null, queryRunner));

    Query query2 = new Query("k").setKeysOnly().addSort(Entity.KEY_RESERVED_PROPERTY);
    PreparedMultiQuery unused1 =
        new PreparedMultiQuery(new Query(), makeMultiQuery(1, 2), null, queryRunner);
    PreparedMultiQuery unused2 =
        new PreparedMultiQuery(query2, makeMultiQuery(query2, 1, 2), null, queryRunner);
  }

  @Test
  public void testDeduping() {
    PriorityQueue<EntitySource> sources = new PriorityQueue<>();
    List<Entity> entities = buildHeap(sources, Lists.newArrayList(23L), Lists.newArrayList(23L));

    List<Entity> result = preparedMultiQueryFrom(sources).asList(withDefaults());
    assertThat(result).containsExactly(entities.get(0));

    entities = buildHeap(sources, Lists.newArrayList(23L, 24L), Lists.newArrayList(23L, 24L));

    result = preparedMultiQueryFrom(sources).asList(withDefaults());
    assertThat(result).containsExactly(entities.get(0), entities.get(1)).inOrder();

    entities = buildHeap(sources, Lists.newArrayList(23L, 24L, 25L), Lists.newArrayList(23L, 25L));

    result = preparedMultiQueryFrom(sources).asList(withDefaults());
    assertThat(result).containsExactly(entities.get(0), entities.get(1), entities.get(2)).inOrder();

    entities =
        buildHeap(
            sources,
            Lists.newArrayList(23L, 24L, 25L),
            Lists.newArrayList(23L, 26L),
            Lists.newArrayList(26L, 27L, 28L));

    result = preparedMultiQueryFrom(sources).asList(withDefaults());
    assertThat(result).hasSize(6);
    assertThat(result.get(0)).isEqualTo(entities.get(0));
    assertThat(result.get(1)).isEqualTo(entities.get(1));
    assertThat(result.get(2)).isEqualTo(entities.get(2));
    assertThat(result.get(3)).isEqualTo(entities.get(4));
    assertThat(result.get(4)).isEqualTo(entities.get(6));
    assertThat(result.get(5)).isEqualTo(entities.get(7));
  }

  @Test
  public void testQueryParallelism() {
    FetchOptions options = withLimit(49);
    List<PreparedQuery> pqs = new ArrayList<>();

    // Create a list of MAX_BUFFERED_QUERIES of PreparedQuerys
    for (int i = 0; i < PreparedMultiQuery.MAX_BUFFERED_QUERIES; i++) {
      PreparedQuery mockPreparedQuery = mock(PreparedQuery.class);
      when(mockPreparedQuery.asIterator(options)).thenReturn(makeEntityIterator(50, 1));
      pqs.add(mockPreparedQuery);
    }

    // Add a query that will be started after we consume the first item, exhausting the first iter.
    PreparedQuery lateQuery = mock(PreparedQuery.class);
    pqs.add(lateQuery);

    // Add a query that will be never started.
    PreparedQuery neverQuery = mock(PreparedQuery.class);
    pqs.add(neverQuery);

    // Create PreparedMultiQuery and verify mocks.
    PreparedQuery[] pqsArray = pqs.toArray(new PreparedQuery[0]);
    PreparedMultiQuery pmq = preparedMultiQueryFrom(pqsArray);
    Iterator<Entity> pmqIter = pmq.asIterator(options);

    for (int i = 0; i < pqsArray.length; i++) {
      PreparedQuery mockPreparedQuery = pqsArray[i];
      if (i < PreparedMultiQuery.MAX_BUFFERED_QUERIES) {
        verify(mockPreparedQuery).asIterator(any());
      } else {
        verifyNoMoreInteractions(mockPreparedQuery);
      }
    }

    // Reset, then verify that lateQuery.asIterator is called after pmqiter.next()
    // and that neverQuery.asIterator isn't.
    when(lateQuery.asIterator(options)).thenReturn(makeTripwireEntityIterator());
    pmqIter.next();
    verify(lateQuery).asIterator(any());
    verifyNoMoreInteractions(neverQuery);
  }

  private static Iterable<Entity> makeEntityIterable(final int count, final int firstId) {
    return () -> makeEntityIterator(count, firstId);
  }

  private static Iterator<Entity> makeEntityIterator(final int count, final int firstId) {
    return new Iterator<Entity>() {
      private int remaining = count;
      private int nextId = firstId;

      @Override
      public boolean hasNext() {
        return remaining > 0;
      }

      @Override
      public Entity next() {
        --remaining;
        return new Entity(KeyFactory.createKey("Foo", nextId++));
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * @return an iterator that fail()s if hasNext() or next() is called. This is used to make sure we
   *     don't incorrectly consume from optimistically created queries.
   */
  private static Iterator<Entity> makeTripwireEntityIterator() {
    return new Iterator<Entity>() {
      @Override
      public boolean hasNext() {
        throw new AssertionError("hasNext() called on TripwireEntityIterator");
      }

      @Override
      public Entity next() {
        throw new AssertionError("next() called on TripwireEntityIterator");
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static EntitySource newEntitySource(Iterator<Entity> entities) {
    return new EntitySource(new EntityComparator(Collections.<Order>emptyList()), entities);
  }

  private static List<MultiQueryBuilder> makeMultiQuery(int height, int width) {
    return makeMultiQuery(new Query(), height, width);
  }

  @SuppressWarnings("deprecation")
  private static List<MultiQueryBuilder> makeMultiQuery(Query baseQuery, int height, int width) {
    List<MultiQueryComponent> components = new ArrayList<>(2);
    if (height > 1) {
      MultiQueryComponent serial = new MultiQueryComponent(MultiQueryComponent.Order.SERIAL);
      for (int i = 0; i < height; ++i) {
        serial.addFilters();
      }
      components.add(serial);
    }
    if (width > 1) {
      MultiQueryComponent parallel = new MultiQueryComponent(MultiQueryComponent.Order.PARALLEL);
      for (int i = 0; i < width; ++i) {
        parallel.addFilters();
      }
      components.add(parallel);
    }
    return Collections.singletonList(
        new MultiQueryBuilder(baseQuery.getFilterPredicates(), components, width));
  }

  private PreparedMultiQuery preparedMultiQueryFrom(final PreparedQuery... preparedQueries) {
    final Iterator<PreparedQuery> itr = Iterators.forArray(preparedQueries);
    return new PreparedMultiQuery(
        new Query(), makeMultiQuery(preparedQueries.length, 1), null, queryRunner) {

      @Override
      protected PreparedQuery prepareQuery(List<FilterPredicate> fitlers, boolean isCountQuery) {
        return itr.next();
      }
    };
  }

  private PreparedMultiQuery preparedMultiQueryFrom(final PriorityQueue<EntitySource> sources) {
    return new PreparedMultiQuery(new Query(), makeMultiQuery(1, 2), null, queryRunner) {
      @Override
      protected Iterator<Entity> makeHeapIterator(Iterable<Iterator<Entity>> queries) {
        return new HeapIterator(sources);
      }
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<Entity> buildHeap(PriorityQueue<EntitySource> sources, List... idLists) {
    List<Entity> allEntities = Lists.newArrayList();
    for (List<Long> idList : idLists) {
      List<Entity> entities = Lists.newArrayList();
      for (Long id : idList) {
        Entity e = new Entity(KeyFactory.createKey("kind", id));
        entities.add(e);
        allEntities.add(e);
      }
      EntitySource source = newEntitySource(entities.iterator());
      sources.add(source);
    }
    return allEntities;
  }
}
