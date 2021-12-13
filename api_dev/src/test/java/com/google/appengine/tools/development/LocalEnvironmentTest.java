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

package com.google.appengine.tools.development;

import junit.framework.TestCase;

/**
 * Unit tests for {@link LocalEnvironment}.
 */
public class LocalEnvironmentTest extends TestCase {
  public void testGetMajorVersion() {
    assertEquals("v", LocalEnvironment.getMajorVersion("s:v.1"));
    assertEquals("vyy", LocalEnvironment.getMajorVersion("sxx:vyy.1"));
    assertEquals("", LocalEnvironment.getMajorVersion("s:.1"));
    assertEquals("v", LocalEnvironment.getMajorVersion("s:v"));
    assertEquals("", LocalEnvironment.getMajorVersion("s:"));
    assertEquals("v", LocalEnvironment.getMajorVersion("v.1"));
    assertEquals("v", LocalEnvironment.getMajorVersion("v"));
    assertEquals("", LocalEnvironment.getMajorVersion(".1"));
    assertEquals("1", LocalEnvironment.getMajorVersion("1"));
    assertEquals("", LocalEnvironment.getMajorVersion(""));
    assertEquals("", LocalEnvironment.getMajorVersion(":"));
  }
}
