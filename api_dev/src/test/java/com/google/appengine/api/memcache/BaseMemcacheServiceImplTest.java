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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link BaseMemcacheServiceImpl} class.
 *
 */
@RunWith(JUnit4.class)
public class BaseMemcacheServiceImplTest {

  @Before
  public void setUp() throws Exception {
    ApiProxy.setEnvironmentForCurrentThread(new MockEnvironment("some-app", "v1"));
  }

  @Test
  public void testErrorHandler() {
    BaseMemcacheService memcache = new BaseMemcacheServiceImpl(null) {};
    assertThat(memcache.getErrorHandler().getClass()).isEqualTo(LogAndContinueErrorHandler.class);
    assertThat(memcache.getErrorHandler()).isSameInstanceAs(ErrorHandlers.getDefault());
    assertThrows(NullPointerException.class, () -> memcache.setErrorHandler(null));
    assertThat(memcache.getErrorHandler().getClass()).isEqualTo(LogAndContinueErrorHandler.class);
    memcache.setErrorHandler(ErrorHandlers.getStrict());
    assertThat(memcache.getErrorHandler()).isSameInstanceAs(ErrorHandlers.getStrict());
  }

  @Test
  public void testInvalidNamespace() {
    assertThrows(IllegalArgumentException.class, () -> new BaseMemcacheServiceImpl("\001") {});
  }

  @Test
  public void testValidNamespace() {
    BaseMemcacheServiceImpl memcache = new BaseMemcacheServiceImpl("ns") {};
    assertThat(memcache.getNamespace()).isEqualTo("ns");
    memcache = new BaseMemcacheServiceImpl(null) {};
    assertThat(memcache.getNamespace()).isNull();
    assertThat(memcache.getEffectiveNamespace()).isEmpty();
    NamespaceManager.set("ns");
    assertThat(memcache.getEffectiveNamespace()).isEqualTo("ns");
  }

  @Test
  public void testInvalidSetNamespace() {
    BaseMemcacheServiceImpl memcache = new BaseMemcacheServiceImpl(null) {};
    // For backwards-compatibility reasons, we *do not* expect an error here.
    memcache.setNamespace("\001");
  }
}
