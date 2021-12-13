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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for the LRU used by the local memcache server
 *
 */
@RunWith(JUnit4.class)
public class LRUTest {
  private static class EmptyChainable extends LRU.AbstractChainable<EmptyChainable> {}

  private EmptyChainable e1;
  private EmptyChainable e2;
  private EmptyChainable e3;
  private LRU<EmptyChainable> lru;

  @Before
  public void setUp() throws Exception {
    e1 = new EmptyChainable();
    e2 = new EmptyChainable();
    e3 = new EmptyChainable();
    lru = new LRU<>();
  }

  private void verifyChain(LRU<EmptyChainable> lru, EmptyChainable... chain) {
    if (chain.length != 0) {
      assertThat(lru.getNewest()).isSameInstanceAs(chain[0]);
      assertThat(lru.getOldest()).isSameInstanceAs(chain[chain.length - 1]);
    } else {
      assertThat(lru.getNewest()).isNull();
      assertThat(lru.getOldest()).isNull();
    }
    for (int i = 0; i < chain.length; i++) {
      if (i != 0) {
        assertThat(chain[i].getNewer()).isSameInstanceAs(chain[i - 1]);
      } else {
        assertThat(chain[i].getNewer()).isNull();
      }
      if (i != chain.length - 1) {
        assertThat(chain[i].getOlder()).isSameInstanceAs(chain[i + 1]);
      } else {
        assertThat(chain[i].getOlder()).isNull();
      }
    }
  }

  private void verifyOrphan(EmptyChainable item) {
    assertThat(item.getNewer()).isNull();
    assertThat(item.getOlder()).isNull();
  }

  private void updateAndVerifyLength(EmptyChainable item, int expectedLength) {
    lru.update(item);
    assertThat(lru.getChainLength()).isEqualTo(expectedLength);
  }

  private void removeAndVerifyLength(EmptyChainable item, int expectedLength) {
    lru.remove(item);
    assertThat(lru.getChainLength()).isEqualTo(expectedLength);
  }

  private void removeOldestAndVerifyLength(int expectedLength) {
    lru.removeOldest();
    assertThat(lru.getChainLength()).isEqualTo(expectedLength);
  }

  @Test
  public void testAddThree() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    verifyChain(lru, e1, e2, e3);
  }

  @Test
  public void testUpdate() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    updateAndVerifyLength(e2, 3);
    verifyChain(lru, e2, e1, e3);
  }

  @Test
  public void testUpdateFirst() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    verifyChain(lru, e1, e2, e3);
    updateAndVerifyLength(e1, 3);
    verifyChain(lru, e1, e2, e3);
  }

  @Test
  public void testUpdateLast() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    verifyChain(lru, e1, e2, e3);
    updateAndVerifyLength(e3, 3);
    verifyChain(lru, e3, e1, e2);
  }

  @Test
  public void testRemove() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    removeAndVerifyLength(e2, 2);
    verifyChain(lru, e1, e3);
    verifyOrphan(e2);
  }

  @Test
  public void testRemoveFirst() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    removeAndVerifyLength(e1, 2);
    verifyChain(lru, e2, e3);
    verifyOrphan(e1);
  }

  @Test
  public void testRemoveLast() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    removeAndVerifyLength(e3, 2);
    verifyChain(lru, e1, e2);
    verifyOrphan(e3);
  }

  @Test
  public void testRemoveOldest() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    removeOldestAndVerifyLength(2);
    verifyChain(lru, e1, e2);
    verifyOrphan(e3);
  }

  @Test
  public void testRemoveAll() {
    updateAndVerifyLength(e3, 1);
    updateAndVerifyLength(e2, 2);
    updateAndVerifyLength(e1, 3);
    removeOldestAndVerifyLength(2);
    removeOldestAndVerifyLength(1);
    removeOldestAndVerifyLength(0);
    verifyChain(lru);
    verifyOrphan(e1);
    verifyOrphan(e2);
    verifyOrphan(e3);
  }
}
