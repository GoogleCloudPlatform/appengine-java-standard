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
import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MaxEntityGroupsPerRpcTest {
  private final LocalServiceTestHelper testHelper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
  private Delegate<Environment> original;
  private RpcCountingDelegate delegate;

  private Delegate<Environment> getDelegate() {
    @SuppressWarnings("unchecked")
    Delegate<Environment> delegate = ApiProxy.getDelegate();
    return delegate;
  }

  @Before
  public void setUp() throws Exception {
    testHelper.setEnvAppId("s~foo");
    testHelper.setUp();
    original = getDelegate();
    delegate = new RpcCountingDelegate(original);
    ApiProxy.setDelegate(delegate);
  }

  @After
  public void tearDown() {
    ApiProxy.setDelegate(original);
    testHelper.tearDown();
  }

  private static class RpcCountingDelegate implements Delegate<Environment> {
    private final Delegate<Environment> delegate;
    private AtomicInteger asyncRpcs = new AtomicInteger(0);

    private RpcCountingDelegate(Delegate<Environment> delegate) {
      this.delegate = delegate;
    }

    @Override
    public byte[] makeSyncCall(
        Environment environment, String packageName, String methodName, byte[] request)
        throws ApiProxy.ApiProxyException {
      return delegate.makeSyncCall(environment, packageName, methodName, request);
    }

    @Override
    public Future<byte[]> makeAsyncCall(
        Environment environment,
        String packageName,
        String methodName,
        byte[] request,
        ApiProxy.ApiConfig apiConfig) {
      asyncRpcs.incrementAndGet();
      return delegate.makeAsyncCall(environment, packageName, methodName, request, apiConfig);
    }

    @Override
    public void log(Environment environment, ApiProxy.LogRecord record) {
      delegate.log(environment, record);
    }

    @Override
    public void flushLogs(Environment environment) {
      delegate.flushLogs(environment);
    }

    @Override
    public List<Thread> getRequestThreads(Environment environment) {
      return delegate.getRequestThreads(environment);
    }
  }

  private static int getExpectedRpcs(int maxEntityGroupsPerRpc) {
    return (10 / maxEntityGroupsPerRpc) + ((10 % maxEntityGroupsPerRpc) == 0 ? 0 : 1);
  }

  @Test
  public void testPut() {
    List<Entity> entities = Lists.newArrayList();
    for (int i = 0; i < 10; i++) {
      entities.add(new Entity("foo"));
    }

    for (int i = 1; i <= 10; i++) {
      delegate.asyncRpcs.set(0);
      DatastoreService ds = getDatastoreService(withMaxEntityGroupsPerRpc(i));
      ds.put(entities);
      assertThat(delegate.asyncRpcs.get()).isEqualTo(getExpectedRpcs(i));
    }
  }

  @Test
  public void testDelete() {
    DatastoreService ds;
    List<Key> keys = Lists.newArrayList();
    for (int i = 0; i < 10; i++) {
      keys.add(KeyFactory.createKey("foo", i + 1));
    }

    for (int i = 1; i <= 10; i++) {
      delegate.asyncRpcs.set(0);
      ds = getDatastoreService(withMaxEntityGroupsPerRpc(i));
      ds.delete(keys);
      assertThat(delegate.asyncRpcs.get()).isEqualTo(getExpectedRpcs(i));
    }
  }

  @Test
  public void testGet() {
    DatastoreService ds;
    List<Key> keys = Lists.newArrayList();
    for (int i = 0; i < 10; i++) {
      keys.add(KeyFactory.createKey("foo", i + 1));
    }

    for (int i = 1; i <= 10; i++) {
      delegate.asyncRpcs.set(0);
      ds = getDatastoreService(withMaxEntityGroupsPerRpc(i));
      ds.get(keys);
      assertThat(delegate.asyncRpcs.get()).isEqualTo(getExpectedRpcs(i));
    }
  }
}
