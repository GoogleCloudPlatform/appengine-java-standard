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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.CallbackContext;
import com.google.appengine.api.datastore.Category;
import com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig;
import com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId;
import com.google.appengine.api.datastore.CloudDatastoreRemoteServiceConfig.AppId.Location;
import com.google.appengine.api.datastore.CommittedButStillApplyingException;
import com.google.appengine.api.datastore.CompositeIndexManager;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DataTypeTranslator;
import com.google.appengine.api.datastore.DataTypeUtils;
import com.google.appengine.api.datastore.DatastoreApiHelper;
import com.google.appengine.api.datastore.DatastoreAttributes;
import com.google.appengine.api.datastore.DatastoreConfig;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreNeedIndexException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.DeleteContext;
import com.google.appengine.api.datastore.Email;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entities;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.EntityProtoComparators;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.GeoPt;
import com.google.appengine.api.datastore.IDatastoreServiceFactory;
import com.google.appengine.api.datastore.IDatastoreServiceFactoryProvider;
import com.google.appengine.api.datastore.IMHandle;
import com.google.appengine.api.datastore.ImplicitTransactionManagementPolicy;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.Link;
import com.google.appengine.api.datastore.PhoneNumber;
import com.google.appengine.api.datastore.PostDelete;
import com.google.appengine.api.datastore.PostLoad;
import com.google.appengine.api.datastore.PostLoadContext;
import com.google.appengine.api.datastore.PostPut;
import com.google.appengine.api.datastore.PostalAddress;
import com.google.appengine.api.datastore.PreDelete;
import com.google.appengine.api.datastore.PreGet;
import com.google.appengine.api.datastore.PreGetContext;
import com.google.appengine.api.datastore.PrePut;
import com.google.appengine.api.datastore.PreQuery;
import com.google.appengine.api.datastore.PreQueryContext;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Projection;
import com.google.appengine.api.datastore.PropertyContainer;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.PutContext;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.GeoRegion;
import com.google.appengine.api.datastore.Query.GeoRegion.Circle;
import com.google.appengine.api.datastore.Query.GeoRegion.Rectangle;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Rating;
import com.google.appengine.api.datastore.RawValue;
import com.google.appengine.api.datastore.ReadPolicy;
import com.google.appengine.api.datastore.ShortBlob;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.apicompat.UsageTracker;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DatastorePb;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.storage.onestore.v3.OnestoreEntity;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import org.mockito.Mockito;

/**
 * Exhaustive usage of the Datastore Api. Used for backward compatibility checks.
 */
// TODO(maxr): Add usage for the rest of the Datastore API.
@SuppressWarnings({"unused", "SelfEquals"})
public class DatastoreApiUsage {

  /**
   * Exhaustive use of {@link DatastoreServiceFactory}.
   */
  public static class DatastoreServiceFactoryUsage extends
      ExhaustiveApiUsage<DatastoreServiceFactory> {

    @Override
    @SuppressWarnings("deprecation")
    public Set<Class<?>> useApi() {
      // oops, this never should have been exposed
      DatastoreServiceFactory factory = new DatastoreServiceFactory();
      DatastoreServiceConfig config = DatastoreServiceConfig.Builder.withDefaults();
      AsyncDatastoreService ads = DatastoreServiceFactory.getAsyncDatastoreService();
      ads = DatastoreServiceFactory.getAsyncDatastoreService(config);
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      ds = DatastoreServiceFactory.getDatastoreService(config);
      ds = DatastoreServiceFactory.getDatastoreService(DatastoreConfig.DEFAULT);
      DatastoreConfig cofig = DatastoreServiceFactory.getDefaultDatastoreConfig();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive test of {@link IDatastoreServiceFactory}
   */
  public static class IDatastoreServiceFactoryUsage extends
    ExhaustiveApiInterfaceUsage<IDatastoreServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IDatastoreServiceFactory iDatastoreServiceFactory) {
      DatastoreServiceConfig config = DatastoreServiceConfig.Builder.withDefaults();

      iDatastoreServiceFactory.getAsyncDatastoreService(config);
      iDatastoreServiceFactory.getDatastoreService(config);

      return classes();
    }
  }

  /**
   * Exhaustive test of {@link IDatastoreServiceFactoryProvider}
   */
  public static class IDatastoreServiceFactoryProviderUsage extends
        ExhaustiveApiUsage<IDatastoreServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IDatastoreServiceFactoryProvider p = new IDatastoreServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreService}.
   */
  public static class DatastoreServiceUsage extends
      ExhaustiveApiInterfaceUsage<DatastoreService> {

    @Override
    @SuppressWarnings("unchecked")
    protected Set<Class<?>> useApi(DatastoreService ds) {
      Key key = null;
      Transaction txn = null;
      Entity entity = null;
      KeyRange range = null;
      String kind = null;
      TransactionOptions options = null;
      DatastoreService.KeyRangeState rangeState = ds.allocateIdRange(range);
      range = ds.allocateIds(kind, 3);
      range = ds.allocateIds(key, kind, 3);
      txn = ds.beginTransaction();
      txn = ds.beginTransaction(options);
      ds.delete(key);
      ds.delete(Arrays.asList(key));

      ds.delete(txn, key);
      ds.delete(txn, Arrays.asList(key));
      try {
        entity = ds.get(key);
      } catch (EntityNotFoundException e) {
        // fine
      }
      Map<Key, Entity> map = ds.get(Arrays.asList(key));
      map = ds.get(txn, Arrays.asList(key));
      try {
        entity = ds.get(txn, key);
      } catch (EntityNotFoundException e) {
        // fine
      }
      DatastoreAttributes attributes = ds.getDatastoreAttributes();
      Map<Index, Index.IndexState> indexMap = ds.getIndexes();

      key = ds.put(entity);
      List<Key> keyList = ds.put(Arrays.asList(entity));
      key = ds.put(txn, entity);
      keyList = ds.put(txn, Arrays.asList(entity));
      return classes(BaseDatastoreService.class);
    }
  }

  /**
   * Exhaustive use of {@link AsyncDatastoreService}.
   */
  public static class AsyncDatastoreServiceUsage extends
      ExhaustiveApiInterfaceUsage<AsyncDatastoreService> {

    @Override
    @SuppressWarnings("unchecked")
    protected Set<Class<?>> useApi(AsyncDatastoreService ads) {
      Key key = null;
      Transaction txn = null;
      Entity entity = null;
      String kind = null;
      TransactionOptions options = null;
      Future<KeyRange> range = ads.allocateIds(kind, 3);
      range = ads.allocateIds(key, kind, 3);
      Future<Transaction> txnFuture = ads.beginTransaction();
      txnFuture = ads.beginTransaction(options);
      Future<Void> voidFuture = ads.delete(key);
      voidFuture = ads.delete(Arrays.asList(key));

      voidFuture = ads.delete(txn, key);
      voidFuture = ads.delete(txn, Arrays.asList(key));
      Future<Entity> entityFuture = ads.get(key);
      Future<Map<Key, Entity>> entityMapFuture = ads.get(Arrays.asList(key));
      entityMapFuture = ads.get(txn, Arrays.asList(key));
      entityFuture = ads.get(txn, key);
      Future<DatastoreAttributes> attributes = ads.getDatastoreAttributes();
      Future<Map<Index, Index.IndexState>> stateMapFuture = ads.getIndexes();

      Future<Key> keyFuture = ads.put(entity);
      Future<List<Key>> keyListFuture = ads.put(Arrays.asList(entity));
      keyFuture = ads.put(txn, entity);
      keyListFuture = ads.put(txn, Arrays.asList(entity));
      return classes(BaseDatastoreService.class);
    }

  }

  /**
   * Exhaustive use of {@link BaseDatastoreService}.
   */
  public static class BaseDatastoreServiceUsage
      extends ExhaustiveApiInterfaceUsage<BaseDatastoreService> {

    @Override
    protected Set<Class<?>> useApi(BaseDatastoreService bds) {
      Transaction txn = null;
      Query query = null;
      PreparedQuery pq = bds.prepare(query);
      pq = bds.prepare(txn, query);

      txn = bds.getCurrentTransaction();
      txn = bds.getCurrentTransaction(txn);
      Collection<Transaction> txns = bds.getActiveTransactions();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link Cursor}.
   */
  public static class CursorUsage extends UsageWithLocalDatastoreService<Cursor> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Cursor cursor = ds.prepare(new Query()).asQueryResultIterator().getCursor();
      cursor = cursor.reverse();
      String strVal = cursor.toString();
      strVal = cursor.toWebSafeString();
      boolean boolVal = cursor.equals(cursor);
      int intVal = cursor.hashCode();
      cursor = Cursor.fromWebSafeString(strVal);
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link FetchOptions}.
   */
  public static class FetchOptionsUsage extends UsageWithLocalDatastoreService<FetchOptions> {

    int ___apiConstant_DEFAULT_CHUNK_SIZE;

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      ___apiConstant_DEFAULT_CHUNK_SIZE = FetchOptions.DEFAULT_CHUNK_SIZE;
      FetchOptions opts = FetchOptions.Builder.withDefaults();
      opts = opts.chunkSize(1);
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Cursor cursor = ds.prepare(new Query()).asQueryResultIterator().getCursor();
      opts = opts.cursor(cursor);
      opts = opts.startCursor(cursor);
      opts = opts.endCursor(cursor);
      boolean boolVal = opts.equals(opts);
      Integer integerVal = opts.getChunkSize();
      cursor = opts.getCursor();
      cursor = opts.getEndCursor();
      integerVal = opts.getLimit();
      integerVal = opts.getOffset();
      integerVal = opts.getPrefetchSize();
      cursor = opts.getStartCursor();
      int intVal = opts.hashCode();
      opts = opts.limit(1);
      opts = opts.offset(1);
      opts = opts.prefetchSize(1);
      String strVal = opts.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link FetchOptions.Builder}.
   */
  public static class FetchOptionsBuilderUsage
      extends UsageWithLocalDatastoreService<FetchOptions.Builder> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      FetchOptions opts = FetchOptions.Builder.withDefaults();
      opts = FetchOptions.Builder.withChunkSize(2);
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Cursor cursor = ds.prepare(new Query()).asQueryResultIterator().getCursor();
      opts = FetchOptions.Builder.withCursor(cursor);
      opts = FetchOptions.Builder.withEndCursor(cursor);
      opts = FetchOptions.Builder.withLimit(2);
      opts = FetchOptions.Builder.withOffset(2);
      opts = FetchOptions.Builder.withPrefetchSize(2);
      opts = FetchOptions.Builder.withStartCursor(cursor);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IMHandle.Scheme}.
   */
  public static class SchemeUsage extends ExhaustiveApiUsage<IMHandle.Scheme> {

    @Override
    public Set<Class<?>> useApi() {
      IMHandle.Scheme[] values = IMHandle.Scheme.values();
      IMHandle.Scheme val = IMHandle.Scheme.valueOf("xmpp");
      val = IMHandle.Scheme.sip;
      val = IMHandle.Scheme.unknown;
      val = IMHandle.Scheme.xmpp;
      return classes(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link RawValue}.
   */
  public static class RawValueUsage extends UsageWithLocalDatastoreService<RawValue> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Entity e = new Entity("yam");
      e.setProperty("yar", 3L);
      Key key = ds.put(e);
      Query proj = new Query("yam");
      proj = proj.addProjection(new PropertyProjection("yar", null));
      RawValue rv = (RawValue) ds.prepare(proj).asSingleEntity().getProperty("yar");
      boolean boolVal = rv.equals(rv);
      Object objVal = rv.getValue();
      int intVal = rv.hashCode();
      String strVal = rv.toString();
      objVal = rv.asType(Long.class);
      Long longVal = rv.asStrictType(Long.class);
      return classes(Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Projection}.
   */
  public static class ProjectionUsage extends ExhaustiveApiUsage<Projection> {
    @Override
    @SuppressWarnings("null")
    public Set<Class<?>> useApi() {
      Projection proj = null;
      try {
        String strVal = proj.getName();
      } catch (NullPointerException e) {
        // ok
      }
      return classes(Serializable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.FilterPredicate}.
   */
  public static class FilterPredicateUsage extends ExhaustiveApiUsage<Query.FilterPredicate> {
    @Override
    public Set<Class<?>> useApi() {
      Query.FilterPredicate pred =
          new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, "23");
      boolean boolVal = pred.equals(pred);
      Query.FilterOperator op = pred.getOperator();
      String strVal = pred.getPropertyName();
      Object objVal = pred.getValue();
      int intVal = pred.hashCode();
      strVal = pred.toString();
      return classes(Serializable.class, Query.Filter.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link KeyFactory.Builder}.
   */
  public static class KeyFactoryBuilderUsage
      extends UsageWithLocalDatastoreService<KeyFactory.Builder> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      KeyFactory.Builder builder = new KeyFactory.Builder("kind", "name");
      builder = new KeyFactory.Builder("kind", 23L);
      builder = new KeyFactory.Builder(KeyFactory.createKey("kind", 23L));
      Key key = builder.getKey();
      String strVal = builder.getString();
      builder = builder.addChild("kind", "name");
      builder = builder.addChild("kind", 23L);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link KeyFactory}.
   */
  public static class KeyFactoryUsage extends UsageWithLocalDatastoreService<KeyFactory> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      KeyFactory.createKey("kind", 23L);
      Key parent = KeyFactory.createKey("kind", "name");
      Key key = KeyFactory.createKey(parent, "kind", "name");
      key = KeyFactory.createKey(parent, "kind", 23L);
      String strVal = KeyFactory.createKeyString("kind", 23L);
      strVal = KeyFactory.createKeyString("kind", "name");
      strVal = KeyFactory.createKeyString(parent, "kind", "name");
      strVal = KeyFactory.createKeyString(parent, "kind", 23L);
      strVal = KeyFactory.keyToString(parent);
      key = KeyFactory.stringToKey(strVal);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Link}.
   */
  public static class LinkUsage extends UsageWithPublicSerializationConstant<Link> {

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = Link.serialVersionUID;
      Link link = new Link("yar");
      Link link2 = new Link("yar");
      int intVal = link.compareTo(link2);
      boolean boolVal = link.equals(link);
      String strVal = link.getValue();
      intVal = link.hashCode();
      strVal = link.toString();
      return classes(Serializable.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link PhoneNumber}.
   */
  public static class PhoneNumberUsage extends UsageWithPublicSerializationConstant<PhoneNumber> {

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = PhoneNumber.serialVersionUID;
      PhoneNumber pn = new PhoneNumber("8");
      PhoneNumber pn2 = new PhoneNumber("8");
      int intVal = pn.compareTo(pn2);
      boolean boolVal = pn.equals(pn);
      String strVal = pn.getNumber();
      intVal = pn.hashCode();
      return classes(Serializable.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GeoPt}.
   */
  public static class GeoPtUsage extends UsageWithPublicSerializationConstant<GeoPt> {

    double ___apiConstant_EARTH_RADIUS_METERS;

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = GeoPt.serialVersionUID;
      ___apiConstant_EARTH_RADIUS_METERS = GeoPt.EARTH_RADIUS_METERS;
      GeoPt pt = new GeoPt(0, 0);
      GeoPt pt2 = new GeoPt(0, 0);
      int intVal = pt.compareTo(pt2);
      boolean boolVal = pt.equals(pt);
      float floatVal = pt.getLatitude();
      floatVal = pt.getLongitude();
      intVal = pt.hashCode();
      String strVal = pt.toString();
      return classes(Serializable.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Category}.
   */
  public static class CategoryUsage extends UsageWithPublicSerializationConstant<Category> {

    // TODO(b/79994182): see go/objecttostring-lsc
    @SuppressWarnings("ObjectToString")
    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = Category.serialVersionUID;
      Category cat = new Category("yar");
      Category cat2 = new Category("yar");
      String strVal = cat.getCategory();
      int intVal = cat.compareTo(cat2);
      boolean boolVal = cat.equals(cat);
      intVal = cat.hashCode();
      // TODO(b/79994182): Category does not implement toString() in cat
      strVal = cat.toString();
      return classes(Serializable.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link CallbackContext}.
   */
  public static class CallbackContextUsage<T>
      extends ExhaustiveApiInterfaceUsage<CallbackContext<T>> {

    @Override
    protected Set<Class<?>> useApi(CallbackContext<T> context) {
      Object obj = context.getCurrentElement();
      int intVal = context.getCurrentIndex();
      Transaction txn = context.getCurrentTransaction();
      List<T> elements = context.getElements();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link DatastoreAttributes.DatastoreType}.
   */
  public static class DatastoreTypeUsage
      extends ExhaustiveApiUsage<DatastoreAttributes.DatastoreType> {

    @Override
    public Set<Class<?>> useApi() {
      DatastoreAttributes.DatastoreType type = DatastoreAttributes.DatastoreType.HIGH_REPLICATION;
      type = DatastoreAttributes.DatastoreType.MASTER_SLAVE;
      type = DatastoreAttributes.DatastoreType.UNKNOWN;
      type = DatastoreAttributes.DatastoreType.valueOf("MASTER_SLAVE");
      DatastoreAttributes.DatastoreType[] types = DatastoreAttributes.DatastoreType.values();
      return classes(Enum.class, Object.class, Comparable.class, Serializable.class);
    }
  }

  /** Exhaustive use of {@link DataTypeTranslator}. */
  public static class DataTypeTranslatorUsage
      extends UsageWithLocalDatastoreService<DataTypeTranslator> {

    @Override
    public Set<Class<?>> useApiWithLocalDatastore() {
      Map<String, Object> strObjMap = Maps.newHashMap();
      OnestoreEntity.EntityProto ep = new OnestoreEntity.EntityProto();
      OnestoreEntity.Path path = new OnestoreEntity.Path();
      path.addElement(new OnestoreEntity.Path.Element().setType("yar").setId(1L));
      ep.setKey(new OnestoreEntity.Reference().setPath(path));
      DataTypeTranslator.addPropertiesToPb(strObjMap, ep);
      DataTypeTranslator.extractImplicitPropertiesFromPb(ep, strObjMap);
      DataTypeTranslator.extractIndexedPropertiesFromPb(ep, strObjMap);
      DataTypeTranslator.extractPropertiesFromPb(ep, strObjMap);
      Collection<OnestoreEntity.Property> props =
          DataTypeTranslator.findIndexedPropertiesOnPb(ep, "yar");
      Comparable<Object> comp =
          DataTypeTranslator.getComparablePropertyValue(new OnestoreEntity.Property());
      Object obj = DataTypeTranslator.getPropertyValue(new OnestoreEntity.Property());
      int intVal = DataTypeTranslator.getTypeRank(Long.class);

      Key key = KeyFactory.createKey("yar", 23L);
      Entity entity = new Entity("kind");
      entity = new Entity("kind", key);

      byte[] v1Entity = DataTypeTranslator.toSerializedV1Proto(entity);
      DataTypeTranslator.toEntityFromSerializedV1Proto(v1Entity);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link DataTypeTranslator.ComparableByteArray}.
   */
  public static class ComparableByteArrayUsage
      extends ExhaustiveApiUsage<DataTypeTranslator.ComparableByteArray> {

    @Override
    public Set<Class<?>> useApi() {
      DataTypeTranslator.ComparableByteArray cba =
          new DataTypeTranslator.ComparableByteArray("bytes".getBytes(UTF_8));
      DataTypeTranslator.ComparableByteArray cba2 =
          new DataTypeTranslator.ComparableByteArray("bytes".getBytes(UTF_8));
      int intVal = cba.compareTo(cba2);
      boolean boolVal = cba.equals(cba);
      intVal = cba.hashCode();
      return classes(Object.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link Index}.
   */
  public static class IndexUsage extends ExhaustiveApiUsage<Index> {

    @Override
    public Set<Class<?>> useApi() {
      Constructor<Index> ctor;
      try {
        ctor = Index.class.getDeclaredConstructor(
            Long.TYPE, String.class, Boolean.TYPE, List.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      ctor.setAccessible(true);
      Index index;
      try {
        index = ctor.newInstance(23L, "yar", false, Collections.emptyList());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      boolean boolVal = index.equals(index);
      long longVal = index.getId();
      String strVal = index.getKind();
      List<Index.Property> propList = index.getProperties();
      int intVal = index.hashCode();
      boolVal = index.isAncestor();
      strVal = index.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Index.IndexState}.
   */
  public static class IndexStateUsage extends ExhaustiveApiUsage<Index.IndexState> {

    @Override
    public Set<Class<?>> useApi() {
      Index.IndexState state = Index.IndexState.BUILDING;
      state = Index.IndexState.DELETING;
      state = Index.IndexState.ERROR;
      state = Index.IndexState.SERVING;
      state = Index.IndexState.valueOf("ERROR");
      Index.IndexState[] states = Index.IndexState.values();
      return classes(Enum.class, Object.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Index.Property}.
   */
  public static class IndexPropertyUsage extends ExhaustiveApiUsage<Index.Property> {

    @Override
    public Set<Class<?>> useApi() {
      Constructor<Index.Property> ctor;
      try {
        ctor = Index.Property.class.getDeclaredConstructor(String.class, Query.SortDirection.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      ctor.setAccessible(true);
      Index.Property prop;
      try {
        prop = ctor.newInstance("yar", Query.SortDirection.ASCENDING);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      Query.SortDirection dir = prop.getDirection();
      boolean boolVal = prop.equals(prop);
      String strVal = prop.getName();
      int intVal = prop.hashCode();
      strVal = prop.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link TransactionOptions}.
   */
  public static class TransactionOptionsUsage
      extends UsageWithLocalDatastoreService<TransactionOptions> {

    @SuppressWarnings("deprecation")
    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      TransactionOptions opts = TransactionOptions.Builder.withDefaults();
      String strVal = opts.toString();
      opts = opts.clearXG();
      opts = opts.setXG(true);
      opts = opts.clearMultipleEntityGroups();
      opts = opts.multipleEntityGroups(true);
      opts = opts.setTransactionMode(TransactionOptions.Mode.READ_WRITE);
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Transaction transaction = ds.beginTransaction();
      opts = opts.setPreviousTransaction(transaction);
      transaction.rollback();

      Boolean booleanVal = opts.allowsMultipleEntityGroups();
      boolean boolVal = opts.isXG();
      int intVal = opts.hashCode();
      boolVal = opts.equals(opts);
      TransactionOptions.Mode mode = opts.transactionMode();
      Transaction previousTransaction = opts.previousTransaction();

      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link TransactionOptions.Builder}.
   */
  public static class TransactionOptionsBuilderUsage
      extends UsageWithLocalDatastoreService<TransactionOptions.Builder> {

    @SuppressWarnings("deprecation")
    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      TransactionOptions opts = TransactionOptions.Builder.withDefaults();
      opts = TransactionOptions.Builder.withXG(true);
      opts = TransactionOptions.Builder.allowMultipleEntityGroups(true);
      opts = TransactionOptions.Builder.withTransactionMode(TransactionOptions.Mode.READ_WRITE);

      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      Transaction transaction = ds.beginTransaction();
      opts = TransactionOptions.Builder.withPreviousTransaction(transaction);
      transaction.rollback();
      return classes(Object.class);
    }
  }

  /** Exhaustive use of {@link TransactionOptions.Mode}. */
  public static class TransactionOptionsModeUsage
      extends UsageWithLocalDatastoreService<TransactionOptions.Mode> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      TransactionOptions.Mode mode = TransactionOptions.Mode.READ_ONLY;
      mode = TransactionOptions.Mode.READ_WRITE;
      mode = TransactionOptions.Mode.valueOf("READ_ONLY");
      TransactionOptions.Mode[] modes = TransactionOptions.Mode.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /** Exhaustive use of {@link PreparedQuery}. */
  @SuppressWarnings("deprecation")
  public static class PreparedQueryUsage extends ExhaustiveApiInterfaceUsage<PreparedQuery> {

    @Override
    protected Set<Class<?>> useApi(PreparedQuery query) {
      FetchOptions opts = FetchOptions.Builder.withDefaults();
      List<Entity> entityList = query.asList(FetchOptions.Builder.withDefaults());
      QueryResultIterable<Entity> queryResultIterable = query.asQueryResultIterable();
      queryResultIterable = query.asQueryResultIterable(opts);
      QueryResultIterator<Entity> queryResultIterator = query.asQueryResultIterator();
      queryResultIterator = query.asQueryResultIterator(opts);
      QueryResultList<Entity> queryResultList = query.asQueryResultList(opts);
      Entity entity = query.asSingleEntity();
      Iterable<Entity> iterable = query.asIterable();
      iterable = query.asIterable(opts);
      Iterator<Entity> iterator = query.asIterator();
      iterator = query.asIterator(opts);
      int intVal = query.countEntities();
      intVal = query.countEntities(opts);
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link KeyRange}.
   */
  public static class KeyRangeUsage extends UsageWithLocalDatastoreService<KeyRange> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      KeyRange keyRange = new KeyRange(KeyFactory.createKey("yar", 23L), "yar", 1L, 2L);
      boolean boolVal = keyRange.equals(keyRange);
      Key key = keyRange.getStart();
      key = keyRange.getEnd();
      long longVal = keyRange.getSize();
      int intVal = keyRange.hashCode();
      Iterator<Key> iter = keyRange.iterator();
      return classes(Object.class, Iterable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Transaction}.
   */
  public static class TransactionUsage extends ExhaustiveApiInterfaceUsage<Transaction> {

    @Override
    protected Set<Class<?>> useApi(Transaction txn) {
      txn.commit();;
      Future<Void> future = txn.commitAsync();
      String strVal = txn.getApp();
      strVal = txn.getId();
      boolean boolVal = txn.isActive();
      txn.rollback();
      future = txn.rollbackAsync();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link ReadPolicy}.
   */
  public static class ReadPolicyUsage extends ExhaustiveApiUsage<ReadPolicy> {

    @Override
    public Set<Class<?>> useApi() {
      ReadPolicy policy = new ReadPolicy(ReadPolicy.Consistency.EVENTUAL);
      boolean boolVal = policy.equals(policy);
      ReadPolicy.Consistency consistency = policy.getConsistency();
      int intVal = policy.hashCode();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ReadPolicy.Consistency}.
   */
  public static class ConsistencyUsage extends ExhaustiveApiUsage<ReadPolicy.Consistency> {

    @Override
    public Set<Class<?>> useApi() {
      ReadPolicy.Consistency consistency = ReadPolicy.Consistency.EVENTUAL;
      consistency = ReadPolicy.Consistency.STRONG;
      consistency = ReadPolicy.Consistency.valueOf("STRONG");
      ReadPolicy.Consistency[] consistencies = ReadPolicy.Consistency.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link CompositeIndexManager}.
   */
  public static class CompositeIndexManagerUsage extends ExhaustiveApiUsage<CompositeIndexManager> {

    static class MyCompositeIndexManager extends CompositeIndexManager {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyCompositeIndexManager() {
        OnestoreEntity.Index index = new OnestoreEntity.Index();
        String strVal = generateXmlForIndex(index, IndexSource.auto);
        IndexComponentsOnlyQuery query = new IndexComponentsOnlyQuery(new DatastorePb.Query());
        index = compositeIndexForQuery(query);
        Collection<OnestoreEntity.Index> coll = Collections.emptyList();
        index = minimumCompositeIndexForQuery(query, coll);
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      CompositeIndexManager mgr = new MyCompositeIndexManager();
      return classes(Object.class);
    }
  }

  public static class MyCompositeIndexManager extends CompositeIndexManager {
    @UsageTracker.DoNotTrackConstructorInvocation
    MyCompositeIndexManager() {
      KeyTranslator translator = new MyKeyTranslator();
    }

    static class MyKeyTranslator extends CompositeIndexManager.KeyTranslator {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyKeyTranslator() {
      }
    }

    /**
     * Exhaustive use of {@link CompositeIndexManager.KeyTranslator}. Needs to reside inside of a
     * class that extends {@link CompositeIndexManager} so that we can access a protected inner
     * class.
     */
    public static class KeyTranslatorUsage
        extends ExhaustiveApiUsage<CompositeIndexManager.KeyTranslator> {

      @Override
      public Set<Class<?>> useApi() {
        MyCompositeIndexManager mgr = new MyCompositeIndexManager();
        // superclass is package protected
        Class<?> packageProtectedKeyTranslator = KeyTranslator.class.getSuperclass();
        return classes(Object.class, packageProtectedKeyTranslator);
      }
    }
  }

  /**
   * Exhaustive use of {@link DatastoreServiceConfig}.
   */
  public static class DatastoreServiceConfigUsage
      extends ExhaustiveApiUsage<DatastoreServiceConfig> {

    // The API test needs this to support static finals.
    String ___apiConstant_DATASTORE_EMPTY_LIST_SUPPORT;

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_DATASTORE_EMPTY_LIST_SUPPORT =
          DatastoreServiceConfig.DATASTORE_EMPTY_LIST_SUPPORT;
      DatastoreServiceConfig.getEmptyListSupport();
      DatastoreServiceConfig config = DatastoreServiceConfig.Builder.withDefaults();
      config = config.deadline(23.5d);
      config = config.implicitTransactionManagementPolicy(ImplicitTransactionManagementPolicy.AUTO);
      config = config.maxEntityGroupsPerRpc(4);
      config = config.readPolicy(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
      Double doubleVal = config.getDeadline();
      ImplicitTransactionManagementPolicy txnPolicy =
          config.getImplicitTransactionManagementPolicy();
      Integer integerVal = config.getMaxEntityGroupsPerRpc();
      ReadPolicy readPolicy = config.getReadPolicy();
      //      config = config.entityCacheConfig(EntityCacheConfig.Builder.withDefaults());
      //      EntityCacheConfig cacheConfig = config.getEntityCacheConfig();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreServiceConfig.Builder}.
   */
  public static class DatastoreServiceConfigBuilderUsage
      extends ExhaustiveApiUsage<DatastoreServiceConfig.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      DatastoreServiceConfig config = DatastoreServiceConfig.Builder.withDefaults();
      config = DatastoreServiceConfig.Builder.withDeadline(23.5d);
      config = DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy(
          ImplicitTransactionManagementPolicy.AUTO);
      config = DatastoreServiceConfig.Builder.withMaxEntityGroupsPerRpc(4);
      config = DatastoreServiceConfig.Builder.withReadPolicy(
          new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
      //      config = DatastoreServiceConfig.Builder.withEntityCacheConfig(
      //          EntityCacheConfig.Builder.withDefaults());
      return classes(Object.class);
    }
  }

  // /**
  //  * Exhaustive use of {@link EntityCacheConfig}.
  //  */
  // public static class EntityCacheConfigUsage
  //     extends ExhaustiveApiUsage<EntityCacheConfig> {

  //   @Override
  //   public Set<Class<?>> useApi() {
  //     EntityCacheConfig config = EntityCacheConfig.Builder.withDefaults();
  //     config = config.entityCachingCriteria(EntityCachingCriteria.ALL_KEYS);
  //     EntityCachingCriteria cacheCriteria = config.getEntityCachingCriteria();
  //     config = config.entityCachePolicy(EntityCachePolicy.CACHE);
  //     EntityCachePolicy policy = config.getEntityCachePolicy();
  //     return classes(Object.class);
  //   }
  // }

  // /**
  //  * Exhaustive use of {@link EntityCacheConfig.Builder}.
  //  */
  // public static class EntityCacheConfigBuilderUsage
  //     extends ExhaustiveApiUsage<EntityCacheConfig.Builder> {

  //   @Override
  //   public Set<Class<?>> useApi() {
  //     EntityCacheConfig config = EntityCacheConfig.Builder.withDefaults();
  //     config = EntityCacheConfig.Builder.withEntityCachingCriteria(
  //         EntityCachingCriteria.ALL_KEYS);
  //     config = EntityCacheConfig.Builder.withEntityCachePolicy(EntityCachePolicy.CACHE);
  //     return classes(Object.class);
  //   }
  // }

  // /**
  //  * Exhaustive use of {@link EntityCachePolicy}.
  //  */
  // public static class EntityCachePolicyUsage extends ExhaustiveApiUsage<EntityCachePolicy> {

  //   @Override
  //   public Set<Class<?>> useApi() {
  //     EntityCachePolicy[] values = EntityCachePolicy.values();
  //     EntityCachePolicy policy =
  //         EntityCachePolicy.valueOf("CACHE_ONLY");
  //     policy = EntityCachePolicy.CACHE;
  //     policy = EntityCachePolicy.CACHE_ONLY;
  //     return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
  //   }
  // }

  // TODO(maxr): Make public when functionality is complete
  // /**
  //  * Exhaustive use of {@link EntityCachingCriteria}.
  //  */
  // public static class EntityCacheCachingCriteriaUsage
  //     extends ExhaustiveApiUsage<EntityCachingCriteria> {

  //   static class MyCachingCriteria extends EntityCachingCriteria {

  //     @UsageTracker.DoNotTrackConstructorInvocation
  //     MyCachingCriteria() {
  //     }

  //     @Override
  //     public boolean isCacheable(Key key) {
  //       return true;
  //     }
  //   }

  //   @Override
  //   public Set<Class<?>> useApi() {
  //     boolean isCacheable = EntityCachingCriteria.ALL_KEYS.isCacheable(null);
  //     EntityCachingCriteria predicate = new MyCachingCriteria();
  //     isCacheable = predicate.isCacheable(null);
  //     return classes(Object.class);
  //   }
  // }

  /**
   * Exhaustive use of {@link DatastoreTimeoutException}.
   */
  public static class DatastoreTimeoutExceptionUsage
      extends ExhaustiveApiUsage<DatastoreTimeoutException> {

    @Override
    public Set<Class<?>> useApi() {
      DatastoreTimeoutException ex = new DatastoreTimeoutException("yar");
      ex = new DatastoreTimeoutException("yar", new Throwable());
      return classes(Object.class, Exception.class, RuntimeException.class, Throwable.class,
                     Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link EntityProtoComparators}.
   */
  public static class EntityProtoComparatorsUsage
      extends ExhaustiveApiUsage<EntityProtoComparators> {

    Comparator<Comparable<Object>> ___apiConstant_MULTI_TYPE_COMPARATOR;

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_MULTI_TYPE_COMPARATOR = EntityProtoComparators.MULTI_TYPE_COMPARATOR;
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link EntityProtoComparators.EntityProtoComparator}.
   */
  public static class EntityProtoComparatorUsage
      extends ExhaustiveApiUsage<EntityProtoComparators.EntityProtoComparator> {

    @Override
    public Set<Class<?>> useApi() {
      List<DatastorePb.Query.Order> orders =
          Collections.singletonList(
              new DatastorePb.Query.Order().setProperty("yar").setDirection(0));
      EntityProtoComparators.EntityProtoComparator comparator =
          new EntityProtoComparators.EntityProtoComparator(orders);
      DatastorePb.Query.Filter filter = new DatastorePb.Query.Filter();
      OnestoreEntity.Property property = new OnestoreEntity.Property().setName("yar").setValue(
          new OnestoreEntity.PropertyValue().setInt64Value(23));
      filter.addProperty(property);
      filter.setOp(DatastorePb.Query.Filter.Operator.EQUAL);
      List<DatastorePb.Query.Filter> filters = Collections.singletonList(filter);
      comparator = new EntityProtoComparators.EntityProtoComparator(orders, filters);
      OnestoreEntity.EntityProto proto = new OnestoreEntity.EntityProto();
      OnestoreEntity.Path path = new OnestoreEntity.Path();
      path.addElement(new OnestoreEntity.Path.Element().setType("yar").setId(1L));
      proto.setKey(new OnestoreEntity.Reference().setPath(path));
      proto.addProperty(property);
      orders = comparator.getAdjustedOrders();
      boolean boolVal = comparator.matches(new OnestoreEntity.Property());
      boolVal = comparator.matches(proto);
      try {
        return classes(Object.class, Comparator.class,
            Class.forName("com.google.appengine.api.datastore.BaseEntityComparator"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link CommittedButStillApplyingException}.
   */
  public static class CommittedButStillApplyingExceptionUsage
      extends ExhaustiveApiUsage<CommittedButStillApplyingException> {

    @Override
    public Set<Class<?>> useApi() {
      CommittedButStillApplyingException ex = new CommittedButStillApplyingException("yar");
      ex = new CommittedButStillApplyingException("yar", new Throwable());
      return classes(Object.class, Exception.class, RuntimeException.class, Throwable.class,
                     Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link ShortBlob}.
   */
  public static class ShortBlobUsage extends UsageWithPublicSerializationConstant<ShortBlob> {

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = ShortBlob.serialVersionUID;
      ShortBlob shortBlob = new ShortBlob("bytes".getBytes(UTF_8));
      ShortBlob shortBlob2 = new ShortBlob("bytes".getBytes(UTF_8));
      int intVal = shortBlob.compareTo(shortBlob2);
      boolean boolVal = shortBlob.equals(shortBlob);
      byte[] bytes = shortBlob.getBytes();
      intVal = shortBlob.hashCode();
      String strVal = shortBlob.toString();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link PostalAddress}.
   */
  public static class PostalAddressUsage
      extends UsageWithPublicSerializationConstant<PostalAddress> {

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = Link.serialVersionUID;
      PostalAddress address = new PostalAddress("yar");
      PostalAddress address2 = new PostalAddress("yar");
      int intVal = address.compareTo(address2);
      boolean boolVal = address.equals(address);
      intVal = address.hashCode();
      // TODO(b/79994182): see go/objecttostring-lsc
      @SuppressWarnings(
          "ObjectToString") // TODO(b/79994182): PostalAddress does not implement toString() in
      // address
      String strVal = address.toString();
      strVal = address.getAddress();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link Entity}.
   */
  public static class EntityUsage extends UsageWithLocalDatastoreService<Entity> {

    String ___apiConstant_VERSION_RESERVED_PROPERTY;
    String ___apiConstant_KEY_RESERVED_PROPERTY;
    String ___apiConstant_SCATTER_RESERVED_PROPERTY;

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      ___apiConstant_VERSION_RESERVED_PROPERTY = Entity.VERSION_RESERVED_PROPERTY;
      ___apiConstant_KEY_RESERVED_PROPERTY = Entity.KEY_RESERVED_PROPERTY;
      ___apiConstant_SCATTER_RESERVED_PROPERTY = Entity.SCATTER_RESERVED_PROPERTY;

      Key key = KeyFactory.createKey("yar", 23L);
      Entity entity = new Entity("kind");
      entity = new Entity("kind", key);
      entity = new Entity("kind", "name");
      entity = new Entity("kind", 23L);
      entity = new Entity("kind", "name", key);
      entity = new Entity("kind", 23L, key);
      entity = new Entity(key);
      entity = entity.clone();
      boolean boolVal = entity.equals(entity);
      String strVal = entity.getAppId();
      key = entity.getKey();
      strVal = entity.getKind();
      strVal = entity.getNamespace();
      key = entity.getParent();
      int intVal = entity.hashCode();
      entity.setPropertiesFrom(entity);
      strVal = entity.toString();
      return classes(Object.class, Serializable.class, Cloneable.class, PropertyContainer.class);
    }
  }

  /**
   * Exhaustive use of {@link EntityTranslator}.
   */
  public static class EntityTranslatorUsage
      extends UsageWithLocalDatastoreService<EntityTranslator> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      OnestoreEntity.EntityProto proto = EntityTranslator.convertToPb(new Entity("yar"));
      OnestoreEntity.Path path = new OnestoreEntity.Path();
      path.addElement(new OnestoreEntity.Path.Element().setType("yar").setId(1L));
      proto.setKey(new OnestoreEntity.Reference().setPath(path));
      Entity entity = EntityTranslator.createFromPb(proto);
      Collection<Projection> projections = Collections.emptyList();
      entity = EntityTranslator.createFromPb(proto, projections);
      entity = EntityTranslator.createFromPbBytes(proto.toByteArray());
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link EmbeddedEntity}.
   */
  public static class EmbeddedEntityUsage extends UsageWithLocalDatastoreService<EmbeddedEntity> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      EmbeddedEntity ee = new EmbeddedEntity();
      ee = ee.clone();
      boolean boolVal = ee.equals(ee);
      Key key = ee.getKey();
      int intVal = ee.hashCode();
      String strVal = ee.toString();
      ee.setKey(key);
      return classes(Object.class, PropertyContainer.class, Serializable.class, Cloneable.class);
    }
  }

  /**
   * Exhaustive use of {@link Rating}.
   */
  public static class RatingUsage extends UsageWithPublicSerializationConstant<Rating> {

    int ___apiConstant_MIN_VALUE;
    int ___apiConstant_MAX_VALUE;

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = Rating.serialVersionUID;
      ___apiConstant_MIN_VALUE = Rating.MIN_VALUE;
      ___apiConstant_MAX_VALUE = Rating.MAX_VALUE;
      Rating rating = new Rating(5);
      Rating rating2 = new Rating(5);
      boolean boolVal = rating.equals(rating);
      int intVal = rating.hashCode();
      int unused = rating.compareTo(rating2);
      intVal = rating.getRating();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query}.
   */
  public static class QueryUsage extends UsageWithLocalDatastoreService<Query> {
    String ___apiConstant_KIND_METADATA_KIND;
    String ___apiConstant_NAMESPACE_METADATA_KIND;
    String ___apiConstant_PROPERTY_METADATA_KIND;

    @Override
    @SuppressWarnings("deprecation")
    Set<Class<?>> useApiWithLocalDatastore() {
      ___apiConstant_KIND_METADATA_KIND = Query.KIND_METADATA_KIND;
      ___apiConstant_NAMESPACE_METADATA_KIND = Query.NAMESPACE_METADATA_KIND;
      ___apiConstant_PROPERTY_METADATA_KIND = Query.PROPERTY_METADATA_KIND;
      Query query = new Query();
      query = new Query("kind");
      Key key = KeyFactory.createKey("yar", 23L);
      query = new Query("kind", key);
      query = new Query(key);
      query = query.setKeysOnly();
      query = query.setDistinct(true);
      query = query.addProjection(new PropertyProjection("yar", String.class));
      query = query.addFilter("yar", Query.FilterOperator.EQUAL, "value");
      query = query.addSort("yar");
      query = query.addSort(Entity.KEY_RESERVED_PROPERTY, Query.SortDirection.ASCENDING);
      query = query.clearKeysOnly();
      boolean boolVal = query.equals(query);
      key = query.getAncestor();
      Query.Filter filter = query.getFilter();
      List<Query.FilterPredicate> filterPreds = query.getFilterPredicates();
      String strVal = query.getKind();
      boolVal = query.getDistinct();
      Collection<Projection> projectionColl = query.getProjections();
      List<Query.SortPredicate> sortPreds = query.getSortPredicates();
      int intVal = query.hashCode();
      boolVal = query.isKeysOnly();
      query = query.reverse();
      query = query.setAncestor(key);
      query = query.setFilter(filter);
      strVal = query.toString();
      String namespace = query.getNamespace();
      String appId = query.getAppId();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.SortDirection}.
   */
  public static class SortDirectionUsage
      extends UsageWithLocalDatastoreService<Query.SortDirection> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      Query.SortDirection dir = Query.SortDirection.ASCENDING;
      dir = Query.SortDirection.DESCENDING;
      dir = Query.SortDirection.valueOf("ASCENDING");
      Query.SortDirection[] dirs = Query.SortDirection.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.SortPredicate}.
   */
  public static class SortPredicateUsage
      extends UsageWithLocalDatastoreService<Query.SortPredicate> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      Query.SortPredicate pred = new Query.SortPredicate("prop", Query.SortDirection.ASCENDING);
      boolean boolVal = pred.equals(pred);
      Query.SortDirection dir = pred.getDirection();
      String strVal = pred.getPropertyName();
      int intVal = pred.hashCode();
      pred = pred.reverse();
      strVal = pred.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreAttributes}.
   */
  public static class DatastoreAttributesUsage
      extends UsageWithLocalDatastoreService<DatastoreAttributes> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      DatastoreAttributes attrs = ds.getDatastoreAttributes();
      DatastoreAttributes.DatastoreType type = attrs.getDatastoreType();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link PreparedQuery.TooManyResultsException}.
   */
  public static class TooManyResultsExceptionUsage
      extends ExhaustiveApiUsage<PreparedQuery.TooManyResultsException> {

    @Override
    public Set<Class<?>> useApi() {
      PreparedQuery.TooManyResultsException ex = new PreparedQuery.TooManyResultsException();
      return classes(Object.class, Exception.class, RuntimeException.class, Throwable.class,
                     Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link QueryResultIterable}.
   */
  public static class QueryResultIterableUsage<T>
      extends UsageWithLocalDatastoreService<QueryResultIterable<T>> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      PreparedQuery pq = ds.prepare(new Query());
      QueryResultIterable<Entity> iterable = pq.asQueryResultIterable();
      QueryResultIterator<Entity> iterator = iterable.iterator();
      return classes(Iterable.class);
    }
  }

  /**
   * Exhaustive use of {@link QueryResultIterator}.
   */
  public static class QueryResultIteratorUsage<T>
      extends UsageWithLocalDatastoreService<QueryResultIterator<T>> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      PreparedQuery pq = ds.prepare(new Query());
      QueryResultIterator<Entity> iterator = pq.asQueryResultIterator();
      Cursor cursor = iterator.getCursor();
      List<Index> indexList = iterator.getIndexList();
      return classes(Iterator.class);
    }
  }

  /** Exhaustive use of {@link QueryResultList}. */
  public static class QueryResultListUsage<T>
      extends UsageWithLocalDatastoreService<QueryResultList<T>> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      PreparedQuery pq = ds.prepare(new Query());
      QueryResultList<Entity> list = pq.asQueryResultList(FetchOptions.Builder.withDefaults());
      Cursor cursor = list.getCursor();
      List<Index> indexList = list.getIndexList();
      return classes(Iterable.class, Collection.class, List.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.CompositeFilter}.
   */
  public static class CompositeFilterUsage
      extends UsageWithLocalDatastoreService<Query.CompositeFilter> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      Query.FilterPredicate pred = new Query.FilterPredicate(
          "name", Query.FilterOperator.EQUAL, "23");
      Query.CompositeFilter filter = new Query.CompositeFilter(
          Query.CompositeFilterOperator.AND, Arrays.<Query.Filter>asList(pred, pred));
      boolean boolVal = filter.equals(filter);
      Query.CompositeFilterOperator op = filter.getOperator();
      List<Query.Filter> filterList = filter.getSubFilters();
      int intVal = filter.hashCode();
      String strVal = filter.toString();
      return classes(Object.class, Query.Filter.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.CompositeFilterOperator}.
   */
  public static class CompositeFilterOperatorUsage
      extends UsageWithLocalDatastoreService<Query.CompositeFilterOperator> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      Query.CompositeFilterOperator op = Query.CompositeFilterOperator.AND;
      op = Query.CompositeFilterOperator.OR;
      op = Query.CompositeFilterOperator.valueOf("OR");
      Query.CompositeFilterOperator[] ops = Query.CompositeFilterOperator.values();
      Query.FilterPredicate pred =
          new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, "23");
      Query.CompositeFilter filter = Query.CompositeFilterOperator.and(pred, pred);
      filter = Query.CompositeFilterOperator.and(Arrays.<Query.Filter>asList(pred, pred));
      filter = Query.CompositeFilterOperator.or(pred, pred);
      filter = Query.CompositeFilterOperator.or(Arrays.<Query.Filter>asList(pred, pred));
      filter = op.of(filter, filter);
      filter = op.of(Arrays.<Query.Filter>asList(filter, filter));
      return classes(Object.class, Enum.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link KeyUsage}.
   */
  public static class KeyUsage extends UsageWithLocalDatastoreService<Key> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      Key key = KeyFactory.createKey("kind", 23L);
      Key key2 = KeyFactory.createKey("kind", 23L);
      int intVal = key.compareTo(key2);
      boolean boolVal = key.equals(key2);
      key = key.getChild("kind", 23L);
      key = key.getChild("kind", "name");
      long longVal = key.getId();
      String strVal = key.getKind();
      strVal = key.getName();
      strVal = key.getNamespace();
      strVal = key.getAppId();
      Key parent = key.getParent();
      intVal = key.hashCode();
      boolVal = key.isComplete();
      strVal = key.toString();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreFailureException}.
   */
  public static class DatastoreFailureExceptionUsage
      extends ExhaustiveApiUsage<DatastoreFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      DatastoreFailureException ex = new DatastoreFailureException("yar");
      ex = new DatastoreFailureException("yar", new Throwable());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
                     Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreNeedIndexException}.
   */
  public static class DatastoreNeedIndexExceptionUsage
      extends ExhaustiveApiUsage<DatastoreNeedIndexException> {

    @Override
    public Set<Class<?>> useApi() {
      DatastoreNeedIndexException ex = new DatastoreNeedIndexException("yar");
      ex = new DatastoreNeedIndexException("yar", new Throwable());
      String strVal = ex.getMessage();
      strVal = ex.getMissingIndexDefinitionXml();
      return classes(Object.class, Exception.class, RuntimeException.class, Throwable.class,
                     Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link EntityNotFoundException}.
   */
  public static class EntityNotFoundExceptionUsage
      extends UsageWithLocalDatastoreService<EntityNotFoundException> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      EntityNotFoundException ex = new EntityNotFoundException(KeyFactory.createKey("kind", 23L));
      Key key = ex.getKey();
      return classes(Object.class, Exception.class, Throwable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link PreGet}.
   */
  public static class PreGetUsage extends ExhaustiveApiInterfaceUsage<PreGet> {

    @Override
    protected Set<Class<?>> useApi(PreGet preGet) {
      String[] strArray = preGet.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link PostLoad}.
   */
  public static class PostLoadUsage extends ExhaustiveApiInterfaceUsage<PostLoad> {

    @Override
    protected Set<Class<?>> useApi(PostLoad postLoad) {
      String[] strArray = postLoad.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link PrePut}.
   */
  public static class PrePutUsage extends ExhaustiveApiInterfaceUsage<PrePut> {

    @Override
    protected Set<Class<?>> useApi(PrePut prePut) {
      String[] strArray = prePut.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link PostPut}.
   */
  public static class PostPutUsage extends ExhaustiveApiInterfaceUsage<PostPut> {

    @Override
    protected Set<Class<?>> useApi(PostPut postPut) {
      String[] strArray = postPut.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link PreDelete}.
   */
  public static class PreDeleteUsage extends ExhaustiveApiInterfaceUsage<PreDelete> {

    @Override
    protected Set<Class<?>> useApi(PreDelete preDelete) {
      String[] strArray = preDelete.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link PostDelete}.
   */
  public static class PostDeleteUsage extends ExhaustiveApiInterfaceUsage<PostDelete> {

    @Override
    protected Set<Class<?>> useApi(PostDelete postDelete) {
      String[] strArray = postDelete.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link PreQuery}.
   */
  public static class PreQueryUsage extends ExhaustiveApiInterfaceUsage<PreQuery> {

    @Override
    protected Set<Class<?>> useApi(PreQuery preQuery) {
      String[] strArray = preQuery.kinds();
      return classes(Annotation.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreConfig}.
   */
  @SuppressWarnings("deprecation")
  public static class DatastoreConfigUsage extends ExhaustiveApiInterfaceUsage<DatastoreConfig> {

    DatastoreConfig ___apiConstant_DEFAULT;

    @Override
    protected Set<Class<?>> useApi(DatastoreConfig datastoreConfig) {
      ImplicitTransactionManagementPolicy policy =
          datastoreConfig.getImplicitTransactionManagementPolicy();
      ___apiConstant_DEFAULT = DatastoreConfig.DEFAULT;
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link ImplicitTransactionManagementPolicy}.
   */
  public static class ImplicitTransactionManagementPolicyUsage
      extends ExhaustiveApiUsage<ImplicitTransactionManagementPolicy> {

    @Override
    public Set<Class<?>> useApi() {
      ImplicitTransactionManagementPolicy[] values = ImplicitTransactionManagementPolicy.values();
      ImplicitTransactionManagementPolicy val = ImplicitTransactionManagementPolicy.valueOf("AUTO");
      val = ImplicitTransactionManagementPolicy.AUTO;
      val = ImplicitTransactionManagementPolicy.NONE;
      return classes(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link Blob}.
   */
  public static class BlobUsage extends ExhaustiveApiUsage<Blob> {

    @Override
    public Set<Class<?>> useApi() {
      Blob blob = new Blob("bytes".getBytes(UTF_8));
      boolean boolVal = blob.equals(blob);
      byte[] bytes = blob.getBytes();
      int intVal = blob.hashCode();
      String strVal = blob.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Text}.
   */
  public static class TextUsage extends UsageWithPublicSerializationConstant<Text> {

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = Text.serialVersionUID;
      Text text = new Text("yar");
      boolean boolVal = text.equals(text);
      int intVal = text.hashCode();
      String strVal = text.getValue();
      strVal = text.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link IMHandle}.
   */
  public static class IMHandleUsage extends UsageWithPublicSerializationConstant<IMHandle> {

    @Override
    public Set<Class<?>> useApi() {
      ___apiConstant_serialVersionUID = IMHandle.serialVersionUID;
      IMHandle handle = new IMHandle(IMHandle.Scheme.xmpp, "address");
      IMHandle handle2 = new IMHandle(IMHandle.Scheme.xmpp, "address");
      int unused = handle.compareTo(handle2);
      try {
        handle = new IMHandle(new URL("http://localhost"), "address");
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      boolean boolVal = handle.equals(handle);
      int intVal = handle.hashCode();
      String strVal = handle.getAddress();
      strVal = handle.getProtocol();
      strVal = handle.toString();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link Email}.
   */
  public static class EmailUsage extends ExhaustiveApiUsage<Email> {

    @Override
    public Set<Class<?>> useApi() {
      Email email = new Email("this@that.com");
      Email email2 = new Email("this@that.com");
      boolean boolVal = email.equals(email);
      int intVal = email.hashCode();
      int unused = email.compareTo(email2);
      // TODO(b/79994182): see go/objecttostring-lsc
      @SuppressWarnings(
          "ObjectToString") // TODO(b/79994182): Email does not implement toString() in email
      String strVal = email.toString();
      strVal = email.getEmail();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreService.KeyRangeState}.
   */
  public static class KeyRangeStateUsage
      extends ExhaustiveApiUsage<DatastoreService.KeyRangeState> {

    @Override
    public Set<Class<?>> useApi() {
      DatastoreService.KeyRangeState[] values = DatastoreService.KeyRangeState.values();
      DatastoreService.KeyRangeState val = DatastoreService.KeyRangeState.valueOf("EMPTY");
      val = DatastoreService.KeyRangeState.COLLISION;
      val = DatastoreService.KeyRangeState.CONTENTION;
      val = DatastoreService.KeyRangeState.EMPTY;
      return classes(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link PropertyProjection}.
   */
  public static class PropertyProjectionUsage extends ExhaustiveApiUsage<PropertyProjection> {

    @Override
    public Set<Class<?>> useApi() {
      PropertyProjection proj = new PropertyProjection("prop", String.class);
      boolean boolVal = proj.equals(proj);
      String strVal = proj.getName();
      Class<?> clsVa = proj.getType();
      int intVal = proj.hashCode();
      strVal = proj.toString();
      return classes(Object.class, Projection.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.FilterOperator}.
   */
  public static class FilterOperatorUsage extends ExhaustiveApiUsage<Query.FilterOperator> {

    @Override
    public Set<Class<?>> useApi() {
      Query.FilterOperator[] values = Query.FilterOperator.values();
      Query.FilterOperator val = Query.FilterOperator.valueOf("EQUAL");
      val = Query.FilterOperator.EQUAL;
      val = Query.FilterOperator.GREATER_THAN;
      val = Query.FilterOperator.GREATER_THAN_OR_EQUAL;
      val = Query.FilterOperator.IN;
      val = Query.FilterOperator.LESS_THAN_OR_EQUAL;
      val = Query.FilterOperator.LESS_THAN;
      val = Query.FilterOperator.NOT_EQUAL;
      String strVal = val.toString();
      Query.FilterPredicate pred = val.of("prop", "value");
      return classes(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link Circle}.
   */
  public static class CircleUsage extends ExhaustiveApiUsage<Circle> {

    @Override
    public Set<Class<?>> useApi() {
      Circle circle = new Circle(new GeoPt(1, 2), 3);
      GeoPt center = circle.getCenter();
      double radius = circle.getRadius();
      boolean e = circle.equals(circle);
      int hash = circle.hashCode();
      String string = circle.toString();
      boolean yes = circle.contains(center);
      return classes(Object.class, Serializable.class, GeoRegion.class);
    }
  }

  /**
   * Exhaustive use of {@link Rectangle}.
   */
  public static class RectangleUsage extends ExhaustiveApiUsage<Rectangle> {

    @Override
    public Set<Class<?>> useApi() {
      GeoPt p1 = new GeoPt(1, 2);
      GeoPt p2 = new GeoPt(3, 4);
      Rectangle rect = new Rectangle(p1, p2);
      GeoPt sw = rect.getSouthwest();
      GeoPt ne = rect.getNortheast();
      boolean e = rect.equals(rect);
      int hash = rect.hashCode();
      String string = rect.toString();
      boolean yes = rect.contains(p1);
      return classes(Object.class, Serializable.class, GeoRegion.class);
    }
  }

  /**
   * Exhaustive use of {@link GeoRegion}.
   */
  public static class GeoRegionUsage extends ExhaustiveApiUsage<GeoRegion> {

    @Override
    public Set<Class<?>> useApi() {
      GeoPt p1 = new GeoPt(1, 2);
      GeoPt p2 = new GeoPt(3, 4);
      GeoRegion g;
      try {
        // Can't invoke the constructor directly because it will show up as an extraneous
        // invocation.
        Constructor<? extends Rectangle> ctor =
            Rectangle.class.getConstructor(GeoPt.class, GeoPt.class);
        g = ctor.newInstance(p1, p2);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      boolean yes = g.contains(p1);
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.Filter}.
   */
  public static class FilterUsage extends ExhaustiveApiUsage<Query.Filter> {

    @Override
    public Set<Class<?>> useApi() {
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link Query.StContainsFilter}.
   */
  public static class StContainsFilterUsage extends ExhaustiveApiUsage<Query.StContainsFilter> {
    @Override
    public Set<Class<?>> useApi() {
      Query.StContainsFilter filter =
          new Query.StContainsFilter("propertyname",
              new Circle(new GeoPt(1, 2), 3));
      String propertyName = filter.getPropertyName();
      GeoRegion region = filter.getRegion();
      boolean e = filter.equals(filter);
      int hash = filter.hashCode();
      String string = filter.toString();
      return classes(Serializable.class, Query.Filter.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link Entities}.
   */
  public static class EntitiesUsage extends UsageWithLocalDatastoreService<Entities> {

    long ___apiConstant_ENTITY_GROUP_METADATA_ID;
    long ___apiConstant_NAMESPACE_METADATA_EMPTY_ID;
    String ___apiConstant_ENTITY_GROUP_METADATA_KIND;
    String ___apiConstant_NAMESPACE_METADATA_KIND;
    String ___apiConstant_KIND_METADATA_KIND;
    String ___apiConstant_PROPERTY_METADATA_KIND;

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      ___apiConstant_ENTITY_GROUP_METADATA_ID = Entities.ENTITY_GROUP_METADATA_ID;
      ___apiConstant_NAMESPACE_METADATA_EMPTY_ID = Entities.NAMESPACE_METADATA_EMPTY_ID;
      ___apiConstant_ENTITY_GROUP_METADATA_KIND = Entities.ENTITY_GROUP_METADATA_KIND;
      ___apiConstant_NAMESPACE_METADATA_KIND = Entities.NAMESPACE_METADATA_KIND;
      ___apiConstant_KIND_METADATA_KIND = Entities.KIND_METADATA_KIND;
      ___apiConstant_PROPERTY_METADATA_KIND = Entities.PROPERTY_METADATA_KIND;

      Entities entities = new Entities(); // TODO(maxr) deprecate
      Key key = KeyFactory.createKey("yar", 23L);
      key = Entities.createEntityGroupKey(key);
      key = Entities.createKindKey("yar");
      key = Entities.createNamespaceKey("yar");
      key = Entities.createPropertyKey("yar", "prop");
      String strVal = Entities.getNamespaceFromNamespaceKey(key);
      Entity entity = new Entity(key);
      entity.setProperty(Entity.VERSION_RESERVED_PROPERTY, 3L);
      long longVal = Entities.getVersionProperty(entity);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link DataTypeUtils}.
   */
  public static class DataTypeUtilsUsage extends ExhaustiveApiUsage<DataTypeUtils> {
    int ___apiConstant_MAX_LINK_PROPERTY_LENGTH;
    int ___apiConstant_MAX_STRING_PROPERTY_LENGTH;
    int ___apiConstant_MAX_SHORT_BLOB_PROPERTY_LENGTH;

    @Override
    public Set<Class<?>> useApi() {
      Object obj = new Object();
      try {
        DataTypeUtils.checkSupportedValue(obj);
      } catch (IllegalArgumentException e) {
        // fine
      }
      try {
        DataTypeUtils.checkSupportedValue("yar", obj);
      } catch (IllegalArgumentException e) {
        // fine
      }
      Set<Class<?>> types = DataTypeUtils.getSupportedTypes();
      Class<?> cls = String.class;
      boolean boolVal = DataTypeUtils.isSupportedType(cls);
      boolean otherBoolVal = DataTypeUtils.isUnindexableType(cls);
      ___apiConstant_MAX_LINK_PROPERTY_LENGTH = DataTypeUtils.MAX_LINK_PROPERTY_LENGTH;
      ___apiConstant_MAX_STRING_PROPERTY_LENGTH = DataTypeUtils.MAX_STRING_PROPERTY_LENGTH;
      ___apiConstant_MAX_SHORT_BLOB_PROPERTY_LENGTH = DataTypeUtils.MAX_SHORT_BLOB_PROPERTY_LENGTH;
      return classes(Object.class);
    }
  }

  /**
   * Container class that allows us to refer to ValidatedQuery and IndexComponentsOnlyQuery
   */
  public static class AnotherCompositeIndexManager extends CompositeIndexManager {

    /**
     * Exhaustive use of {@link ValidatedQuery}.
     */
    public static class ValidatedQueryUsage extends ExhaustiveApiUsage<ValidatedQuery> {

      @Override
      public Set<Class<?>> useApi() {
        try {
          ValidatedQuery query = new ValidatedQuery(new DatastorePb.Query());
          return classes(Object.class,
              Class.forName("com.google.appengine.api.datastore.ValidatedQuery"),
              Class.forName("com.google.appengine.api.datastore.NormalizedQuery"));
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    }

    /**
     * Exhaustive use of {@link IndexComponentsOnlyQuery}.
     */
    public static class IndexComponentsOnlyQueryUsage
        extends ExhaustiveApiUsage<IndexComponentsOnlyQuery> {

      @Override
      public Set<Class<?>> useApi() {
        try {
          IndexComponentsOnlyQuery query = new IndexComponentsOnlyQuery(new DatastorePb.Query());
          return classes(Object.class,
              Class.forName("com.google.appengine.api.datastore.ValidatedQuery"),
              Class.forName("com.google.appengine.api.datastore.IndexComponentsOnlyQuery"),
              Class.forName("com.google.appengine.api.datastore.NormalizedQuery"));
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Exhaustive use of {@link PutContext}.
   */
  public static class PutContextUsage extends ExhaustiveApiUsage<PutContext> {
    @Override
    public Set<Class<?>> useApi() {
      try {
        return classes(Object.class, CallbackContext.class,
            Class.forName("com.google.appengine.api.datastore.BaseCallbackContext"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link DeleteContext}.
   */
  public static class DeleteContextUsage extends ExhaustiveApiUsage<DeleteContext> {
    @Override
    public Set<Class<?>> useApi() {
      try {
        return classes(Object.class, CallbackContext.class,
            Class.forName("com.google.appengine.api.datastore.BaseCallbackContext"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link PreGetContext}.
   */
  public static class PreGetContextUsage extends UsageWithLocalDatastoreService<PreGetContext> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      Class<?> currentTxnProviderCls;
      try {
         currentTxnProviderCls =
             Class.forName("com.google.appengine.api.datastore.CurrentTransactionProvider");
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      Object proxy = Proxy.newProxyInstance(DatastoreService.class.getClassLoader(),
          new Class<?>[]{currentTxnProviderCls}, new InvocationHandler() {
        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
          return null;
        }
      });
      @SuppressWarnings("unchecked")
      Constructor<PreGetContext> ctor =
          (Constructor<PreGetContext>) PreGetContext.class.getDeclaredConstructors()[0];
      ctor.setAccessible(true);
      List<Key> keyList = Lists.newArrayList(KeyFactory.createKey("yar", 23L));
      Map<Key, Entity> map = Maps.newHashMap();
      map.put(keyList.get(0), new Entity(keyList.get(0)));
      PreGetContext pgc;
      try {
        pgc = ctor.newInstance(proxy, keyList, map);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      pgc.setResultForCurrentElement(new Entity(keyList.get(0)));
      try {
        return classes(Object.class, CallbackContext.class,
            Class.forName("com.google.appengine.api.datastore.BaseCallbackContext"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link PostLoadContext}.
   */
  public static class PostLoadContextUsage extends ExhaustiveApiUsage<PostLoadContext> {
    @Override
    public Set<Class<?>> useApi() {
      try {
        return classes(Object.class, CallbackContext.class,
            Class.forName("com.google.appengine.api.datastore.BaseCallbackContext"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link PreQueryContext}.
   */
  public static class PreQueryContextUsage extends ExhaustiveApiUsage<PreQueryContext> {
    @Override
    public Set<Class<?>> useApi() {
      try {
        return classes(Object.class, CallbackContext.class,
            Class.forName("com.google.appengine.api.datastore.BaseCallbackContext"));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link PropertyContainer}.
   */
  public static class PropertyContainerUsage
      extends UsageWithLocalDatastoreService<PropertyContainer> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      PropertyContainer container;
      try {
        // Can't invoke the Entity constructor directly because it will show up as an extraneous
        // invocation.
        Constructor<? extends PropertyContainer> ctor = Entity.class.getConstructor(String.class);
        container = ctor.newInstance("yar");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      Map<String, Object> props = container.getProperties();
      Object obj = container.getProperty("yar");
      boolean boolVal = container.hasProperty("yar");
      boolVal = container.isUnindexedProperty("yar");
      container.removeProperty("yar");
      container.setPropertiesFrom(container);
      container.setProperty("yar", 23L);
      container.setIndexedProperty("yar", 23L);
      container.setUnindexedProperty("yar", 23L);
      return classes(Object.class, Serializable.class, Cloneable.class);
    }
  }

  /**
   * Exhaustive use of {@link DatastoreApiHelper}.
   */
  public static class DatastoreApiHelperUsage
      extends UsageWithLocalDatastoreService<DatastoreApiHelper> {

    @Override
    Set<Class<?>> useApiWithLocalDatastore() {
      RuntimeException ex = DatastoreApiHelper.translateError(new ApiProxy.ApplicationException(1));
      return classes(Object.class);
    }
  }

  /**
   * Base class that configures the local datastore prior to using the api.
   * @param <T> The type of the api class.
   */
  private abstract static class UsageWithLocalDatastoreService<T> extends ExhaustiveApiUsage<T> {

    private LocalServiceTestHelper helper = new LocalServiceTestHelper(
        new LocalDatastoreServiceTestConfig());

    abstract Set<Class<?>> useApiWithLocalDatastore();

    @Override
    public Set<Class<?>> useApi() {
      helper.setUp();
      try {
        return useApiWithLocalDatastore();
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Base class with an api constant member for the serialization constant.
   *
   * @param <T> The type of the api class.
   */
  public abstract static class UsageWithPublicSerializationConstant<T>
      extends ExhaustiveApiUsage<T> {
    long ___apiConstant_serialVersionUID;
  }

  /** Exhaustive use of {@link CloudDatastoreRemoteServiceConfig}. */
  public static class CloudDatastoreRemoteServiceConfigTest
      extends ExhaustiveApiUsage<CloudDatastoreRemoteServiceConfig> {

    @Override
    public Set<Class<?>> useApi() {
      CloudDatastoreRemoteServiceConfig.clear();
      CloudDatastoreRemoteServiceConfig.setConfig(
          CloudDatastoreRemoteServiceConfig.builder()
              .appId(AppId.create(Location.US_CENTRAL, "test"))
              .useComputeEngineCredential(false)
              .asyncStackTraceCaptureEnabled(false)
              .build());
      CloudDatastoreRemoteServiceConfig.clear();
      return classes(Object.class);
    }
  }

  /** Exhaustive use of {@link CloudDatastoreRemoteServiceConfig.Builder}. */
  public static class CloudDatastoreRemoteServiceConfigBuilderTest
      extends ExhaustiveApiUsage<CloudDatastoreRemoteServiceConfig.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      PrivateKey privateKey = Mockito.mock(PrivateKey.class);

      CloudDatastoreRemoteServiceConfig.builder()
          .appId(AppId.create(Location.US_CENTRAL, "test"))
          .useComputeEngineCredential(false)
          .emulatorHost("localhost");
      CloudDatastoreRemoteServiceConfig.builder()
          .appId(AppId.create(Location.US_CENTRAL, "test"))
          .useServiceAccountCredential("foo@example.com", privateKey);
      CloudDatastoreRemoteServiceConfig.builder()
          .appId(AppId.create(Location.US_CENTRAL, "test"))
          .useComputeEngineCredential(false)
          .hostOverride("")
          .maxRetries(3)
          .additionalAppIds(Collections.emptySet())
          .httpConnectTimeoutMillis(1000)
          .installApiProxyEnvironment(false)
          .asyncStackTraceCaptureEnabled(false)
          .build();
      return classes(Object.class);
    }
  }

  /** Exhaustive use of {@link CloudDatastoreRemoteServiceConfig.AppId}. */
  public static class CloudDatastoreRemoteServiceConfigAppIdTest
      extends ExhaustiveApiUsage<CloudDatastoreRemoteServiceConfig.AppId> {

    @Override
    public Set<Class<?>> useApi() {
      AppId.create(Location.US_CENTRAL, "test");
      return classes(Object.class);
    }
  }

  /** Exhaustive use of {@link CloudDatastoreRemoteServiceConfig.AppId}. */
  public static class CloudDatastoreRemoteServiceConfigAppIdLocationTest
      extends ExhaustiveApiUsage<CloudDatastoreRemoteServiceConfig.AppId.Location> {
    @Override
    public Set<Class<?>> useApi() {
      Location[] unused =
          new Location[] {
            Location.AUSTRALIA_SOUTHEAST1,
            Location.ASIA_NORTHEAST1,
            Location.EUROPE_WEST,
            Location.EUROPE_WEST1,
            Location.EUROPE_WEST3,
            Location.US_CENTRAL,
            Location.US_EAST1,
            Location.US_EAST4
          };
      Location.values();
      Location.fromString("us-central");
      Location.valueOf("US_CENTRAL");
      return classes(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }
}
