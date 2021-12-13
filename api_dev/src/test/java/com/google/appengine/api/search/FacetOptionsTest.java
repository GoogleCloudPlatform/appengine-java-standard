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
import com.google.appengine.api.search.proto.SearchServicePb;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for FacetOptions */
@RunWith(JUnit4.class)
public class FacetOptionsTest extends FacetTestBase {

  @Test
  public void testBuilder_missingDepth() {
    assertThat(FacetOptions.newBuilder().build().getDepth()).isNull();
  }

  @Test
  public void testBuilder_missingDiscoveryLimit() {
    assertThat(FacetOptions.newBuilder().build().getDiscoveryLimit()).isNull();
  }

  @Test
  public void testBuilder_missingDiscoveryValueLimit() {
    assertThat(FacetOptions.newBuilder().build().getDiscoveryValueLimit()).isNull();
  }

  @Test
  public void testBuilder_validDepth() {
    assertThat((int) FacetOptions.newBuilder().setDepth(1).build().getDepth()).isEqualTo(1);
    assertThat(
            (int)
                FacetOptions.newBuilder()
                    .setDepth(SearchApiLimits.FACET_MAXIMUM_DEPTH)
                    .build()
                    .getDepth())
        .isEqualTo(SearchApiLimits.FACET_MAXIMUM_DEPTH);
  }

  @Test
  public void testBuilder_invalidDepth() {
    assertThrows(IllegalArgumentException.class, () -> FacetOptions.newBuilder().setDepth(0));
    assertThrows(IllegalArgumentException.class, () -> FacetOptions.newBuilder().setDepth(-1));
    assertThrows(
        IllegalArgumentException.class,
        () -> FacetOptions.newBuilder().setDepth(SearchApiLimits.FACET_MAXIMUM_DEPTH + 1));
  }

  @Test
  public void testBuilder_validDiscoveryLimit() {
    assertThat((int) FacetOptions.newBuilder().setDiscoveryLimit(1).build().getDiscoveryLimit())
        .isEqualTo(1);
    assertThat(
            (int)
                FacetOptions.newBuilder()
                    .setDiscoveryLimit(SearchApiLimits.FACET_MAXIMUM_DISCOVERY_LIMIT)
                    .build()
                    .getDiscoveryLimit())
        .isEqualTo(SearchApiLimits.FACET_MAXIMUM_DISCOVERY_LIMIT);
  }

  @Test
  public void testBuilder_invalidDiscoveryLimit() {
    assertThrows(
        IllegalArgumentException.class, () -> FacetOptions.newBuilder().setDiscoveryLimit(0));
    assertThrows(
        IllegalArgumentException.class, () -> FacetOptions.newBuilder().setDiscoveryLimit(-1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FacetOptions.newBuilder()
                .setDiscoveryLimit(SearchApiLimits.FACET_MAXIMUM_DISCOVERY_LIMIT + 1));
  }

  @Test
  public void testBuilder_validDiscoveryValueLimit() {
    assertThat(
            (int)
                FacetOptions.newBuilder()
                    .setDiscoveryValueLimit(1)
                    .build()
                    .getDiscoveryValueLimit())
        .isEqualTo(1);
    assertThat(
            (int)
                FacetOptions.newBuilder()
                    .setDiscoveryValueLimit(SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT)
                    .build()
                    .getDiscoveryValueLimit())
        .isEqualTo(SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT);
  }

  @Test
  public void testBuilder_invalidDiscoveryValueLimit() {
    assertThrows(
        IllegalArgumentException.class, () -> FacetOptions.newBuilder().setDiscoveryValueLimit(0));
    assertThrows(
        IllegalArgumentException.class, () -> FacetOptions.newBuilder().setDiscoveryValueLimit(-1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FacetOptions.newBuilder()
                .setDiscoveryValueLimit(SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT + 1));
  }

  @Test
  public void test_copyToProtocolBuffer() {
    SearchServicePb.SearchParams.Builder paramBuilder = SearchServicePb.SearchParams.newBuilder();
    // Test with enableFacetDiscovery
    FacetOptions.newBuilder()
        .setDepth(1234)
        .setDiscoveryLimit(8)
        .setDiscoveryValueLimit(4)
        .build()
        .copyToProtocolBuffer(paramBuilder, true);
    assertThat(paramBuilder.getFacetDepth()).isEqualTo(1234);
    assertThat(paramBuilder.getFacetAutoDetectParam().getValueLimit()).isEqualTo(4);
    assertThat(paramBuilder.getAutoDiscoverFacetCount()).isEqualTo(8);

    // Test with enableFacetDiscovery disabled
    FacetOptions.newBuilder()
        .setDepth(1234)
        .setDiscoveryLimit(8)
        .setDiscoveryValueLimit(4)
        .build()
        .copyToProtocolBuffer(paramBuilder, false);
    assertThat(paramBuilder.getFacetDepth()).isEqualTo(1234);
    assertThat(paramBuilder.getFacetAutoDetectParam().getValueLimit()).isEqualTo(4);
    assertThat(paramBuilder.hasAutoDiscoverFacetCount()).isFalse();

    // Test with enableFacetDiscovery enabled but no discoverLimit
    FacetOptions.newBuilder()
        .setDepth(1234)
        .setDiscoveryValueLimit(4)
        .build()
        .copyToProtocolBuffer(paramBuilder, true);
    assertThat(paramBuilder.getFacetDepth()).isEqualTo(1234);
    assertThat(paramBuilder.getFacetAutoDetectParam().getValueLimit()).isEqualTo(4);
    assertThat(paramBuilder.getAutoDiscoverFacetCount())
        .isEqualTo(SearchApiLimits.FACET_DEFAULT_DISCOVERY_LIMIT);
  }
}
