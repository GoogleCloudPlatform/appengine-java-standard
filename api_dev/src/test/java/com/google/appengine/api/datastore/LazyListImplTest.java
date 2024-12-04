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

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withMaxEntityGroupsPerRpc;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the internals of {@link LazyList} by issuing queries with a variety of {@link FetchOptions}
 * permutations and then looking at the underlying RPCs that are made when interacting with the
 * {@link List} returned by the query.
 *
 * <p>It's nearly impossible to be exhaustive here, but between these implementation tests and the
 * functionality tests we have in {@link LazyListTest} we should be in pretty good shape.
 *
 */
@RunWith(JUnit4.class)
public class LazyListImplTest {
  @Rule
  public LocalServiceTestHelperRule testHelperRule =
      new LocalServiceTestHelperRule(
          new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()));

  /**
   * A {@link com.google.apphosting.api.ApiProxy.Delegate} implementation that keeps track of all
   * query related RPCs that pass through. This is much simpler than writing mocks that return query
   * results.
   */
  private static final class RecordingDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
    private final ApiProxy.Delegate<ApiProxy.Environment> inner;
    private final List<DatastoreV3Pb.Query> runQueryCalls = Lists.newArrayList();
    private final List<DatastoreV3Pb.NextRequest> nextCalls = Lists.newArrayList();

    private RecordingDelegate(ApiProxy.Delegate<ApiProxy.Environment> inner) {
      this.inner = inner;
    }

    private void clear() {
      runQueryCalls.clear();
      nextCalls.clear();
    }

    @Override
    public byte[] makeSyncCall(
        ApiProxy.Environment environment, String packageName, String methodName, byte[] request)
        throws ApiProxy.ApiProxyException {
      throw new RuntimeException("should not be any sync rpc calls");
    }

    @Override
    public Future<byte[]> makeAsyncCall(
        ApiProxy.Environment environment,
        String packageName,
        String methodName,
        byte[] request,
        ApiProxy.ApiConfig apiConfig) {
      if (packageName.equals("datastore_v3")) {
        if (methodName.equals("RunQuery")) {
          DatastoreV3Pb.Query query = DatastoreV3Pb.Query.getDefaultInstance();
          try {
            query = query.parseFrom(request);
          }catch (InvalidProtocolBufferException e) {
          }
          runQueryCalls.add(query);
        } else if (methodName.equals("Next")) {
          DatastoreV3Pb.NextRequest next = DatastoreV3Pb.NextRequest.getDefaultInstance();
          try {
            next = next.parseFrom(request);
          } catch (InvalidProtocolBufferException e) {
          }
          nextCalls.add(next);
        }
      }
      return inner.makeAsyncCall(environment, packageName, methodName, request, apiConfig);
    }

    @Override
    public void log(ApiProxy.Environment environment, ApiProxy.LogRecord record) {
      inner.log(environment, record);
    }

    @Override
    public void flushLogs(ApiProxy.Environment environment) {
      inner.flushLogs(environment);
    }

    @Override
    public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
      return inner.getRequestThreads(environment);
    }
  }

  private RecordingDelegate recordingDelegate;
  // Turn off client-side batching so that we get entities back in the order we
  // put them.
  private final DatastoreService ds =
      DatastoreServiceFactory.getDatastoreService(withMaxEntityGroupsPerRpc(Integer.MAX_VALUE));

  private ApiProxy.Delegate<ApiProxy.Environment> originalDelegate;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() throws Exception {
    originalDelegate = ApiProxy.getDelegate();
    recordingDelegate = new RecordingDelegate(originalDelegate);
    ApiProxy.setDelegate(recordingDelegate);
  }

  @After
  public void tearDown() {
    ApiProxy.setDelegate(originalDelegate);
  }

  private List<Entity> createNewEntities(int num) {
    List<Entity> entities = Lists.newArrayList();
    for (int i = 0; i < num; i++) {
      Entity entity = new Entity("Foo");
      entity.setProperty("ordinal", i);
      entities.add(entity);
    }
    ds.put(entities);
    return entities;
  }

  private List<Entity> createNewNumberedEntities(int num) {
    List<Entity> entities = Lists.newArrayList();
    Entity entity;
    for (int i = 0; i < num; i++) {
      entity = new Entity("Foo");
      entity.setProperty("number", i);
      entities.add(entity);
    }
    ds.put(entities);
    return entities;
  }

  /*
   * See b/7671334.  This test ensures only requested entries are being loaded
   * into LazyList.  The requested sublist asks for an item in order.
   */
  @Test
  public void testSublistLoadGetPosition1() {
    Query query = new Query("Foo").addSort("number", SortDirection.ASCENDING);
    createNewNumberedEntities(5);

    LazyList entities = (LazyList) ds.prepare(query).asList(withDefaults());
    List<Entity> returnedSubList = entities.subList(0, 1);
    assertThat(returnedSubList).hasSize(1);
    List<Entity> internalResultList = entities.results;
    assertThat(internalResultList).hasSize(1);
  }

  /*
   * See b/7671334.  This test ensures only requested entries are being loaded
   * into LazyList.  The requested sublist asks for an item in skipped order.
   */
  @Test
  public void testSublistLoadGetPosition2() {
    Query query = new Query("Foo").addSort("number", SortDirection.ASCENDING);
    createNewNumberedEntities(5);

    LazyList entities = (LazyList) ds.prepare(query).asList(withDefaults());
    List<Entity> returnedSubList = entities.subList(1, 2);
    assertThat(returnedSubList).hasSize(1);
    List<Entity> internalResultList = entities.results;
    assertThat(internalResultList).hasSize(2);
  }

  /*
   * See b/7671334.  This test ensures only requested entries are being loaded
   * into LazyList.  Multiple subsequence sublist requests to ensure internal
   * entity list is in good state.
   */
  @Test
  public void testSublistLoadMultipleGets() {
    Query query = new Query("Foo").addSort("number", SortDirection.ASCENDING);
    createNewNumberedEntities(5);

    LazyList entities = (LazyList) ds.prepare(query).asList(withDefaults());

    // Get the second item via sublist
    List<Entity> returnedSubList = entities.subList(1, 2);
    List<Entity> internalResultList = entities.results;
    assertThat(returnedSubList).hasSize(1);
    assertThat(internalResultList).hasSize(2);
    assertThat(internalResultList.get(0).getProperty("number").toString()).isEqualTo("0");
    assertThat(internalResultList.get(1).getProperty("number").toString()).isEqualTo("1");

    // Get the first item via sublist
    returnedSubList = entities.subList(0, 1);
    assertThat(returnedSubList).hasSize(1);
    assertThat(internalResultList).hasSize(2);
    assertThat(internalResultList.get(0).getProperty("number").toString()).isEqualTo("0");
    assertThat(internalResultList.get(1).getProperty("number").toString()).isEqualTo("1");

    // Get the 3rd item via sublist
    returnedSubList = entities.subList(2, 3);
    assertThat(returnedSubList).hasSize(1);
    assertThat(internalResultList).hasSize(3);
    assertThat(internalResultList.get(0).getProperty("number").toString()).isEqualTo("0");
    assertThat(internalResultList.get(1).getProperty("number").toString()).isEqualTo("1");
    assertThat(internalResultList.get(2).getProperty("number").toString()).isEqualTo("2");

    // Get all items via sublist
    returnedSubList = entities.subList(0, 5);
    assertThat(returnedSubList).hasSize(5);
    assertThat(internalResultList).hasSize(5);
    assertThat(internalResultList.get(0).getProperty("number").toString()).isEqualTo("0");
    assertThat(internalResultList.get(1).getProperty("number").toString()).isEqualTo("1");
    assertThat(internalResultList.get(2).getProperty("number").toString()).isEqualTo("2");
    assertThat(internalResultList.get(3).getProperty("number").toString()).isEqualTo("3");
    assertThat(internalResultList.get(4).getProperty("number").toString()).isEqualTo("4");
  }

  @Test
  public void testSize() {
    Query query = new Query("Foo").addSort("ordinal");
    List<Entity> newEntities = createNewEntities(11);

    // default fetch options
    List<Entity> entities = ds.prepare(query).asList(withDefaults());
    assertThat(entities).hasSize(11); // force all results to be pulled back
    assertThat(entities).isEqualTo(newEntities);
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).hasCount()).isFalse();
    assertThat(recordingDelegate.nextCalls).isEmpty();

    recordingDelegate.clear();

    // chunk size
    entities = ds.prepare(query).asList(withChunkSize(3));
    assertThat(entities).hasSize(11); // force all results to be pulled back
    assertThat(entities).isEqualTo(newEntities);
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(3);
    assertThat(recordingDelegate.nextCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls.get(0).getCount()).isEqualTo(Integer.MAX_VALUE - 3);

    recordingDelegate.clear();

    // limit
    entities = ds.prepare(query).asList(withLimit(10));
    assertThat(entities).hasSize(10); // force all results to be pulled back
    assertThat(entities).isEqualTo(newEntities.subList(0, 10));
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).hasCount()).isFalse();
    assertThat(recordingDelegate.nextCalls).isEmpty();

    recordingDelegate.clear();

    // chunk size + limit
    entities = ds.prepare(query).asList(withChunkSize(3).limit(12));
    assertThat(entities).hasSize(11); // force all results to be pulled back
    assertThat(entities).isEqualTo(newEntities);
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(3);
    assertThat(recordingDelegate.nextCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls.get(0).getCount()).isEqualTo(Integer.MAX_VALUE - 3);

    recordingDelegate.clear();

    // chunk size + limit that is smaller than chunk size
    entities = ds.prepare(query).asList(withChunkSize(5).limit(4));
    assertThat(entities).hasSize(4); // force all results to be pulled back
    assertThat(entities).isEqualTo(newEntities.subList(0, 4));
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(5);
    assertThat(recordingDelegate.nextCalls).isEmpty();
  }

  private void addAllViaIteration(List<Entity> fillMe, Iterable<Entity> resultIterable) {
    for (Entity e : resultIterable) {
      fillMe.add(e);
    }
  }

  @Test
  public void testIterate() {
    Query query = new Query("Foo").addSort("ordinal");
    final List<Entity> newEntities = createNewEntities(11);
    final ArrayList<Entity> arrayList = Lists.newArrayList();

    // default fetch options
    List<Entity> resultList = ds.prepare(query).asList(withDefaults());
    addAllViaIteration(arrayList, resultList);

    class DefaultFetchOptionsAssertions {
      public void validate() {
        assertThat(arrayList).isEqualTo(newEntities);
        assertThat(recordingDelegate.runQueryCalls).hasSize(1);
        assertThat(recordingDelegate.runQueryCalls.get(0).hasCount()).isFalse();
        assertThat(recordingDelegate.nextCalls).isEmpty();
      }
    }
    new DefaultFetchOptionsAssertions().validate();
    arrayList.clear();

    // Iterate again to ensure nothing else gets requested
    addAllViaIteration(arrayList, resultList);
    new DefaultFetchOptionsAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // Same test but with an Iterable.  RPCs should be the same.
    Iterable<Entity> resultIterable = ds.prepare(query).asIterable(withDefaults());
    addAllViaIteration(arrayList, resultIterable);
    new DefaultFetchOptionsAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // chunk size
    resultList = ds.prepare(query).asList(withChunkSize(5));
    addAllViaIteration(arrayList, resultList);

    class ChunkSizeAssertions {
      public void validate() {
        assertThat(arrayList).isEqualTo(newEntities);
        assertThat(recordingDelegate.runQueryCalls).hasSize(1);
        assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(5);
        assertThat(recordingDelegate.nextCalls).hasSize(2);
        assertThat(recordingDelegate.nextCalls.get(0).getCount()).isEqualTo(5);
        assertThat(recordingDelegate.nextCalls.get(1).getCount()).isEqualTo(5);
      }
    }
    new ChunkSizeAssertions().validate();
    arrayList.clear();

    // Iterate again to ensure nothing else gets requested
    addAllViaIteration(arrayList, resultList);
    new ChunkSizeAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // Same test but with an Iterable.  RPCs should be the same.
    resultIterable = ds.prepare(query).asIterable(withChunkSize(5));
    addAllViaIteration(arrayList, resultIterable);
    new ChunkSizeAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // limit
    resultList = ds.prepare(query).asList(withLimit(5));
    addAllViaIteration(arrayList, resultList);
    class LimitAssertions {
      public void validate() {
        assertThat(arrayList).isEqualTo(newEntities.subList(0, 5));
        assertThat(recordingDelegate.runQueryCalls).hasSize(1);
        assertThat(recordingDelegate.runQueryCalls.get(0).hasCount()).isFalse();
        assertThat(recordingDelegate.nextCalls).isEmpty();
      }
    }
    new LimitAssertions().validate();
    arrayList.clear();

    // Iterate again to ensure nothing else gets requested
    addAllViaIteration(arrayList, resultList);
    new LimitAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // Same test but with an Iterable.  RPCs should be the same.
    resultIterable = ds.prepare(query).asIterable(withLimit(5));
    addAllViaIteration(arrayList, resultIterable);
    new LimitAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // chunk size + limit
    resultList = ds.prepare(query).asList(withChunkSize(5).limit(10));
    addAllViaIteration(arrayList, resultList);
    class ChunkSizeAndLimitAssertions {
      public void validate() {
        assertThat(arrayList).isEqualTo(newEntities.subList(0, 10));
        assertThat(recordingDelegate.runQueryCalls).hasSize(1);
        assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(5);
        assertThat(recordingDelegate.nextCalls).hasSize(1);
        assertThat(recordingDelegate.nextCalls.get(0).getCount()).isEqualTo(5);
      }
    }
    new ChunkSizeAndLimitAssertions().validate();
    arrayList.clear();

    // Iterate again to ensure nothing else gets requested
    addAllViaIteration(arrayList, resultList);
    new ChunkSizeAndLimitAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // Same test but with an Iterable.  RPCs should be the same.
    resultIterable = ds.prepare(query).asIterable(withChunkSize(5).limit(10));
    addAllViaIteration(arrayList, resultIterable);
    new ChunkSizeAndLimitAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // chunk size + limit that is smaller than chunk size
    resultList = ds.prepare(query).asList(withChunkSize(5).limit(4));
    addAllViaIteration(arrayList, resultList);
    class ChunkSizeAndSmallLimitAssertions {
      public void validate() {
        assertThat(arrayList).isEqualTo(newEntities.subList(0, 4));
        assertThat(recordingDelegate.runQueryCalls).hasSize(1);
        assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(5);
        assertThat(recordingDelegate.nextCalls).isEmpty();
      }
    }
    new ChunkSizeAndSmallLimitAssertions().validate();
    arrayList.clear();

    // Iterate again to ensure that nothing new gets requested
    addAllViaIteration(arrayList, resultList);
    new ChunkSizeAndSmallLimitAssertions().validate();
    arrayList.clear();
    recordingDelegate.clear();

    // Same test but with an Iterable.  RPCs should be the same.
    resultIterable = ds.prepare(query).asIterable(withChunkSize(5).limit(4));
    addAllViaIteration(arrayList, resultIterable);
    new ChunkSizeAndSmallLimitAssertions().validate();
  }

  @Test
  public void testGet() {
    Query query = new Query("Foo").addSort("ordinal");
    List<Entity> newEntities = createNewEntities(11);
    // This will return entities 1 and 2 in the prefetch.
    List<Entity> results = ds.prepare(query).asList(withChunkSize(2));

    // We haven't asked for any data yet.
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(2);
    assertThat(recordingDelegate.nextCalls).isEmpty();

    // Now ask for the first result, which forces us to block on the result of
    // the RunQuery.  When we get this result (1 and 2), we initiate a Next to
    // get the next 2 results.
    assertThat(results.get(0)).isEqualTo(newEntities.get(0));
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls.get(0).getCount()).isEqualTo(2);

    // Now ask for the 5th result, which forces us to block on the result of
    // the outstanding Next.  This next returns 3 and 4, which isn't enough to
    // get us the 5th result, so we request the next batch, which gets us 5 and
    // 6.  When this response comes back we kick off a Next for the next batch,
    // which will return 7 and 8 when someone needs those results.
    assertThat(results.get(4)).isEqualTo(newEntities.get(4));
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).hasSize(3);
    assertThat(recordingDelegate.nextCalls.get(1).getCount()).isEqualTo(2);
    assertThat(recordingDelegate.nextCalls.get(2).getCount()).isEqualTo(2);

    // Now ask for the 6th result.  This is already loaded, so no new rpcs are
    // initiated.
    assertThat(results.get(5)).isEqualTo(newEntities.get(5));
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).hasSize(3);

    // Now ask for the 11th result.  We'll block on the result of our most
    // recent Next, which returns 7 and 8.  We need 3 more results so we issue
    // a Next to fetch them.
    assertThat(results.get(10)).isEqualTo(newEntities.get(10));
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).hasSize(4);
    assertThat(recordingDelegate.nextCalls.get(3).getCount()).isEqualTo(3);
  }

  @Test
  public void testClear() {
    Query query = new Query("Foo");
    createNewEntities(11);
    // This will return entities 1 and 2 in the prefetch.
    QueryResultList<Entity> results =
        ds.prepare(query).asQueryResultList(withChunkSize(2).limit(10));

    // We haven't asked for any data yet.
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.runQueryCalls.get(0).getCount()).isEqualTo(2);
    assertThat(recordingDelegate.nextCalls).isEmpty();

    results.clear();
    // No new RPCs
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).isEmpty();

    // Even though we didn't fetch the entire result set, to the user the list
    // is empty.
    assertThat(results).isEmpty();
    // No new RPCs
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).isEmpty();

    // now we ask for a Cursor and we do get a new RPC because the cursor
    // request forces the entire result set to be pulled back
    results.getCursor();
    assertThat(recordingDelegate.runQueryCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls).hasSize(1);
    assertThat(recordingDelegate.nextCalls.get(0).getCount()).isEqualTo(Integer.MAX_VALUE - 2);
  }

  private QueryResultIteratorImpl newQueryResultIterator() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query q = new Query("foo");
    FetchOptions opts = FetchOptions.Builder.withLimit(100);
    PreparedQuery pq = ds.prepare(q);
    DatastoreV3Pb.QueryResult.Builder result = DatastoreV3Pb.QueryResult.newBuilder();
    result.addResult(EntityTranslator.convertToPb(new Entity("blar1")));
    result.addResult(EntityTranslator.convertToPb(new Entity("blar2")));
    QueryResultsSource source =
        new QueryResultsSourceV3(
            DatastoreServiceConfig.CALLBACKS,
            opts,
            null,
            new Query(q),
            new FutureHelper.FakeFuture<DatastoreV3Pb.QueryResult>(result.buildPartial()),
            new ApiProxy.ApiConfig());
    return new QueryResultIteratorImpl(pq, source, opts, null);
  }

  @SuppressWarnings("CheckReturnValue")
  @Test
  public void testAbstractListImplementation() {
    class SizeNotAllowed extends RuntimeException {}

    /**
     * An extension to {@link LazyList} that throws an exception whenever size() is called. Used to
     * verify that we've overridden everything in {@link java.util.AbstractList} that results in a
     * call to size(), which defeats the purpose of our implementation, which should only retrieve
     * the bare minimum number of results.
     */
    class VeryLazyList extends LazyList {
      VeryLazyList() {
        super(newQueryResultIterator());
      }

      @Override
      public int size() {
        throw new SizeNotAllowed();
      }
    }
    // We're going to see which calls on the List interface force all results
    // to be resolved.
    LazyList lazyList1 = new VeryLazyList();

    assertThrows(SizeNotAllowed.class, () -> lazyList1.add(new Entity("blar")));
    // add needs to add to the end of the list.  We could
    // delay this operation except for the fact that we need to return
    // whether or not the Collection was modified, and we can't always
    // figure this out without pulling in all results.

    LazyList lazyList2 = new VeryLazyList();
    lazyList2.add(0, new Entity("blar"));

    LazyList lazyList3 = new VeryLazyList();
    assertThrows(SizeNotAllowed.class, () -> lazyList3.addAll(ImmutableSet.of(new Entity("blar"))));
    // addAll needs to add all elements to the end of the list.  We could
    // delay this operation except for the fact that we need to return
    // whether or not the Collection was modified, and we can't always
    // figure this out without pulling in all results.

    assertThrows(
        SizeNotAllowed.class,
        () ->
            new VeryLazyList()
                .addAll(0, Lists.newArrayList(new Entity("blar"), new Entity("blar"))));
    // addAll needs to shift all elements to the right after the provided
    // index.  We could delay this operation except for the fact that we need
    // to return whether or not the Collection was modified, and we can't
    // always figure this out without pulling in all results.
    lazyList3.clear();
    boolean unused = new VeryLazyList().contains(new Entity("blar"));
    boolean unused2 =
        new VeryLazyList().containsAll(Lists.newArrayList(new Entity("blar"), new Entity("blar")));
    new VeryLazyList().equals(Collections.<Entity>singletonList(new Entity("blar")));
    new VeryLazyList().get(0);
    new VeryLazyList().hashCode();
    new VeryLazyList().indexOf(new Entity("blar"));
    new VeryLazyList().isEmpty();
    Iterator<Entity> iter = new VeryLazyList().iterator();
    while (iter.hasNext()) {
      iter.next();
      iter.remove();
    }
    try {
      new VeryLazyList().lastIndexOf(new Entity("blar"));
    } catch (SizeNotAllowed e) {
      // You need to start at the end of the list, so this is ok
    }
    ListIterator<Entity> listIter = new VeryLazyList().listIterator();
    assertThat(listIter.hasPrevious()).isFalse();
    assertThat(listIter.previousIndex()).isEqualTo(-1);
    while (listIter.hasNext()) {
      listIter.next();
    }
    assertThat(listIter.nextIndex()).isEqualTo(2);
    assertThat(listIter.hasPrevious()).isTrue();
    while (listIter.hasPrevious()) {
      listIter.previous();
    }
    assertThrows(SizeNotAllowed.class, () -> new VeryLazyList().listIterator(1));
    // See the comment on LazyList.listIterator(int) for why this is expected

    new VeryLazyList().remove(1);
    new VeryLazyList().remove(new Entity("blam"));
    new VeryLazyList().removeAll(Lists.newArrayList(new Entity("blar")));
    new VeryLazyList().retainAll(Lists.newArrayList(new Entity("blar")));
    new VeryLazyList().set(0, new Entity("blar"));
    assertThrows(SizeNotAllowed.class, lazyList3::size);
    // we explicitly force this to fail
    new VeryLazyList().subList(0, 0);
    assertThrows(SizeNotAllowed.class, () -> new VeryLazyList().toArray());
    // It needs to resolve the entire result set in order to know how big an
    // Array to allocate
    assertThrows(SizeNotAllowed.class, () -> new VeryLazyList().toArray(new Entity[2]));
    // It needs to resolve the entire result set in order to know how big an
    // Array to allocate

    iter = new VeryLazyList().listIterator();
    iter.hasNext();
    iter.hasNext();
    iter.next();
    iter.remove();
  }
}
