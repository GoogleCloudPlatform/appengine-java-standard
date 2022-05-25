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

package com.google.appengine.api.memcache.jsr107cache;

import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT;
import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT;
import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.SET_ALWAYS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.Stats;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheStatistics;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
@SuppressWarnings("ModifiedButNotUsed")
// Because Cache is a Map, Error Prone thinks we are creating one in some tests but not using it.
// In fact we are verifying its interactions with the underlying MemcacheService. Hence
// ModifiedButNotUsed.
public class GCacheTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String KEY = "a key";
  private static final String VALUE = "a value";
  private static final String KEY2 = "another key";
  private static final String VALUE2 = "another value";

  @Mock private MemcacheService service;
  @Mock private Stats stats;
  private Map<String, Object> properties;

  @Before
  public void setUp() {
    properties = new HashMap<>();
    properties.put(GCacheFactory.MEMCACHE_SERVICE, service);
  }

  @Test
  public void testPut_setAlways() {
    when(service.put(any(), any(), any(), any())).thenReturn(true);
    Cache cache = new GCache(properties);
    cache.put(KEY, VALUE);
    verify(service).put(KEY, VALUE, null, SET_ALWAYS);
  }

  @Test
  public void testPut_replaceIfPresent_notPresent() {
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(true);
    cache.put(KEY, VALUE);
    verify(service).put(KEY, VALUE, null, REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPut_addIfNotPresent_isPresent() {
    properties.put(GCacheFactory.SET_POLICY, ADD_ONLY_IF_NOT_PRESENT);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    cache.put(KEY, VALUE); // Should not throw.
    verify(service).put(KEY, VALUE, null, ADD_ONLY_IF_NOT_PRESENT);
  }

  @Test
  public void testPut_replaceIfPresent_isPresent() {
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    cache.put(KEY2, VALUE); // Should not throw.
    verify(service).put(KEY2, VALUE, null, REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPut_addIfNotPresent_throws() {
    properties.put(GCacheFactory.THROW_ON_PUT_FAILURE, true);
    properties.put(GCacheFactory.SET_POLICY, ADD_ONLY_IF_NOT_PRESENT);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    assertThrows(GCacheException.class, () -> cache.put(KEY, VALUE));
    verify(service).put(KEY, VALUE, null, ADD_ONLY_IF_NOT_PRESENT);
  }

  @Test
  public void testPut_replaceIfPresent_throws() {
    properties.put(GCacheFactory.THROW_ON_PUT_FAILURE, true);
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    assertThrows(GCacheException.class, () -> cache.put(KEY2, VALUE));
    verify(service).put(KEY2, VALUE, null, REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPut_expirationSeconds() {
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    properties.put(GCacheFactory.EXPIRATION_DELTA, 123);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    cache.put(KEY, VALUE);
    verify(service).put(KEY, VALUE, Expiration.byDeltaSeconds(123), REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPut_expirationDate() {
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Date now = new Date();
    properties.put(GCacheFactory.EXPIRATION, now);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    cache.put(KEY, VALUE);
    verify(service).put(KEY, VALUE, Expiration.onDate(now), REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPut_expirationMillis() {
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    properties.put(GCacheFactory.EXPIRATION_DELTA_MILLIS, 12345678);
    Cache cache = new GCache(properties);
    when(service.put(any(), any(), any(), any())).thenReturn(false);
    cache.put(KEY, VALUE);
    verify(service).put(KEY, VALUE, Expiration.byDeltaMillis(12345678), REPLACE_ONLY_IF_PRESENT);
  }

  /** Tests that a putAll operation passes to the MemcacheService correctly. */
  @Test
  public void testPutAll_setAlways() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);
    when(service.putAll(map, null, SET_ALWAYS)).thenReturn(map.keySet());
    Cache cache = new GCache(properties);
    cache.putAll(map);
    verify(service).putAll(map, null, SET_ALWAYS);
  }

  @Test
  public void testPutAll_replaceIfPresent_notPresent() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);

    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Cache cache = new GCache(properties);
    when(service.putAll(map, null, REPLACE_ONLY_IF_PRESENT)).thenReturn(map.keySet());
    cache.putAll(map);
    verify(service).putAll(map, null, REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPutAll_addIfNotPresent_somePresent() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);
    ImmutableSet<String> failureSet = ImmutableSet.of(KEY);

    properties.put(GCacheFactory.SET_POLICY, ADD_ONLY_IF_NOT_PRESENT);
    Cache cache = new GCache(properties);
    when(service.putAll(map, null, ADD_ONLY_IF_NOT_PRESENT)).thenReturn(failureSet);
    cache.putAll(map); // Should not throw.
    verify(service).putAll(map, null, ADD_ONLY_IF_NOT_PRESENT);
  }

  @Test
  public void testPutAll_replaceIfPresent_somePresent() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);
    ImmutableSet<String> failureSet = ImmutableSet.of(KEY);

    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Cache cache = new GCache(properties);
    when(service.putAll(map, null, REPLACE_ONLY_IF_PRESENT)).thenReturn(failureSet);
    cache.putAll(map); // Should not throw.
  }

  @Test
  public void testPutAll_addIfNotPresent_throws() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);
    ImmutableSet<String> failureSet = ImmutableSet.of(KEY);

    properties.put(GCacheFactory.THROW_ON_PUT_FAILURE, Boolean.TRUE);
    properties.put(GCacheFactory.SET_POLICY, ADD_ONLY_IF_NOT_PRESENT);
    Cache cache = new GCache(properties);
    when(service.putAll(map, null, REPLACE_ONLY_IF_PRESENT)).thenReturn(failureSet);
    assertThrows(GCacheException.class, () -> cache.putAll(map));
  }

  @Test
  public void testPutAll_replaceIfPresent_throws() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);
    ImmutableSet<String> failureSet = ImmutableSet.of(KEY);

    properties.put(GCacheFactory.THROW_ON_PUT_FAILURE, Boolean.TRUE);
    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Cache cache = new GCache(properties);
    when(service.putAll(map, null, REPLACE_ONLY_IF_PRESENT)).thenReturn(failureSet);
    assertThrows(GCacheException.class, () -> cache.putAll(map));
  }

  @Test
  public void testPutAll_expirationSeconds() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);

    when(service.putAll(eq(map), any(), any())).thenReturn(map.keySet());

    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    properties.put(GCacheFactory.EXPIRATION_DELTA, 123);
    Cache cache = new GCache(properties);
    cache.putAll(map);
    verify(service).putAll(map, Expiration.byDeltaSeconds(123), REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPutAll_expirationDate() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);

    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    Date now = new Date();
    properties.put(GCacheFactory.EXPIRATION, now);
    Cache cache = new GCache(properties);
    cache.putAll(map);
    verify(service).putAll(map, Expiration.onDate(now), REPLACE_ONLY_IF_PRESENT);
  }

  @Test
  public void testPutAll_expirationMillis() {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);

    properties.put(GCacheFactory.SET_POLICY, REPLACE_ONLY_IF_PRESENT);
    properties.put(GCacheFactory.EXPIRATION_DELTA_MILLIS, 12345678);
    Cache cache = new GCache(properties);
    cache.putAll(map);
    verify(service).putAll(map, Expiration.byDeltaMillis(12345678), REPLACE_ONLY_IF_PRESENT);
  }

  /** Tests that a peek operation passes to the MemcacheService correctly. */
  @Test
  public void testPeek() {
    Cache cache = new GCache(properties);
    when(service.get(KEY)).thenReturn(VALUE);
    assertThat(cache.peek(KEY)).isEqualTo(VALUE);
    verify(service).get(KEY);
    verifyNoMoreInteractions(service);
  }

  /** Tests that a get operation passes to the MemcacheService correctly. */
  @Test
  public void testGet() {
    Cache cache = new GCache(properties);
    when(service.get(KEY)).thenReturn(VALUE);
    assertThat(cache.get(KEY)).isEqualTo(VALUE);
    verify(service).get(KEY);
    verifyNoMoreInteractions(service);
  }

  /** Tests that a getAll operation passes to the MemcacheService correctly. */
  @Test
  public void testGetAll() throws Exception {
    ImmutableMap<String, Object> map = ImmutableMap.of(KEY, VALUE, KEY2, VALUE2);
    ImmutableSet<String> keys = ImmutableSet.of(KEY, KEY2);
    when(service.getAll(keys)).thenReturn(map);
    Cache cache = new GCache(properties);
    assertThat(cache.getAll(keys)).isEqualTo(map);
  }

  /** Tests that a containsKey operation passes to the MemcacheService correctly. */
  @Test
  @SuppressWarnings("ContainsKeyTruth") // Truth doesn't call the .containsKey method.
  public void testContainsKey() {
    when(service.contains(KEY)).thenReturn(true);
    when(service.contains(KEY2)).thenReturn(false);
    Cache cache = new GCache(properties);
    assertThat(cache.containsKey(KEY)).isTrue();
    assertThat(cache.containsKey(KEY2)).isFalse();
    verify(service).contains(KEY);
    verify(service).contains(KEY2);
    verifyNoMoreInteractions(service);
  }

  /** Tests that a clear operation passes to the MemcacheService correctly. */
  @Test
  public void testClear() {
    Cache cache = new GCache(properties);
    cache.clear();
    verify(service).clearAll();
  }

  /** Tests that a getCacheEntry operation passes to the MemcacheService correctly. */
  @Test
  public void testGetCacheEntry1() {
    Cache cache = new GCache(properties);
    when(service.get(KEY)).thenReturn(VALUE);
    assertThat(cache.getCacheEntry(KEY)).isEqualTo(new GCacheEntry(cache, KEY, VALUE));
    verify(service).get(KEY);
    verifyNoMoreInteractions(service);
  }

  @Test
  public void testGetCacheEntry2() {
    // If `get` returns null, we call `contains` to distinguish between a missing key and a key that
    // is present with a null value.
    Cache cache = new GCache(properties);
    when(service.get(KEY2)).thenReturn(null);
    when(service.contains(KEY2)).thenReturn(true);
    assertThat(cache.getCacheEntry(KEY2)).isEqualTo(new GCacheEntry(cache, KEY2, null));
    verify(service).get(KEY2);
    verify(service).contains(KEY2);
    verifyNoMoreInteractions(service);
  }

  @Test
  public void testGetCacheEntry3() {
    Cache cache = new GCache(properties);
    when(service.get(KEY)).thenReturn(null);
    when(service.contains(KEY)).thenReturn(false);
    assertThat(cache.getCacheEntry(KEY)).isNull();
    verify(service).get(KEY);
    verify(service).contains(KEY);
    verifyNoMoreInteractions(service);
  }

  /** Tests that remove operations pass and return values to the MemcacheService correctly. */
  @Test
  public void testRemove1() {
    Cache cache = new GCache(properties);
    when(service.get(KEY)).thenReturn(VALUE);
    when(service.delete(KEY)).thenReturn(true);
    assertThat(cache.remove(KEY)).isEqualTo(VALUE);
    verify(service).get(KEY);
    verify(service).delete(KEY);
    verifyNoMoreInteractions(service);
  }

  @Test
  public void testRemove2() {
    Cache cache = new GCache(properties);
    when(service.get(KEY)).thenReturn(null);
    when(service.delete(KEY)).thenReturn(false);
    assertThat(cache.remove(KEY)).isNull();
    verify(service).get(KEY);
    verify(service).delete(KEY);
    verifyNoMoreInteractions(service);
  }

  @Test
  public void testRemove3() {
    Cache cache = new GCache(properties);
    when(service.get(KEY2)).thenReturn(null);
    when(service.delete(KEY2)).thenReturn(true);
    assertThat(cache.remove(KEY2)).isNull();
    verify(service).get(KEY2);
    verify(service).delete(KEY2);
    verifyNoMoreInteractions(service);
  }

  /**
   * Tests that GetCacheStatistics return a statistics object that correctly returns stats from the
   * stats object returned by the MemcacheService.
   */
  @Test
  public void testGetCacheStatistics() {
    Cache cache = new GCache(properties);
    when(service.getStatistics()).thenReturn(stats);
    when(stats.getItemCount()).thenReturn(40L);
    when(stats.getHitCount()).thenReturn(20L);
    when(stats.getMissCount()).thenReturn(3L);
    CacheStatistics statistics = cache.getCacheStatistics();
    assertThat(statistics.getObjectCount()).isEqualTo(40);
    assertThat(statistics.getCacheHits()).isEqualTo(20);
    assertThat(statistics.getCacheMisses()).isEqualTo(3);
    assertThrows(UnsupportedOperationException.class, statistics::clearStatistics);
  }

  /** Tests that size operations pass and return values to the MemcacheService correctly. */
  @Test
  public void testSize() {
    Cache cache = new GCache(properties);
    when(service.getStatistics()).thenReturn(stats);
    when(stats.getItemCount()).thenReturn(30L);
    assertThat(cache).hasSize(30);
  }

  /** Tests that isEmpty operations pass and return values to the MemcacheService correctly. */
  @Test
  public void testIsEmpty() {
    Cache cache = new GCache(properties);
    when(service.getStatistics()).thenReturn(stats);
    when(stats.getItemCount()).thenReturn(30L).thenReturn(0L);
    assertThat(cache).isNotEmpty();
    assertThat(cache).isEmpty();
  }

  @Test
  public void testNamespaceSetting() {
    ImmutableMap<String, Object> customProperties =
        ImmutableMap.of(GCacheFactory.MEMCACHE_SERVICE, service, GCacheFactory.NAMESPACE, "ns");

    GCache unused = new GCache(customProperties);
    verifyNoMoreInteractions(service);
  }

  /** Tests that all methods that are not expected to be implemented throw the correct exception. */
  @Test
  public void testUnsupportedOperations() {
    Cache cache = new GCache(properties);
    assertThrows(UnsupportedOperationException.class, () -> cache.containsValue(null));
    assertThrows(UnsupportedOperationException.class, cache::entrySet);
    assertThrows(UnsupportedOperationException.class, cache::keySet);
    assertThrows(UnsupportedOperationException.class, () -> cache.load(null));
    assertThrows(UnsupportedOperationException.class, () -> cache.loadAll(null));
    assertThrows(UnsupportedOperationException.class, cache::values);
  }
}
