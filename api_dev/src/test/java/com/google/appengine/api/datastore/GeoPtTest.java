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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.Integer.signum;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService GeoPt class.
 *
 */
@RunWith(JUnit4.class)
public class GeoPtTest {

  @Test
  public void testCompareTo() {
    GeoPt p1 = new GeoPt(1.1f, 2.2f);
    GeoPt p2 = new GeoPt(1.2f, 2.1f);
    GeoPt p3 = new GeoPt(1.1f, 2.2f);
    GeoPt p4 = new GeoPt(1.1f, 2.1f);
    GeoPt p5 = new GeoPt(1.1f, 2.3f);
    GeoPt p6 = new GeoPt(1.0f, 3.3f);
    assertThat(p1.compareTo(p3)).isEqualTo(0);
    GeoPt[] ordered = {p6, p4, p1, p5, p2};
    for (int i = 0; i < ordered.length; i++) {
      for (int j = 0; j < ordered.length; j++) {
        int expected = signum(Integer.compare(i, j));
        int actual = signum(ordered[i].compareTo(ordered[j]));
        assertWithMessage("%s :: %s", ordered[i], ordered[j]).that(actual).isEqualTo(expected);
      }
    }
  }

  private void assertThrowsIAE(float lat, float lon) {
    assertThrows(IllegalArgumentException.class, () -> new GeoPt(lat, lon));
  }

  @Test
  public void testConstructor() {
    new GeoPt(-90.00f, -180.00f);
    new GeoPt(90.00f, 180.00f);
    assertThrowsIAE(-90.01f, 0f);
    assertThrowsIAE(0, -180.01f);
    assertThrowsIAE(0, 180.01f);
  }
}
