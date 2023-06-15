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

/**
 * Test for {@link GetIndexesRequest}.
 *
 */
@RunWith(JUnit4.class)
public class GetIndexesRequestTest {

  @Test
  public void testOffsetMustBeNonNegative() {
    assertThrows(
        IllegalArgumentException.class, () -> GetIndexesRequest.newBuilder().setOffset(-1).build());
  }

  @Test
  public void testLimitMustBePositive() {
    assertThrows(
        IllegalArgumentException.class, () -> GetIndexesRequest.newBuilder().setLimit(0).build());
  }

  @Test
  public void testLimitSetToDefaultIfNotSet() {
    GetIndexesRequest req = GetIndexesRequest.newBuilder().build();
    assertThat(req.getLimit()).isEqualTo((Integer) SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT);
  }

  @Test
  public void testIndexNamePrefixIsCopied() {
    SearchServicePb.ListIndexesParams listIndexParams =  GetIndexesRequest.newBuilder()
        .setIndexNamePrefix("foo").build().copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasIndexNamePrefix()).isTrue();
    assertThat(listIndexParams.getIndexNamePrefix()).isEqualTo("foo");
  }

  @Test
  public void testStartIndexNameIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setStartIndexName("foo").build();
    SearchServicePb.ListIndexesParams listIndexParams =  request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.getStartIndexName()).isEqualTo(request.getStartIndexName());
  }

  @Test
  public void testLimitIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setLimit(57).build();
    SearchServicePb.ListIndexesParams listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasLimit()).isTrue();
    assertThat((Integer) listIndexParams.getLimit()).isEqualTo(request.getLimit());
  }

  @Test
  public void testOffsetIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setOffset(61).build();
    SearchServicePb.ListIndexesParams listIndexParams =  request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasOffset()).isTrue();
    assertThat((Integer) listIndexParams.getOffset()).isEqualTo(request.getOffset());
  }

  @Test
  public void testFetchSchemaIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setSchemaFetched(true).build();
    SearchServicePb.ListIndexesParams listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasFetchSchema()).isTrue();
    assertThat(listIndexParams.getFetchSchema()).isEqualTo(request.isSchemaFetched());
    request = GetIndexesRequest.newBuilder().setSchemaFetched(false).build();
    listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasFetchSchema()).isTrue();
    assertThat(listIndexParams.getFetchSchema()).isEqualTo(request.isSchemaFetched());

    request = GetIndexesRequest.newBuilder().build();
    listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasFetchSchema()).isFalse();
  }

  @Test
  public void testAllNamespacesIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setAllNamespaces(true).build();
    SearchServicePb.ListIndexesParams listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasAllNamespaces()).isTrue();
    assertThat(listIndexParams.getAllNamespaces()).isEqualTo(request.isAllNamespaces());
    request = GetIndexesRequest.newBuilder().setAllNamespaces(false).build();
    listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasAllNamespaces()).isTrue();
    assertThat(listIndexParams.getAllNamespaces()).isEqualTo(request.isAllNamespaces());

    request = GetIndexesRequest.newBuilder().build();
    listIndexParams = request.copyToProtocolBuffer().build();
    assertThat(listIndexParams.hasAllNamespaces()).isFalse();
  }

  private SearchServicePb.ListIndexesParams testCopyingIsReflexiveHelper(GetIndexesRequest lhs) {
    GetIndexesRequest rhs = GetIndexesRequest.newBuilder(lhs).build();
    assertThat(rhs).isEqualTo(lhs);
    SearchServicePb.ListIndexesParams proto = lhs.copyToProtocolBuffer().build();
    assertThat(rhs.copyToProtocolBuffer().build()).isEqualTo(proto);
    return proto;
  }

  @Test
  public void testIncludeStartIndexIsNotCopied() {
    // If start_index_name is not set, then include_start_index is not copied
    SearchServicePb.ListIndexesParams listIndexParams = testCopyingIsReflexiveHelper(
        GetIndexesRequest.newBuilder().setIncludeStartIndex(true).build());
    assertThat(listIndexParams.hasIncludeStartIndex()).isFalse();
    listIndexParams = testCopyingIsReflexiveHelper(
        GetIndexesRequest.newBuilder().setIncludeStartIndex(false).build());
    assertThat(listIndexParams.hasIncludeStartIndex()).isFalse();
  }

  @Test
  public void testIncludeStartIndexIsCopied() {
    // If start_index_name is set, then include_start_index is copied
    GetIndexesRequest request = GetIndexesRequest.newBuilder()
        .setIncludeStartIndex(true).setStartIndexName("a").build();
    SearchServicePb.ListIndexesParams listIndexParams = testCopyingIsReflexiveHelper(request);
    assertThat(listIndexParams.hasIncludeStartIndex()).isTrue();
    assertThat(listIndexParams.getIncludeStartIndex()).isEqualTo(request.isIncludeStartIndex());
    request = GetIndexesRequest.newBuilder()
        .setIncludeStartIndex(false).setStartIndexName("b").build();
    listIndexParams = testCopyingIsReflexiveHelper(request);
    assertThat(listIndexParams.hasIncludeStartIndex()).isTrue();
    assertThat(listIndexParams.getIncludeStartIndex()).isEqualTo(request.isIncludeStartIndex());
  }

  @Test
  public void testIncludeStartIndexIsNull() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().build();
    SearchServicePb.ListIndexesParams listIndexParams = testCopyingIsReflexiveHelper(request);
    assertThat(listIndexParams.hasIncludeStartIndex()).isFalse();
    assertThat(request.isIncludeStartIndex()).isTrue();
    request = GetIndexesRequest.newBuilder().setStartIndexName("a").build();
    listIndexParams = testCopyingIsReflexiveHelper(request);
    assertThat(listIndexParams.hasIncludeStartIndex()).isTrue();
    assertThat(request.isIncludeStartIndex()).isTrue();
  }

  @Test
  public void testCopyConstructor() {
    GetIndexesRequest source = GetIndexesRequest.newBuilder()
        .setIncludeStartIndex(false).setIndexNamePrefix("prefix").setLimit(71)
        .setOffset(11).setSchemaFetched(true)
        .setStartIndexName("startIndexName").setAllNamespaces(true).build();
    GetIndexesRequest target = GetIndexesRequest.newBuilder(source).build();
    assertThat(target).isEqualTo(source);
    assertThat(source).isEqualTo(target);
    assertThat(target).isEqualTo(source);
    assertThat(source.hashCode()).isEqualTo(source.hashCode());
    assertThat(target.hashCode()).isEqualTo(source.hashCode());
  }

  @Test
  public void testStartIndexNameEmptyOrNull() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setStartIndexName("").build();
    assertThat(request.getStartIndexName()).isEmpty();

    request = GetIndexesRequest.newBuilder().setStartIndexName(null).build();
    assertThat(request.getStartIndexName()).isNull();
  }

  @Test
  public void testIndexNamePrefixEmptyOrNull() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().setIndexNamePrefix("").build();
    assertThat(request.getIndexNamePrefix()).isEmpty();

    request = GetIndexesRequest.newBuilder().setIndexNamePrefix(null).build();
    assertThat(request.getIndexNamePrefix()).isNull();
  }

}
