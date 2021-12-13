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

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This is the approach to memcache unit tests that we recommend in
 * http://code.google.com/appengine/docs/java/howto/unittesting.html
 *
 * If any of these tests are failing or are not compiling, the change
 * we make to fix the problem should also be made in the above article.
 *
 */
@RunWith(JUnit4.class)
public class UnitTestHowToTestBase {

  private LocalServiceTestHelper helper;

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalMemcacheServiceTestConfig());
    helper.setUp();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  // we'll run this test twice to prove that we're not leaking any state across
  // tests
  private void doTest() {
    MemcacheService ms = MemcacheServiceFactory.getMemcacheService();
    assertThat(ms.contains("yar")).isFalse();
    ms.put("yar", "foo");
    assertThat(ms.contains("yar")).isTrue();
  }

  @Test
  public void testInsert1() {
    doTest();
  }

  @Test
  public void testInsert2() {
    doTest();
  }
}
