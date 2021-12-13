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
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link GetRequest}.
 *
 */
@RunWith(JUnit4.class)
public class GetRequestTest {

  private ApiProxy.Environment environment;
  private SearchServicePb.IndexSpec.Builder indexBuilder;

  @Before
  public void setUp() {
    environment = new MockEnvironment("some-app", "v1");
    ApiProxy.setEnvironmentForCurrentThread(environment);

    indexBuilder = SearchServicePb.IndexSpec.newBuilder().setName("test-index");
  }

  @Test
  public void testLimitMustBeNonNegative() {
    assertThrows(
        IllegalArgumentException.class, () -> GetRequest.newBuilder().setLimit(-1).build());
  }

  @Test
  public void testDefaults() {
    GetRequest req = GetRequest.newBuilder().build();
    assertThat(req.getStartId()).isNull();
    assertThat(req.isIncludeStart()).isFalse();
    assertThat(req.isReturningIdsOnly()).isFalse();
    assertThat(req.getLimit()).isEqualTo(SearchApiLimits.GET_RANGE_DEFAULT_LIMIT);
  }

  @Test
  public void testStartIdIsCopied() {
    GetRequest request = GetRequest.newBuilder().setStartId("foo").build();
    SearchServicePb.ListDocumentsParams listDocumentsParams =
        request.copyToProtocolBuffer().setIndexSpec(indexBuilder).build();
    assertThat(listDocumentsParams.getStartDocId()).isEqualTo(request.getStartId());
  }

  @Test
  public void testLimitIsCopied() {
    GetRequest request = GetRequest.newBuilder().setLimit(57).build();
    SearchServicePb.ListDocumentsParams listDocumentsParams =
        request.copyToProtocolBuffer().setIndexSpec(indexBuilder).build();
    assertThat(listDocumentsParams.hasLimit()).isTrue();
    assertThat(listDocumentsParams.getLimit()).isEqualTo(request.getLimit());
  }

  @Test
  public void testIncludeStartIsNotCopied() {
    // If start doc Id is not set, then include start document is not copied
    GetRequest request = GetRequest.newBuilder().setIncludeStart(true).build();
    SearchServicePb.ListDocumentsParams listDocumentsParams =
        request.copyToProtocolBuffer().setIndexSpec(indexBuilder).build();
    assertThat(listDocumentsParams.hasIncludeStartDoc()).isTrue();
    assertThat(listDocumentsParams.getIncludeStartDoc()).isFalse();

    request = GetRequest.newBuilder().setIncludeStart(false).build();
    listDocumentsParams = request.copyToProtocolBuffer().setIndexSpec(indexBuilder).build();
    assertThat(listDocumentsParams.hasIncludeStartDoc()).isTrue();
    assertThat(listDocumentsParams.getIncludeStartDoc()).isFalse();
  }

  @Test
  public void testIncludeStartIsCopied() {
    // If start doc Id is set, the include start doc is copied
    GetRequest request = GetRequest.newBuilder().setIncludeStart(true).setStartId("a").build();
    SearchServicePb.ListDocumentsParams listDocumentsParams =
        request.copyToProtocolBuffer().setIndexSpec(indexBuilder).build();
    assertThat(listDocumentsParams.hasIncludeStartDoc()).isFalse();
    // The default value from the proto is true.
    assertThat(listDocumentsParams.getIncludeStartDoc()).isEqualTo(request.isIncludeStart());

    request = GetRequest.newBuilder().setIncludeStart(false).setStartId("b").build();
    listDocumentsParams = request.copyToProtocolBuffer().setIndexSpec(indexBuilder).build();
    assertThat(listDocumentsParams.hasIncludeStartDoc()).isTrue();
    assertThat(listDocumentsParams.getIncludeStartDoc()).isEqualTo(request.isIncludeStart());
  }

  @Test
  public void testCopyConstructor_deprecatedKeysOnl() {
    GetRequest source =
        GetRequest.newBuilder()
            .setIncludeStart(false)
            .setLimit(71)
            .setReturningIdsOnly(true)
            .setStartId("startIndexName")
            .build();
    GetRequest target = GetRequest.newBuilder(source).build();
    assertThat(target).isEqualTo(source);
    assertThat(source).isEqualTo(target);
    assertThat(target).isEqualTo(source);
    assertThat(source.hashCode()).isEqualTo(source.hashCode());
    assertThat(target.hashCode()).isEqualTo(source.hashCode());
  }

  @Test
  public void testCopyConstructor() {
    GetRequest source =
        GetRequest.newBuilder()
            .setIncludeStart(false)
            .setLimit(71)
            .setReturningIdsOnly(true)
            .setStartId("startIndexName")
            .build();
    GetRequest target = GetRequest.newBuilder(source).build();
    assertThat(target).isEqualTo(source);
    assertThat(source).isEqualTo(target);
    assertThat(target).isEqualTo(source);
    assertThat(source.hashCode()).isEqualTo(source.hashCode());
    assertThat(target.hashCode()).isEqualTo(source.hashCode());
  }

  @Test
  public void testStartIdEmptyOrNull() {
    GetRequest request = GetRequest.newBuilder().setStartId("").build();
    assertThat(request.getStartId()).isEmpty();

    request = GetRequest.newBuilder().setStartId(null).build();
    assertThat(request.getStartId()).isNull();
  }
}
