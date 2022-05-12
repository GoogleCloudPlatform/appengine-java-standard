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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GCacheEntryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String KEY = "a key";
  private static final String VALUE = "a value";
  private static final String VALUE2 = "another value";

  @Mock private Cache cache;
  private CacheEntry entry;

  @Before
  public void setUp() throws Exception {
    entry = new GCacheEntry(cache, KEY, VALUE);
  }

  /**
   * Tests that setValue passes the correct argument to the cache and updates its value correctly.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testSetValue() throws Exception {
    when(cache.put(KEY, VALUE2)).thenReturn(VALUE);
    assertThat(entry.setValue(VALUE2)).isEqualTo(VALUE);
    assertThat(entry.getValue()).isEqualTo(VALUE2);
  }

  /** Tests that the correct key is returned. */
  @Test
  public void testGetKey() throws Exception {
    assertThat(entry.getKey()).isEqualTo(KEY);
  }

  /** Tests that a the correct value is returned. */
  @Test
  public void testGetValue() throws Exception {
    assertThat(entry.getValue()).isEqualTo(VALUE);
  }

  @Test
  public void testIsValid() throws Exception {
    when(cache.getCacheEntry(KEY))
        .thenReturn(new GCacheEntry(cache, KEY, VALUE))
        .thenReturn(new GCacheEntry(cache, KEY, VALUE2))
        .thenReturn(null);
    assertThat(entry.isValid()).isTrue();
    assertThat(entry.isValid()).isFalse();
    assertThat(entry.isValid()).isFalse();
  }

  /** Tests that all methods that are not expected to be implemented throw the correct exception. */
  @Test
  public void testUnsupportedOperations() throws Exception {
    assertThrows(UnsupportedOperationException.class, () -> entry.getCost());
    assertThrows(UnsupportedOperationException.class, () -> entry.getCreationTime());
    assertThrows(UnsupportedOperationException.class, () -> entry.getExpirationTime());
    assertThrows(UnsupportedOperationException.class, () -> entry.getHits());
    assertThrows(UnsupportedOperationException.class, () -> entry.getLastAccessTime());
    assertThrows(UnsupportedOperationException.class, () -> entry.getLastUpdateTime());
    assertThrows(UnsupportedOperationException.class, () -> entry.getVersion());
  }
}
