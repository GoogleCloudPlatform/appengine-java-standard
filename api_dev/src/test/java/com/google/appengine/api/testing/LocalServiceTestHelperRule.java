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

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.junit.rules.ExternalResource;

/**
 * Rule that manages a {@link LocalServiceTestHelper}.
 *
 * <p>Example usage:
 * <pre>
 * {@code @Rule} public LocalServiceTestHelperRule testHelperRule =
 *     new LocalServiceTestHelperRule();
 * </pre>
 */
public final class LocalServiceTestHelperRule extends ExternalResource {
  private final LocalServiceTestHelper testHelper;

  public LocalServiceTestHelperRule() {
    this(new LocalServiceTestHelper());
  }

  public LocalServiceTestHelperRule(LocalServiceTestHelper helper) {
    this.testHelper = helper;
  }

  @Override
  protected void before() {
    testHelper.setUp();
  }

  @Override
  protected void after() {
    testHelper.tearDown();
  }

  public LocalServiceTestHelper testHelper() {
    return testHelper;
  }
}
