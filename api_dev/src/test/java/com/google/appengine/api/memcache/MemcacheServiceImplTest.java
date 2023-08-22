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

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.memcache.AsyncMemcacheServiceImpl.IdentifiableValueImpl;
import com.google.appengine.api.memcache.AsyncMemcacheServiceImpl.ItemForPeekImpl;
import com.google.appengine.api.memcache.MemcacheSerialization.Flag;
import com.google.appengine.api.memcache.MemcacheSerialization.ValueAndFlags;
import com.google.appengine.api.memcache.MemcacheService.CasValues;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;
import com.google.appengine.api.memcache.MemcacheService.ItemForPeek;
import com.google.appengine.api.memcache.MemcacheService.SetPolicy;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse.IncrementStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheServiceError;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse.SetStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MergedNamespaceStats;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.TestLogHandler;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * Unit tests for the MemcacheServiceImpl class.
 *
 */
@RunWith(JUnit4.class)
public class MemcacheServiceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final Integer INT_123 = 123;
  private static final byte[] INT_123_BYTES = "123".getBytes(UTF_8);
  private static final String ONE = "one";
  private static final String TWO = "two";
  private static final NonStringKey THREE = new NonStringKey(3);

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  private ApiProxy.Environment environment;
  private ApiProxy.ApiConfig apiConfig;

  private static class NonStringKey implements Serializable {
    final int number;

    public NonStringKey(int number) {
      this.number = number;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof NonStringKey) {
        // test only the number; maybe names are localized
        return (this.number == ((NonStringKey) other).number);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return Integer.hashCode(number);
    }
  }

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);
    environment = new MockEnvironment("some-app", "v1");
    apiConfig = new ApiProxy.ApiConfig();
    ApiProxy.setEnvironmentForCurrentThread(environment);
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  private void expectAsyncCall(String methodName, Message request, Message response) {
    byte[] responseBytes = (response == null) ? new byte[0] : response.toByteArray();
    expectAsyncCall(methodName, request, responseBytes);
  }

  private void expectAsyncCall(String methodName, Message request, byte[] responseBytes) {
    Future<byte[]> result = immediateFuture(responseBytes);
    expectAsyncCall(methodName, request, result);
  }

  private void expectAsyncCall(String methodName, Message request, Throwable ex) {
    Future<byte[]> result = immediateFailedFuture(ex);
    expectAsyncCall(methodName, request, result);
  }

  private void expectAsyncCall(String methodName, Message request, Future<byte[]> response) {
    reset(delegate);
    when(delegate.makeAsyncCall(
            same(environment),
            eq(MemcacheServiceApiHelper.PACKAGE),
            eq(methodName),
            eq(request.toByteArray()),
            eq(apiConfig)))
        .thenReturn(response);
  }

  private void expectAsyncCallWithoutReset(String methodName, Message request, Message response) {
    Future<byte[]> result = immediateFuture(response.toByteArray());
    when(delegate.makeAsyncCall(
            same(environment),
            eq(MemcacheServiceApiHelper.PACKAGE),
            eq(methodName),
            eq(request.toByteArray()),
            eq(apiConfig)))
        .thenReturn(result);
  }

  private void verifyAsyncCall(String methodName, Message request) {
    verify(delegate)
        .makeAsyncCall(
            same(environment),
            eq(MemcacheServiceApiHelper.PACKAGE),
            eq(methodName),
            eq(request.toByteArray()),
            eq(apiConfig));
  }

  private static byte[] makePbKey(Object key) {
    try {
      byte[] pbKey = MemcacheSerialization.makePbKey(key);
      assertThat(pbKey.length).isAtMost(MemcacheSerialization.MAX_KEY_BYTE_COUNT);
      return pbKey;
    } catch (IOException ex) {
      fail("MemcacheSerialization.makePbKey failed");
      return null; // will never happen
    }
  }

  private static ValueAndFlags serialize(Object value) {
    try {
      return MemcacheSerialization.serialize(value);
    } catch (IOException ex) {
      fail("MemcacheSerialization.serialize failed");
      return null; // will never happen
    }
  }

  @Test
  public void testIdentifiableValueImpl() {
    IdentifiableValue val = new IdentifiableValueImpl("str1", 1);
    assertThat(new IdentifiableValueImpl("str1", 1)).isEqualTo(val);
    assertThat(new IdentifiableValueImpl("str1", 1).hashCode()).isEqualTo(val.hashCode());
    // Explicitly call equals() to guarantee that IdentifiableValueImpl.equals() is being invoked.
    new EqualsTester().addEqualityGroup(val).addEqualityGroup("str").testEquals();
    assertThat(new IdentifiableValueImpl("str1", 2)).isNotEqualTo(val);
    assertThat(new IdentifiableValueImpl("str1", 2).hashCode()).isNotEqualTo(val.hashCode());
    assertThat(new IdentifiableValueImpl("str2", 1)).isNotEqualTo(val);
    assertThat(new IdentifiableValueImpl("str2", 1).hashCode()).isNotEqualTo(val.hashCode());

    // Test null value.
    val = new IdentifiableValueImpl(null, 1);
    assertThat(new IdentifiableValueImpl(null, 1)).isEqualTo(val);
    assertThat(new IdentifiableValueImpl(null, 1).hashCode()).isEqualTo(val.hashCode());
  }

  @Test
  public void testCasValues() {
    IdentifiableValue idVal1 = new IdentifiableValueImpl("oldVal1", 1);
    CasValues casVal = new CasValues(idVal1, "newVal1");
    assertThat(new CasValues(idVal1, "newVal1")).isEqualTo(casVal);
    assertThat(new CasValues(idVal1, "newVal1").hashCode()).isEqualTo(casVal.hashCode());
    // Explicitly call equals() to guarantee that CasValues.equals() is being invoked.
    new EqualsTester().addEqualityGroup(casVal).addEqualityGroup("str3").testEquals();
    assertThat(new CasValues(idVal1, null)).isNotEqualTo(casVal);
    assertThat(new CasValues(idVal1, null).hashCode()).isNotEqualTo(casVal.hashCode());
    IdentifiableValue idVal2 = new IdentifiableValueImpl("oldVal1", 2);
    assertThat(new CasValues(idVal2, "newVal1")).isNotEqualTo(casVal);

    // Test expiration
    Expiration exp1 = Expiration.byDeltaMillis(10);
    casVal = new CasValues(idVal1, "newVal1", exp1);
    assertThat(new CasValues(idVal1, "newVal1")).isNotEqualTo(casVal);
    assertThat(new CasValues(idVal1, "newVal1").hashCode()).isNotEqualTo(casVal.hashCode());
    assertThat(new CasValues(idVal1, "newVal1", exp1)).isEqualTo(casVal);
    assertThat(new CasValues(idVal1, "newVal1", exp1).hashCode()).isEqualTo(casVal.hashCode());
  }

  private void containsTest(MemcacheService memcache, String namespace) {
    String[] keys = {ONE, null};
    for (String key : keys) {
      byte[] oneKey = makePbKey(key);
      MemcacheGetRequest request =
          MemcacheGetRequest.newBuilder()
              .setNameSpace(namespace)
              .addKey(ByteString.copyFrom(oneKey))
              .build();
      MemcacheGetResponse response =
          MemcacheGetResponse.newBuilder()
              .addItem(
                  MemcacheGetResponse.Item.newBuilder()
                      .setFlags(Flag.UTF8.ordinal())
                      .setKey(ByteString.copyFrom(oneKey))
                      .setValue(ByteString.copyFrom(serialize(key).value)))
              .build();
      expectAsyncCall("Get", request, response);
      assertThat(memcache.contains(key)).isTrue();
      verifyAsyncCall("Get", request);
    }
  }

  @Test
  public void testContains() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    containsTest(memcache, "");
    NamespaceManager.set("ns");
    containsTest(memcache, "ns");
    memcache = new MemcacheServiceImpl("ns2");
    containsTest(memcache, "ns2");
  }

  private void oneGetTest(MemcacheService memcache, String namespace) {
    String[] keys = {ONE, TWO, null, null};
    String[] values = {ONE, null, TWO, null};
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      String value = values[i];
      byte[] pbKey = makePbKey(key);
      MemcacheGetRequest request =
          MemcacheGetRequest.newBuilder()
              .setNameSpace(namespace)
              .addKey(ByteString.copyFrom(pbKey))
              .build();
      MemcacheGetResponse response =
          MemcacheGetResponse.newBuilder()
              .addItem(
                  MemcacheGetResponse.Item.newBuilder()
                      .setKey(ByteString.copyFrom(pbKey))
                      .setFlags(value == null ? Flag.OBJECT.ordinal() : Flag.UTF8.ordinal())
                      .setValue(ByteString.copyFrom(serialize(value).value)))
              .build();
      expectAsyncCall("Get", request, response);
      assertThat(memcache.get(key)).isEqualTo(value);
    }
  }

  @Test
  public void testOneGet() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    oneGetTest(memcache, "");
    NamespaceManager.set("ns");
    oneGetTest(memcache, "ns");
    memcache = new MemcacheServiceImpl("ns2");
    oneGetTest(memcache, "ns2");
  }

  private void multiGetTest(MemcacheService memcache, String namespace) {
    byte[] oneKey = makePbKey(ONE);
    byte[] twoKey = makePbKey(TWO);
    byte[] nullKey = makePbKey(null);
    MemcacheGetRequest request =
        MemcacheGetRequest.newBuilder()
            .setNameSpace(namespace)
            .addKey(ByteString.copyFrom(oneKey))
            .addKey(ByteString.copyFrom(twoKey))
            .addKey(ByteString.copyFrom(nullKey))
            .build();
    MemcacheGetResponse response =
        MemcacheGetResponse.newBuilder()
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setFlags(Flag.INTEGER.ordinal())
                    .setKey(ByteString.copyFrom(twoKey))
                    .setValue(ByteString.copyFrom(serialize(123).value)))
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setFlags(Flag.OBJECT.ordinal())
                    .setKey(ByteString.copyFrom(nullKey))
                    .setValue(ByteString.copyFrom(serialize(null).value)))
            .build();
    expectAsyncCall("Get", request, response);
    List<String> collection = new ArrayList<>();
    collection.add(ONE);
    collection.add(TWO);
    collection.add(null);
    Map<String, Object> result = memcache.getAll(collection);
    assertThat(result).hasSize(2);
    assertThat(result.get(TWO)).isEqualTo(123);
    assertThat(result).containsKey(null);
    assertThat(result.get(null)).isNull();
  }

  @Test
  public void testMultiGet() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    multiGetTest(memcache, "");
    NamespaceManager.set("ns");
    multiGetTest(memcache, "ns");
    memcache = new MemcacheServiceImpl("ns2");
    multiGetTest(memcache, "ns2");
  }

  private void multiPutTest(
      MemcacheService memcache,
      String namespace,
      int expirySecs,
      SetPolicy setPolicy,
      Object... expectedPuts) {
    byte[] nullKey = makePbKey(null);
    byte[] twoKey = makePbKey(TWO);
    byte[] threeKey = makePbKey(THREE);

    // Assume that responses[1] (i.e., TWO) is in the cache and the
    // others aren't.
    MemcacheSetResponse.SetStatusCode[] responses = null;
    MemcacheSetRequest.SetPolicy realPolicy = null;
    if (setPolicy == null || setPolicy == SetPolicy.SET_ALWAYS) {
      realPolicy = MemcacheSetRequest.SetPolicy.SET;
      responses =
          new MemcacheSetResponse.SetStatusCode[] {
            MemcacheSetResponse.SetStatusCode.STORED,
            MemcacheSetResponse.SetStatusCode.STORED,
            MemcacheSetResponse.SetStatusCode.STORED,
          };
    } else if (setPolicy == SetPolicy.ADD_ONLY_IF_NOT_PRESENT) {
      realPolicy = MemcacheSetRequest.SetPolicy.ADD;
      responses =
          new MemcacheSetResponse.SetStatusCode[] {
            MemcacheSetResponse.SetStatusCode.STORED,
            MemcacheSetResponse.SetStatusCode.NOT_STORED,
            MemcacheSetResponse.SetStatusCode.STORED,
          };
    } else if (setPolicy == SetPolicy.REPLACE_ONLY_IF_PRESENT) {
      realPolicy = MemcacheSetRequest.SetPolicy.REPLACE;
      responses =
          new MemcacheSetResponse.SetStatusCode[] {
            MemcacheSetResponse.SetStatusCode.NOT_STORED,
            MemcacheSetResponse.SetStatusCode.STORED,
            MemcacheSetResponse.SetStatusCode.NOT_STORED,
          };
    } else {
      throw new RuntimeException("Should'nt hit this - New MemcacheService.SetPolicy value?");
    }

    MemcacheSetRequest request =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(namespace)
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(nullKey))
                    .setFlags(Flag.BYTES.ordinal())
                    .setValue(ByteString.copyFrom(INT_123_BYTES))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(twoKey))
                    .setFlags(Flag.OBJECT.ordinal())
                    .setValue(ByteString.copyFrom(serialize(THREE).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(threeKey))
                    .setFlags(Flag.OBJECT.ordinal())
                    .setValue(ByteString.copyFrom(serialize(null).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .build();
    MemcacheSetResponse response =
        MemcacheSetResponse.newBuilder()
            .addSetStatus(responses[0])
            .addSetStatus(responses[1])
            .addSetStatus(responses[2])
            .build();

    expectAsyncCall("Set", request, response);
    // use a LinkedHashMap so the order of insertion matches the request PB
    Map<Object, Object> collection = new LinkedHashMap<>();
    collection.put(null, INT_123_BYTES);
    collection.put(TWO, THREE);
    collection.put(THREE, null);

    Expiration expiration;
    if (expirySecs == 0) {
      expiration = null;
    } else {
      expiration = Expiration.onDate(new Date((long) expirySecs * 1000));
    }

    if (setPolicy == null) {
      if (expirySecs == 0) {
        memcache.putAll(collection);
      } else {
        memcache.putAll(collection, expiration);
      }
    } else {
      Set<Object> added = memcache.putAll(collection, expiration, setPolicy);
      assertThat(added).hasSize(expectedPuts.length);
      assertThat(added).containsExactlyElementsIn(expectedPuts);
    }
    verifyAsyncCall("Set", request);
  }

  @Test
  public void testMultiPutNoPolicy() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    multiPutTest(memcache, "", 0, null);
    NamespaceManager.set("ns");
    multiPutTest(memcache, "ns", 0, null);
    memcache = new MemcacheServiceImpl("ns2");
    multiPutTest(memcache, "ns2", 0, null);
  }

  @Test
  public void testMultiPutAdds() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    int expires = (int) (System.currentTimeMillis() / 1000) + 600;
    multiPutTest(memcache, "", expires, SetPolicy.ADD_ONLY_IF_NOT_PRESENT, null, THREE);
    NamespaceManager.set("ns");
    multiPutTest(memcache, "ns", expires, SetPolicy.ADD_ONLY_IF_NOT_PRESENT, null, THREE);
  }

  @Test
  public void testMultiPutSets() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    int expires = (int) (System.currentTimeMillis() / 1000) + 600;
    multiPutTest(memcache, "", expires, SetPolicy.SET_ALWAYS, null, TWO, THREE);
    NamespaceManager.set("ns");
    multiPutTest(memcache, "ns", expires, SetPolicy.SET_ALWAYS, null, TWO, THREE);
  }

  @Test
  public void testMultiPutReplace() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    int expires = (int) (System.currentTimeMillis() / 1000) + 600;
    multiPutTest(memcache, "", expires, SetPolicy.REPLACE_ONLY_IF_PRESENT, TWO);
    NamespaceManager.set("ns");
    multiPutTest(memcache, "ns", expires, SetPolicy.REPLACE_ONLY_IF_PRESENT, TWO);
  }

  @Test
  public void testOnePut() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    onePutTest(memcache, "");
    NamespaceManager.set("ns");
    onePutTest(memcache, "ns");
    memcache = new MemcacheServiceImpl("ns2");
    onePutTest(memcache, "ns2");
  }

  @Test
  public void testKeyBoundarySizes() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");

    // test zero and one length keys
    singlePutTest(memcache, "", "", "some value");
    singlePutTest(memcache, "", "x", "some value");

    // test around the boundary at which keys get hashed
    final int hashBoundary = MemcacheSerialization.MAX_KEY_BYTE_COUNT;
    for (int size = hashBoundary - 3; size <= hashBoundary + 3; ++size) {
      singlePutTest(memcache, "", Strings.repeat("x", size), "some value");
    }

    // test very large key
    singlePutTest(memcache, "", Strings.repeat("x", 2 * 1000 * 1000), "some value");
  }

  @Test
  public void testMultibyteUnicodeKeySizes() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    String zhong = "\u4e2d"; // Chinese character 中, encodes to 3 bytes in UTF-8
    singlePutTest(memcache, "", zhong, "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 81), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 82), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 82) + "a", "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 82) + "aa", "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 83), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 83) + "a", "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 83) + "aa", "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 84), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 85), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 86), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 247), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 248), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 249), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 250), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 251), "some value");
    singlePutTest(memcache, "", Strings.repeat(zhong, 2 * 1000 * 1000), "some value");

    // a few extra tests to cover all the different lengths around MAX_KEY_BYTE_COUNT
    StringBuilder key = new StringBuilder();
    key.append("\uc280"); // Euro symbol €, encodes to 3 bytes in UTF-8
    while (key.length() < MemcacheSerialization.MAX_KEY_BYTE_COUNT - 10) {
      key.append("a");
    }
    while (key.length() < MemcacheSerialization.MAX_KEY_BYTE_COUNT + 10) {
      singlePutTest(memcache, "", key.toString(), "some value");
      key.append("a");
    }
  }

  private void onePutTest(MemcacheService memcache, String namespace) {
    String[] keys = {ONE, TWO, null, null};
    String[] values = {ONE, null, ONE, null};
    for (int i = 0; i < keys.length; i++) {
      singlePutTest(memcache, namespace, keys[i], values[i]);
    }
  }

  private void singlePutTest(MemcacheService memcache, String namespace, Object key, String value) {
    byte[] keyValue = makePbKey(key);
    MemcacheSetRequest request =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(namespace)
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(keyValue))
                    .setFlags(value == null ? Flag.OBJECT.ordinal() : Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value).value))
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.SET)
                    .setExpirationTime(0))
            .build();
    MemcacheSetResponse response =
        MemcacheSetResponse.newBuilder()
            .addSetStatus(MemcacheSetResponse.SetStatusCode.STORED)
            .build();
    expectAsyncCall("Set", request, response);
    assertThat(memcache.put(key, value, null, SetPolicy.SET_ALWAYS)).isTrue();
    verifyAsyncCall("Set", request);
  }

  private void multiDeleteTest(MemcacheService memcache, String namespace, int timeoutMillis) {
    MemcacheDeleteRequest request =
        MemcacheDeleteRequest.newBuilder()
            .setNameSpace(namespace)
            .addItem(
                MemcacheDeleteRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(ONE)))
                    .setDeleteTime(timeoutMillis / 1000))
            .addItem(
                MemcacheDeleteRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(null)))
                    .setDeleteTime(timeoutMillis / 1000))
            .build();
    MemcacheDeleteResponse response;
    if (timeoutMillis == 0) {
      response =
          MemcacheDeleteResponse.newBuilder()
              .addDeleteStatus(MemcacheDeleteResponse.DeleteStatusCode.NOT_FOUND)
              .addDeleteStatus(MemcacheDeleteResponse.DeleteStatusCode.DELETED)
              .build();
    } else {
      response =
          MemcacheDeleteResponse.newBuilder()
              .addDeleteStatus(MemcacheDeleteResponse.DeleteStatusCode.DELETED)
              .addDeleteStatus(MemcacheDeleteResponse.DeleteStatusCode.NOT_FOUND)
              .build();
    }
    expectAsyncCall("Delete", request, response);
    ArrayList<String> collection = new ArrayList<>();
    collection.add(ONE);
    collection.add(null);
    if (timeoutMillis == 0) {
      Set<String> resp = memcache.deleteAll(collection);
      assertThat(resp).hasSize(1);
      assertThat(resp).contains(null);
    } else {
      Set<String> resp = memcache.deleteAll(collection, timeoutMillis);
      assertThat(resp).hasSize(1);
      assertThat(resp).contains(ONE);
    }
    verifyAsyncCall("Delete", request);
  }

  @Test
  public void testMultiDeleteNoHold() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    multiDeleteTest(memcache, "", 0);
    NamespaceManager.set("ns");
    multiDeleteTest(memcache, "ns", 0);
    memcache = new MemcacheServiceImpl("ns2");
    multiDeleteTest(memcache, "ns2", 0);
  }

  @Test
  public void testMultiDeleteAndHold() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    multiDeleteTest(memcache, "", 10000);
    NamespaceManager.set("ns");
    multiDeleteTest(memcache, "ns", 10000);
    memcache = new MemcacheServiceImpl("ns2");
    multiDeleteTest(memcache, "ns2", 10000);
  }

  private void oneDeleteTest(MemcacheService memcache, String namespace, int timeoutMillis) {
    String[] keys = {ONE, null};
    for (String key : keys) {
      MemcacheDeleteRequest request =
          MemcacheDeleteRequest.newBuilder()
              .setNameSpace(namespace)
              .addItem(
                  MemcacheDeleteRequest.Item.newBuilder()
                      .setKey(ByteString.copyFrom(makePbKey(key)))
                      .setDeleteTime(timeoutMillis / 1000))
              .build();
      MemcacheDeleteResponse response;
      if (timeoutMillis == 0) {
        response =
            MemcacheDeleteResponse.newBuilder()
                .addDeleteStatus(MemcacheDeleteResponse.DeleteStatusCode.DELETED)
                .build();
      } else {
        response =
            MemcacheDeleteResponse.newBuilder()
                .addDeleteStatus(MemcacheDeleteResponse.DeleteStatusCode.NOT_FOUND)
                .build();
      }
      expectAsyncCall("Delete", request, response);
      if (timeoutMillis == 0) {
        assertThat(memcache.delete(key)).isTrue();
      } else {
        assertThat(memcache.delete(key, timeoutMillis)).isFalse();
      }
      verifyAsyncCall("Delete", request);
    }
  }

  @Test
  public void testOneDeleteNoHold() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    oneDeleteTest(memcache, "", 0);
    NamespaceManager.set("ns");
    oneDeleteTest(memcache, "ns", 0);
    memcache = new MemcacheServiceImpl("ns2");
    oneDeleteTest(memcache, "ns2", 0);
  }

  @Test
  public void testOneDeleteAndHold() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    oneDeleteTest(memcache, "", 10000);
    NamespaceManager.set("ns");
    oneDeleteTest(memcache, "ns", 10000);
    memcache = new MemcacheServiceImpl("ns2");
    oneDeleteTest(memcache, "ns2", 10000);
  }

  private void incrementTest(MemcacheService memcache, String namespace, Long initialValue) {
    for (Integer key : new Integer[] {INT_123, null}) {
      byte[] sha1bytes = makePbKey(key);
      MemcacheIncrementRequest.Builder requestBuilder =
          MemcacheIncrementRequest.newBuilder()
              .setNameSpace(namespace)
              .setKey(ByteString.copyFrom(sha1bytes))
              .setDelta(17)
              .setDirection(MemcacheIncrementRequest.Direction.DECREMENT);
      if (initialValue != null) {
        requestBuilder.setInitialValue(initialValue);
        requestBuilder.setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal());
      }
      MemcacheIncrementRequest request = requestBuilder.build();
      MemcacheIncrementResponse response =
          MemcacheIncrementResponse.newBuilder().setNewValue(25).build();
      expectAsyncCall("Increment", request, response);
      if (initialValue == null) {
        assertThat(memcache.increment(key, -17)).isEqualTo(Long.valueOf(25));
      } else {
        assertThat(memcache.increment(key, -17, initialValue)).isEqualTo(Long.valueOf(25));
      }
    }
  }

  @Test
  public void testIncrement() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    incrementTest(memcache, "", null);
    NamespaceManager.set("ns");
    incrementTest(memcache, "ns", null);
    NamespaceManager.set("");
    incrementTest(memcache, "", Long.valueOf(123));
    NamespaceManager.set("ns");
    incrementTest(memcache, "ns", Long.valueOf(123));
  }

  @Test
  public void testIncrementAll() {
    MemcacheBatchIncrementRequest.Builder batchRequestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace("hi");
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 1")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 2")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 3")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));

    MemcacheBatchIncrementResponse.Builder responseBuilder =
        MemcacheBatchIncrementResponse.newBuilder();
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder()
            .setNewValue(123)
            .setIncrementStatus(IncrementStatusCode.OK));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.NOT_CHANGED));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.ERROR));

    MemcacheBatchIncrementRequest request = batchRequestBuilder.build();
    MemcacheBatchIncrementResponse response = responseBuilder.build();
    expectAsyncCall("BatchIncrement", request, response);
    ArrayList<String> keys = new ArrayList<>();
    keys.add("my key 1");
    keys.add("my key 2");
    keys.add("my key 3");
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("my key 1", 123L);
    expected.put("my key 2", null);
    expected.put("my key 3", null);

    MemcacheService memcache = new MemcacheServiceImpl("hi");
    assertThat(memcache.incrementAll(keys, 22L)).isEqualTo(expected);
    verifyAsyncCall("BatchIncrement", request);
  }

  @Test
  public void testIncrementAllDifferentTypes() {
    MemcacheBatchIncrementRequest.Builder batchRequestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace("hi");
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 1")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey(null)))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey(3L)))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));

    MemcacheBatchIncrementResponse.Builder responseBuilder =
        MemcacheBatchIncrementResponse.newBuilder();
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder()
            .setNewValue(123)
            .setIncrementStatus(IncrementStatusCode.OK));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.NOT_CHANGED));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.ERROR));

    MemcacheBatchIncrementRequest request = batchRequestBuilder.build();
    MemcacheBatchIncrementResponse response = responseBuilder.build();
    expectAsyncCall("BatchIncrement", request, response);

    ArrayList<Object> keys = new ArrayList<>();
    keys.add("my key 1");
    keys.add(null);
    keys.add(3L);
    Map<Object, Long> expected = new LinkedHashMap<>();
    expected.put("my key 1", 123L);
    expected.put(null, null);
    expected.put(3L, null);

    MemcacheService memcache = new MemcacheServiceImpl("hi");
    assertThat(memcache.incrementAll(keys, 22L)).isEqualTo(expected);
    verifyAsyncCall("BatchIncrement", request);
  }

  @Test
  public void testIncrementAllKeysWithInitialValue() {
    MemcacheBatchIncrementRequest.Builder batchRequestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace("hi");
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 1")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setInitialValue(44)
            .setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal())
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 2")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setInitialValue(44)
            .setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal())
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 3")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setInitialValue(44)
            .setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal())
            .setDelta(22));

    MemcacheBatchIncrementResponse.Builder responseBuilder =
        MemcacheBatchIncrementResponse.newBuilder();
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder()
            .setNewValue(123)
            .setIncrementStatus(IncrementStatusCode.OK));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.NOT_CHANGED));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.ERROR));

    MemcacheBatchIncrementRequest request = batchRequestBuilder.build();
    MemcacheBatchIncrementResponse response = responseBuilder.build();
    expectAsyncCall("BatchIncrement", request, response);

    ArrayList<String> keys = new ArrayList<>();
    keys.add("my key 1");
    keys.add("my key 2");
    keys.add("my key 3");
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("my key 1", 123L);
    expected.put("my key 2", null);
    expected.put("my key 3", null);

    MemcacheService memcache = new MemcacheServiceImpl("hi");
    assertThat(memcache.incrementAll(keys, 22L, 44L)).isEqualTo(expected);
    verifyAsyncCall("BatchIncrement", request);
  }

  @Test
  public void testIncrementAllOffsets() {
    MemcacheBatchIncrementRequest.Builder batchRequestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace("hi");
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 1")))
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .setDelta(33));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 2")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 3")))
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .setDelta(11));

    MemcacheBatchIncrementResponse.Builder responseBuilder =
        MemcacheBatchIncrementResponse.newBuilder();
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder()
            .setNewValue(123)
            .setIncrementStatus(IncrementStatusCode.OK));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.NOT_CHANGED));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.ERROR));

    MemcacheBatchIncrementRequest request = batchRequestBuilder.build();
    MemcacheBatchIncrementResponse response = responseBuilder.build();
    expectAsyncCall("BatchIncrement", request, response);

    Map<String, Long> offsets = new LinkedHashMap<>();
    offsets.put("my key 1", -33L);
    offsets.put("my key 2", 22L);
    offsets.put("my key 3", -11L);
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("my key 1", 123L);
    expected.put("my key 2", null);
    expected.put("my key 3", null);

    MemcacheService memcache = new MemcacheServiceImpl("hi");
    assertThat(memcache.incrementAll(offsets)).isEqualTo(expected);
  }

  @Test
  public void testIncrementAllOffsetsInitialValue() {
    MemcacheBatchIncrementRequest.Builder batchRequestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace("hi");
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 1")))
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .setInitialValue(44)
            .setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal())
            .setDelta(33));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 2")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setInitialValue(44)
            .setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal())
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 3")))
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .setInitialValue(44)
            .setInitialFlags(MemcacheSerialization.Flag.LONG.ordinal())
            .setDelta(11));

    MemcacheBatchIncrementResponse.Builder responseBuilder =
        MemcacheBatchIncrementResponse.newBuilder();
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder()
            .setNewValue(123)
            .setIncrementStatus(IncrementStatusCode.OK));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.NOT_CHANGED));
    responseBuilder.addItem(
        MemcacheIncrementResponse.newBuilder().setIncrementStatus(IncrementStatusCode.ERROR));

    MemcacheBatchIncrementRequest request = batchRequestBuilder.build();
    MemcacheBatchIncrementResponse response = responseBuilder.build();
    expectAsyncCall("BatchIncrement", request, response);

    Map<String, Long> offsets = new LinkedHashMap<>();
    offsets.put("my key 1", -33L);
    offsets.put("my key 2", 22L);
    offsets.put("my key 3", -11L);
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("my key 1", 123L);
    expected.put("my key 2", null);
    expected.put("my key 3", null);

    MemcacheService memcache = new MemcacheServiceImpl("hi");
    assertThat(memcache.incrementAll(offsets, 44L)).isEqualTo(expected);
    verifyAsyncCall("BatchIncrement", request);
  }

  @Test
  public void testIncrementAllFailed() {
    MemcacheBatchIncrementRequest.Builder batchRequestBuilder =
        MemcacheBatchIncrementRequest.newBuilder().setNameSpace("hi");
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 1")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 2")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));
    batchRequestBuilder.addItem(
        MemcacheIncrementRequest.newBuilder()
            .setKey(ByteString.copyFrom(makePbKey("my key 3")))
            .setDirection(MemcacheIncrementRequest.Direction.INCREMENT)
            .setDelta(22));

    MemcacheBatchIncrementRequest request = batchRequestBuilder.build();
    expectAsyncCall("BatchIncrement", request, new ApiProxy.ApplicationException(1, "Error"));

    ArrayList<String> keys = new ArrayList<>();
    keys.add("my key 1");
    keys.add("my key 2");
    keys.add("my key 3");
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("my key 1", null);
    expected.put("my key 2", null);
    expected.put("my key 3", null);

    MemcacheService memcache = new MemcacheServiceImpl("hi");
    assertThat(memcache.incrementAll(keys, 22L)).isEqualTo(expected);
  }

  @Test
  public void testIncrementInvalidType() {
    MemcacheIncrementRequest request =
        MemcacheIncrementRequest.newBuilder()
            .setNameSpace("")
            .setKey(ByteString.copyFrom(makePbKey(INT_123)))
            .setDelta(17)
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .build();
    expectAsyncCall("Increment", request, new ApiProxy.ApplicationException(6, "Error"));
    assertThrows(
        InvalidValueException.class, () -> new MemcacheServiceImpl(null).increment(INT_123, -17));
  }

  @Test
  public void testIncrementMissing() {
    MemcacheIncrementRequest request =
        MemcacheIncrementRequest.newBuilder()
            .setNameSpace("")
            .setKey(ByteString.copyFrom(makePbKey(INT_123)))
            .setDelta(17)
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .build();
    MemcacheIncrementResponse response = MemcacheIncrementResponse.getDefaultInstance();
    expectAsyncCall("Increment", request, response);
    Long result = new MemcacheServiceImpl(null).increment(INT_123, -17);
    assertThat(result).isEqualTo(null);
    verifyAsyncCall("Increment", request);
  }

  @Test
  public void testClearAll() {
    MemcacheFlushRequest request = MemcacheFlushRequest.getDefaultInstance();
    MemcacheFlushResponse response = MemcacheFlushResponse.getDefaultInstance();
    expectAsyncCall("FlushAll", request, response);
    new MemcacheServiceImpl(null).clearAll();
    verifyAsyncCall("FlushAll", request);
  }

  @Test
  public void testGetStatistics() {
    MemcacheStatsRequest request = MemcacheStatsRequest.getDefaultInstance();
    MemcacheStatsResponse response =
        MemcacheStatsResponse.newBuilder()
            .setStats(
                MergedNamespaceStats.newBuilder()
                    .setHits(1)
                    .setMisses(2)
                    .setByteHits(3)
                    .setItems(4)
                    .setBytes(5)
                    .setOldestItemAge(6))
            .build();
    expectAsyncCall("Stats", request, response);
    Stats stats = new MemcacheServiceImpl(null).getStatistics();
    assertThat(stats.getHitCount()).isEqualTo(1);
    assertThat(stats.getMissCount()).isEqualTo(2);
    assertThat(stats.getBytesReturnedForHits()).isEqualTo(3);
    assertThat(stats.getItemCount()).isEqualTo(4);
    assertThat(stats.getTotalItemBytes()).isEqualTo(5);
    assertThat(stats.getMaxTimeWithoutAccess()).isEqualTo(6);
  }

  @Test
  public void testNullStatistics() {
    MemcacheStatsRequest request = MemcacheStatsRequest.getDefaultInstance();
    MemcacheStatsResponse response = MemcacheStatsResponse.getDefaultInstance();
    expectAsyncCall("Stats", request, response);
    Stats stats = new MemcacheServiceImpl(null).getStatistics();
    verifyAsyncCall("Stats", request);
    assertThat(stats).isNotNull();
    assertThat(stats.getHitCount()).isEqualTo(0);
    assertThat(stats.getMissCount()).isEqualTo(0);
    assertThat(stats.getBytesReturnedForHits()).isEqualTo(0);
    assertThat(stats.getItemCount()).isEqualTo(0);
    assertThat(stats.getTotalItemBytes()).isEqualTo(0);
    assertThat(stats.getMaxTimeWithoutAccess()).isEqualTo(0);
  }

  @Test
  public void testErrorHandlingPermissive() {
    errorHandlingPermissiveTest(false, ErrorHandlers.getDefault());
  }

  @Test
  public void testConsistentErrorHandlingPermissive() {
    errorHandlingPermissiveTest(true, ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
  }

  private void errorHandlingPermissiveTest(boolean isConsistent, ErrorHandler errorHandler) {
    byte[] oneKey = makePbKey(ONE);
    byte[] oneValue = serialize(ONE).value;

    MemcacheService memcache = new MemcacheServiceImpl("");
    memcache.setErrorHandler(errorHandler);

    // Get calls
    MemcacheGetRequest getRequest =
        MemcacheGetRequest.newBuilder()
            .setNameSpace("")
            .addKey(ByteString.copyFrom(oneKey))
            .build();
    expectAsyncCall("Get", getRequest, new ApiProxy.ApplicationException(1, "Error"));
    Object value = memcache.get(ONE);
    assertThat(value).isNull();
    verifyAsyncCall("Get", getRequest);

    expectAsyncCall("Get", getRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(memcache.contains(ONE)).isFalse();
    verifyAsyncCall("Get", getRequest);

    expectAsyncCall("Get", getRequest, new byte[] {10, 20});
    assertThat(memcache.get(ONE)).isNull();
    verifyAsyncCall("Get", getRequest);

    // Set calls
    MemcacheSetRequest setRequest =
        MemcacheSetRequest.newBuilder()
            .setNameSpace("")
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(oneKey))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(oneValue))
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.ADD)
                    .setExpirationTime(0))
            .build();
    // Because of AllowPartialResults for Set method, Set should almost always succeed and return
    // any error code in the response payload. Application error can occur only for early checks,
    // e.g. out of quota, ShardLock check failure or missing ServingState.
    expectAsyncCall("Set", setRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT)).isFalse();
    verifyAsyncCall("Set", setRequest);

    expectAsyncCall("Set", setRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(
            memcache.putAll(ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isEmpty();
    verifyAsyncCall("Set", setRequest);

    // Successful set -> no error logged or exception thrown.
    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());
    assertThat(memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT)).isTrue();
    verifyAsyncCall("Set", setRequest);

    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());
    assertThat(
            memcache.putAll(ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isEqualTo(ImmutableSet.of(ONE));
    verifyAsyncCall("Set", setRequest);

    // Item not stored because of set policy -> logs an error or throws, depending on the error
    // handler used (for backwards compatibility).
    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.ERROR).build());
    if (isConsistent) {
      assertThat(memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT)).isFalse();
    } else {
      assertThrows(
          MemcacheServiceException.class,
          () -> memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT));
    }
    verifyAsyncCall("Set", setRequest);

    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.ERROR).build());
    if (isConsistent) {
      assertThat(
              memcache.putAll(ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
          .isEmpty();
    } else {
      assertThrows(
          MemcacheServiceException.class,
          () ->
              memcache.putAll(
                  ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT));
    }
    verifyAsyncCall("Set", setRequest);

    // Increment calls
    MemcacheIncrementRequest incrementRequest =
        MemcacheIncrementRequest.newBuilder()
            .setNameSpace("")
            .setKey(ByteString.copyFrom(makePbKey(INT_123)))
            .setDelta(17)
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .build();
    expectAsyncCall("Increment", incrementRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(memcache.increment(INT_123, -17)).isEqualTo(null);
    verifyAsyncCall("Increment", incrementRequest);

    expectAsyncCall("Increment", incrementRequest, new ApiProxy.ApplicationException(6, "Error"));
    assertThrows(InvalidValueException.class, () -> memcache.increment(INT_123, -17));
    verifyAsyncCall("Increment", incrementRequest);

    // BatchIncrement calls
    MemcacheBatchIncrementRequest batchIncrementRequest =
        MemcacheBatchIncrementRequest.newBuilder()
            .setNameSpace("")
            .addItem(
                MemcacheIncrementRequest.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(INT_123)))
                    .setDelta(17)
                    .setDirection(MemcacheIncrementRequest.Direction.DECREMENT))
            .build();
    expectAsyncCall(
        "BatchIncrement", batchIncrementRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(memcache.incrementAll(Arrays.asList(INT_123), -17))
        .isEqualTo(Collections.singletonMap(INT_123, null));
    verifyAsyncCall("BatchIncrement", batchIncrementRequest);

    expectAsyncCall(
        "BatchIncrement",
        batchIncrementRequest,
        MemcacheBatchIncrementResponse.newBuilder()
            .addItem(
                MemcacheIncrementResponse.newBuilder()
                    .setIncrementStatus(IncrementStatusCode.ERROR))
            .build());
    assertThat(memcache.incrementAll(Arrays.asList(INT_123), -17))
        .isEqualTo(Collections.singletonMap(INT_123, null));
    verifyAsyncCall("BatchIncrement", batchIncrementRequest);

    // Delete calls
    MemcacheDeleteRequest deleteRequest =
        MemcacheDeleteRequest.newBuilder()
            .setNameSpace("")
            .addItem(
                MemcacheDeleteRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(oneKey))
                    .setDeleteTime(0))
            .build();
    expectAsyncCall("Delete", deleteRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(memcache.delete(ONE)).isFalse();
    verifyAsyncCall("Delete", deleteRequest);

    expectAsyncCall("Delete", deleteRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThat(memcache.deleteAll(Arrays.asList(ONE))).isEmpty();
    verifyAsyncCall("Delete", deleteRequest);

    // Flush calls
    MemcacheFlushRequest flushRequest = MemcacheFlushRequest.getDefaultInstance();
    expectAsyncCall("FlushAll", flushRequest, new ApiProxy.ApplicationException(1, "Error"));
    memcache.clearAll();
    verifyAsyncCall("FlushAll", flushRequest);
  }

  @Test
  public void testErrorHandlingStrict() {
    byte[] oneKey = makePbKey(ONE);
    byte[] oneValue = serialize(ONE).value;

    MemcacheService memcache = new MemcacheServiceImpl("");
    memcache.setErrorHandler(ErrorHandlers.getStrict());

    MemcacheGetRequest getRequest =
        MemcacheGetRequest.newBuilder()
            .setNameSpace("")
            .addKey(ByteString.copyFrom(oneKey))
            .build();
    expectAsyncCall("Get", getRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(MemcacheServiceException.class, () -> memcache.get(ONE));
    verifyAsyncCall("Get", getRequest);

    expectAsyncCall("Get", getRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(MemcacheServiceException.class, () -> memcache.contains(ONE));
    verifyAsyncCall("Get", getRequest);

    MemcacheSetRequest setRequest =
        MemcacheSetRequest.newBuilder()
            .setNameSpace("")
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(oneKey))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(oneValue))
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.ADD)
                    .setExpirationTime(0))
            .build();
    expectAsyncCall("Set", setRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(
        MemcacheServiceException.class,
        () -> memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT));
    verifyAsyncCall("Set", setRequest);

    expectAsyncCall("Set", setRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(
        MemcacheServiceException.class,
        () ->
            memcache.putAll(ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT));
    verifyAsyncCall("Set", setRequest);

    // Successful set -> no error logged or exception thrown.
    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());
    assertThat(memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT)).isTrue();
    verifyAsyncCall("Set", setRequest);

    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.STORED).build());
    assertThat(
            memcache.putAll(ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT))
        .isEqualTo(ImmutableSet.of(ONE));
    verifyAsyncCall("Set", setRequest);

    // Item not stored because of set policy.
    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.ERROR).build());
    assertThrows(
        MemcacheServiceException.class,
        () -> memcache.put(ONE, "one", null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT));
    verifyAsyncCall("Set", setRequest);

    expectAsyncCall(
        "Set",
        setRequest,
        MemcacheSetResponse.newBuilder().addSetStatus(SetStatusCode.ERROR).build());
    assertThrows(
        MemcacheServiceException.class,
        () ->
            memcache.putAll(ImmutableMap.of(ONE, "one"), null, SetPolicy.ADD_ONLY_IF_NOT_PRESENT));
    verifyAsyncCall("Set", setRequest);

    MemcacheIncrementRequest incrementRequest =
        MemcacheIncrementRequest.newBuilder()
            .setNameSpace("")
            .setKey(ByteString.copyFrom(makePbKey(INT_123)))
            .setDelta(17)
            .setDirection(MemcacheIncrementRequest.Direction.DECREMENT)
            .build();
    expectAsyncCall("Increment", incrementRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(MemcacheServiceException.class, () -> memcache.increment(INT_123, -17));
    verifyAsyncCall("Increment", incrementRequest);

    expectAsyncCall("Increment", incrementRequest, new ApiProxy.ApplicationException(6, "Error"));
    assertThrows(InvalidValueException.class, () -> memcache.increment(INT_123, -17));
    verifyAsyncCall("Increment", incrementRequest);

    MemcacheBatchIncrementRequest batchIncrementRequest =
        MemcacheBatchIncrementRequest.newBuilder()
            .setNameSpace("")
            .addItem(
                MemcacheIncrementRequest.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(INT_123)))
                    .setDelta(17)
                    .setDirection(MemcacheIncrementRequest.Direction.DECREMENT))
            .build();
    expectAsyncCall(
        "BatchIncrement", batchIncrementRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(
        MemcacheServiceException.class, () -> memcache.incrementAll(Arrays.asList(INT_123), -17));
    verifyAsyncCall("BatchIncrement", batchIncrementRequest);

    // Partial BatchIncrement error doesn't throw, but returns null.
    expectAsyncCall(
        "BatchIncrement",
        batchIncrementRequest,
        MemcacheBatchIncrementResponse.newBuilder()
            .addItem(
                MemcacheIncrementResponse.newBuilder()
                    .setIncrementStatus(IncrementStatusCode.ERROR))
            .build());
    assertThat(memcache.incrementAll(Arrays.asList(INT_123), -17)).containsExactly(INT_123, null);

    MemcacheDeleteRequest deleteRequest =
        MemcacheDeleteRequest.newBuilder()
            .setNameSpace("")
            .addItem(
                MemcacheDeleteRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(oneKey))
                    .setDeleteTime(0))
            .build();
    expectAsyncCall("Delete", deleteRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(MemcacheServiceException.class, () -> memcache.delete(ONE));
    verifyAsyncCall("Delete", deleteRequest);

    expectAsyncCall("Delete", deleteRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(MemcacheServiceException.class, () -> memcache.deleteAll(Arrays.asList(ONE)));
    verifyAsyncCall("Delete", deleteRequest);

    MemcacheFlushRequest flushRequest = MemcacheFlushRequest.getDefaultInstance();
    expectAsyncCall("FlushAll", flushRequest, new ApiProxy.ApplicationException(1, "Error"));
    assertThrows(MemcacheServiceException.class, memcache::clearAll);
    verifyAsyncCall("FlushAll", flushRequest);
  }

  private MemcacheService setupOnePutWithError(
      String namespace, String key, String value, Exception injected) {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    memcache.setNamespace(namespace); // using deprecated method to bypass validation

    // Setting up the mock, so that when it sees this large value
    // it will get the exception that would result from an
    // RPC application error of INVALID_REQUEST
    MemcacheSetRequest request =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(nullToEmpty(namespace))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(key)))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value).value))
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.SET)
                    .setExpirationTime(0))
            .build();
    expectAsyncCall("Set", request, injected);
    return memcache;
  }

  private void testOnePutWithException(
      String namespace, String key, String value, Exception injected, String expectedInException) {
    MemcacheService memcache = setupOnePutWithError(namespace, key, value, injected);

    // Make sure that an RPC application error of INVALID_REQUEST will result
    // in an exception with a message containing the expected string
    memcache.setErrorHandler(ErrorHandlers.getStrict());
    MemcacheServiceException ex =
        assertThrows(MemcacheServiceException.class, () -> memcache.put(key, value));
    assertWithMessage("Expected exception to mention \"" + expectedInException + "\"")
        .that(ex)
        .hasMessageThat()
        .ignoringCase()
        .contains(expectedInException);
  }

  private void testOnePutIgnoringError(
      String namespace, String key, String value, Exception injected) {
    MemcacheService memcache = setupOnePutWithError(namespace, key, value, injected);

    // Make sure that an RPC application error of INVALID_REQUEST will not result in an exception
    // being thrown when using error-catching handler.
    memcache.setErrorHandler(new ConsistentLogAndContinueErrorHandler(Level.ALL));
    memcache.put(key, value);
  }

  /** Make sure exceptions are logged. */
  @Test
  public void testLogging() {
    TestLogHandler handler = new TestLogHandler();
    Logger logger = Logger.getLogger(MemcacheServiceApiHelper.class.getName());
    logger.addHandler(handler);
    try {
      testOnePutIgnoringError(
          null,
          ONE,
          "my #1 value",
          new ApiProxy.ApplicationException(
              MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE, "my #1 exception message"));
      assertWithMessage("expecting 1 log message").that(handler.getStoredLogRecords()).hasSize(1);
      String logged = handler.getStoredLogRecords().get(0).getMessage();
      assertWithMessage(logged).that(logged.length() < 200).isTrue();
      assertWithMessage(logged).that(logged.contains("my #1 value")).isTrue();
      assertWithMessage(logged).that(logged.contains("my #1 exception message")).isTrue();
    } finally {
      logger.removeHandler(handler);
    }
  }

  /**
   * Make sure exceptions caused by very large values do not include complete text of those values
   * in the log message.
   */
  @Test
  public void testLoggingHugeValue() {
    TestLogHandler handler = new TestLogHandler();
    Logger logger = Logger.getLogger(MemcacheServiceApiHelper.class.getName());
    logger.addHandler(handler);
    try {
      String veryLargeString = "my #2 value " + Strings.repeat("x", 1000000);
      testOnePutIgnoringError(
          null,
          ONE,
          veryLargeString,
          new ApiProxy.ApplicationException(
              MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE, "my #2 exception message"));

      String logged = handler.getStoredLogRecords().get(0).getMessage();
      int loggedCharCount = logged.length();
      assertWithMessage("string of " + loggedCharCount + " chars added to log")
          .that(loggedCharCount < 300)
          .isTrue();
      assertWithMessage(logged).that(logged.contains("my #2 value")).isTrue();
      assertWithMessage(logged).that(logged.contains("my #2 exception message")).isTrue();
      assertWithMessage(logged).that(logged.contains("...")).isTrue(); // truncation by ellipsis
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testOnePutWithOversizeValueLogsOnceToConsistentErrorHandler() {
    String tooBigValue = Strings.repeat("x", 110 * AsyncMemcacheServiceImpl.ITEM_SIZE_LIMIT / 100);
    Exception injected =
        new ApiProxy.ApplicationException(
            MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE, "Error");
    MemcacheService memcache = setupOnePutWithError(null, ONE, tooBigValue, injected);
    final AtomicInteger numCalls = new AtomicInteger(0);
    memcache.setErrorHandler(
        new ConsistentLogAndContinueErrorHandler(Level.INFO) {
          @Override
          public void handleServiceError(MemcacheServiceException ex) {
            numCalls.incrementAndGet();
            super.handleServiceError(ex);
          }
        });
    memcache.put(ONE, tooBigValue);
    assertThat(numCalls.get()).isEqualTo(1);
  }

  @Test
  public void testOnePutWithRegularSizeValueLogsOnceToConsistentErrorHandler() {
    String regularValue = "value";
    Exception injected =
        new ApiProxy.ApplicationException(
            MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE, "Error");
    MemcacheService memcache = setupOnePutWithError(null, ONE, regularValue, injected);
    final AtomicInteger numCalls = new AtomicInteger(0);
    memcache.setErrorHandler(
        new ConsistentLogAndContinueErrorHandler(Level.INFO) {
          @Override
          public void handleServiceError(MemcacheServiceException ex) {
            numCalls.incrementAndGet();
            super.handleServiceError(ex);
          }
        });
    memcache.put(ONE, regularValue);
    assertThat(numCalls.get()).isEqualTo(1);
  }

  @Test
  public void testOnePutWithOversizeValueLogsOnceToErrorHandlerAndThrows() {
    String tooBigValue = Strings.repeat("x", 110 * AsyncMemcacheServiceImpl.ITEM_SIZE_LIMIT / 100);
    Exception injected = new MemcacheServiceException("Error");
    MemcacheService memcache = setupOnePutWithError(null, ONE, tooBigValue, injected);
    final AtomicInteger numCalls = new AtomicInteger(0);
    memcache.setErrorHandler(
        new LogAndContinueErrorHandler(Level.INFO) {
          @Override
          public void handleServiceError(MemcacheServiceException ex) {
            numCalls.incrementAndGet();
            super.handleServiceError(ex);
          }
        });
    assertThrows(MemcacheServiceException.class, () -> memcache.put(ONE, tooBigValue));
    assertThat(numCalls.get()).isEqualTo(1);
  }

  @Test
  public void testOnePutWithOversizeValue() {
    String tooBigValue = Strings.repeat("x", 110 * AsyncMemcacheServiceImpl.ITEM_SIZE_LIMIT / 100);
    Exception injected =
        new ApiProxy.ApplicationException(
            MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE, "Error");
    testOnePutWithException(null, ONE, tooBigValue, injected, "bigger than maximum allowed");
    testOnePutIgnoringError(null, ONE, tooBigValue, injected);
  }

  @Test
  public void testSetTooLargeSingleItemError() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    String namespace = "";
    NamespaceManager.set(namespace);
    int expirySecs = (int) (MILLISECONDS.toSeconds(System.currentTimeMillis()) + 600);
    SetPolicy setPolicy = SetPolicy.SET_ALWAYS;
    MemcacheSetRequest.SetPolicy realPolicy = MemcacheSetRequest.SetPolicy.SET;
    Expiration expiration = Expiration.onDate(new Date(SECONDS.toMillis(expirySecs)));

    String testkey = "testkey";
    byte[] key = makePbKey(testkey);
    String value = Strings.repeat("v", (AsyncMemcacheServiceImpl.MAX_ITEM_SIZE - key.length + 1));
    int itemSize = value.length() + key.length;
    String expectedMessage =
        String.format(
            "Memcache put: Item may not be more than %d bytes in " + "length; received %d bytes.",
            AsyncMemcacheServiceImpl.MAX_ITEM_SIZE, itemSize);

    MemcacheSetRequest request =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(namespace)
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(key))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .build();
    MemcacheSetResponse response =
        MemcacheSetResponse.newBuilder()
            .addSetStatus(MemcacheSetResponse.SetStatusCode.ERROR)
            .build();

    expectAsyncCall("Set", request, response);
    MemcacheServiceException ex =
        assertThrows(
            MemcacheServiceException.class, () -> memcache.put(key, value, expiration, setPolicy));
    assertThat(ex).hasMessageThat().isEqualTo(expectedMessage);
  }

  @Test
  public void testSetTooLargeMultiError() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    String namespace = "";
    NamespaceManager.set(namespace);
    int expirySecs = (int) (MILLISECONDS.toSeconds(System.currentTimeMillis()) + 600);
    SetPolicy setPolicy = SetPolicy.SET_ALWAYS;
    MemcacheSetRequest.SetPolicy realPolicy = MemcacheSetRequest.SetPolicy.SET;
    Expiration expiration = Expiration.onDate(new Date(SECONDS.toMillis(expirySecs)));
    String expectedMessage =
        "Memcache put: 1 items failed for exceeding "
            + AsyncMemcacheServiceImpl.MAX_ITEM_SIZE
            + " bytes; keys: two. ";

    byte[] key1 = makePbKey(ONE);
    byte[] key2 = makePbKey(TWO);
    byte[] key3 = makePbKey(THREE);

    // Only the second item will fail.
    String value1 = Strings.repeat("v", 1000);
    String value2 = Strings.repeat("v", AsyncMemcacheServiceImpl.MAX_ITEM_SIZE - key2.length + 1);
    String value3 = Strings.repeat("v", AsyncMemcacheServiceImpl.MAX_ITEM_SIZE - key3.length);

    MemcacheSetRequest request =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(namespace)
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(key1))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value1).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(key2))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value2).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(key3))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value3).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .build();
    MemcacheSetResponse.SetStatusCode[] responses =
        new MemcacheSetResponse.SetStatusCode[] {
          MemcacheSetResponse.SetStatusCode.STORED,
          MemcacheSetResponse.SetStatusCode.ERROR,
          MemcacheSetResponse.SetStatusCode.STORED,
        };

    MemcacheSetResponse response =
        MemcacheSetResponse.newBuilder()
            .addSetStatus(responses[0])
            .addSetStatus(responses[1])
            .addSetStatus(responses[2])
            .build();

    Map<Serializable, String> collection =
        ImmutableMap.of(
            ONE, value1,
            TWO, value2,
            THREE, value3);

    expectAsyncCall("Set", request, response);
    MemcacheServiceException ex =
        assertThrows(
            MemcacheServiceException.class,
            () -> memcache.putAll(collection, expiration, setPolicy));
    assertThat(ex).hasMessageThat().isEqualTo(expectedMessage);
  }

  @Test
  public void testOnePutWithEmbeddedNull() {
    String keyWithEmbeddedNull = "foo\u0000bar";
    Exception injected = new MemcacheServiceException("Error");
    testOnePutWithException(null, keyWithEmbeddedNull, "some value", injected, "embedded null");
    testOnePutIgnoringError(null, keyWithEmbeddedNull, "some value", injected);
  }

  @Test
  public void testOnePutWithTooBigNamespace() {
    String tooBigNamespace = Strings.repeat("x", 110);
    Exception injected = new MemcacheServiceException("Error");
    testOnePutWithException(tooBigNamespace, ONE, "some value", injected, "namespace");
    testOnePutIgnoringError(tooBigNamespace, ONE, "some value", injected);
  }

  @Test
  public void testMultiPutWithOversizeValue() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    int expirySecs = (int) (System.currentTimeMillis() / 1000) + 600;
    SetPolicy setPolicy = SetPolicy.SET_ALWAYS;
    String namespace = "";
    MemcacheSetRequest.SetPolicy realPolicy = MemcacheSetRequest.SetPolicy.SET;
    Expiration expiration = Expiration.onDate(new Date((long) expirySecs * 1000));

    byte[] oneKey = makePbKey(ONE);
    byte[] twoKey = makePbKey(TWO);
    byte[] threeKey = makePbKey(THREE);
    String oneValue = "first value";
    String theVeryLongValue =
        Strings.repeat("x", 110 * AsyncMemcacheServiceImpl.ITEM_SIZE_LIMIT / 100);
    String threeValue = "third value";

    MemcacheSetRequest request =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(namespace)
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(oneKey))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(oneValue).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(twoKey))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(theVeryLongValue).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(threeKey))
                    .setFlags(Flag.UTF8.ordinal())
                    .setValue(ByteString.copyFrom(serialize(threeValue).value))
                    .setExpirationTime(expirySecs)
                    .setSetPolicy(realPolicy))
            .build();

    expectAsyncCall(
        "Set",
        request,
        new ApiProxy.ApplicationException(
            MemcacheServiceError.ErrorCode.UNSPECIFIED_ERROR_VALUE, "Error"));

    // ImmutableMap has deterministic ordering so the order of insertion matches the request PB
    Map<Serializable, String> collection =
        ImmutableMap.of(
            ONE, oneValue,
            TWO, theVeryLongValue,
            THREE, threeValue);

    // Make sure that an RPC application error of INVALID_REQUEST will result
    // in an exception with a message containing "bigger than maximum allowed"
    memcache.setErrorHandler(ErrorHandlers.getStrict());
    MemcacheServiceException ex =
        assertThrows(
            MemcacheServiceException.class,
            () -> memcache.putAll(collection, expiration, setPolicy));
    assertWithMessage("Expected exception to mention that maximum value exceeded but got " + ex)
        .that(ex)
        .hasMessageThat()
        .contains("bigger than maximum allowed");
  }

  @Test
  public void testDeserializationExceptionErrorHandling() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");

    IOException ioException = new IOException("deserialize test io exception");
    DeserializationExceptionGenerator.setDeserializationException(ioException);

    memcache.setErrorHandler(ErrorHandlers.getStrict());
    expectDeserializationExceptionOnGet();
    InvalidValueException e1 =
        assertThrows(InvalidValueException.class, () -> memcache.get("deserialize_error_key"));
    assertThat(e1).hasCauseThat().isEqualTo(ioException);
    SaveDeserializationExceptionErrorHandler errorHandler =
        new SaveDeserializationExceptionErrorHandler();
    memcache.setErrorHandler(errorHandler);
    expectDeserializationExceptionOnGet();
    memcache.get("deserialize_error_key");
    assertThat(errorHandler.getDeserializationException()).isNotNull();
    assertThat(errorHandler.getDeserializationException())
        .hasCauseThat()
        .isSameInstanceAs(ioException);
    ClassNotFoundException classNotFoundException =
        new ClassNotFoundException("deserialize class not found exception");
    DeserializationExceptionGenerator.setDeserializationException(classNotFoundException);

    memcache.setErrorHandler(ErrorHandlers.getStrict());
    expectDeserializationExceptionOnGet();
    InvalidValueException e2 =
        assertThrows(InvalidValueException.class, () -> memcache.get("deserialize_error_key"));
        assertThat(e2).hasCauseThat().isEqualTo(classNotFoundException);
 errorHandler = new SaveDeserializationExceptionErrorHandler();
    memcache.setErrorHandler(errorHandler);
    expectDeserializationExceptionOnGet();
    memcache.get("deserialize_error_key");
    assertThat(errorHandler.getDeserializationException()).isNotNull();
        assertThat(errorHandler.getDeserializationException())
        .hasCauseThat()
    .isSameInstanceAs(classNotFoundException);
  }

  private void expectDeserializationExceptionOnGet() {
    String key = "deserialize_error_key";
    byte[] pbKey = makePbKey(key);
    Object value = new DeserializationExceptionGenerator();
    MemcacheGetRequest request =
        MemcacheGetRequest.newBuilder().setNameSpace("").addKey(ByteString.copyFrom(pbKey)).build();
    MemcacheGetResponse response =
        MemcacheGetResponse.newBuilder()
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setKey(ByteString.copyFrom(pbKey))
                    .setFlags(Flag.OBJECT.ordinal())
                    .setValue(ByteString.copyFrom(serialize(value).value)))
            .build();
    expectAsyncCall("Get", request, response);
  }

  @Test
  public void testGetIdentifiable() {
    MemcacheServiceImpl memcache = new MemcacheServiceImpl(null);
    String namespace = "";
    long casId = 5;
    String[] keys = {ONE, TWO, null, null};
    String[] values = {ONE, null, TWO, null};
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      String value = values[i];
      byte[] pbKey = makePbKey(key);
      MemcacheGetRequest request =
          MemcacheGetRequest.newBuilder()
              .setNameSpace(namespace)
              .setForCas(true)
              .addKey(ByteString.copyFrom(pbKey))
              .build();
      MemcacheGetResponse response =
          MemcacheGetResponse.newBuilder()
              .addItem(
                  MemcacheGetResponse.Item.newBuilder()
                      .setFlags(value == null ? Flag.OBJECT.ordinal() : Flag.UTF8.ordinal())
                      .setKey(ByteString.copyFrom(pbKey))
                      .setValue(ByteString.copyFrom(serialize(value).value))
                      .setCasId(casId))
              .build();
      expectAsyncCall("Get", request, response);
      IdentifiableValueImpl v = (IdentifiableValueImpl) memcache.getIdentifiable(key);
      assertThat(v.getCasId()).isEqualTo(casId);
      assertThat(v.getValue()).isEqualTo(value);
      verifyAsyncCall("Get", request);
    }
  }

  private void multiGetIdentifiableTest(MemcacheService memcache, String namespace) {
    MemcacheGetRequest request =
        MemcacheGetRequest.newBuilder()
            .setNameSpace(namespace)
            .setForCas(true)
            .addKey(ByteString.copyFrom(makePbKey(ONE)))
            .addKey(ByteString.copyFrom(makePbKey(null)))
            .addKey(ByteString.copyFrom(makePbKey("Missing")))
            .build();
    MemcacheGetResponse response =
        MemcacheGetResponse.newBuilder()
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(ONE)))
                    .setCasId(10)
                    .setFlags(Flag.OBJECT.ordinal())
                    .setValue(ByteString.copyFrom(serialize(null).value)))
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(null)))
                    .setCasId(20)
                    .setFlags(Flag.INTEGER.ordinal())
                    .setValue(ByteString.copyFrom(serialize(456).value)))
            .build();
    expectAsyncCall("Get", request, response);
    ArrayList<String> collection = new ArrayList<>();
    collection.add(ONE);
    collection.add(null);
    collection.add("Missing");
    Map<String, IdentifiableValue> result = memcache.getIdentifiables(collection);
        assertThat(result).hasSize(2);
assertThat(result.get(ONE).getValue()).isEqualTo(null);
    assertThat(((IdentifiableValueImpl) result.get(ONE)).getCasId()).isEqualTo(10);
    assertThat(result.get(null).getValue()).isEqualTo(456);
    assertThat(((IdentifiableValueImpl) result.get(null)).getCasId()).isEqualTo(20);
    verifyAsyncCall("Get", request);
  }

  @Test
  public void testMultiGetIdentifiable() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    multiGetIdentifiableTest(memcache, "");
    NamespaceManager.set("ns");
    multiGetIdentifiableTest(memcache, "ns");
    memcache = new MemcacheServiceImpl("ns2");
    multiGetIdentifiableTest(memcache, "ns2");
  }

  @Test
  public void testGetForPeek() {
    MemcacheServiceImpl memcache = new MemcacheServiceImpl(null);
    String namespace = "";
    String[] keys = {ONE, TWO, null, null};
    String[] values = {ONE, null, TWO, null};
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      String value = values[i];
      byte[] pbKey = makePbKey(key);
      MemcacheGetRequest request =
          MemcacheGetRequest.newBuilder()
              .setNameSpace(namespace)
              .setForPeek(true)
              .addKey(ByteString.copyFrom(pbKey))
              .build();
      MemcacheGetResponse response =
          MemcacheGetResponse.newBuilder()
              .addItem(
                  MemcacheGetResponse.Item.newBuilder()
                      .setFlags(value == null ? Flag.OBJECT.ordinal() : Flag.UTF8.ordinal())
                      .setKey(ByteString.copyFrom(pbKey))
                      .setValue(ByteString.copyFrom(serialize(value).value))
                      .setTimestamps(
                          MemcacheServicePb.ItemTimestamps.newBuilder()
                              .setExpirationTimeSec(1)
                              .setLastAccessTimeSec(2)
                              .setDeleteLockTimeSec(3)
                              .build()))
              .build();
      expectAsyncCall("Get", request, response);
      ItemForPeek v = memcache.getItemForPeek(key);
      assertThat(v.getExpirationTimeSec()).isEqualTo(Long.valueOf(1));
      assertThat(v.getLastAccessTimeSec()).isEqualTo(Long.valueOf(2));
      assertThat(v.getDeleteLockTimeSec()).isEqualTo(Long.valueOf(3));
      assertThat(v.getValue()).isEqualTo(value);
      verifyAsyncCall("Get", request);
    }
  }

  private void multiItemsForPeekTest(MemcacheService memcache, String namespace) {
    MemcacheGetRequest request =
        MemcacheGetRequest.newBuilder()
            .setNameSpace(namespace)
            .setForPeek(true)
            .addKey(ByteString.copyFrom(makePbKey(ONE)))
            .addKey(ByteString.copyFrom(makePbKey(null)))
            .addKey(ByteString.copyFrom(makePbKey("Missing")))
            .build();
    MemcacheGetResponse response =
        MemcacheGetResponse.newBuilder()
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(ONE)))
                    .setTimestamps(
                        MemcacheServicePb.ItemTimestamps.newBuilder()
                            .setExpirationTimeSec(1)
                            .setLastAccessTimeSec(2)
                            .setDeleteLockTimeSec(3)
                            .build())
                    .setFlags(Flag.OBJECT.ordinal())
                    .setValue(ByteString.copyFrom(serialize(null).value)))
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(null)))
                    .setTimestamps(
                        MemcacheServicePb.ItemTimestamps.newBuilder()
                            .setExpirationTimeSec(10)
                            .setLastAccessTimeSec(20)
                            .setDeleteLockTimeSec(30)
                            .build())
                    .setFlags(Flag.INTEGER.ordinal())
                    .setValue(ByteString.copyFrom(serialize(456).value)))
            .build();
    expectAsyncCall("Get", request, response);
    ArrayList<String> collection = new ArrayList<>();
    collection.add(ONE);
    collection.add(null);
    collection.add("Missing");
    Map<String, ItemForPeek> result = memcache.getItemsForPeek(collection);
    assertThat(result).hasSize(2);
    assertThat(result.get(ONE).getValue()).isEqualTo(null);
    assertThat(((ItemForPeekImpl) result.get(ONE)).getExpirationTimeSec()).isEqualTo(1);
    assertThat(((ItemForPeekImpl) result.get(ONE)).getLastAccessTimeSec()).isEqualTo(2);
    assertThat(((ItemForPeekImpl) result.get(ONE)).getDeleteLockTimeSec()).isEqualTo(3);
    assertThat(result.get(null).getValue()).isEqualTo(456);
    assertThat(((ItemForPeekImpl) result.get(null)).getExpirationTimeSec()).isEqualTo(10);
    assertThat(((ItemForPeekImpl) result.get(null)).getLastAccessTimeSec()).isEqualTo(20);
    assertThat(((ItemForPeekImpl) result.get(null)).getDeleteLockTimeSec()).isEqualTo(30);
    verifyAsyncCall("Get", request);
  }

  @Test
  public void testMultiItemForPeek() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    multiItemsForPeekTest(memcache, "");
    NamespaceManager.set("ns");
    multiItemsForPeekTest(memcache, "ns");
    memcache = new MemcacheServiceImpl("ns2");
    multiItemsForPeekTest(memcache, "ns2");
  }

  @Test
  public void testGetWithPeekAfterDelete() {
    MemcacheService memcache = new MemcacheServiceImpl(null);
    NamespaceManager.set("");
    byte[] pbKey = makePbKey(ONE);
    MemcacheGetRequest request =
        MemcacheGetRequest.newBuilder()
            .setNameSpace("")
            .setForPeek(true)
            .addKey(ByteString.copyFrom(pbKey))
            .build();
    MemcacheGetResponse response =
        MemcacheGetResponse.newBuilder()
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setFlags(Flag.BOOLEAN.ordinal())
                    .setKey(ByteString.copyFrom(pbKey))
                    .setValue(ByteString.copyFromUtf8(""))
                    .setIsDeleteLocked(true)
                    .setTimestamps(
                        MemcacheServicePb.ItemTimestamps.newBuilder()
                            .setDeleteLockTimeSec(3)
                            .build()))
            .build();
    expectAsyncCall("Get", request, response);
    ItemForPeek v = memcache.getItemForPeek(ONE);
    assertThat(v.getDeleteLockTimeSec()).isEqualTo(Long.valueOf(3));
    assertThat(v.getValue()).isEqualTo(null);
    verifyAsyncCall("Get", request);
  }

  @Test
  public void testPutIfUntouched() {
    MemcacheServiceImpl memcache = new MemcacheServiceImpl(null);
    String namespace = "";
    long casId = 5;
    for (String key : new String[] {ONE, null}) {
      byte[] pbKey = makePbKey(key);
      MemcacheSerialization.ValueAndFlags oneVaf = serialize(ONE);
      MemcacheSerialization.ValueAndFlags twoVaf = serialize(TWO);
      MemcacheGetRequest getRequest =
          MemcacheGetRequest.newBuilder()
              .setNameSpace(namespace)
              .setForCas(true)
              .addKey(ByteString.copyFrom(pbKey))
              .build();
      MemcacheGetResponse getResponse =
          MemcacheGetResponse.newBuilder()
              .addItem(
                  MemcacheGetResponse.Item.newBuilder()
                      .setFlags(oneVaf.flags.ordinal())
                      .setKey(ByteString.copyFrom(pbKey))
                      .setValue(ByteString.copyFrom(oneVaf.value))
                      .setCasId(casId))
              .build();
      expectAsyncCall("Get", getRequest, getResponse);
      MemcacheSetRequest setRequest =
          MemcacheSetRequest.newBuilder()
              .setNameSpace(namespace)
              .addItem(
                  MemcacheSetRequest.Item.newBuilder()
                      .setValue(ByteString.copyFrom(twoVaf.value))
                      .setFlags(twoVaf.flags.ordinal())
                      .setKey(ByteString.copyFrom(pbKey))
                      .setExpirationTime(0)
                      .setSetPolicy(MemcacheSetRequest.SetPolicy.CAS)
                      .setCasId(casId))
              .build();
      MemcacheSetResponse setResponse =
          MemcacheSetResponse.newBuilder()
              .addSetStatus(MemcacheSetResponse.SetStatusCode.STORED)
              .build();
      expectAsyncCallWithoutReset("Set", setRequest, setResponse);
      IdentifiableValueImpl v = (IdentifiableValueImpl) memcache.getIdentifiable(key);
      assertThat(v.getCasId()).isEqualTo(casId);
      assertThat(v.getValue()).isEqualTo(ONE);
      assertThat(memcache.putIfUntouched(key, v, TWO)).isTrue();
      verifyAsyncCall("Set", setRequest);
    }
  }

  @Test
  public void testMultiPutIfUntouched() {
    NamespaceManager.set("");
    multiPutIfUntouched("", Expiration.onDate(new Date(100)));
    NamespaceManager.set("ns");
    multiPutIfUntouched("ns", null);
  }

  private void multiPutIfUntouched(String ns, Expiration requestExpiration) {
    CasValues one =
        new CasValues(
            new IdentifiableValueImpl(ONE, 10), "new." + ONE, Expiration.onDate(new Date(200)));
    ValueAndFlags oneValueAndFlag = serialize(one.getNewValue());
    CasValues two = new CasValues(new IdentifiableValueImpl(TWO, 20), null);
    ValueAndFlags twoValueAndFlag = serialize(two.getNewValue());
    MemcacheServiceImpl memcache = new MemcacheServiceImpl(null);
    MemcacheSetRequest setRequest =
        MemcacheSetRequest.newBuilder()
            .setNameSpace(ns)
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey(null)))
                    .setValue(ByteString.copyFrom(oneValueAndFlag.value))
                    .setFlags(oneValueAndFlag.flags.ordinal())
                    .setExpirationTime(one.getExipration().getSecondsValue())
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.CAS)
                    .setCasId(10))
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(makePbKey("2")))
                    .setValue(ByteString.copyFrom(twoValueAndFlag.value))
                    .setFlags(twoValueAndFlag.flags.ordinal())
                    .setExpirationTime(
                        requestExpiration == null ? 0 : requestExpiration.getSecondsValue())
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.CAS)
                    .setCasId(20))
            .build();
    MemcacheSetResponse setResponse =
        MemcacheSetResponse.newBuilder()
            .addSetStatus(MemcacheSetResponse.SetStatusCode.STORED)
            .addSetStatus(MemcacheSetResponse.SetStatusCode.EXISTS)
            .build();
    expectAsyncCall("Set", setRequest, setResponse);
    Map<String, CasValues> values = new LinkedHashMap<>();
    values.put(null, one);
    values.put("2", two);
    Set<String> response = memcache.putIfUntouched(values, requestExpiration);
    assertThat(response).hasSize(1);
    assertThat(response).contains(null);
    verifyAsyncCall("Set", setRequest);
  }

  /** Class that saves any de-serialization error that occurs. */
  private static final class SaveDeserializationExceptionErrorHandler
      implements ConsistentErrorHandler {
    private InvalidValueException deserializationException;

    @Override
    public void handleDeserializationError(InvalidValueException deserializationException) {
      assertThat(this.deserializationException).isNull();
      this.deserializationException = deserializationException;
    }

    public InvalidValueException getDeserializationException() {
      return deserializationException;
    }

    @Override
    public void handleServiceError(MemcacheServiceException t) {
      throw new IllegalStateException("Unexpected service error", t);
    }
  }

  /**
   * Instances of this class generate the exception specified by {@link
   * #setDeserializationException}.
   */
  private static final class DeserializationExceptionGenerator implements Serializable {

    private static IOException ioException;
    private static ClassNotFoundException classNotFoundException;

    public static void setDeserializationException(IOException ioException) {
      clearDeserializationException();
      DeserializationExceptionGenerator.ioException = ioException;
    }

    public static void setDeserializationException(ClassNotFoundException classNotFoundException) {
      clearDeserializationException();
      DeserializationExceptionGenerator.classNotFoundException = classNotFoundException;
    }

    private static void clearDeserializationException() {
      ioException = null;
      classNotFoundException = null;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      if (ioException != null) {
        throw ioException;
      } else if (classNotFoundException != null) {
        throw classNotFoundException;
      } else {
        throw new IllegalStateException("Exception not set for DeserializeExceptionGenerator");
      }
    }
  }
}
