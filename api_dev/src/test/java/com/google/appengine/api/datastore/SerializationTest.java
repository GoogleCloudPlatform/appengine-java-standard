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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.KeyFactory.createKey;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.TransactionOptions.Mode;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.appengine.api.testing.SerializationTestBase;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.PropertyValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {
  @Rule
  public LocalServiceTestHelperRule testHelperRule =
      new LocalServiceTestHelperRule(
          new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()).setEnvAppId("app"));

  @Override
  protected ImmutableList<Serializable> getCanonicalObjects() {
    return ImmutableList.of(
        KeyFactory.createKey("yar", "the name"),
        canonicalEntity(),
        canonicalStructuredValue(true),
        canonicalQuery(),
        canonicalCursor(),
        canonicalIndex(),
        canonicalIndexProperty(),
        canonicalIndexState(),
        new AppIdNamespace("the appid", "the namespace"),
        new Blob("the rain in spain falls mainly on the plain".getBytes(UTF_8)),
        new DatastoreNeedIndexException("boom"),
        new DatastoreTimeoutException("boom"),
        new Entity.UnindexedValue("yar"),
        new Entity.WrappedValueImpl("yar", true, true),
        new EntityNotFoundException(KeyFactory.createKey("yar", 33)),
        ImplicitTransactionManagementPolicy.AUTO,
        new Link("www.google.com"),
        new PreparedQuery.TooManyResultsException(),
        Query.FilterOperator.EQUAL,
        Query.CompositeFilterOperator.OR,
        new Query.FilterPredicate("prop name", Query.FilterOperator.EQUAL, 33),
        new Query.CompositeFilter(
            Query.CompositeFilterOperator.OR,
            Arrays.<Query.Filter>asList(
                new Query.FilterPredicate("prop name", Query.FilterOperator.EQUAL, 33),
                new Query.FilterPredicate("prop name", Query.FilterOperator.EQUAL, 44))),
        new Query.StContainsFilter("prop name", new Query.GeoRegion.Circle(new GeoPt(1, 2), 3)),
        new Query.GeoRegion.Circle(new GeoPt(1, 2), 3),
        new Query.GeoRegion.Rectangle(new GeoPt(1, 2), new GeoPt(3, 4)),
        Query.SortDirection.ASCENDING,
        QuerySplitComponent.Order.ARBITRARY,
        MultiQueryComponent.Order.PARALLEL,
        new Query.SortPredicate("prop name", Query.SortDirection.ASCENDING),
        new ShortBlob("the rain in spain falls mainly on the plain".getBytes(UTF_8)),
        new Text("the rain in spain falls mainly on the plain"),
        TransactionImpl.TransactionState.BEGUN,
        new GeoPt(3.33f, 4.44f),
        new IMHandle(IMHandle.Scheme.xmpp, "yar"),
        IMHandle.Scheme.xmpp,
        new PhoneNumber("555-1234"),
        new PostalAddress("yar"),
        new Category("blarg"),
        new Rating(47),
        new Email("yar"),
        new KeyRange(
            KeyFactory.createKey("yar", 44),
            "yam",
            23,
            24,
            DatastoreApiHelper.getCurrentAppIdNamespace()),
        CompositeIndexManager.IndexSource.auto,
        new ValidatedQuery.IllegalQueryException(
            "boom", ValidatedQuery.IllegalQueryType.FILTER_WITH_MULTIPLE_PROPS),
        ValidatedQuery.IllegalQueryType.FILTER_WITH_MULTIPLE_PROPS,
        AbstractIterator.State.DONE,
        ReadPolicy.Consistency.EVENTUAL,
        new DatastoreFailureException("boom"),
        DatastoreService.KeyRangeState.EMPTY,
        DatastoreAttributes.DatastoreType.UNKNOWN,
        DatastoreCallbacksImpl.CallbackType.PrePut,
        new DatastoreCallbacksImpl.InvalidCallbacksConfigException("boom"),
        canonicalLazyList(),
        new PropertyProjection("prop", String.class),
        new MonitoredIndexUsageTracker.UsageIdCacheMap(1),
        DataTypeUtils.CheckValueOption.ALLOW_MULTI_VALUE,
        new RawValue(PropertyValue.getDefaultInstance()),
        CloudDatastoreRemoteServiceConfig.AppId.Location.US_CENTRAL,
        Mode.READ_WRITE);
  }

  @Override
  protected ImmutableList<Serializable> getAdditionalObjects() {
    return ImmutableList.of(
        IndexTranslator.convertFromPb(IndexTranslator.convertToPb(canonicalIndex())));
  }

  /** Entity equality only checks the key, so we need manually check the properties as well. */
  @Test
  public void testEntityBackwardsCompatibility() throws IOException, ClassNotFoundException {
    try (InputStream is = getGoldenFileForClass(Entity.class);
        ObjectInputStream ois = new ObjectInputStream(is)) {
      Entity actual = (Entity) ois.readObject();
      Entity expected = canonicalEntity();
      assertThat(actual.getKey()).isEqualTo(expected.getKey());
      assertThat(actual.getPropertyMap()).isEqualTo(expected.getPropertyMap());
    }
  }

  private static LazyList canonicalLazyList() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity e = new Entity("foo");
    ds.put(e);
    Query query = new Query("foo");
    LazyList list = (LazyList) ds.prepare(query).asList(withDefaults());
    // force all data to resolve
    int unused = list.size();
    return list;
  }

  private static Entity canonicalEntity() {
    Entity e = new Entity("the kind", "the name");
    e.setProperty("p1", "aString");
    e.setProperty("p2", 44L);
    e.setProperty("p3", canonicalStructuredValue(true));
    e.setUnindexedProperty("p4", 8.6);
    e.setUnindexedProperty("p5", canonicalStructuredValue(true));
    return e;
  }

  private static EmbeddedEntity canonicalStructuredValue(boolean nest) {
    EmbeddedEntity ee = new EmbeddedEntity();
    ee.setKey(createKey("the kind", "the name"));
    ee.setProperty("p1", "aString");
    ee.setProperty("p2", 44L);
    ee.setUnindexedProperty("p4", 8.6);
    if (nest) {
      ee.setProperty("p3", canonicalStructuredValue(false));
      ee.setUnindexedProperty("p5", canonicalStructuredValue(false));
    }
    return ee;
  }

  @SuppressWarnings("deprecation")
  private static Query canonicalQuery() {
    Query q = new Query("the kind");
    q.setAncestor(KeyFactory.createKey("parent kind", "the parent"));
    q.addFilter("p1", Query.FilterOperator.EQUAL, 33L);
    q.addSort("p2", Query.SortDirection.DESCENDING);
    q.setKeysOnly();
    q.addProjection(new PropertyProjection("Hi", String.class));
    q.setDistinct(true);
    return q;
  }

  private static Cursor canonicalCursor() {
    DatastoreV3Pb.CompiledCursor.Builder compiledCursor = DatastoreV3Pb.CompiledCursor.newBuilder();
    compiledCursor
        .getPositionBuilder()
        .setStartKey(ByteString.copyFromUtf8("Happiness is a warm cursor"))
        .setStartInclusive(true);
    return new Cursor(compiledCursor.build().toByteString());
  }

  private static Index canonicalIndex() {
    List<Index.Property> properties =
        Arrays.asList(
            new Index.Property("name1", SortDirection.ASCENDING),
            new Index.Property("name2", SortDirection.DESCENDING));
    return new Index(10, "kind", true, properties);
  }

  private static Serializable canonicalIndexProperty() {
    return new Index.Property("name", SortDirection.ASCENDING);
  }

  private static Serializable canonicalIndexState() {
    return Index.IndexState.SERVING;
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return DatastoreService.class;
  }

  /** Instructions for generating new golden files are in the BUILD file in this directory. */
  public static void main(String[] args) throws IOException {
    new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()).setEnvAppId("app").setUp();
    SerializationTest st = new SerializationTest();
    st.writeCanonicalObjects();
  }
}
