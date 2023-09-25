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

package com.google.apphosting.runtime.jetty9;

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.runtime.jetty9.CacheControlHeader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CacheControlHeaderTest {
  @Test
  public void fromExpirationTime_parsesCorrectlyFormattedExpirationTime() throws Exception {
    CacheControlHeader cacheControlHeader = CacheControlHeader.fromExpirationTime("1d 2h 3m");
    assertThat(cacheControlHeader.getValue()).isEqualTo("public, max-age=93780");
  }

  @Test
  public void fromExpirationTime_usesDefaultMaxAgeForInvalidExpirationTime() throws Exception {
    CacheControlHeader cacheControlHeader = CacheControlHeader.fromExpirationTime("asdf");
    assertThat(cacheControlHeader.getValue()).isEqualTo("public, max-age=600");
  }

  @Test
  public void fromExpirationTime_usesDefaultMaxAgeForEmptyExpirationTime() throws Exception {
    CacheControlHeader cacheControlHeader = CacheControlHeader.fromExpirationTime("");
    assertThat(cacheControlHeader.getValue()).isEqualTo("public, max-age=600");
  }

  @Test
  public void fromExpirationTime_usesDefaultMaxAgeForIncorrectTimeUnits() throws Exception {
    CacheControlHeader cacheControlHeader = CacheControlHeader.fromExpirationTime("3g");
    assertThat(cacheControlHeader.getValue()).isEqualTo("public, max-age=600");
  }
}
