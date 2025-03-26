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

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;

/** Provides support for translation of calls between userland and appserver land. */
class SearchApiHelper {

  private static final String PACKAGE = "search";
  private final ByteString appIdOverride;

  SearchApiHelper() {
    this.appIdOverride = null;
  }

  SearchApiHelper(String appIdOverride) {
    this.appIdOverride = ByteString.copyFromUtf8(appIdOverride);
  }

  Future<SearchServicePb.DeleteDocumentResponse.Builder> makeAsyncDeleteDocumentCall(
      SearchServicePb.DeleteDocumentParams.Builder params, @Nullable Double deadline) {
    SearchServicePb.DeleteDocumentRequest.Builder requestBuilder =
        SearchServicePb.DeleteDocumentRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.DeleteDocumentResponse.Builder responseBuilder =
        SearchServicePb.DeleteDocumentResponse.newBuilder();
    return makeAsyncCall("DeleteDocument", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.DeleteSchemaResponse.Builder> makeAsyncDeleteSchemaCall(
      SearchServicePb.DeleteSchemaParams.Builder params, @Nullable Double deadline) {
    SearchServicePb.DeleteSchemaRequest.Builder requestBuilder =
        SearchServicePb.DeleteSchemaRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.DeleteSchemaResponse.Builder responseBuilder =
        SearchServicePb.DeleteSchemaResponse.newBuilder();
    return makeAsyncCall("DeleteSchema", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.IndexDocumentResponse.Builder> makeAsyncIndexDocumentCall(
      SearchServicePb.IndexDocumentParams.Builder params, @Nullable Double deadline) {
    SearchServicePb.IndexDocumentRequest.Builder requestBuilder =
        SearchServicePb.IndexDocumentRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.IndexDocumentRequest request = requestBuilder.build();
    SearchServicePb.IndexDocumentResponse.Builder responseBuilder =
        SearchServicePb.IndexDocumentResponse.newBuilder();
    return makeAsyncCall(
        "IndexDocument", request, responseBuilder, deadline, new IndexExceptionConverter(request));
  }

  Future<SearchServicePb.ListDocumentsResponse.Builder> makeAsyncListDocumentsCall(
      SearchServicePb.ListDocumentsParams.Builder params, @Nullable Double deadline) {
    SearchServicePb.ListDocumentsRequest.Builder requestBuilder =
        SearchServicePb.ListDocumentsRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.ListDocumentsResponse.Builder responseBuilder =
        SearchServicePb.ListDocumentsResponse.newBuilder();
    return makeAsyncCall("ListDocuments", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.ListIndexesResponse.Builder> makeAsyncListIndexesCall(
      SearchServicePb.ListIndexesParams params, @Nullable Double deadline) {
    SearchServicePb.ListIndexesRequest.Builder requestBuilder =
        SearchServicePb.ListIndexesRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.ListIndexesResponse.Builder responseBuilder =
        SearchServicePb.ListIndexesResponse.newBuilder();
    return makeAsyncCall("ListIndexes", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.SearchResponse.Builder> makeAsyncSearchCall(
      SearchServicePb.SearchParams.Builder params, @Nullable Double deadline) {
    SearchServicePb.SearchRequest.Builder requestBuilder =
        SearchServicePb.SearchRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.SearchResponse.Builder responseBuilder =
        SearchServicePb.SearchResponse.newBuilder();
    return makeAsyncCall("Search", requestBuilder.build(), responseBuilder, deadline);
  }

  /**
   * Handles OverQuotaException specially for the IndexDocuments case, appending index name and
   * potentially namespace to the message. Format is "message; index = <index>", or "message; index
   * = <index> in namespace <namespace>".
   */
  // @VisibleForTesting
  static class IndexExceptionConverter implements Function<Throwable, Throwable> {
    private final SearchServicePb.IndexDocumentRequest request;

    IndexExceptionConverter(SearchServicePb.IndexDocumentRequest request) {
      this.request = request;
    }

    @Override
    public Throwable apply(Throwable input) {
      if (input instanceof ApiProxy.OverQuotaException) {
        SearchServicePb.IndexSpec spec = request.getParams().getIndexSpec();
        StringBuilder message =
            new StringBuilder(input.getMessage()).append("; index = ").append(spec.getName());
        if (!spec.getNamespace().isEmpty()) {
          message.append(" in namespace ").append(spec.getNamespace());
        }
        return new ApiProxy.OverQuotaException(message.toString(), input);
      }
      return input;
    }
  }

  // Makes asynchronous calls to appserver.
  /**
   * Makes an asynchronous call. Propagates exceptions unchanged.
   *
   * @param method the method on the API to call
   * @param request the request to forward to the API
   * @param responseBuilder the response builder used to fill the response
   * @param deadline the deadline of the call. if it is null, the default api deadline will be used
   */
  private <T extends AbstractMessage.Builder<T>> Future<T> makeAsyncCall(
      String method, AbstractMessage request, final T responseBuilder, @Nullable Double deadline) {
    return makeAsyncCall(
        method, request, responseBuilder, deadline, Functions.<Throwable>identity());
  }

  /**
   * Makes an asynchronous call. May modify exceptions thrown by {@link ApiProxy}}.
   *
   * @param method the method on the API to call
   * @param request the request to forward to the API
   * @param responseBuilder the response builder used to fill the response
   * @param deadline the deadline of the call. if it is null, the default api deadline will be used
   * @param exceptionConverter Function to transform Throwables on the error path
   * @see ApiProxy#makeSyncCall(String, String, byte[],
   *     com.google.apphosting.api.ApiProxy.ApiConfig)
   */
  private <T extends AbstractMessage.Builder<T>> Future<T> makeAsyncCall(
      String method,
      AbstractMessage request,
      final T responseBuilder,
      @Nullable Double deadline,
      final Function<Throwable, Throwable> exceptionConverter) {
    Future<byte[]> response;
    if (deadline == null) {
      response = ApiProxy.makeAsyncCall(PACKAGE, method, request.toByteArray());
    } else {
      ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
      apiConfig.setDeadlineInSeconds(deadline);
      response = ApiProxy.makeAsyncCall(PACKAGE, method, request.toByteArray(), apiConfig);
    }
    return new FutureWrapper<byte[], T>(response) {
      @Override
      protected T wrap(byte[] responseBytes) {
        // This null check is mainly for the benefit of unit tests
        // (specifically ones using EasyMock, where the default behavior
        // is to return null).
        if (responseBytes != null) {
          try {
            responseBuilder.mergeFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
          } catch (InvalidProtocolBufferException e) {
            throw new SearchServiceException(e.toString());
          }
        }
        return responseBuilder;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return exceptionConverter.apply(cause);
      }
    };
  }
}
