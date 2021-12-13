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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LazyListSerializationTest {
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  @Before
  public void setUp() throws Exception {
    helper.setUp();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  @Test
  public void testSerialization() throws Exception {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    List<Entity> toPut = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      Entity e = new Entity("foo");
      toPut.add(e);
    }
    datastore.put(toPut);

    Query query = new Query("foo");
    LazyList list = (LazyList) datastore.prepare(query).asList(withChunkSize(3));
    list.get(8);
    list = roundtrip(list);
    // prevent any additional rpcs from running
    ApiProxy.Delegate<?> delegate = ApiProxy.getDelegate();
    ApiProxy.setDelegate(null);
    try {
      assertThat(list).hasSize(50);
      assertThat(list.getCursor()).isNull();
    } finally {
      ApiProxy.setDelegate(delegate);
    }
  }

  private static LazyList roundtrip(LazyList lazyList) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayInputStream bais = null;
    try {
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(lazyList);
      bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      return (LazyList) ois.readObject();
    } finally {
      baos.close();
      if (bais != null) {
        bais.close();
      }
    }
  }
}
