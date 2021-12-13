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

import static com.google.appengine.api.search.FutureHelper.quietGet;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.concurrent.Future;

/**
 * A concrete implementation of {@link SearchService}.
 */
class SearchServiceImpl implements SearchService {

  private final SearchApiHelper apiHelper;
  private final SearchServiceConfig config;

  /** Only our classes may create instances of this class. */
  SearchServiceImpl(SearchApiHelper apiHelper, SearchServiceConfig config) {
    this.apiHelper = apiHelper;
    this.config = config.toBuilder().setNamespace(getAppNamespace(config.getNamespace())).build();
  }

  @Override
  public GetResponse<Index> getIndexes(GetIndexesRequest request) {
    return quietGet(getIndexesAsync(request));
  }

  @Override
  public GetResponse<Index> getIndexes(GetIndexesRequest.Builder builder) {
    return getIndexes(builder.build());
  }

  @Override
  public Future<GetResponse<Index>> getIndexesAsync(GetIndexesRequest.Builder builder) {
    return getIndexesAsync(builder.build());
  }

  @Override
  public Future<GetResponse<Index>> getIndexesAsync(final GetIndexesRequest request) {
    Boolean box = request.isSchemaFetched();
    final boolean fetchSchema = box != null && box.booleanValue();
    SearchServicePb.ListIndexesParams.Builder paramsBuilder = request
        .copyToProtocolBuffer().setNamespace(config.getNamespace());
    Future<SearchServicePb.ListIndexesResponse.Builder> future =
        apiHelper.makeAsyncListIndexesCall(paramsBuilder.build(), config.getDeadline());
    return new FutureWrapper<SearchServicePb.ListIndexesResponse.Builder, GetResponse<Index>>(
        future) {
      @Override
      protected Throwable convertException(Throwable cause) {
        OperationResult result = OperationResult.convertToOperationResult(cause);
        return (result == null) ? cause : new GetException(result);
      }

      @Override
      protected GetResponse<Index> wrap(SearchServicePb.ListIndexesResponse.Builder key)
          throws Exception {
        SearchServicePb.ListIndexesResponse response = key.build();
        OperationResult operationResult = new OperationResult(response.getStatus());
        if (operationResult.getCode() != StatusCode.OK) {
          throw new GetException(operationResult);
        }
        ArrayList<Index> indexes = new ArrayList<Index>(response.getIndexMetadataCount());
        for (SearchServicePb.IndexMetadata metadata : response.getIndexMetadataList()) {
          SearchServicePb.IndexSpec indexSpec = metadata.getIndexSpec();
          IndexSpec.Builder builder = IndexSpec.newBuilder().setName(indexSpec.getName());
          if (indexSpec.hasNamespace()) {
            Preconditions.checkArgument(
                indexSpec.getNamespace().equals(config.getNamespace()),
                "Index with incorrect namespace received '%s' != '%s'",
                indexSpec.getNamespace(),
                config.getNamespace());
          } else if (!config.getNamespace().isEmpty()) {
            Preconditions.checkArgument(
                indexSpec.getNamespace().equals(config.getNamespace()),
                "Index with incorrect namespace received '' != '%s'",
                config.getNamespace());
          }
          Long amountUsed = null;
          Long limit = null;
          if (metadata.hasStorage()) {
            amountUsed = metadata.getStorage().getAmountUsed();
            limit = metadata.getStorage().getLimit();
          }
          Schema schema = fetchSchema ? Schema.createSchema(metadata) : null;
          indexes.add(new IndexImpl(apiHelper, config, builder.build(), schema, amountUsed, limit));
        }
        return new GetResponse<Index>(indexes);
      }
    };
  }

  @Override
  public Index getIndex(IndexSpec.Builder builder) {
    return getIndex(builder.build());
  }

  @Override
  public Index getIndex(IndexSpec indexSpec) {
    return new IndexImpl(apiHelper, config, indexSpec);
  }

  @Override
  public String getNamespace() {
    return config.getNamespace();
  }

  /**
   * Returns a namespace, preferring one passed via {@code namespaceGiven}
   * parameter. If {@code null} is passed, it attempts to use namespace set
   * in the {@link NamespaceManager}. If that one is not set, it returns
   * an empty namespace.
   *
   * @param namespaceGiven the externally provided namespace
   * @return a namespace which will not be null
   */
  private static String getAppNamespace(String namespaceGiven) {
    if (namespaceGiven != null) {
      return namespaceGiven;
    }
    String currentNamespace = NamespaceManager.get();
    return (currentNamespace == null) ? "" : currentNamespace;
  }
}
