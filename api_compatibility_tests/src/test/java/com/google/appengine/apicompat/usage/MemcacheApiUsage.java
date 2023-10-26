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

import com.google.appengine.api.memcache.AsyncMemcacheService;
import com.google.appengine.api.memcache.BaseMemcacheService;
import com.google.appengine.api.memcache.ConsistentErrorHandler;
import com.google.appengine.api.memcache.ConsistentLogAndContinueErrorHandler;
import com.google.appengine.api.memcache.ErrorHandler;
import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.IMemcacheServiceFactory;
import com.google.appengine.api.memcache.IMemcacheServiceFactoryProvider;
import com.google.appengine.api.memcache.InvalidValueException;
import com.google.appengine.api.memcache.LogAndContinueErrorHandler;
import com.google.appengine.api.memcache.MemcacheSerialization;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.Stats;
import com.google.appengine.api.memcache.StrictErrorHandler;
import com.google.appengine.api.memcache.stdimpl.GCache;
import com.google.appengine.api.memcache.stdimpl.GCacheEntry;
import com.google.appengine.api.memcache.stdimpl.GCacheException;
import com.google.appengine.api.memcache.stdimpl.GCacheFactory;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import javax.cache.Cache;
import javax.cache.CacheEntry;
import javax.cache.CacheFactory;
import javax.cache.CacheListener;
import javax.cache.CacheStatistics;

/** Exhaustive usage of the Memcache Api. Used for backward compatibility checks. */
public final class MemcacheApiUsage {

  public static class BaseMemcacheServiceApiUsage
      extends ExhaustiveApiInterfaceUsage<BaseMemcacheService> {

    @Override
    protected Set<Class<?>> useApi(BaseMemcacheService svc) {
      svc.getErrorHandler();
      svc.getNamespace();
      svc.setErrorHandler(ErrorHandlers.getDefault());
      return Collections.emptySet();
    }
  }

  /**
   * Exhaustive use of {@link IMemcacheServiceFactory}.
   */
  public static class IMemcacheServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IMemcacheServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IMemcacheServiceFactory iMemcacheServiceFactory) {
      AsyncMemcacheService ams = iMemcacheServiceFactory.getAsyncMemcacheService("yar");
      MemcacheService ms = iMemcacheServiceFactory.getMemcacheService("yar");
      return Collections.emptySet();
    }
  }

  /**
   * Exhaustive use of {@link IMemcacheServiceFactoryProvider}.
   */
  public static class IMemcacheServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IMemcacheServiceFactoryProvider> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      IMemcacheServiceFactoryProvider iMemcacheServiceFactoryProvider
          = new IMemcacheServiceFactoryProvider();
      return Sets.newHashSet(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  public static class GCacheFactoryApiUsage extends ExhaustiveApiUsage<GCacheFactory> {

    int ___apiConstant_EXPIRATION_DELTA;
    int ___apiConstant_EXPIRATION_DELTA_MILLIS;
    int ___apiConstant_EXPIRATION;
    int ___apiConstant_SET_POLICY;
    int ___apiConstant_MEMCACHE_SERVICE;
    int ___apiConstant_NAMESPACE;

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      ___apiConstant_EXPIRATION_DELTA = GCacheFactory.EXPIRATION_DELTA;
      ___apiConstant_EXPIRATION_DELTA_MILLIS = GCacheFactory.EXPIRATION_DELTA_MILLIS;
      ___apiConstant_EXPIRATION = GCacheFactory.EXPIRATION;
      ___apiConstant_SET_POLICY = GCacheFactory.SET_POLICY;
      ___apiConstant_MEMCACHE_SERVICE = GCacheFactory.MEMCACHE_SERVICE;
      ___apiConstant_NAMESPACE = GCacheFactory.NAMESPACE;

      GCacheFactory factory = new GCacheFactory();
      factory.createCache(Maps.newHashMap());
      return Sets.newHashSet(CacheFactory.class, Object.class);
    }
  }

  public static class MemcacheSerializationApiUsage
      extends ExhaustiveApiUsage<MemcacheSerialization> {

    String ___apiConstant_USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY;

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      ___apiConstant_USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY =
          MemcacheSerialization.USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY;

      try {
        MemcacheSerialization.deserialize(new byte[0], 0);
      } catch (ClassNotFoundException e) {
        // fine
      } catch (IOException e) {
        // fine
      }
      try {
        MemcacheSerialization.makePbKey("yam");
      } catch (IOException e) {
        // fine
      }

      try {
        MemcacheSerialization.serialize("yar");
      } catch (IOException e) {
        // fine
      }
      return Sets.<Class<?>>newHashSet(Object.class);
    }
  }

  public static class CasValuesApiUsage extends ExhaustiveApiUsage<MemcacheService.CasValues> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      MemcacheService.IdentifiableValue iValue = new MemcacheService.IdentifiableValue() {
        @Override
        public Object getValue() {
          return "yar";
        }
      };
      MemcacheService.CasValues values = new MemcacheService.CasValues(iValue, "yar");
      values = new MemcacheService.CasValues(iValue, "yar", Expiration.byDeltaSeconds(1));
      values.getExipration();
      values.getNewValue();
      values.getOldValue();
      boolean isEqual = values.equals(values);
      int hashCode = values.hashCode();
      return Sets.<Class<?>>newHashSet(Object.class);
    }
  }

  public static class AsyncMemcacheServiceApiUsage
      extends ExhaustiveApiInterfaceUsage<AsyncMemcacheService> {

    @Override
    protected Set<Class<?>> useApi(AsyncMemcacheService svc) {
      Object obj = new Object();
      Collection<Object> coll = Collections.singleton(obj);
      Future<Void> voidResult = svc.clearAll();
      Future<Boolean> boolResult = svc.contains(obj);
      boolResult = svc.delete(obj);
      boolResult = svc.delete(obj, 23);
      Future<Set<Object>> objSetResult = svc.deleteAll(coll);
      objSetResult = svc.deleteAll(coll, 23);
      Future<Object> objResult = svc.get(obj);
      Future<Map<Object, Object>> objMapResult = svc.getAll(coll);
      Future<MemcacheService.IdentifiableValue> iValueResult = svc.getIdentifiable(obj);
      Future<Map<Object, MemcacheService.IdentifiableValue>>
          iValueMapResult =
          svc.getIdentifiables(coll);
      Future<Stats> statsResult = svc.getStatistics();
      Future<Long> longResult = svc.increment(obj, 23);
      longResult = svc.increment(obj, 23, 23L);
      Future<Map<Object, Long>> longMapResult = svc.incrementAll(coll, 23);
      longMapResult = svc.incrementAll(coll, 23, 23L);
      longMapResult = svc.incrementAll(Maps.<Object, Long>newHashMap());
      longMapResult = svc.incrementAll(Maps.<Object, Long>newHashMap(), 23L);
      voidResult = svc.put(obj, obj);
      voidResult = svc.put(obj, obj, Expiration.byDeltaSeconds(1));
      boolResult =
          svc.put(obj, obj, Expiration.byDeltaSeconds(1), MemcacheService.SetPolicy.SET_ALWAYS);
      voidResult = svc.putAll(Maps.<Object, Object>newHashMap());
      voidResult = svc.putAll(Maps.<Object, Object>newHashMap(), Expiration.byDeltaSeconds(1));
      objSetResult =
          svc.putAll(Maps.<Object, Object>newHashMap(), Expiration.byDeltaSeconds(1),
                     MemcacheService.SetPolicy.SET_ALWAYS);
      objSetResult = svc.putIfUntouched(Maps.<Object, MemcacheService.CasValues>newHashMap());
      objSetResult = svc.putIfUntouched(Maps.<Object, MemcacheService.CasValues>newHashMap(),
                                        Expiration.byDeltaSeconds(1));
      MemcacheService.IdentifiableValue iValue = new MemcacheService.IdentifiableValue() {
        @Override
        public Object getValue() {
          return "yam";
        }
      };
      boolResult = svc.putIfUntouched(obj, iValue, obj);
      boolResult = svc.putIfUntouched(obj, iValue, obj, Expiration.byDeltaSeconds(1));
      return Sets.<Class<?>>newHashSet(BaseMemcacheService.class);
    }
  }

  public static class ValueAndFlagsApiUsage
      extends ExhaustiveApiUsage<MemcacheSerialization.ValueAndFlags> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      try {
        MemcacheSerialization.ValueAndFlags vaf = MemcacheSerialization.serialize("yar");
        MemcacheSerialization.Flag flag = vaf.flags;
        byte[] value = vaf.value;
      } catch (IOException e) {
        // fine
      }
      return Sets.<Class<?>>newHashSet(Object.class);
    }
  }

  public static class ConsistentErrorHandlerApiUsage
      extends ExhaustiveApiInterfaceUsage<ConsistentErrorHandler> {

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    protected Set<Class<?>> useApi(ConsistentErrorHandler handler) {
      handler.handleDeserializationError(new InvalidValueException("yar"));
      handler.handleServiceError(new MemcacheServiceException("yar"));
      return Sets.<Class<?>>newHashSet(ErrorHandler.class);
    }
  }

  @SuppressWarnings("deprecation")
  public static class ErrorHandlerApiUsage extends ExhaustiveApiInterfaceUsage<ErrorHandler> {

    @Override
    @SuppressWarnings("deprecation")
    protected Set<Class<?>> useApi(ErrorHandler handler) {
      handler.handleDeserializationError(new InvalidValueException("boom"));
      handler.handleServiceError(new MemcacheServiceException("boom"));
      return Sets.newHashSet();
    }
  }

  public static class LogAndContinueErrorHandlerApiUsage
      extends ExhaustiveApiUsage<LogAndContinueErrorHandler> {

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public Set<Class<?>> useApi() {
      LogAndContinueErrorHandler handler = new LogAndContinueErrorHandler(Level.ALL);
      handler.handleDeserializationError(new InvalidValueException("boom"));
      handler.handleServiceError(new MemcacheServiceException("boom"));
      return Sets.newHashSet(Object.class, ErrorHandler.class);
    }
  }

  public static class StatsApiUsage extends ExhaustiveApiInterfaceUsage<Stats> {

    @Override
    protected Set<Class<?>> useApi(Stats stats) {
      long longVal = stats.getBytesReturnedForHits();
      longVal = stats.getHitCount();
      longVal = stats.getItemCount();
      int intVal = stats.getMaxTimeWithoutAccess();
      longVal = stats.getMissCount();
      longVal = stats.getTotalItemBytes();
      return Sets.newHashSet();
    }
  }

  public static class MemcacheServiceApiUsage extends ExhaustiveApiInterfaceUsage<MemcacheService> {

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    protected Set<Class<?>> useApi(MemcacheService svc) {
      Object obj = new Object();
      Collection<Object> coll = Collections.singleton(obj);
      svc.clearAll();
      boolean boolResult = svc.contains(obj);
      boolResult = svc.delete(obj);
      boolResult = svc.delete(obj, 23);
      Set<Object> objSetResult = svc.deleteAll(coll);
      objSetResult = svc.deleteAll(coll, 23);
      Object objResult = svc.get(obj);
      Map<Object, Object> objMapResult = svc.getAll(coll);
      MemcacheService.IdentifiableValue iValueResult = svc.getIdentifiable(obj);
      Map<Object, MemcacheService.IdentifiableValue> iValueMapResult = svc.getIdentifiables(coll);
      Stats statsResult = svc.getStatistics();
      Long longResult = svc.increment(obj, 23);
      longResult = svc.increment(obj, 23, 23L);
      Map<Object, Long> longMapResult = svc.incrementAll(coll, 23);
      longMapResult = svc.incrementAll(coll, 23, 23L);
      longMapResult = svc.incrementAll(Maps.<Object, Long>newHashMap());
      longMapResult = svc.incrementAll(Maps.<Object, Long>newHashMap(), 23L);
      svc.put(obj, obj);
      svc.put(obj, obj, Expiration.byDeltaSeconds(1));
      boolResult =
          svc.put(obj, obj, Expiration.byDeltaSeconds(1), MemcacheService.SetPolicy.SET_ALWAYS);
      svc.putAll(Maps.<Object, Object>newHashMap());
      svc.putAll(Maps.<Object, Object>newHashMap(), Expiration.byDeltaSeconds(1));
      objSetResult =
          svc.putAll(Maps.<Object, Object>newHashMap(), Expiration.byDeltaSeconds(1),
                     MemcacheService.SetPolicy.SET_ALWAYS);
      objSetResult = svc.putIfUntouched(Maps.<Object, MemcacheService.CasValues>newHashMap());
      objSetResult = svc.putIfUntouched(Maps.<Object, MemcacheService.CasValues>newHashMap(),
                                        Expiration.byDeltaSeconds(1));
      MemcacheService.IdentifiableValue iValue = new MemcacheService.IdentifiableValue() {
        @Override
        public Object getValue() {
          return "yam";
        }
      };
      boolResult = svc.putIfUntouched(obj, iValue, obj);
      boolResult = svc.putIfUntouched(obj, iValue, obj, Expiration.byDeltaSeconds(1));
      svc.setNamespace("yar");
      return Sets.<Class<?>>newHashSet(BaseMemcacheService.class);
    }
  }

  public static class IdentifiableValueApiUsage
      extends ExhaustiveApiInterfaceUsage<MemcacheService.IdentifiableValue> {

    @Override
    protected Set<Class<?>> useApi(MemcacheService.IdentifiableValue val) {
      Object obj = val.getValue();
      return Sets.newHashSet();
    }
  }

  public static class InvalidValueExceptionApiUsage
      extends ExhaustiveApiUsage<InvalidValueException> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      InvalidValueException ex = new InvalidValueException("boom");
      ex = new InvalidValueException("boom", null);
      return Sets.newHashSet(RuntimeException.class, Exception.class, Throwable.class,
                             Serializable.class, Object.class);
    }
  }

  public static class StrictErrorHandlerApiUsage extends ExhaustiveApiUsage<StrictErrorHandler> {

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public Set<Class<?>> useApi() {
      StrictErrorHandler handler = new StrictErrorHandler();
      try {
        handler.handleDeserializationError(new InvalidValueException("boom"));
      } catch (InvalidValueException e) {
        // fine
      }
      try {
        handler.handleServiceError(new MemcacheServiceException("boom"));
      } catch (MemcacheServiceException e) {
        // fine
      }
      return Sets.newHashSet(ConsistentErrorHandler.class, ErrorHandler.class, Object.class);
    }
  }

  public static class SetPolicyApiUsage extends ExhaustiveApiUsage<MemcacheService.SetPolicy> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      MemcacheService.SetPolicy policy = MemcacheService.SetPolicy.SET_ALWAYS;
      policy = MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT;
      policy = MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT;
      MemcacheService.SetPolicy[] values = MemcacheService.SetPolicy.values();
      policy = MemcacheService.SetPolicy.valueOf("REPLACE_ONLY_IF_PRESENT");
      return Sets.newHashSet(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }

  public static class ExpirationApiUsage extends ExhaustiveApiUsage<Expiration> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      Expiration exp = Expiration.onDate(new Date(0));
      exp = Expiration.byDeltaSeconds(0);
      exp = Expiration.byDeltaMillis(0);
      boolean boolVal = exp.equals(exp);
      long longVal = exp.getMillisecondsValue();
      int intVal = exp.getSecondsValue();
      intVal = exp.hashCode();
      return Sets.<Class<?>>newHashSet(Object.class);
    }
  }

  public static class ErrorHandlersApiUsage extends ExhaustiveApiUsage<ErrorHandlers> {

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public Set<Class<?>> useApi() {
      StrictErrorHandler strict = ErrorHandlers.getStrict();
      ErrorHandler defaultHandler = ErrorHandlers.getDefault();
      LogAndContinueErrorHandler lacHandler = ErrorHandlers.getConsistentLogAndContinue(Level.ALL);
      lacHandler = ErrorHandlers.getLogAndContinue(Level.ALL);
      return Sets.<Class<?>>newHashSet(Object.class);
    }
  }

  public static class GCacheExceptionApiUsage extends ExhaustiveApiUsage<GCacheException> {


    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      GCacheException ex = new GCacheException("yar");
      Throwable throwable = new Throwable();
      ex = new GCacheException("yar", throwable);
      return Sets.newHashSet(Object.class, Exception.class, RuntimeException.class, Throwable.class,
                             Serializable.class);
    }
  }

  public static class GCacheApiUsage extends ExhaustiveApiUsage<GCache> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalMemcacheServiceTestConfig());
      helper.setUp();
      try {
        return useApiInternal();
      } finally {
        helper.tearDown();
      }
    }

    @SuppressWarnings({"unchecked"})
    private Set<Class<?>> useApiInternal() {
      GCache cache = new GCache(new HashMap<>());
      CacheListener listener =
          new CacheListener() {
            @Override
            public void onClear() {}

            @Override
            public void onEvict(Object o) {}

            @Override
            public void onLoad(Object o) {}

            @Override
            public void onPut(Object o) {}

            @Override
            public void onRmove(Object o) {}
          };
      cache.addListener(listener);
      cache.evict();
      cache.clear();
      boolean boolResult = cache.containsKey("hi");
      try {
        boolResult = cache.containsValue("hi");
      } catch (UnsupportedOperationException e) {
        // fine
      }
      try {
        Set<?> set = cache.entrySet();
      } catch (UnsupportedOperationException e) {
        // fine
      }
      Object objResult = cache.get("hi");
      Collection<?> coll = Collections.emptyList();
      Map<?, ?> map = cache.getAll(coll);
      CacheEntry entry = cache.getCacheEntry("yar");
      CacheStatistics stats = cache.getCacheStatistics();
      boolResult = cache.isEmpty();
      try {
        Set<?> set = cache.keySet();
      } catch (UnsupportedOperationException e) {
        // fine
      }
      try {
        cache.load("yar");
      } catch (UnsupportedOperationException e) {
        // fine
      }
      try {
        cache.loadAll(coll);
      } catch (UnsupportedOperationException e) {
        // fine
      }
      objResult = cache.peek("yar");
      cache.put("yar", "yam");
      cache.putAll(map);
      cache.remove("yar");
      cache.removeListener(listener);
      int intResult = cache.size();
      try {
        coll = cache.values();
      } catch (UnsupportedOperationException e) {
        // fine
      }
      return Sets.newHashSet(Object.class, Cache.class, Map.class);
    }
  }

  public static class GCacheEntryApiUsage extends ExhaustiveApiUsage<GCacheEntry> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalMemcacheServiceTestConfig());
      helper.setUp();
      try {
        return useApiInternal();
      } finally {
        helper.tearDown();
      }
    }

    @SuppressWarnings({"unchecked"})
    private Set<Class<?>> useApiInternal() {
      GCache cache = new GCache(new HashMap<>());
      cache.put("this", "that");
      // oops, GCacheEntry probably should not be part of the public api
      GCacheEntry entry = (GCacheEntry) cache.getCacheEntry("this");
      boolean unusedEquals = entry.equals(entry);
      long longVal;
      try {
        longVal = entry.getCost();
      } catch (UnsupportedOperationException e) {
        // fine
      }
      try {
        longVal = entry.getCreationTime();
      } catch (UnsupportedOperationException e) {
        // fine
      }
      try {
        longVal = entry.getExpirationTime();
      } catch (UnsupportedOperationException e) {
        // fine
      }

      try {
        longVal = entry.getHits();
      } catch (UnsupportedOperationException e) {
        // fine
      }

      try {
        longVal = entry.getLastAccessTime();
      } catch (UnsupportedOperationException e) {
        // fine
      }

      try {
        longVal = entry.getLastUpdateTime();
      } catch (UnsupportedOperationException e) {
        // fine
      }

      try {
        longVal = entry.getVersion();
      } catch (UnsupportedOperationException e) {
        // fine
      }
      Object objVal = entry.getKey();
      objVal = entry.getValue();
      int hashCode = entry.hashCode();
      entry.setValue("yar");
      boolean boolValue = entry.isValid();
      return Sets.newHashSet(Object.class, CacheEntry.class, Map.Entry.class);
    }
  }

  public static class FlagApiUsage extends ExhaustiveApiUsage<MemcacheSerialization.Flag> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      MemcacheSerialization.Flag flag = MemcacheSerialization.Flag.valueOf("BYTE");
      MemcacheSerialization.Flag[] values = MemcacheSerialization.Flag.values();
      flag = MemcacheSerialization.Flag.fromInt(0);
      flag = MemcacheSerialization.Flag.BOOLEAN;
      flag = MemcacheSerialization.Flag.BYTE;
      flag = MemcacheSerialization.Flag.BYTES;
      flag = MemcacheSerialization.Flag.INTEGER;
      flag = MemcacheSerialization.Flag.LONG;
      flag = MemcacheSerialization.Flag.OBJECT;
      flag = MemcacheSerialization.Flag.SHORT;
      flag = MemcacheSerialization.Flag.UTF8;
      return Sets.newHashSet(Enum.class, Object.class, Serializable.class, Comparable.class);
    }
  }

  public static class MemcacheServiceFactoryApiUsage extends
      ExhaustiveApiUsage<MemcacheServiceFactory> {

    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      AsyncMemcacheService ams = MemcacheServiceFactory.getAsyncMemcacheService();
      ams = MemcacheServiceFactory.getAsyncMemcacheService("yar");
      MemcacheService ms = MemcacheServiceFactory.getMemcacheService();
      ms = MemcacheServiceFactory.getMemcacheService("yar");
      return Sets.<Class<?>>newHashSet(Object.class);
    }
  }

  public static class MemcacheServiceExceptionApiUsage
      extends ExhaustiveApiUsage<MemcacheServiceException> {
    @Override
    @SuppressWarnings({"unchecked"})
    public Set<Class<?>> useApi() {
      MemcacheServiceException mwe = new MemcacheServiceException("yar", new Throwable());
      mwe = new MemcacheServiceException("yar");
      return Sets.newHashSet(RuntimeException.class, Exception.class, Throwable.class,
                             Serializable.class, Object.class);
    }
  }

  public static class ConsistentLogAndContinueErrorHandlerApiUsage extends
      ExhaustiveApiUsage<ConsistentLogAndContinueErrorHandler> {

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public Set<Class<?>> useApi() {
      ConsistentLogAndContinueErrorHandler handler =
          new ConsistentLogAndContinueErrorHandler(Level.ALL);
      return Sets.newHashSet(Object.class, ErrorHandler.class, LogAndContinueErrorHandler.class,
          ConsistentErrorHandler.class);
    }
  }
}
