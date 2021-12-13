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

package com.google.appengine.api.memcache.dev;

import static com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig.getLocalMemcacheService;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.InvalidValueException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;
import com.google.appengine.api.memcache.MemcacheService.SetPolicy;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.Stats;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.concurrent.NotThreadSafe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for the LocalMemcacheService, absent any actual stubby interaction */
@RunWith(JUnit4.class)
public class LocalMemcacheServiceTest {
  static final String ONE = "one";
  static final String VAL_ONE = "one";
  // 5 == MemcacheSerialization.makePbKey(ONE).length, but it's not visible
  static final int SIZE_ONE = 5 + VAL_ONE.length();

  static final Integer TWO = 2;
  static final String VAL_TWO = "123";
  // 19 == MemcacheSerialization.makePbKey(TWO).length, but it's not visible
  static final int SIZE_TWO = 19 + VAL_TWO.length();

  static final String THREE = "three";
  static final String VAL_THREE = "three";
  // 7 == MemcacheSerialization.makePbKey(THREE).length, but it's not visible
  static final int SIZE_THREE = 7 + VAL_THREE.length();

  // Used to control the clock during time-dependent tests.
  private JavaMockClock clock;

  // A handle on the memcache service under test.
  private MemcacheService memcache;

  // The test helper used for the memcache service test.
  private LocalServiceTestHelper helper;

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalMemcacheServiceTestConfig());
    clock = new JavaMockClock();
    helper.setClock(clock);
    helper.setUp();
    memcache = MemcacheServiceFactory.getMemcacheService();
  }

  @After
  public void tearDown() throws Exception {
    memcache = null;
    helper.tearDown();
  }

  void sleep(long millis) {
    clock.advance(millis);
  }

  @Test
  public void testOneDelete() {
    // check delete of not present
    boolean val = memcache.delete(ONE);
    assertThat(val).isFalse();
    verifyStats(0, 0, 0, 0, 0, 0);

    // check basic delete of present
    memcache.put(ONE, VAL_ONE);
    verifyStats(0, 0, 0, 1, null, null);
    val = memcache.delete(ONE);
    assertThat(val).isTrue();
    verifyStats(0, 0, 0, 0, 0, 0);

    // check delete with delete hold
    memcache.put(ONE, VAL_ONE);
    verifyStats(0, 0, 0, 1, null, null);
    val = memcache.delete(ONE, 1000);
    assertThat(val).isTrue();
    verifyStats(0, 0, 0, 0, 0, 0);
    assertThat(memcache.put(ONE, VAL_ONE, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isFalse();
    verifyStats(0, 0, 0, 0, 0, 0);
    assertThat(memcache.get(ONE)).isNull();
    verifyStats(0, 1, 0, 0, 0, 0);
    // test set overrides hold
    memcache.put(ONE, VAL_ONE);
    verifyStats(0, 1, 0, 1, null, null);
    assertThat(memcache.get(ONE)).isEqualTo(VAL_ONE);
    verifyStats(1, 1, SIZE_ONE, 1, null, null);

    // test expiration of hold
    memcache.put(ONE, VAL_ONE);
    verifyStats(1, 1, SIZE_ONE, 1, null, null);
    val = memcache.delete(ONE, 1000);
    assertThat(val).isTrue();
    verifyStats(1, 1, SIZE_ONE, 0, 0, 0);
    assertThat(memcache.put(ONE, VAL_ONE, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isFalse();
    verifyStats(1, 1, SIZE_ONE, 0, 0, 0);
    sleep(1000);
    assertThat(memcache.put(ONE, VAL_ONE, null, MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isTrue();
    verifyStats(1, 1, SIZE_ONE, 1, null, null);
  }

  @Test
  public void testEmptyGets() {
    // single get
    assertThat(memcache.get(ONE)).isNull();
    verifyStats(0, 1, 0, 0, 0, 0);

    // double get
    List<Object> list = new ArrayList<Object>();
    list.add(ONE);
    list.add(TWO);
    Map<Object, Object> rsp = memcache.getAll(list);
    assertThat(rsp.size()).isEqualTo(0);
    verifyStats(0, 3, 0, 0, 0, 0);
  }

  @Test
  public void testFlush() {
    // empty test
    memcache.clearAll();
    verifyStats(0, 0, 0, 0, 0, 0);

    // add some stuff, incuding some get stats, and flush-with-content
    memcache.put(ONE, VAL_ONE);
    memcache.put(TWO, VAL_TWO);
    assertThat(memcache.get(ONE)).isEqualTo(VAL_ONE);
    memcache.clearAll();
    verifyStats(0, 0, 0, 0, 0, 0);
    assertThat(memcache.get(ONE)).isNull();
  }

  @Test
  public void testGetAndSet() {
    // initial set succeeds...
    memcache.put(ONE, VAL_ONE);

    // ...and that first  set can be fetched
    assertThat(memcache.get(ONE)).isEqualTo(VAL_ONE);

    // counters are right?
    verifyStats(1, 0, SIZE_ONE, 1, SIZE_ONE, null);

    // but non-added keys aren't there
    assertThat(memcache.get(TWO)).isNull();

    // counters are right.
    verifyStats(1, 1, SIZE_ONE, 1, SIZE_ONE, null);

    // add another, watching flags
    memcache.put(TWO, VAL_TWO);

    // get it, and check stats
    assertThat(memcache.get(TWO)).isEqualTo(VAL_TWO);
    verifyStats(2, 1, SIZE_ONE + SIZE_TWO, 2, SIZE_ONE + SIZE_TWO, null);
  }

  @Test
  public void testKeysBytesPassthru() {
    // relying on fact that we know Boolean keys map to "true" and "false"

    // Put as Boolean key, get as bytes key
    memcache.put(false, "put as Boolean key");
    assertThat(memcache.get("false".getBytes(UTF_8))).isEqualTo("put as Boolean key");

    // Put as bytes key, get as Boolean key
    memcache.put("true".getBytes(UTF_8), "put as bytes key");
  }

  @Test
  public void testExpires() {
    Expiration shortTime = Expiration.onDate(new Date(clock.getCurrentTime() + 1000));
    Expiration longTime = Expiration.onDate(new Date(clock.getCurrentTime() + 100000));

    memcache.put(ONE, VAL_ONE, shortTime);
    memcache.put(TWO, VAL_TWO, longTime);

    verifyStats(0, 0, 0, 2, SIZE_ONE + SIZE_TWO, null);
    assertThat(memcache.get(ONE)).isNotNull();
    assertThat(memcache.get(TWO)).isNotNull();

    sleep(2000);

    // lazy discard is visible...
    verifyStats(2, 0, SIZE_ONE + SIZE_TWO, 2, SIZE_ONE + SIZE_TWO, null);
    assertThat(memcache.get(ONE)).isNull();
    assertThat(memcache.get(TWO)).isNotNull();
    // but by now is discarded.
    verifyStats(3, 1, SIZE_ONE + 2 * SIZE_TWO, 1, SIZE_TWO, null);
  }

  @Test
  public void testSetNull() {
    // set of null key succeeds...
    memcache.put(null, VAL_ONE);
    verifyStats(0, 0, 0, 1, null, null);
    assertThat(memcache.get(null)).isEqualTo(VAL_ONE);

    // set of null value succeeds...
    memcache.put(ONE, null);
    assertThat(memcache.get(ONE)).isNull();
    assertThat(memcache.contains(ONE)).isTrue();
    verifyStats(3, 0, 13 /* 9 for 3 "one"'s, plus the null sizes */, 2, null, null);
  }

  @Test
  public void testIncrement() {
    // Increment is unique in that the LocalMemcacheService has to deserialize
    // and mutate values.
    memcache.put(ONE, 1);
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(2));
    assertThat(memcache.get(ONE)).isEqualTo(2);

    assertThat(memcache.increment(ONE, 3)).isEqualTo(Long.valueOf(5));
    assertThat(memcache.increment(ONE, -4)).isEqualTo(Long.valueOf(1));
    assertThat(memcache.increment(ONE, -3)).isEqualTo(Long.valueOf(0));
    assertThat(memcache.increment(ONE, 3)).isEqualTo(Long.valueOf(3));

    // check Byte range overflow
    memcache.put(ONE, Byte.MAX_VALUE);
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(Byte.MAX_VALUE + 1L));
    assertThat(memcache.get(ONE)).isEqualTo(Byte.MIN_VALUE);
    memcache.put(ONE, (byte) 0);
    assertThat(memcache.increment(ONE, Long.MAX_VALUE)).isEqualTo(Long.valueOf(Long.MAX_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo(Byte.valueOf((byte) Long.MAX_VALUE));
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo(Byte.valueOf((byte) Long.MIN_VALUE));

    // check Integer range overflow
    memcache.put(ONE, Integer.MAX_VALUE);
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(Integer.MAX_VALUE + 1L));
    assertThat(memcache.get(ONE)).isEqualTo(Integer.MIN_VALUE);
    memcache.put(ONE, Integer.valueOf(0));
    assertThat(memcache.increment(ONE, Long.MAX_VALUE)).isEqualTo(Long.valueOf(Long.MAX_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo(Integer.valueOf((int) Long.MAX_VALUE));
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo(Integer.valueOf((int) Long.MIN_VALUE));

    // check Long range overflow
    memcache.put(ONE, Long.MAX_VALUE);
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
    memcache.put(ONE, String.valueOf(Long.MAX_VALUE));
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo("9223372036854775808");

    // check UINT64 overflow (18446744073709551615)
    memcache.put(ONE, "18446744073709551615");
    assertThat(memcache.increment(ONE, 1)).isEqualTo(Long.valueOf(0));
    assertThat(memcache.get(ONE)).isEqualTo("0");
    memcache.put(ONE, "18446744073709551614");
    assertThat(memcache.increment(ONE, 3)).isEqualTo(Long.valueOf(1));
    assertThat(memcache.get(ONE)).isEqualTo("1");

    // error check
    // can't increment non-integer value
    memcache.put(ONE, "frogs");
    assertThrows(InvalidValueException.class, () -> memcache.increment(ONE, 2));

    // can't increment negative value
    memcache.put(ONE, -1);
    assertThrows(InvalidValueException.class, () -> memcache.increment(ONE, 2));
    assertThrows(InvalidValueException.class, () -> memcache.increment(ONE, -2));

    // can't increment value exceeding UINT64 range
    memcache.put(ONE, "18446744073709551616");
    assertThrows(InvalidValueException.class, () -> memcache.increment(ONE, -2));
  }

  @Test
  public void testIncrementWithInitialValue() {
    assertThat(memcache.increment(ONE, 1, 22L)).isEqualTo(Long.valueOf(23));
    assertThat(memcache.get(ONE)).isEqualTo(Long.valueOf(23));

    memcache.clearAll();
    assertThat(memcache.increment(ONE, -100, 10L)).isEqualTo(Long.valueOf(0));
    assertThat(memcache.get(ONE)).isEqualTo(Long.valueOf(0));

    // Test with a negative initial value
    memcache.clearAll();
    assertThat(memcache.increment(ONE, 1, -10L)).isEqualTo(Long.valueOf(-9));
    assertThat(memcache.get(ONE)).isEqualTo(Long.valueOf(-9));
    assertThat(memcache.increment(TWO, -3, -5L)).isEqualTo(Long.valueOf(-8));
    assertThat(memcache.get(TWO)).isEqualTo(Long.valueOf(-8));
    assertThat(memcache.increment(THREE, 5, -3L)).isEqualTo(Long.valueOf(2));
    assertThat(memcache.get(THREE)).isEqualTo(Long.valueOf(2));

    // Test with a Long.MAX_VALUE as initial value
    memcache.clearAll();
    assertThat(memcache.increment(ONE, 0, Long.MAX_VALUE)).isEqualTo(Long.valueOf(Long.MAX_VALUE));
    assertThat(memcache.get(ONE)).isEqualTo(Long.valueOf(Long.MAX_VALUE));
    assertThat(memcache.increment(TWO, 1, Long.MAX_VALUE)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
    assertThat(memcache.get(TWO)).isEqualTo(Long.valueOf(Long.MIN_VALUE));
  }

  @Test
  public void testIncrementAll() {
    ArrayList<Object> keys = new ArrayList<Object>();
    keys.add("first");
    keys.add("second");
    keys.add("third");
    HashMap<Object, Long> offsets = new HashMap<Object, Long>();
    HashMap<Object, Long> expected = new HashMap<Object, Long>();

    // all keys
    memcache.put("first", "123");
    memcache.put("second", "77");
    expected.clear();
    expected.put("first", 124L);
    expected.put("second", 78L);
    expected.put("third", null);
    assertThat(memcache.incrementAll(keys, 1)).isEqualTo(expected);

    // all keys with initial value
    memcache.clearAll();
    memcache.put("first", "123");
    memcache.put("second", "77");
    expected.clear();
    expected.put("first", 124L);
    expected.put("second", 78L);
    expected.put("third", 11L);
    assertThat(memcache.incrementAll(keys, 1, 10L)).isEqualTo(expected);

    // offsets
    memcache.clearAll();
    memcache.put("first", "123");
    memcache.put("second", "77");
    offsets.clear();
    offsets.put("first", -22L);
    offsets.put("second", 14L);
    offsets.put("third", 0L);
    expected.clear();
    expected.put("first", 101L);
    expected.put("second", 91L);
    expected.put("third", null);
    assertThat(memcache.incrementAll(offsets)).isEqualTo(expected);

    // offsets with initial value
    memcache.clearAll();
    memcache.put("first", "123");
    memcache.put("second", "77");
    offsets.clear();
    offsets.put("first", -22L);
    offsets.put("second", 14L);
    offsets.put("third", 0L);
    expected.clear();
    expected.put("first", 101L);
    expected.put("second", 91L);
    expected.put("third", 11L);
    assertThat(memcache.incrementAll(offsets, 11L)).isEqualTo(expected);
  }

  private static String keyThatDoesNotExist() {
    return UUID.randomUUID().toString();
  }

  @Test
  public void testIncrementAllCollectionOnNonExisting() {
    String key = keyThatDoesNotExist();
    long delta = 33;
    long initialValue = 1000;
    Collection<String> keys = new ArrayList<String>();
    keys.add(key);

    Map<String, Long> result = memcache.incrementAll(keys, delta, initialValue);

    // First check the result immediately returned by the increment
    Long expected = initialValue + delta;
    assertThat(result.get(key)).isEqualTo(expected);

    // Then check the result fetched from the cache
    Object fetched = memcache.get(key);
    assertWithMessage("For type of fetched value %s", fetched)
        .that(fetched)
        .isInstanceOf(Long.class);

    assertThat(fetched).isEqualTo(expected);
  }

  @Test
  public void testIncrementAllMapOnNonExisting() {
    String key = keyThatDoesNotExist();
    long delta = 33;
    long initialValue = 1000;
    Map<String, Long> deltasForeachKey = new HashMap<String, Long>();
    deltasForeachKey.put(key, delta);

    Map<String, Long> result = memcache.incrementAll(deltasForeachKey, initialValue);

    // First check the result immediately returned by the increment
    Long expected = initialValue + delta;
    assertThat(result.get(key)).isEqualTo(expected);

    // Then check the result fetched from the cache
    Object fetched = memcache.get(key);
    assertWithMessage("For type of fetched value %s", fetched)
        .that(fetched)
        .isInstanceOf(Long.class);
    assertThat(fetched).isEqualTo(expected);
  }

  @Test
  public void testMultiDelete() {
    // populate cache with three values
    memcache.put(ONE, VAL_ONE);
    memcache.put(TWO, VAL_TWO);
    memcache.put(THREE, VAL_THREE);
    verifyStats(0, 0, 0, 3, SIZE_ONE + SIZE_TWO + SIZE_THREE, null);

    // multi-delete two of them...
    List<Object> list = new ArrayList<Object>();
    list.add(ONE);
    list.add(TWO);
    Set<Object> keys = memcache.deleteAll(list);
    assertThat(keys.size()).isEqualTo(2);
    assertThat(keys.contains(ONE)).isTrue();
    assertThat(keys.contains(TWO)).isTrue();
    verifyStats(0, 0, 0, 1, SIZE_THREE, null);

    // multi-delete two values, although only one succeeds
    list.remove(1);
    list.add(THREE);
    keys = memcache.deleteAll(list);
    assertThat(keys.size()).isEqualTo(1);
    assertThat(keys.contains(THREE)).isTrue();

    verifyStats(0, 0, 0, 0, 0, 0);
  }

  @Test
  public void testMultiGetSet() {
    // initial set succeeds...
    memcache.put(ONE, VAL_ONE);

    // initial multi-fetch, split result
    List<Object> list = new ArrayList<Object>();
    list.add(ONE);
    list.add(TWO);
    Map<Object, Object> map = memcache.getAll(list);
    assertThat(map.size()).isEqualTo(1);
    assertThat(map.get(ONE)).isEqualTo(VAL_ONE);
    verifyStats(1, 1, SIZE_ONE, 1, SIZE_ONE, null);

    // multi-set succeeds...
    map = new HashMap<Object, Object>();
    map.put(TWO, VAL_TWO);
    map.put(THREE, VAL_THREE);
    memcache.putAll(map);
    verifyStats(1, 1, SIZE_ONE, 3, SIZE_ONE + SIZE_TWO + SIZE_THREE, null);

    // successful multi-fetch
    map = memcache.getAll(list);
    assertThat(map.size()).isEqualTo(2);
    assertThat(map.get(ONE)).isEqualTo(VAL_ONE);
    assertThat(map.get(TWO)).isEqualTo(VAL_TWO);

    verifyStats(3, 1, (2 * SIZE_ONE) + SIZE_TWO, 3, SIZE_ONE + SIZE_TWO + SIZE_THREE, null);
  }

  @Test
  public void testAddReplace() {
    // initial replace fails...
    boolean set = memcache.put(ONE, VAL_ONE, null, SetPolicy.REPLACE_ONLY_IF_PRESENT);
    assertThat(set).isFalse();
    verifyStats(0, 0, 0, 0, 0, 0);

    // initial add succeeds...
    set = memcache.put(ONE, VAL_ONE, null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    assertThat(set).isTrue();
    verifyStats(0, 0, 0, 1, SIZE_ONE, null);

    // subsequent add fails...
    set = memcache.put(ONE, VAL_TWO, null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT);
    assertThat(set).isFalse();
    verifyStats(0, 0, 0, 1, SIZE_ONE, null);

    // subsequent replace succeeds...
    set = memcache.put(ONE, VAL_TWO, null, SetPolicy.REPLACE_ONLY_IF_PRESENT);
    assertThat(set).isTrue();
    verifyStats(0, 0, 0, 1, 5 + VAL_TWO.length(), null);
  }

  @Test
  public void testMaxAgeStats() {
    // good initial state...
    verifyStats(0, 0, 0, 0, 0, 0);

    // add one; initially, max age will still be 0 (under 1 sec since add)
    memcache.put(ONE, VAL_ONE);
    verifyStats(0, 0, 0, 1, SIZE_ONE, 0);

    // sleep, check that it ages
    sleep(1200);
    verifyStats(0, 0, 0, 1, SIZE_ONE, 1);

    // set a second value, to check (a) removal to non-zero, and (b) re-touch
    memcache.put(TWO, VAL_TWO);
    sleep(1200);

    verifyStats(0, 0, 0, 2, SIZE_ONE + SIZE_TWO, 2);

    // discard first value (back to oldest is 1sec old, added at sec #1)
    memcache.delete(ONE);
    verifyStats(0, 0, 0, 1, SIZE_TWO, 1);

    // update 2nd, oldest value; should reset time to 0
    memcache.put(TWO, VAL_TWO);
    verifyStats(0, 0, 0, 1, SIZE_TWO, 0);

    // discard last value (back to empty-state zero)
    memcache.delete(TWO);
    verifyStats(0, 0, 0, 0, 0, 0);
  }

  @Test
  public void testSizeLimit() {
    // discard our setup memcache, for a restricted one
    helper.tearDown();
    memcache = null;
    helper.setUp();
    ApiProxyLocal delegate = LocalServiceTestHelper.getApiProxyLocal();
    delegate.setProperty(LocalMemcacheService.SIZE_PROPERTY, "35");
    memcache = MemcacheServiceFactory.getMemcacheService();

    // good initial state...
    verifyStats(0, 0, 0, 0, 0, 0);

    // add two items, filling the cache
    memcache.put(ONE, VAL_ONE);
    memcache.put(TWO, VAL_TWO);
    verifyStats(null, null, null, 2, SIZE_ONE + SIZE_TWO, null);

    // now, overflow one...
    memcache.put(THREE, VAL_THREE);
    assertThat(memcache.contains(TWO)).isTrue();
    assertThat(memcache.contains(THREE)).isTrue();
    verifyStats(null, null, null, 2, SIZE_THREE + SIZE_TWO, null);

    // and make sure increment tracks as an LRU policy...
    memcache.increment(TWO, 1);
    verifyStats(null, null, null, 2, SIZE_THREE + SIZE_TWO, null);

    // ...by replacing ONE and seeing THREE go away
    memcache.put(ONE, VAL_ONE);
    assertThat(memcache.contains(ONE)).isTrue();
    assertThat(memcache.contains(TWO)).isTrue();
    verifyStats(null, null, null, 2, SIZE_ONE + SIZE_TWO, null);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testNamespaces() {
    MemcacheService ns1 = MemcacheServiceFactory.getMemcacheService();
    ns1.setNamespace("ns1");
    MemcacheService ns2 = MemcacheServiceFactory.getMemcacheService();
    ns2.setNamespace("ns2");
    verifyStats(0, 0, 0, 0, 0, 0);
    ns1.put(ONE, VAL_ONE);
    ns2.put(TWO, VAL_TWO);
    memcache.put(THREE, VAL_THREE);
    verifyStats(null, null, null, 3, SIZE_ONE + SIZE_TWO + SIZE_THREE, null);

    assertThat(memcache.get(ONE)).isNull();
    assertThat(memcache.get(TWO)).isNull();
    assertThat(memcache.get(THREE)).isEqualTo(VAL_THREE);

    assertThat(ns1.get(ONE)).isEqualTo(VAL_ONE);
    assertThat(ns1.get(TWO)).isNull();
    assertThat(ns1.get(THREE)).isNull();

    assertThat(ns2.get(ONE)).isNull();
    assertThat(ns2.get(TWO)).isEqualTo(VAL_TWO);
    assertThat(ns2.get(THREE)).isNull();
    verifyStats(3, 6, null, 3, SIZE_ONE + SIZE_TWO + SIZE_THREE, null);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testLRU() {
    // Testing that we push out entries from other namespaces.
    // discard our setup memcache, for a restricted one
    ApiProxyLocal delegate = LocalServiceTestHelper.getApiProxyLocal();
    delegate.setProperty(LocalMemcacheService.SIZE_PROPERTY, "35");
    memcache = MemcacheServiceFactory.getMemcacheService();
    MemcacheService ns1 = MemcacheServiceFactory.getMemcacheService();
    ns1.setNamespace("ns1");
    MemcacheService ns2 = MemcacheServiceFactory.getMemcacheService();
    ns2.setNamespace("ns2");

    // good initial state...
    verifyStats(0, 0, 0, 0, 0, 0);

    // add two items, filling the cache
    ns1.put(ONE, VAL_ONE, null, SetPolicy.SET_ALWAYS);
    ns2.put(TWO, VAL_TWO, null, SetPolicy.SET_ALWAYS);
    verifyStats(null, null, null, 2, SIZE_ONE + SIZE_TWO, null);

    // now, overflow one...
    memcache.put(THREE, VAL_THREE);
    assertThat(ns1.contains(ONE)).isFalse();
    assertThat(ns2.contains(TWO)).isTrue();
    assertThat(memcache.contains(THREE)).isTrue();
    verifyStats(null, null, null, 2, SIZE_THREE + SIZE_TWO, null);

    // and make sure increment tracks as an LRU policy...
    ns2.increment(TWO, 1);
    verifyStats(null, null, null, 2, SIZE_THREE + SIZE_TWO, null);

    // ...by replacing ONE and seeing THREE go away
    ns1.put(ONE, VAL_ONE, null, SetPolicy.SET_ALWAYS);
    assertThat(ns1.contains(ONE)).isTrue();
    assertThat(ns2.contains(TWO)).isTrue();
    assertThat(memcache.contains(THREE)).isFalse();
    verifyStats(null, null, null, 2, SIZE_ONE + SIZE_TWO, null);
  }

  @Test
  public void testPutIfUntouched() {
    IdentifiableValue v;
    String k;

    verifyStats(0, 0, 0, 0, 0, 0);

    // An "untouched" put should succeed.
    k = "k1";
    memcache.put(k, "foo");
    verifyStats(0, 0, null, 1, null, null);
    v = memcache.getIdentifiable(k);
    assertThat(v.getValue()).isEqualTo("foo");
    assertThat(memcache.putIfUntouched(k, v, "bar")).isTrue();
    verifyStats(1, 0, null, 1, null, null);
    assertThat(memcache.get(k)).isEqualTo("bar");
    verifyStats(2, 0, null, 1, null, null);

    // A "touched" put should fail.
    k = "k2";
    memcache.put(k, "foo");
    verifyStats(2, 0, null, 2, null, null);
    v = memcache.getIdentifiable(k);
    assertThat(v.getValue()).isEqualTo("foo");
    verifyStats(3, 0, null, 2, null, null);
    memcache.put(k, "bar");
    assertThat(memcache.putIfUntouched(k, v, "baz")).isFalse();
    assertThat(memcache.get(k)).isEqualTo("bar");
    verifyStats(4, 0, null, 2, null, null);
  }

  @Test
  public void testPutIfUntouchedMisuses() {
    // Both versions of memcache.putIfUntouched() should
    //   throw an IllegalArgumentException if oldValue is null.
    assertThrows(
        IllegalArgumentException.class, () -> memcache.putIfUntouched("k", null, "foo", null));
    assertThrows(IllegalArgumentException.class, () -> memcache.putIfUntouched("k", null, "foo"));
  }

  @Test
  public void testSettingSameObjectDoesNotLeak() {
    // Verify that the LRU starts out empty
    assertThat(getLocalMemcacheService().getLRU().getChainLength()).isEqualTo(0);

    for (int i = 0; i < 10; i++) {
      memcache.put("this", "that");
    }
    // There is only 1 key in the cache so the LRU should only have 1 entry.
    assertThat(getLocalMemcacheService().getLRU().getChainLength()).isEqualTo(1);

    // Now repeat the test but with different values, expiration values, and set policies.
    for (int i = 0; i < 10; i++) {
      for (SetPolicy policy : SetPolicy.values()) {
        memcache.put("this", "that" + i, Expiration.byDeltaSeconds(60), policy);
      }
    }
    // It's all the same key so the LRU should still only have 1 entry.
    assertThat(getLocalMemcacheService().getLRU().getChainLength()).isEqualTo(1);
  }

  @Test
  public void testIncrementDoesNotLeak() {
    // Verify that the LRU starts out empty
    assertThat(getLocalMemcacheService().getLRU().getChainLength()).isEqualTo(0);

    memcache.put("this", 1);
    // There is only 1 key in the cache so the LRU should only have 1 entry.
    assertThat(getLocalMemcacheService().getLRU().getChainLength()).isEqualTo(1);
    for (int i = 0; i < 10; i++) {
      memcache.increment("this", 1);
    }
    // There is still only 1 key in the cache so the LRU should only have 1 entry.
    assertThat(getLocalMemcacheService().getLRU().getChainLength()).isEqualTo(1);
  }

  @Test
  public void testIncrementAfterDeleteWithTimeout() {
    String key = "Deleted!!!";
    memcache.put(key, 37);
    memcache.delete(key);

    // Delete should have gone through, so increment should restart at the
    // given initial value
    Long expected = 1L;
    assertThat(memcache.increment(key, 1L, 0L)).isEqualTo(expected);

    // Now delete with a timeout
    memcache.delete(key, 5000);

    // Increment should still restart from the specified initial value during the
    // lock period, as opposed to incrementing from the value we just deleted.
    // See b/7104813
    expected = 11L;
    assertThat(memcache.increment(key, 1L, 10L)).isEqualTo(expected);
  }

  @Test
  public void testReplaceAfterDeleteWithTimeout() {
    String key = "Deleted!!!";
    memcache.put(key, 37);
    memcache.delete(key, 5000);

    // During the timeout period, add should fail
    assertThat(
            memcache.put(
                key,
                42L,
                Expiration.byDeltaSeconds(10),
                MemcacheService.SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isFalse();

    // During the timeout period, a replace should also normally fail
    assertThat(
            memcache.put(
                key,
                42L,
                Expiration.byDeltaSeconds(10),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT))
        .isFalse();

    // An unconditional put, however, should still work during the timeout
    memcache.put(key, 1337L);
    assertThat(memcache.get(key)).isEqualTo(1337L);

    // Now that the put has happened, a replace should work
    assertThat(
            memcache.put(
                key,
                42L,
                Expiration.byDeltaSeconds(10),
                MemcacheService.SetPolicy.REPLACE_ONLY_IF_PRESENT))
        .isTrue();
    assertThat(memcache.get(key)).isEqualTo(42L);
  }

  // This is a verifying test for fixing b/11468252.
  @Test
  public void testDeltaExpirationTime() {
    String key = "delta-key";
    int expiration = 30 * 24 * 60 * 60; // 30 days.
    assertThat(
            memcache.put(
                key,
                12345L,
                Expiration.byDeltaSeconds(expiration),
                MemcacheService.SetPolicy.SET_ALWAYS))
        .isTrue();
    assertThat(memcache.get(key)).isEqualTo(12345L);
  }

  /**
   * Helper method to check the current statistics snapshot
   *
   * @param hits
   * @param misses
   * @param hitBytes
   * @param count
   * @param totalSize
   * @param oldestAge
   */
  void verifyStats(
      Integer hits,
      Integer misses,
      Integer hitBytes,
      Integer count,
      Integer totalSize,
      Integer oldestAge) {
    Stats stats = memcache.getStatistics();
    assertThat(stats).isNotNull();
    if (hits != null) {
      assertThat(stats.getHitCount()).isEqualTo(hits.longValue());
    }
    if (misses != null) {
      assertThat(stats.getMissCount()).isEqualTo(misses.longValue());
    }
    if (hitBytes != null) {
      assertThat(stats.getBytesReturnedForHits()).isEqualTo(hitBytes.longValue());
    }
    if (count != null) {
      assertThat(stats.getItemCount()).isEqualTo(count.longValue());
    }
    if (totalSize != null) {
      assertThat(stats.getTotalItemBytes()).isEqualTo(totalSize.longValue());
    }
    if (oldestAge != null) {
      assertWithMessage("Wrong oldest age")
          .that(stats.getMaxTimeWithoutAccess())
          .isEqualTo(oldestAge.longValue());
    }
  }

  @NotThreadSafe
  private static class JavaMockClock implements Clock {
    // Hardcoded for deterministic testing.
    private long currentTime = 13333333337L;

    @Override
    public long getCurrentTime() {
      return currentTime;
    }

    void advance(long millis) {
      currentTime += millis;
    }
  }
}
