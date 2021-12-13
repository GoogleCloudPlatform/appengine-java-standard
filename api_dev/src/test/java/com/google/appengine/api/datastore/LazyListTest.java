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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.Lists;
import com.google.common.collect.testing.ListTestSuiteBuilder;
import com.google.common.collect.testing.SampleElements;
import com.google.common.collect.testing.TestListGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.ListFeature;
import com.google.common.collect.testing.testers.CollectionContainsAllTester;
import com.google.common.collect.testing.testers.CollectionContainsTester;
import com.google.common.collect.testing.testers.CollectionCreationTester;
import com.google.common.collect.testing.testers.CollectionIteratorTester;
import com.google.common.collect.testing.testers.CollectionRemoveAllTester;
import com.google.common.collect.testing.testers.CollectionRemoveTester;
import com.google.common.collect.testing.testers.CollectionRetainAllTester;
import com.google.common.collect.testing.testers.CollectionToStringTester;
import com.google.common.collect.testing.testers.ListAddTester;
import com.google.common.collect.testing.testers.ListCreationTester;
import com.google.common.collect.testing.testers.ListEqualsTester;
import com.google.common.collect.testing.testers.ListIndexOfTester;
import com.google.common.collect.testing.testers.ListLastIndexOfTester;
import com.google.common.collect.testing.testers.ListRemoveTester;
import com.google.common.collect.testing.testers.ListRetainAllTester;
import com.google.common.collect.testing.testers.ListSetTester;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * TestSuite for {@link LazyList}. Relies on a collection testing framework to exhaustively test the
 * behavior according to the {@link List} contract. We just tell the framework what the
 * characteristics of our implementation are and provide some sample data.
 *
 * <p>See {@link LazyListImplTest} for tests that verify the number and nature of the RPCs that get
 * sent to the backend.
 *
 */
public class LazyListTest {

  /** Order of the sample elements must match the order in which the query returns results. */
  private static final class Entities extends SampleElements<Entity> {
    public Entities() {
      super(
          new Entity("foo", "a"),
          new Entity("foo", "b"),
          new Entity("foo", "c"),
          new Entity("foo", "d"),
          new Entity("foo", "e"));
    }
  }

  private static class TestEntityLazyListGenerator implements TestListGenerator<Entity> {
    private final int chunkSize;

    private TestEntityLazyListGenerator(int chunkSize) {
      this.chunkSize = chunkSize;
    }

    @Override
    public SampleElements<Entity> samples() {
      return new Entities();
    }

    @Override
    public List<Entity> create(Object... elements) {
      Entity[] array = new Entity[elements.length];
      int i = 0;
      for (Object e : elements) {
        array[i++] = (Entity) e;
      }
      return create(array);
    }

    /** Creates a new collection containing the given elements. */
    protected LazyList create(Entity[] elements) {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      for (Entity e : elements) {
        ds.put(e);
      }
      Query q = new Query("foo");
      // Make sure we don't truncate, and make sure the prefetch size is small
      // enough that we don't get the entire result set in the first batch,
      // otherwise we're not really testing anything interesting.
      FetchOptions opts =
          withLimit(elements.length + 1).prefetchSize(chunkSize).chunkSize(chunkSize);
      return (LazyList) ds.prepare(q).asList(opts);
    }

    @Override
    public Entity[] createArray(int length) {
      return new Entity[length];
    }

    /** Returns the original element list, unchanged. */
    @Override
    public List<Entity> order(List<Entity> insertionOrder) {
      return insertionOrder;
    }
  }

  /** A generator that produces LazyLists that have been serialized and then deserialized. */
  private static class TestEntityDeserializedLazyListGenerator extends TestEntityLazyListGenerator {
    private TestEntityDeserializedLazyListGenerator(int chunkSize) {
      super(chunkSize);
    }

    @Override
    protected LazyList create(Entity[] elements) {
      LazyList list = super.create(elements);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ByteArrayInputStream bais = null;
      try {
        try {
          ObjectOutputStream oos = new ObjectOutputStream(baos);
          oos.writeObject(list);
          bais = new ByteArrayInputStream(baos.toByteArray());
          ObjectInputStream ois = new ObjectInputStream(bais);
          return (LazyList) ois.readObject();
        } finally {
          baos.close();
          if (bais != null) {
            bais.close();
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  private static List<Method> methodsToSuppress() throws NoSuchMethodException {
    // I'm not thrilled about suppressing these tests but there isn't
    // really another good option.  Many of these tests require the
    // list to be initialized with a null value.  LazyList doesn't support
    // this because a query result will never return a null value.
    // However, we don't want to run the test without
    // CollectionFeature.ALLOWS_NULL_VALUES because users _can_ add null
    // values to the List once we've returned it to them.  So, we're just
    // disabling the tests that don't pass.  Not great, but it's pretty
    // unlikely that there are null-specific bugs since there isn't any
    // null-specific logic in our implementation.
    // There is a similar issue relating to duplicate results.  I'm suppressing
    // those tests as well.
    List<Method> methodsToSuppress =
        Lists.newArrayList(
            CollectionContainsTester.class.getMethod("testContains_nonNullWhenNullContained"),
            CollectionContainsTester.class.getMethod("testContains_nullContained"),
            CollectionContainsAllTester.class.getMethod("testContainsAll_nullPresent"),
            CollectionCreationTester.class.getMethod("testCreateWithNull_supported"),
            CollectionIteratorTester.class.getMethod("testIterator_nullElement"),
            CollectionRemoveAllTester.class.getMethod("testRemoveAll_containsNullYes"),
            CollectionRemoveTester.class.getMethod("testRemove_nullPresent"),
            CollectionRetainAllTester.class.getMethod(
                "testRetainAll_nullSingletonPreviouslySingletonWithNull"),
            CollectionRetainAllTester.class.getMethod(
                "testRetainAll_nullSingletonPreviouslySeveralWithNull"),
            CollectionRetainAllTester.class.getMethod("testRetainAll_containsNonNullWithNull"),
            CollectionToStringTester.class.getMethod("testToString_null"),
            ListAddTester.class.getMethod("testAdd_supportedNullPresent"),
            ListCreationTester.class.getMethod("testCreateWithDuplicates"),
            ListEqualsTester.class.getMethod("testEquals_containingNull"),
            ListIndexOfTester.class.getMethod("testFind_nonNullWhenNullContained"),
            ListIndexOfTester.class.getMethod("testFind_nullContained"),
            ListLastIndexOfTester.class.getMethod("testFind_nonNullWhenNullContained"),
            ListLastIndexOfTester.class.getMethod("testFind_nullContained"),
            ListLastIndexOfTester.class.getMethod("testLastIndexOf_duplicate"),
            ListRemoveTester.class.getMethod("testRemove_duplicate"),
            ListRetainAllTester.class.getMethod("testRetainAll_duplicatesKept"),
            ListRetainAllTester.class.getMethod("testRetainAll_countIgnored"),
            ListSetTester.class.getMethod("testSet_replacingNull"));
    try {
      // TODO Once java8 libraries are enabled in google3, include this method properly
      // using the above technique.
      Class<?> c =
          Class.forName("com.google.common.collect.testing.testers.CollectionSpliteratorTester");
      methodsToSuppress.add(c.getMethod("testSpliteratorNullable"));
    } catch (ClassNotFoundException e) {
      // Expected in Java 7 environment as there is no CollectionSpliteratorTester.
    }
    return methodsToSuppress;
  }

  public static Test suite() throws NoSuchMethodException {
    TestSuite suite = new TestSuite();
    // Create one test for each chunk size between 1 and 7.  This should give
    // us good coverage over our interactions with the datastore backend.
    for (int chunkSize = 1; chunkSize < 7; chunkSize++) {
      addTestWithChunkSize(suite, chunkSize, new TestEntityLazyListGenerator(chunkSize));
    }
    // Run the test suite once against a LazyList that has been serialized
    // and deserialized.
    addTestWithChunkSize(suite, 7, new TestEntityDeserializedLazyListGenerator(7));
    return suite;
  }

  private static void addTestWithChunkSize(
      TestSuite suite, final int chunkSize, TestListGenerator<Entity> generator)
      throws NoSuchMethodException {
    suite.addTest(
        ListTestSuiteBuilder.using(generator)
            .named(String.format("chunk size: %d", chunkSize))
            .withFeatures(
                CollectionSize.ANY,
                CollectionFeature.ALLOWS_NULL_VALUES,
                CollectionFeature.GENERAL_PURPOSE,
                ListFeature.GENERAL_PURPOSE,
                ListFeature.SUPPORTS_SET)
            .suppressing(methodsToSuppress())
            .withSetUp(
                new Runnable() {
                  @Override
                  public void run() {
                    helper.setUp();
                  }
                })
            .withTearDown(
                new Runnable() {
                  @Override
                  public void run() {
                    helper.tearDown();
                  }
                })
            .createTestSuite());
  }
}
