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

package com.google.appengine.api.testing;

import com.google.appengine.tools.development.testing.LocalServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import junit.framework.TestCase;
import org.junit.Test;

/**
 * Base {@link TestCase} implementation that manages a
 * {@link LocalServiceTestHelper}.
 *
 */
public class LocalServiceTestHelperTestCase extends TestCase {

  protected final LocalServiceTestHelper testHelper = new LocalServiceTestHelper(getTestConfigs());

  /**
   * By default we don't return any test configs. Subclasses that need to
   * configure specific services should override this method and return the
   * appropriate test configs.
   *
   * @return The test configs with which to configure the
   * {@link LocalServiceTestHelper}.
   */
  protected LocalServiceTestConfig[] getTestConfigs() {
    return new LocalServiceTestConfig[0];
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testHelper.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    testHelper.tearDown();
    super.tearDown();
  }

  @Test
  public void testNop() {}
}
