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

import static com.google.appengine.api.datastore.BaseQueryResultsSource.lastChunkSizeWarning;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class QueryResultsSourceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  @Mock Logger mockLogger;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    lastChunkSizeWarning.set(0);
  }

  @After
  public void tearDown() {
    lastChunkSizeWarning.set(0);
    helper.tearDown();
  }

  private void addData(int count) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    List<Entity> toPut = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      toPut.add(new Entity("foo"));
    }
    datastore.put(toPut);
  }

  private interface ResultIterableProvider {
    Iterable<Entity> asIterable(PreparedQuery query, FetchOptions opts);
  }

  private static Iterable<Entity> asIterable(PreparedQuery query, FetchOptions opts) {
    return query.asIterable(opts);
  }

  private static Iterable<Entity> asList(PreparedQuery query, FetchOptions opts) {
    return query.asList(opts);
  }

  private void doQueries(ResultIterableProvider provider) {
    Logger original = BaseQueryResultsSource.logger;
    BaseQueryResultsSource.logger = mockLogger;
    try {
      DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
      Query query = new Query("foo");
      // This won't trigger the warning because we're not pulling back enough.
      for (@SuppressWarnings("unused")
      Entity e : provider.asIterable(datastore.prepare(query), withLimit(1000))) {}
      // This won't trigger the warning because we're setting an explicit chunk size.
      for (@SuppressWarnings("unused")
      Entity e : provider.asIterable(datastore.prepare(query), withChunkSize(21))) {}
      // This will trigger the warning.
      for (@SuppressWarnings("unused")
      Entity e : provider.asIterable(datastore.prepare(query), withDefaults())) {}
      // Do it again - we shouldn't log a second time because we only log every
      // 5 minutes.
      for (@SuppressWarnings("unused")
      Entity e : provider.asIterable(datastore.prepare(query), withDefaults())) {}
    } finally {
      BaseQueryResultsSource.logger = original;
    }
  }

  @Test
  public void testLogChunkSizeWarning() {
    Consumer<ResultIterableProvider> test =
        provider -> {
          doQueries(provider);
          assertThat(lastChunkSizeWarning.get()).isGreaterThan(0);
        };
    addData(1001);
    test.accept(QueryResultsSourceImplTest::asIterable);
    reset(mockLogger);
    lastChunkSizeWarning.set(0);
    test.accept(QueryResultsSourceImplTest::asList);
  }

  @Test
  public void testLogChunkSizeWarning_After5Minutes() {
    Consumer<ResultIterableProvider> test =
        provider -> {
          doQueries(provider);
          // Turn back time by 10 minutes
          lastChunkSizeWarning.set(lastChunkSizeWarning.get() - (1000 * 60 * 10));
          // Run again, we'll get one more log warning
          doQueries(provider);
     
          assertThat(lastChunkSizeWarning.get()).isGreaterThan(0);
        };
    addData(1001);
    test.accept(QueryResultsSourceImplTest::asIterable);
    reset(mockLogger);
    lastChunkSizeWarning.set(0);
    test.accept(QueryResultsSourceImplTest::asList);
  }

  @Test
  public void testLogChunkSizeWarning_LoggingDisabled() {
    Consumer<ResultIterableProvider> test =
        provider -> {
          System.setProperty("appengine.datastore.disableChunkSizeWarning", "value ignored");
          try {
            doQueries(provider);
          } finally {
            System.clearProperty("appengine.datastore.disableChunkSizeWarning");
          }
          assertThat(lastChunkSizeWarning.get()).isEqualTo(0);
          verifyNoMoreInteractions(mockLogger);
        };
    addData(1001);
    test.accept(QueryResultsSourceImplTest::asIterable);
    reset(mockLogger);
    lastChunkSizeWarning.set(0);
    test.accept(QueryResultsSourceImplTest::asList);
  }
}
