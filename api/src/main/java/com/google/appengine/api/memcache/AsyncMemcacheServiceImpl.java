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

package com.google.appengine.api.memcache;

import static com.google.appengine.api.memcache.MemcacheServiceApiHelper.makeAsyncCall;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.memcache.MemcacheSerialization.ValueAndFlags;
import com.google.appengine.api.memcache.MemcacheService.CasValues;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;
import com.google.appengine.api.memcache.MemcacheService.ItemForPeek;
import com.google.appengine.api.memcache.MemcacheService.SetPolicy;
import com.google.appengine.api.memcache.MemcacheServiceApiHelper.Provider;
import com.google.appengine.api.memcache.MemcacheServiceApiHelper.RpcResponseHandler;
import com.google.appengine.api.memcache.MemcacheServiceApiHelper.Transformer;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse.DeleteStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest.Direction;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse.IncrementStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheServiceError;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse.SetStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MergedNamespaceStats;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Java bindings for the AsyncMemcache service.
 *
 */
class AsyncMemcacheServiceImpl extends BaseMemcacheServiceImpl implements AsyncMemcacheService {
  private static final ErrorHandler DO_NOTHING_ERROR_HANDLER =
      new ErrorHandler() {
        @Override
        public void handleDeserializationError(InvalidValueException ivx) {}

        @Override
        public void handleServiceError(MemcacheServiceException ex) {}
      };
  private static final ErrorHandler DO_NOTHING_CONSISTENT_ERROR_HANDLER =
      new ConsistentErrorHandler() {
        @Override
        public void handleDeserializationError(InvalidValueException ivx) {}

        @Override
        public void handleServiceError(MemcacheServiceException ex) {}
      };

  static class StatsImpl implements Stats {
    private final long hits;
    private final long misses;
    private final long bytesFetched;
    private final long items;
    private final long bytesStored;
    private final int maxCachedTime;

    StatsImpl(MergedNamespaceStats stats) {
      if (stats != null) {
        hits = stats.getHits();
        misses = stats.getMisses();
        bytesFetched = stats.getByteHits();
        items = stats.getItems();
        bytesStored = stats.getBytes();
        maxCachedTime = stats.getOldestItemAge();
      } else {
        hits = misses = bytesFetched = items = bytesStored = 0;
        maxCachedTime = 0;
      }
    }

    @Override
    public long getHitCount() {
      return hits;
    }

    @Override
    public long getMissCount() {
      return misses;
    }

    @Override
    public long getBytesReturnedForHits() {
      return bytesFetched;
    }

    @Override
    public long getItemCount() {
      return items;
    }

    @Override
    public long getTotalItemBytes() {
      return bytesStored;
    }

    @Override
    public int getMaxTimeWithoutAccess() {
      return maxCachedTime;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Hits: ").append(hits).append('\n');
      builder.append("Misses: ").append(misses).append('\n');
      builder.append("Bytes Fetched: ").append(bytesFetched).append('\n');
      builder.append("Bytes Stored: ").append(bytesStored).append('\n');
      builder.append("Items: ").append(items).append('\n');
      builder.append("Max Cached Time: ").append(maxCachedTime).append('\n');
      return builder.toString();
    }
  }
 
  //@VisibleForTesting
  static final class ItemForPeekImpl implements MemcacheService.ItemForPeek {
    private final Object value;
    private final Long expirationTimeSec;
    private final Long lastAccessTimeSec;
    private final Long deleteLockTimeSec;
    ItemForPeekImpl(Object value, Long expirationTimeSec, Long lastAccessTimeSec, Long deleteLockTimeSec ) {
      this.value = value;
      this.expirationTimeSec = expirationTimeSec;
      this.lastAccessTimeSec = lastAccessTimeSec;
      this.deleteLockTimeSec = deleteLockTimeSec;
    }

    @Override
    public Object getValue() {
      return value;
    }

    @Override
    public Long getExpirationTimeSec() {
      return expirationTimeSec;
    }

    @Override
    public Long getLastAccessTimeSec() {
      return lastAccessTimeSec;
    }

    @Override
    public Long getDeleteLockTimeSec() {
      return deleteLockTimeSec;
    }

  }
  
  //@VisibleForTesting
  static final class IdentifiableValueImpl implements IdentifiableValue {
    private final Object value;
    private final long casId;

    IdentifiableValueImpl(Object value, long casId) {
      this.value = value;
      this.casId = casId;
    }

    @Override
    public Object getValue() {
      return value;
    }

    //@VisibleForTesting
    long getCasId() {
      return casId;
    }

    @Override
    public boolean equals(Object otherObj) {
      if (this == otherObj) {
        return true;
      }
      if ((otherObj == null) || (getClass() != otherObj.getClass())) {
        return false;
      }
      IdentifiableValueImpl otherIdentifiableValue = (IdentifiableValueImpl) otherObj;
      return Objects.equals(value, otherIdentifiableValue.value) &&
          (casId == otherIdentifiableValue.casId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, casId);
    }
  }

  private static class DefaultValueProviders {

    @SuppressWarnings("rawtypes")
    private static final Provider NULL_PROVIDER = new Provider() {
          @Override public Object get() {
            return null;
          }
        };

    private static final Provider<Boolean> FALSE_PROVIDER = new Provider<Boolean>() {
          @Override public Boolean get() {
            return Boolean.FALSE;
          }
        };

    @SuppressWarnings("rawtypes")
    private static final Provider SET_PROVIDER =
        new Provider<Set<?>>() {
          @Override
          public Set<?> get() {
            return new HashSet<>(0, 1);
          }
        };

    @SuppressWarnings("rawtypes")
    private static final Provider MAP_PROVIDER =
        new Provider<Map<?, ?>>() {
          @Override
          public Map<?, ?> get() {
            return new HashMap<>(0, 1);
          }
        };

    private static final Provider<Stats> STATS_PROVIDER = new Provider<Stats>() {
          final Stats emptyStats =  new AsyncMemcacheServiceImpl.StatsImpl(null);

          @Override public Stats get() {
            return emptyStats;
          }
        };

    static Provider<Boolean> falseValue() {
      return FALSE_PROVIDER;
    }

    @SuppressWarnings("unchecked")
    static <T> Provider<T> nullValue() {
      return NULL_PROVIDER;
    }

    @SuppressWarnings("unchecked")
    static <T> Provider<Set<T>> emptySet() {
      return SET_PROVIDER;
    }

    @SuppressWarnings("unchecked")
    static <K, V> Provider<Map<K, V>> emptyMap() {
      return MAP_PROVIDER;
    }

    static Provider<Stats> emptyStats() {
      return STATS_PROVIDER;
    }
  }

  private static class VoidFutureWrapper<K> extends FutureWrapper<K, Void> {

    private VoidFutureWrapper(Future<K> parent) {
      super(parent);
    }

    private static <K> Future<Void> wrap(Future<K> parent) {
      return new VoidFutureWrapper<K>(parent);
    }

    @Override
    protected Void wrap(K value) {
      return null;
    }

    @Override
    protected Throwable convertException(Throwable cause) {
      return cause;
    }
  }

  private static final class KeyValuePair<K, V> {
    private final K key;
    private final V value;

    private KeyValuePair(K key, V value) {
      this.key = key;
      this.value = value;
    }

    static <K, V> KeyValuePair<K, V> of(K key, V value) {
      return new KeyValuePair<K, V>(key, value);
    }
  }

  AsyncMemcacheServiceImpl(String namespace) {
    super(namespace);
  }

  static <T, V> Map<T, V> makeMap(Collection<T> keys, V value) {
    Map<T, V> map = new LinkedHashMap<T, V>(keys.size(), 1);
    for (T key : keys) {
      map.put(key, value);
    }
    return map;
  }

  private static ByteString makePbKey(Object key) {
    try {
      return ByteString.copyFrom(MemcacheSerialization.makePbKey(key));
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as a key: '" + key + "'", ex);
    }
  }

  private static ValueAndFlags serializeValue(Object value) {
    try {
      return MemcacheSerialization.serialize(value);
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot use as value: '" + value + "'", ex);
    }
  }

  private Object deserializeItem(Object key, MemcacheGetResponse.Item item) {
    try {
      return MemcacheSerialization.deserialize(item.getValue().toByteArray(), item.getFlags());
    } catch (ClassNotFoundException ex) {
      getErrorHandler().handleDeserializationError(
          new InvalidValueException("Can't find class for value of key '" + key + "'", ex));
    } catch (IOException ex) {
      getErrorHandler().handleDeserializationError(
          new InvalidValueException("Failed to parse the value for '" + key + "'", ex));
    }
    return null;
  }

  private <M extends Message, T> RpcResponseHandler<M, T> createRpcResponseHandler(
      M response, String errorText, Transformer<M, T> responseTransformer) {
    return new RpcResponseHandler<M, T>(
        response, errorText, responseTransformer, getErrorHandler());
  }

  private <T> RpcResponseHandlerForPut<T> createRpcResponseHandlerForPut(
      Iterable<MemcacheSetRequest.Item.Builder> itemBuilders, String namespace,
      MemcacheSetResponse response, String errorText,
      Transformer<MemcacheSetResponse, T> responseTransformer) {
    return new RpcResponseHandlerForPut<T>(
        itemBuilders, namespace, response, errorText, responseTransformer, getErrorHandler());
  }

  private class RpcResponseHandlerForPut<T> extends RpcResponseHandler<MemcacheSetResponse, T> {

    /**
     * We remember what was sent to the backend, so that if we get an error response we can test to
     * see if the error was caused by the API being given invalid values.
     */
    private final Iterable<MemcacheSetRequest.Item.Builder> itemsSentToBackend;
    private final String namespace;

    RpcResponseHandlerForPut(Iterable<MemcacheSetRequest.Item.Builder> itemsSentToBackend,
        String namespace,
        MemcacheSetResponse response, String errorText,
        Transformer<MemcacheSetResponse, T> responseTransfomer,
        ErrorHandler errorHandler) {
      super(response, errorText, responseTransfomer, errorHandler);
      this.itemsSentToBackend = itemsSentToBackend;
      this.namespace = namespace;
    }

    /**
     * When we get an error from the backend we check to see if it could possibly have been caused
     * by an invalid key or value being passed into the API from the app. If so we test the original
     * key and value passed in and if we find them to be invalid we throw an exception that is more
     * informative to the app writer than the default exception.
     */
    @Override
    void handleApiProxyException(Throwable cause) throws Exception {
      ErrorHandler errorHandler = getErrorHandler();
      if (cause instanceof ApiProxy.ApplicationException) {
        ApiProxy.ApplicationException applicationException = (ApiProxy.ApplicationException) cause;
        if (applicationException.getApplicationError()
            == MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE) {
          errorHandler =
              handleApiProxyException(applicationException.getErrorDetail(), errorHandler);
        }
      } else if (cause instanceof MemcacheServiceException){
        errorHandler = handleApiProxyException(cause.getMessage(), errorHandler);
      }
      super.handleApiProxyException(cause, errorHandler);
    }

    private ErrorHandler handleApiProxyException(String msg,
        ErrorHandler errorHandler) throws MemcacheServiceException {
      boolean errorHandlerCalled = false;
      for (MemcacheSetRequest.Item.Builder itemBuilder : itemsSentToBackend) {
        ByteString pbKey = itemBuilder.getKey();
        ByteString value = itemBuilder.getValue();
        // Do cheaper test first:
        if (value.size() + pbKey.size() > ITEM_SIZE_LIMIT) {
          errorHandlerCalled |=
              maybeThrow(errorHandler, "Key+value is bigger than maximum allowed. " + msg);
        }
        // This does a slightly expensive scanning of a key for null, but it should happen
        // rarely:
        if (Bytes.contains(pbKey.toByteArray(), (byte) 0)) {
          errorHandlerCalled |= maybeThrow(errorHandler, "Key contains embedded null byte. " + msg);
        }
        // Finally, test for the unlikely case of a bad namespace:
        if (namespace != null) {
          try {
            NamespaceManager.validateNamespace(namespace);
          } catch (IllegalArgumentException ex) {
            // Can only happen if the app writer uses deprecated or undocumented calls to bypass
            // the outgoing checks.
            errorHandlerCalled |= maybeThrow(errorHandler, ex.toString());
          }
        }
      }
      if (errorHandlerCalled) {
        // Prevent super.handleApiProxyException from handling the error twice. Since it checks
        // which interface the handler inherits, we must do the same.
        if (errorHandler instanceof ConsistentErrorHandler) {
          return DO_NOTHING_CONSISTENT_ERROR_HANDLER;
        } else {
          return DO_NOTHING_ERROR_HANDLER;
        }
      } else {
        return errorHandler;
      }
    }

    /**
     * Will throw exception or just log it, depending on what error handler is set.
     *
     * @return true if handleServiceError was called.
     */
    private boolean maybeThrow(ErrorHandler errorHandler, String msg) {
      // TODO replace MemcacheServiceException with new MemcachePutException
      errorHandler.handleServiceError(new MemcacheServiceException(msg));
      return true;
    }
  }

  /**
   * Matches limit documented in
   * https://developers.google.com/appengine/docs/python/memcache/#Python_Limits
   *
   * <p>TODO This is too conservative; need to revisit. The docs are conflating item size limit with
   * total cache size accounting.
   */
  static final int ITEM_SIZE_LIMIT = 1024 * 1024 - 96;

  @Override
  public Future<Boolean> contains(Object key) {
    return doGet(
        key,
        false, // forCas
        false, // forPeek
        "Memcache contains: exception testing contains (" + key + ")",
        new Transformer<MemcacheGetResponse, Boolean>() {
          @Override
          public Boolean transform(MemcacheGetResponse response) {
            return response.getItemCount() > 0;
          }
        },
        DefaultValueProviders.falseValue());
  }

  private <T> Future<T> doGet(Object key, boolean forCas, boolean forPeek, String errorText,
      final Transformer<MemcacheGetResponse, T> responseTransfomer, Provider<T> defaultValue) {
    MemcacheGetRequest.Builder requestBuilder = MemcacheGetRequest.newBuilder();
    requestBuilder.addKey(makePbKey(key));
    requestBuilder.setNameSpace(getEffectiveNamespace());
    if (forCas) {
      requestBuilder.setForCas(true);
    }
    if (forPeek) {
      requestBuilder.setForPeek(true);
    }
    return makeAsyncCall("Get", requestBuilder.build(),
        createRpcResponseHandler(MemcacheGetResponse.getDefaultInstance(), errorText,
            responseTransfomer), defaultValue);
  }

  @Override
  public Future<Object> get(final Object key) {
    return doGet(
        key,
        false, // forCas
        false, // forPeek
        "Memcache get: exception getting 1 key (" + key + ")",
        new Transformer<MemcacheGetResponse, Object>() {
          @Override
          public Object transform(MemcacheGetResponse response) {
            return response.getItemCount() == 0 ? null : deserializeItem(key, response.getItem(0));
          }
        },
        DefaultValueProviders.nullValue());
  }

  @Override
  public Future<IdentifiableValue> getIdentifiable(final Object key) {
    return doGet(
        key,
        true,  // forCas
        false, // forPeek
        "Memcache getIdentifiable: exception getting 1 key (" + key + ")",
        new IdentifiableTransformer(key),
        DefaultValueProviders.<IdentifiableValue>nullValue());
  }

  private class IdentifiableTransformer
      implements Transformer<MemcacheGetResponse, IdentifiableValue> {
    private final Object key;

    IdentifiableTransformer(Object key) {
      this.key = key;
    }

    @Override
    public IdentifiableValue transform(MemcacheGetResponse response) {
      if (response.getItemCount() == 0) {
        return null;
      }
      MemcacheGetResponse.Item item = response.getItem(0);
      return new IdentifiableValueImpl(deserializeItem(key, item), item.getCasId());
    }
  }

  @Override
  public <K> Future<Map<K, IdentifiableValue>> getIdentifiables(Collection<K> keys) {
    return doGetAll(
        keys,
        true,  // forCas
        false, // forKeep
        "Memcache getIdentifiables: exception getting multiple keys",
        new Transformer<KeyValuePair<K, MemcacheGetResponse.Item>, IdentifiableValue>() {
          @Override
          public IdentifiableValue transform(KeyValuePair<K, MemcacheGetResponse.Item> pair) {
            MemcacheGetResponse.Item item = pair.value;
            Object value = deserializeItem(pair.key, item);
            return new IdentifiableValueImpl(value, item.getCasId());
          }
        },
        DefaultValueProviders.<K, IdentifiableValue>emptyMap());
  }

  
  @Override
  public Future<ItemForPeek> getItemForPeek(final Object key) {
    return doGet(
        key,
        false, // forCas
        true,  // forPeek
        "Memcache getItemForPeek: exception getting 1 key (" + key + ")",
        new ItemForPeekTransformer(key),
        DefaultValueProviders.<ItemForPeek>nullValue());
  }

  private class ItemForPeekTransformer
      implements Transformer<MemcacheGetResponse, ItemForPeek> {
    private final Object key;

    ItemForPeekTransformer(Object key) {
      this.key = key;
    }

    @Override
    public ItemForPeek transform(MemcacheGetResponse response) {
      if (response.getItemCount() == 0) {
        return null;
      }
      MemcacheGetResponse.Item item = response.getItem(0);
      Object value;
      if (item.hasIsDeleteLocked() && item.getIsDeleteLocked()) {
        value = null;
      } else {
        value = deserializeItem(key, item);
      }

      return new ItemForPeekImpl(
          value,
          item.hasTimestamps() ? item.getTimestamps().getExpirationTimeSec() : null,
          item.hasTimestamps() ? item.getTimestamps().getLastAccessTimeSec() : null,
          item.hasTimestamps() ? item.getTimestamps().getDeleteLockTimeSec() : null);
    }
  }

  @Override
  public <K> Future<Map<K, ItemForPeek>> getItemsForPeek(Collection<K> keys) {
    return doGetAll(
        keys,
        false, // forCas
        true, // forPeek
        "Memcache getItemsForPeek: exception getting multiple keys",
        new Transformer<KeyValuePair<K, MemcacheGetResponse.Item>, MemcacheService.ItemForPeek>() {
          @Override
          public MemcacheService.ItemForPeek transform(
              KeyValuePair<K, MemcacheGetResponse.Item> pair) {
            MemcacheGetResponse.Item item = pair.value;
            Object value;
            if (item.hasIsDeleteLocked() && item.getIsDeleteLocked()) {
              value = null;
            } else {
              value = deserializeItem(pair.key, item);
            }

            return new ItemForPeekImpl(
                value,
                item.hasTimestamps() ? item.getTimestamps().getExpirationTimeSec() : null,
                item.hasTimestamps() ? item.getTimestamps().getLastAccessTimeSec() : null,
                item.hasTimestamps() ? item.getTimestamps().getDeleteLockTimeSec() : null);
          }
        },
        DefaultValueProviders.<K, MemcacheService.ItemForPeek>emptyMap());
  }

  @Override
  public <K> Future<Map<K, Object>> getAll(Collection<K> keys) {
    return doGetAll(
        keys,
        false, // forCas
        false, // forPeek
        "Memcache getAll: exception getting multiple keys",
        new Transformer<KeyValuePair<K, MemcacheGetResponse.Item>, Object>() {
          @Override
          public Object transform(KeyValuePair<K, MemcacheGetResponse.Item> pair) {
            return deserializeItem(pair.key, pair.value);
          }
        },
        DefaultValueProviders.<K, Object>emptyMap());
  }

  private <K, V> Future<Map<K, V>> doGetAll(Collection<K> keys, boolean forCas, boolean forPeek,
      String errorText,
      Transformer<KeyValuePair<K, MemcacheGetResponse.Item>, V> responseTransformer,
      Provider<Map<K, V>> defaultValue) {
    MemcacheGetRequest.Builder requestBuilder = MemcacheGetRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());
    final Map<ByteString, K> byteStringToKey = new HashMap<ByteString, K>(keys.size(), 1);
    for (K key : keys) {
      ByteString pbKey = makePbKey(key);
      byteStringToKey.put(pbKey, key);
      requestBuilder.addKey(pbKey);
    }
    if (forCas) {
      requestBuilder.setForCas(forCas);
    }
    if (forPeek) {
      requestBuilder.setForPeek(forPeek);
    }
    Transformer<MemcacheGetResponse, Map<K, V>> rpcResponseTransformer =
        new GetAllRpcResponseTransformer<>(byteStringToKey, responseTransformer);
    return makeAsyncCall(
        "Get",
        requestBuilder.build(),
        createRpcResponseHandler(
            MemcacheGetResponse.getDefaultInstance(), errorText, rpcResponseTransformer),
        defaultValue);
  }

  private static class GetAllRpcResponseTransformer<K, V>
      implements Transformer<MemcacheGetResponse, Map<K, V>> {
    private final Map<ByteString, K> byteStringToKey;
    private final Transformer<KeyValuePair<K, MemcacheGetResponse.Item>, V> responseTransformer;

    GetAllRpcResponseTransformer(
        Map<ByteString, K> byteStringToKey,
        Transformer<KeyValuePair<K, MemcacheGetResponse.Item>, V> responseTransformer) {
      this.byteStringToKey = byteStringToKey;
      this.responseTransformer = responseTransformer;
    }

    @Override
    public Map<K, V> transform(MemcacheGetResponse response) {
      Map<K, V> result = new HashMap<K, V>();
      for (MemcacheGetResponse.Item item : response.getItemList()) {
        K key = byteStringToKey.get(item.getKey());
        V obj = responseTransformer.transform(KeyValuePair.of(key, item));
        result.put(key, obj);
      }
      return result;
    }
  }

  /** Use this to make sure we don't write arbitrarily big log messages. */
  private static final int MAX_LOGGED_VALUE_SIZE = 100;

  /**
   * Represents the overhead to an item as it is stored in memcacheg
   * if it has a value bigger than 65535 bytes and all the optional fields are set.
   * This number was determined by fiddling with cacheserving/memcacheg/server:item_test
   * and analyzing CalculateItemSize from cacheserving/memcacheg/server/item.cc.
   *
   * <p>The overhead can be between 61 and 73 bytes depending on whether optional
   * fields (flag, expiration and CAS) are set or not, adding 4 bytes for each of
   * them.
   */
  private static final int MEMCACHEG_OVERHEAD = 73;
  private static final int ONE_MEGABYTE = 1024 * 1024;
  public static final int MAX_ITEM_SIZE = ONE_MEGABYTE - MEMCACHEG_OVERHEAD;

  /**
   * Note: non-null oldValue implies Compare-and-Swap operation.
   */
  private Future<Boolean> doPut(Object key, IdentifiableValue oldValue, Object value,
      Expiration expires, MemcacheSetRequest.SetPolicy policy) {
    MemcacheSetRequest.Builder requestBuilder = MemcacheSetRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());
    MemcacheSetRequest.Item.Builder itemBuilder = MemcacheSetRequest.Item.newBuilder();
    ValueAndFlags vaf = serializeValue(value);
    itemBuilder.setValue(ByteString.copyFrom(vaf.value));
    itemBuilder.setFlags(vaf.flags.ordinal());
    itemBuilder.setKey(makePbKey(key));
    itemBuilder.setExpirationTime(expires == null ? 0 : expires.getSecondsValue());
    itemBuilder.setSetPolicy(policy);
    if (policy == MemcacheSetRequest.SetPolicy.CAS) {
      if (oldValue == null) {
        throw new IllegalArgumentException("oldValue must not be null.");
      }
      if (!(oldValue instanceof IdentifiableValueImpl)) {
        throw new IllegalArgumentException(
            "oldValue is an instance of an unapproved IdentifiableValue implementation.  " +
            "Perhaps you implemented your own version of IdentifiableValue?  " +
            "If so, don't do this.");
      }
      itemBuilder.setCasId(((IdentifiableValueImpl) oldValue).getCasId());
    }
    final int itemSize = itemBuilder.getKey().size() + itemBuilder.getValue().size();
    requestBuilder.addItem(itemBuilder);

    // When creating string for logging truncate with ellipsis if necessary.
    String valueAsString =
        Ascii.truncate(String.valueOf(value), MAX_LOGGED_VALUE_SIZE, "...");
    return makeAsyncCall(
        "Set",
        requestBuilder.build(),
        createRpcResponseHandlerForPut(
            Arrays.asList(itemBuilder),
            requestBuilder.getNameSpace(),
            MemcacheSetResponse.getDefaultInstance(),
            String.format("Memcache put: exception setting 1 key (%s) to '%s'", key, valueAsString),
            new PutResponseTransformer(key, itemSize)),
        DefaultValueProviders.falseValue());
  }

  private static class PutResponseTransformer implements Transformer<MemcacheSetResponse, Boolean> {
    private final Object key;
    private final int itemSize;

    PutResponseTransformer(Object key, int itemSize) {
      this.key = key;
      this.itemSize = itemSize;
    }

    @Override public Boolean transform(MemcacheSetResponse response) {
      if (response.getSetStatusCount() != 1) {
        throw new MemcacheServiceException("Memcache put: Set one item, got "
            + response.getSetStatusCount() + " response statuses");
      }
      SetStatusCode status = response.getSetStatus(0);
      if (status == SetStatusCode.ERROR) {
        if (itemSize > MAX_ITEM_SIZE) {
          throw new MemcacheServiceException(
              String.format("Memcache put: Item may not be more than %d bytes in length; "
                  + "received %d bytes.", MAX_ITEM_SIZE, itemSize));
        }
        throw new MemcacheServiceException(
            "Memcache put: Error setting single item (" + key + ")");
      }
      return status == SetStatusCode.STORED;
    }
  }

  private static MemcacheSetRequest.SetPolicy convertSetPolicyToPb(SetPolicy policy) {
    switch (policy) {
      case SET_ALWAYS:
        return MemcacheSetRequest.SetPolicy.SET;
      case ADD_ONLY_IF_NOT_PRESENT:
        return MemcacheSetRequest.SetPolicy.ADD;
      case REPLACE_ONLY_IF_PRESENT:
        return MemcacheSetRequest.SetPolicy.REPLACE;
    }
    throw new IllegalArgumentException("Unknown policy: " + policy);
  }

  @Override
  public Future<Boolean> put(Object key, Object value, Expiration expires, SetPolicy policy) {
    return doPut(key, null, value, expires, convertSetPolicyToPb(policy));
  }

  @Override
  public Future<Void> put(Object key, Object value, Expiration expires) {
    return VoidFutureWrapper.wrap(
        doPut(key, null, value, expires, MemcacheSetRequest.SetPolicy.SET));
  }

  @Override
  public Future<Void> put(Object key, Object value) {
    return VoidFutureWrapper.wrap(
        doPut(key, null, value, null, MemcacheSetRequest.SetPolicy.SET));
  }

  @Override
  public Future<Boolean> putIfUntouched(Object key, IdentifiableValue oldValue,
      Object newValue, Expiration expires) {
    return doPut(key, oldValue, newValue, expires, MemcacheSetRequest.SetPolicy.CAS);
  }

  @Override
  public Future<Boolean> putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue) {
    return doPut(key, oldValue, newValue, null, MemcacheSetRequest.SetPolicy.CAS);
  }

  @Override
  public <T> Future<Set<T>> putIfUntouched(Map<T, CasValues> values) {
    return doPutAll(values, null, MemcacheSetRequest.SetPolicy.CAS, "putIfUntouched");
  }

  @Override
  public <T> Future<Set<T>> putIfUntouched(Map<T, CasValues> values, Expiration expiration) {
    return doPutAll(values, expiration, MemcacheSetRequest.SetPolicy.CAS, "putIfUntouched");
  }

  private <T> Future<Set<T>> doPutAll(Map<T, ?> values, Expiration expires,
      MemcacheSetRequest.SetPolicy policy, String operation) {
    MemcacheSetRequest.Builder requestBuilder = MemcacheSetRequest.newBuilder();
    requestBuilder.setNameSpace(getEffectiveNamespace());

    List<T> requestedKeys = new ArrayList<T>(values.size());
    Set<Integer> oversized = new HashSet<>();
    int itemIndex = 0;

    for (Map.Entry<T, ?> entry : values.entrySet()) {
      MemcacheSetRequest.Item.Builder itemBuilder = MemcacheSetRequest.Item.newBuilder();
      requestedKeys.add(entry.getKey());
      itemBuilder.setKey(makePbKey(entry.getKey()));
      ValueAndFlags vaf;
      if (policy == MemcacheSetRequest.SetPolicy.CAS) {
        CasValues value = (CasValues) entry.getValue();
        if (value == null) {
          throw new IllegalArgumentException(entry.getKey() + " has a null for CasValues");
        }
        vaf = serializeValue(value.getNewValue());
        if (!(value.getOldValue() instanceof IdentifiableValueImpl)) {
          throw new IllegalArgumentException(
            entry.getKey() + " CasValues has an oldValue instance of an unapproved " +
            "IdentifiableValue implementation.  Perhaps you implemented your own " +
            "version of IdentifiableValue?  If so, don't do this.");
        }
        itemBuilder.setCasId(((IdentifiableValueImpl) value.getOldValue()).getCasId());
        if (value.getExipration() != null) {
          itemBuilder.setExpirationTime(value.getExipration().getSecondsValue());
        } else {
          itemBuilder.setExpirationTime(expires == null ? 0 : expires.getSecondsValue());
        }
      } else {
        vaf = serializeValue(entry.getValue());
        itemBuilder.setExpirationTime(expires == null ? 0 : expires.getSecondsValue());
      }
      itemBuilder.setValue(ByteString.copyFrom(vaf.value));
      itemBuilder.setFlags(vaf.flags.ordinal());
      itemBuilder.setSetPolicy(policy);
      requestBuilder.addItem(itemBuilder);

      int itemSize = itemBuilder.getKey().size() + itemBuilder.getValue().size();
      if (itemSize > MAX_ITEM_SIZE) {
        oversized.add(itemIndex);
      }
      itemIndex++;
    }

    return makeAsyncCall(
        "Set",
        requestBuilder.build(),
        createRpcResponseHandlerForPut(
            requestBuilder.getItemBuilderList(),
            requestBuilder.getNameSpace(),
            MemcacheSetResponse.getDefaultInstance(),
            "Memcache " + operation + ": Unknown exception setting " + values.size() + " keys",
            new PutAllResponseTransformer<>(requestedKeys, oversized)),
        DefaultValueProviders.<T>emptySet());
  }

  private static class PutAllResponseTransformer<T>
      implements Transformer<MemcacheSetResponse, Set<T>> {
    private final List<T> requestedKeys;
    private final Set<Integer> oversized;

    PutAllResponseTransformer(List<T> requestedKeys, Set<Integer> oversized) {
      this.requestedKeys = requestedKeys;
      this.oversized = oversized;
    }

    @Override
    public Set<T> transform(MemcacheSetResponse response) {
      if (response.getSetStatusCount() != requestedKeys.size()) {
        throw new MemcacheServiceException(
            String.format(
                "Memcache put: Set %d items, got %d response statuses",
                requestedKeys.size(), response.getSetStatusCount()));
      }
      HashSet<T> result = new HashSet<>();
      HashSet<T> sizeErrors = new HashSet<>();
      HashSet<T> otherErrors = new HashSet<>();
      Iterator<SetStatusCode> statusIter = response.getSetStatusList().iterator();
      int itemIndex = 0;

      for (T requestedKey : requestedKeys) {
        SetStatusCode status = statusIter.next();
        if (status == MemcacheSetResponse.SetStatusCode.ERROR) {
          if (oversized.contains(itemIndex)) {
            sizeErrors.add(requestedKey);
          } else {
            otherErrors.add(requestedKey);
          }
        } else if (status == MemcacheSetResponse.SetStatusCode.STORED) {
          result.add(requestedKey);
        }
        itemIndex++;
      }
      // Throw exception if there is any error.
      if (!sizeErrors.isEmpty() || !otherErrors.isEmpty()) {
        StringBuilder builder = new StringBuilder("Memcache put: ");
        if (!sizeErrors.isEmpty()) {
          builder.append(sizeErrors.size());
          builder.append(" items failed for exceeding ");
          builder.append(MAX_ITEM_SIZE).append(" bytes; keys: ");
          builder.append(Joiner.on(", ").join(sizeErrors));
          builder.append(". ");
        }
        if (!otherErrors.isEmpty()) {
          builder.append("Set failed to set ");
          builder.append(otherErrors.size()).append(" keys: ");
          builder.append(Joiner.on(", ").join(otherErrors));
        }
        throw new MemcacheServiceException(builder.toString());
      }
      return result;
    }
  }

  @Override
  public <T> Future<Set<T>> putAll(Map<T, ?> values, Expiration expires, SetPolicy policy) {
    return doPutAll(values, expires, convertSetPolicyToPb(policy), "putAll");
  }

  @Override
  public Future<Void> putAll(Map<?, ?> values, Expiration expires) {
    return VoidFutureWrapper.wrap(
        doPutAll(values, expires, MemcacheSetRequest.SetPolicy.SET, "putAll"));
  }

  @Override
  public Future<Void> putAll(Map<?, ?> values) {
    return VoidFutureWrapper.wrap(
        doPutAll(values, null, MemcacheSetRequest.SetPolicy.SET, "putAll"));
  }

  @Override
  public Future<Boolean> delete(Object key) {
    return delete(key, 0);
  }

  @Override
  public Future<Boolean> delete(Object key, long millisNoReAdd) {
    MemcacheDeleteRequest request = MemcacheDeleteRequest.newBuilder()
        .setNameSpace(getEffectiveNamespace())
        .addItem(MemcacheDeleteRequest.Item.newBuilder()
            .setKey(makePbKey(key))
            .setDeleteTime((int) TimeUnit.SECONDS.convert(millisNoReAdd, TimeUnit.MILLISECONDS)))
        .build();
    return makeAsyncCall(
        "Delete",
        request,
        createRpcResponseHandler(
            MemcacheDeleteResponse.getDefaultInstance(),
            "Memcache delete: Unknown exception deleting key: " + key,
            new Transformer<MemcacheDeleteResponse, Boolean>() {
              @Override
              public Boolean transform(MemcacheDeleteResponse response) {
                return response.getDeleteStatus(0) == DeleteStatusCode.DELETED;
              }
            }),
        DefaultValueProviders.falseValue());
  }

  @Override
  public <T> Future<Set<T>> deleteAll(Collection<T> keys) {
    return deleteAll(keys, 0);
  }

  @Override
  public <T> Future<Set<T>> deleteAll(Collection<T> keys, long millisNoReAdd) {
    MemcacheDeleteRequest.Builder requestBuilder =
        MemcacheDeleteRequest.newBuilder().setNameSpace(getEffectiveNamespace());
    List<T> requestedKeys = new ArrayList<T>(keys.size());
    for (T key : keys) {
      requestedKeys.add(key);
      requestBuilder.addItem(MemcacheDeleteRequest.Item.newBuilder()
                                 .setDeleteTime((int) (millisNoReAdd / 1000))
                                 .setKey(makePbKey(key)));
    }
    return makeAsyncCall(
        "Delete",
        requestBuilder.build(),
        createRpcResponseHandler(
            MemcacheDeleteResponse.getDefaultInstance(),
            "Memcache deleteAll: Unknown exception deleting multiple keys",
            new MemcacheDeleteResponseTransformer<>(requestedKeys)),
        DefaultValueProviders.<T>emptySet());
  }

  private static class MemcacheDeleteResponseTransformer<T>
      implements Transformer<MemcacheDeleteResponse, Set<T>> {
    private final List<T> requestedKeys;

    MemcacheDeleteResponseTransformer(List<T> requestedKeys) {
      this.requestedKeys = requestedKeys;
    }

    @Override
    public Set<T> transform(MemcacheDeleteResponse response) {
      Set<T> retval = new LinkedHashSet<T>();
      Iterator<T> requestedKeysIter = requestedKeys.iterator();
      for (DeleteStatusCode deleteStatus : response.getDeleteStatusList()) {
        T requestedKey = requestedKeysIter.next();
        if (deleteStatus == DeleteStatusCode.DELETED) {
          retval.add(requestedKey);
        }
      }
      return retval;
    }
  }

  private static MemcacheIncrementRequest.Builder newIncrementRequestBuilder(
      Object key, long delta, Long initialValue) {
    MemcacheIncrementRequest.Builder requestBuilder = MemcacheIncrementRequest.newBuilder();
    requestBuilder.setKey(makePbKey(key));
    if (delta > 0) {
      requestBuilder.setDirection(Direction.INCREMENT);
      requestBuilder.setDelta(delta);
    } else {
      requestBuilder.setDirection(Direction.DECREMENT);
      requestBuilder.setDelta(-delta);
    }
    if (initialValue != null) {
      requestBuilder.setInitialValue(initialValue);
      requestBuilder.setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal());
    }
    return requestBuilder;
  }

  @Override
  public Future<Long> increment(Object key, long delta) {
    return increment(key, delta, null);
  }

  @Override
  public Future<Long> increment(Object key, long delta, Long initialValue) {
    MemcacheIncrementRequest request = newIncrementRequestBuilder(key, delta, initialValue)
        .setNameSpace(getEffectiveNamespace())
        .build();
    return makeAsyncCall(
        "Increment",
        request,
        new RpcResponseHandlerForIncrement(key, getErrorHandler()),
        DefaultValueProviders.<Long>nullValue());
  }

  private static class RpcResponseHandlerForIncrement
      extends RpcResponseHandler<MemcacheIncrementResponse, Long> {
    private final Object key;
    private final ErrorHandler errorHandler;

    RpcResponseHandlerForIncrement(Object key, ErrorHandler errorHandler) {
      super(
          MemcacheIncrementResponse.getDefaultInstance(),
          "Memcache increment: exception when incrementing key '" + key + "'",
          new IncrementResponseTransformer(),
          errorHandler);
      this.key = key;
      this.errorHandler = errorHandler;
    }

    @Override
    void handleApiProxyException(Throwable cause) throws Exception {
      // Note the custom error handling here.  Typically we delegate to the
      // error handler to deal with exceptions that are thrown from
      // makeSyncCall, but use of the error handler requires that there be
      // some scenario where it is reasonable for the api to swallow the
      // exception and behave as if the cached data simply isn't available
      // (memcache is unavailable for example).
      // In this case, however, the programmer has either asked for the wrong
      // thing or is wrong about the type of data associated with the provided
      // key, so it never makes sense to swallow the exception.
      if (cause instanceof ApiProxy.ApplicationException) {
        ApiProxy.ApplicationException applicationException = (ApiProxy.ApplicationException) cause;
        getLogger().info(applicationException.getErrorDetail());
        if (applicationException.getApplicationError()
            == MemcacheServiceError.ErrorCode.INVALID_VALUE_VALUE) {
          // value is not incrementable
          throw new InvalidValueException("Non-incrementable value for key '" + key + "'");
        }
      }
      super.handleApiProxyException(cause);
    }

    private static class IncrementResponseTransformer
        implements Transformer<MemcacheIncrementResponse, Long> {
      @Override
      public Long transform(MemcacheIncrementResponse response) {
        return response.hasNewValue() ? response.getNewValue() : null;
      }
    }
  }

  @Override
  public <T> Future<Map<T, Long>> incrementAll(Collection<T> keys, long delta) {
    return incrementAll(keys, delta, null);
  }

  @Override
  public <T> Future<Map<T, Long>> incrementAll(Collection<T> keys, long delta, Long initialValue) {
    return incrementAll(makeMap(keys, delta), initialValue);
  }

  @Override
  public <T> Future<Map<T, Long>> incrementAll(Map<T, Long> offsets) {
    return incrementAll(offsets, null);
  }

  @Override
  public <T> Future<Map<T, Long>> incrementAll(Map<T, Long> offsets, Long initialValue) {
    MemcacheBatchIncrementRequest.Builder requestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace(getEffectiveNamespace());
    final List<T> requestedKeys = new ArrayList<T>(offsets.size());
    for (Map.Entry<T, Long> entry : offsets.entrySet()) {
      requestedKeys.add(entry.getKey());
      requestBuilder.addItem(
          newIncrementRequestBuilder(entry.getKey(), entry.getValue(), initialValue));
    }
    Provider<Map<T, Long>> defaultValue = new Provider<Map<T, Long>>() {
      @Override
      public Map<T, Long> get() {
        return makeMap(requestedKeys, null);
      }
    };
    return makeAsyncCall(
        "BatchIncrement",
        requestBuilder.build(),
        createRpcResponseHandler(
            MemcacheBatchIncrementResponse.getDefaultInstance(),
            "Memcache incrementAll: exception incrementing multiple keys",
            new MemcacheBatchIncrementResponseTransformer<>(requestedKeys)),
        defaultValue);
  }

  private static class MemcacheBatchIncrementResponseTransformer<T>
      implements Transformer<MemcacheBatchIncrementResponse, Map<T, Long>> {
    private final List<T> requestedKeys;

    MemcacheBatchIncrementResponseTransformer(List<T> requestedKeys) {
      this.requestedKeys = requestedKeys;
    }

    @Override
    public Map<T, Long> transform(MemcacheBatchIncrementResponse response) {
      Map<T, Long> result = new LinkedHashMap<>();
      Iterator<MemcacheIncrementResponse> items = response.getItemList().iterator();
      for (T requestedKey : requestedKeys) {
        MemcacheIncrementResponse item = items.next();
        if (item.getIncrementStatus().equals(IncrementStatusCode.OK) && item.hasNewValue()) {
          result.put(requestedKey, item.getNewValue());
        } else {
          result.put(requestedKey, null);
        }
      }
      return result;
    }
  }

  @Override
  public Future<Void> clearAll() {
    return makeAsyncCall(
        "FlushAll",
        MemcacheFlushRequest.getDefaultInstance(),
        createRpcResponseHandler(
            MemcacheFlushResponse.getDefaultInstance(),
            "Memcache clearAll: exception",
            new Transformer<MemcacheFlushResponse, Void>() {
              @Override
              public Void transform(MemcacheFlushResponse response) {
                return null;
              }
            }),
        DefaultValueProviders.<Void>nullValue());
  }

  @Override
  public Future<Stats> getStatistics() {
    return makeAsyncCall(
        "Stats",
        MemcacheStatsRequest.getDefaultInstance(),
        createRpcResponseHandler(
            MemcacheStatsResponse.getDefaultInstance(),
            "Memcache getStatistics: exception",
            new Transformer<MemcacheStatsResponse, Stats>() {
              @Override
              public Stats transform(MemcacheStatsResponse response) {
                return new StatsImpl(response.getStats());
              }
            }),
        DefaultValueProviders.emptyStats());
  }
}
