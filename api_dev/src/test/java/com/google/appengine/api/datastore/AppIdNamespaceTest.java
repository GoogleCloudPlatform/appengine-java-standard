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

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the AppIdNamespace class.
 *
 */
@RunWith(JUnit4.class)
public class AppIdNamespaceTest {
  @Test
  public void testGoodAppIdNamespaceDemangle() {
    assertThat(AppIdNamespace.parseEncodedAppIdNamespace("A!B"))
        .isEqualTo(new AppIdNamespace("A", "B"));
    assertThat(AppIdNamespace.parseEncodedAppIdNamespace("A"))
        .isEqualTo(new AppIdNamespace("A", ""));
    assertThat(AppIdNamespace.parseEncodedAppIdNamespace("A!B").toEncodedString()).isEqualTo("A!B");
    assertThat(AppIdNamespace.parseEncodedAppIdNamespace("A").toEncodedString()).isEqualTo("A");
  }

  @Test
  public void testBadAppIdNamespaceDemangle() {
    assertThrows(
        IllegalArgumentException.class, () -> AppIdNamespace.parseEncodedAppIdNamespace("!"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AppIdNamespace.parseEncodedAppIdNamespace(
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"));
    assertThrows(
        IllegalArgumentException.class, () -> AppIdNamespace.parseEncodedAppIdNamespace("A!"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AppIdNamespace.parseEncodedAppIdNamespace("A!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"));
    assertThrows(
        IllegalArgumentException.class, () -> AppIdNamespace.parseEncodedAppIdNamespace("A!B!"));
    assertThrows(
        IllegalArgumentException.class, () -> AppIdNamespace.parseEncodedAppIdNamespace("A!B!C"));
  }

  @Test
  public void testAppIdNamespaceConstruct() {
    assertThrows(IllegalArgumentException.class, () -> new AppIdNamespace(null, null));

    assertThrows(IllegalArgumentException.class, () -> new AppIdNamespace(null, ""));

    assertThrows(IllegalArgumentException.class, () -> new AppIdNamespace("", null));
  }

  @Test
  public void testAppIdNamespaceCompare() {
    AppIdNamespace ainsC = new AppIdNamespace("C", "");
    AppIdNamespace ainsCB = new AppIdNamespace("C", "B");
    AppIdNamespace ainsAB = new AppIdNamespace("A", "B");
    AppIdNamespace ainsA = new AppIdNamespace("A", "");
    AppIdNamespace ainsCB2 = new AppIdNamespace("C", "B");
    assertThat(ainsCB.compareTo(ainsCB2)).isEqualTo(0);
    AppIdNamespace[] ordered = {ainsA, ainsAB, ainsC, ainsCB};
    for (int i = 0; i < ordered.length; i++) {
      for (int j = 0; j < ordered.length; j++) {
        int expected = signum(Integer.compare(i, j));
        int actual = signum(ordered[i].compareTo(ordered[j]));
        assertWithMessage("%s :: %s", ordered[i], ordered[j]).that(actual).isEqualTo(expected);
      }
    }
  }

  @Test
  public void testAppIdNamespaceEquals() {
    AppIdNamespace ainsAA1 = new AppIdNamespace("A", "A");
    AppIdNamespace ainsAA2 = new AppIdNamespace("A", "A");
    AppIdNamespace ainsAB1 = new AppIdNamespace("A", "B");
    AppIdNamespace ainsAB2 = new AppIdNamespace("A", "B");
    AppIdNamespace ainsBA1 = new AppIdNamespace("B", "A");
    AppIdNamespace ainsBA2 = new AppIdNamespace("B", "A");
    AppIdNamespace ainsBB1 = new AppIdNamespace("B", "B");
    AppIdNamespace ainsBB2 = new AppIdNamespace("B", "B");

    new EqualsTester()
        .addEqualityGroup(ainsAA1, ainsAA2)
        .addEqualityGroup(ainsAB1, ainsAB2)
        .addEqualityGroup(ainsBA1, ainsBA2)
        .addEqualityGroup(ainsBB1, ainsBB2)
        .testEquals();
  }
}
