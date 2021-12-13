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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FacetValue;
import com.google.common.base.Strings;
import com.google.common.testing.EqualsTester;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for Facet */
@RunWith(JUnit4.class)
public class FacetTest extends FacetTestBase {

  @Test
  public void testSetName_validNames() {
    for (String name : generateValidNames()) {
      assertThat(Facet.withAtom(name, "atom").getName()).isEqualTo(name);
    }
  }

  @Test
  public void testSetName_invalidNames() {
    for (String name : generateInvalidNames()) {
      assertThrows(IllegalArgumentException.class, () -> Facet.withAtom(name, "value"));
    }
    assertThrows(NullPointerException.class, () -> Facet.withAtom(null, "value"));
  }

  @Test
  public void testSetAtom() throws Exception {
    assertThat(Facet.withNumber("name", 10.0).getAtom()).isNull();
    assertThat(Facet.withAtom("name", "a").getAtom()).isEqualTo("a");
    String atom = Strings.repeat("a", SearchApiLimits.MAXIMUM_ATOM_LENGTH);
    assertThat(Facet.withAtom("name", atom).getAtom()).isEqualTo(atom);

    assertThrows(
        IllegalArgumentException.class,
        () -> Facet.withAtom("name", Strings.repeat("a", SearchApiLimits.MAXIMUM_ATOM_LENGTH + 1)));
    assertThrows(IllegalArgumentException.class, () -> Facet.withAtom("name", ""));
    assertThrows(IllegalArgumentException.class, () -> Facet.withAtom("name", null));
  }

  @Test
  public void testSetNumber() throws Exception {
    assertThat(Facet.withAtom("name", "test").getNumber()).isNull();
    assertThat(Facet.withNumber("name", -1023.2).getNumber()).isEqualTo(-1023.2);
    assertThat(Facet.withNumber("name", 0.0).getNumber()).isEqualTo(0.0);
    assertThat(Facet.withNumber("name", 1023.4).getNumber()).isEqualTo(1023.4);

    try {
      Facet.withNumber("name", SearchApiLimits.MINIMUM_NUMBER_VALUE - 0.001);
    } catch (IllegalArgumentException e) {
      // Success.
    }
    try {
      Facet.withNumber("name", SearchApiLimits.MAXIMUM_NUMBER_VALUE + 0.001);
    } catch (IllegalArgumentException e) {
      // Success.
    }
  }

  @Test
  public void testCopyToProtocolBuffer() throws Exception {
    DocumentPb.Facet pb = Facet.withAtom("name", "value").copyToProtocolBuffer();
    assertThat(pb.getName()).isEqualTo("name");
    FacetValue value = pb.getValue();
    assertThat(value.getStringValue()).isEqualTo("value");
    assertThat(value.getType()).isEqualTo(FacetValue.ContentType.ATOM);

    value = Facet.withNumber("name", 10.0).copyToProtocolBuffer().getValue();
    assertThat(value.getStringValue()).isEqualTo("10.0");
    assertThat(value.getType()).isEqualTo(FacetValue.ContentType.NUMBER);
  }

  @Test
  public void testEquals() throws Exception {
    Facet facet1 = Facet.withAtom("name1", "value");
    Facet facet2 = Facet.withAtom("name2", "value");
    Facet facet3 = Facet.withAtom("name1", "value2");
    Facet facet4 = Facet.withAtom("name1", "value");
    new EqualsTester()
        .addEqualityGroup(facet1, facet4)
        .addEqualityGroup(facet2)
        .addEqualityGroup(facet3)
        .testEquals();
    HashSet<Facet> facets = new HashSet<>();
    facets.add(facet1);
    facets.add(facet2);
    facets.add(facet3);
    facets.add(facet4);
    assertThat(facets).hasSize(3);
    assertThat(facets).contains(facet1);
    assertThat(facets).contains(facet2);
    assertThat(facets).contains(facet3);
    assertThat(facets).contains(facet4);
  }

  @Test
  public void testToString() throws Exception {
    assertThat(Facet.withAtom("name", "text").toString()).isEqualTo("Facet(name=name, atom=text)");

    assertThat(Facet.withNumber("name", 123.456).toString())
        .isEqualTo("Facet(name=name, number=123.456)");
  }

  @Test
  public void testDoubleIsParsable() {
    double value = 1000000;
    Facet numberFacet = Facet.withNumber("number", value);
    DocumentPb.Facet pbFacet = numberFacet.copyToProtocolBuffer();
    assertThat(Double.parseDouble(pbFacet.getValue().getStringValue())).isEqualTo(value);
  }
}
