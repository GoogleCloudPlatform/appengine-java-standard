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

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService Rating class.
 *
 */
@RunWith(JUnit4.class)
public class RatingTest {
  @Test
  public void testBadConstructorInput() {
    assertThrows(IllegalArgumentException.class, () -> new Rating(Rating.MIN_VALUE - 1));

    assertThrows(IllegalArgumentException.class, () -> new Rating(Rating.MAX_VALUE + 1));
  }

  @Test
  public void testGoodConstructorInput() {
    Rating unused1 = new Rating(Rating.MIN_VALUE);
    Rating unused2 = new Rating(Rating.MAX_VALUE);
    Rating unused3 = new Rating(Rating.MIN_VALUE + 1);
    Rating unused4 = new Rating(Rating.MAX_VALUE - 1);
  }
}
