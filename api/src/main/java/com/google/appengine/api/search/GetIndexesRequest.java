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

import com.google.appengine.api.search.checkers.GetIndexesRequestChecker;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb.ListIndexesParams;
import com.google.common.base.Preconditions;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A request to get a range of indexes. You can specify a number of
 * restrictions, such as the number of indexes to return, the prefix
 * with which names of the returned indexes must begin, etc.
 *
 * A namespace may be specified, otherwise the default namespace will
 * be used.  Only the indexes defined in the namespace, default or
 * otherwise, will be returned.
 *
 * <pre>{@code
 *   GetIndexesRequest request = GetIndexesRequest.newBuilder()
 *       .setIndexNamePrefix("a")
 *       .setOffset(100)
 *       .setLimit(10)
 *       .build();
 * }</pre>
 *
 */
public final class GetIndexesRequest {

  /**
   * The builder of {@link GetIndexesRequest}s.
   */
  public static final class Builder {

    // Optional
    @Nullable private Integer offset;
    @Nullable private String indexNamePrefix;
    @Nullable private Boolean includeStartIndex;
    @Nullable private String startIndexName;
    @Nullable private Integer limit;
    @Nullable private Boolean schemaFetched;
    @Nullable private String namespace;

    private Builder() {
      includeStartIndex = true;
    }

    private Builder(GetIndexesRequest request) {
      offset = request.getOffset();
      indexNamePrefix = request.getIndexNamePrefix();
      includeStartIndex = request.isIncludeStartIndex();
      startIndexName = request.getStartIndexName();
      limit = request.getLimit();
      schemaFetched = request.isSchemaFetched();
      namespace = request.getNamespace();
    }

    /**
     * Sets the offset of the first index to return. This method comes with
     * a performance penalty and if you just want to page through all indexes
     * you should consider {@link #setStartIndexName(String)} method.
     *
     * @param offset the offset of the first returned index
     * @return this builder
     * @throws IllegalArgumentException if negative or too large offset is given
     */
    public Builder setOffset(Integer offset) {
      this.offset = GetIndexesRequestChecker.checkOffset(offset);
      return this;
    }

    /**
     * Sets the prefix to be matched against the names of returned indexes.
     * If the prefix is set to, say "a", only indexes with names starting with
     * 'a' will be returned.
     *
     * @param indexNamePrefix the prefix used to select returned indexes
     * @return this builder
     * @throws IllegalArgumentException if invalid index name is given
     */
    public Builder setIndexNamePrefix(String indexNamePrefix) {
      this.indexNamePrefix = GetIndexesRequestChecker.checkIndexNamePrefix(indexNamePrefix);
      return this;
    }

    /**
     * Sets whether or not to include the index whose name is specified via
     * the {@link #setStartIndexName(String)} method.
     *
     * @param includeStartIndex whether or not to return the start index
     * @return this builder
     */
    public Builder setIncludeStartIndex(boolean includeStartIndex) {
      this.includeStartIndex = includeStartIndex;
      return this;
    }

    /**
     * Sets the name of the first index to return. You may exclude this index by
     * using the {@link #setIncludeStartIndex(boolean)} method.
     *
     * @param startIndexName the name of the first index to be returned
     * @return this builder
     * @throws IllegalArgumentException if invalid start index name is given
     */
    public Builder setStartIndexName(String startIndexName) {
      this.startIndexName = GetIndexesRequestChecker.checkStartIndexName(startIndexName);
      return this;
    }

    /**
     * Sets the maximum number of indexes to return.
     *
     * @param limit the number of indexes to return
     * @return this builder
     * @throws IllegalArgumentException if negative or too large limit is given
     */
    public Builder setLimit(Integer limit) {
      this.limit = GetIndexesRequestChecker.checkLimit(limit);
      return this;
    }

    /**
     * Sets whether or not the schema is returned with indexes. An index schema
     * is a map from field names to field types.
     *
     * @param schemaFetched whether or not schemas are present in returned indexes
     * @return this builder
     */
    public Builder setSchemaFetched(boolean schemaFetched) {
      this.schemaFetched = schemaFetched;
      return this;
    }

    /**
     * Sets the namespace to use for this request.  Only indexes
     * defined within this namespace will be fetched.
     *
     * @param namespace The namespace for this request.
     * @return this builder
     */
    public Builder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    /**
     * @return builds and returns a brand new instance of
     * a {@link GetIndexesRequest} using values set on this builder
     */
    public GetIndexesRequest build() {
      return new GetIndexesRequest(this);
    }
  }

  private final Integer offset;
  private final String indexNamePrefix;
  private final Boolean includeStartIndex;
  private final String startIndexName;
  private final Integer limit;
  private final Boolean schemaFetched;
  private final String namespace;

  private GetIndexesRequest(Builder builder) {
    offset = builder.offset;
    indexNamePrefix = builder.indexNamePrefix;
    startIndexName = builder.startIndexName;
    includeStartIndex = Util.defaultIfNull(builder.includeStartIndex, Boolean.TRUE);
    limit = Util.defaultIfNull(builder.limit, SearchApiLimits.SEARCH_DEFAULT_LIMIT);
    schemaFetched = builder.schemaFetched;
    namespace = builder.namespace;
    checkValid();
  }


  public static final Builder newBuilder() {
    return new Builder();
  }

  public static final Builder newBuilder(GetIndexesRequest request) {
    return new Builder(request);
  }

  /**
   * @return the offset of the first returned index
   */
  public Integer getOffset() {
    return offset;
  }

  /**
   * @return the prefix matching names of all returned indexes
   */
  public String getIndexNamePrefix() {
    return indexNamePrefix;
  }

  /**
   * @return whether or not the index with the start index name is returned
   */
  public boolean isIncludeStartIndex() {
    return includeStartIndex == null ? true : includeStartIndex;
  }

  /**
   * @return the name of the first index to be returned
   */
  public String getStartIndexName() {
    return startIndexName;
  }

  /**
   * @return the maximum number of indexes returned by this request
   */
  public Integer getLimit() {
    return limit;
  }

  /**
   * @return whether or not index schema is returned with each index
   */
  public Boolean isSchemaFetched() {
    return schemaFetched;
  }

  /**
   * @return the namespace for this request, or null for the default
   * namespace.
   */
  public String getNamespace() {
    return namespace;
  }

  private GetIndexesRequest checkValid() {
    if (limit != null) {
      Preconditions.checkArgument(limit > 0, "Limit must be positive");
    }
    if (offset != null) {
      Preconditions.checkArgument(offset >= 0, "Offset must be non-negative");
    }
    return this;
  }

  ListIndexesParams.Builder copyToProtocolBuffer() {
    ListIndexesParams.Builder builder = ListIndexesParams.newBuilder();
    if (schemaFetched != null) {
      builder.setFetchSchema(schemaFetched);
    }
    if (offset != null) {
      builder.setOffset(offset);
    }
    if (indexNamePrefix != null) {
      builder.setIndexNamePrefix(indexNamePrefix);
    }
    if (startIndexName != null) {
      builder.setStartIndexName(startIndexName);
      builder.setIncludeStartIndex(includeStartIndex);
    }
    if (limit != null) {
      builder.setLimit(limit);
    }
    if (namespace != null) {
      builder.setNamespace(namespace);
    }
    return builder;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((includeStartIndex == null) ? 0 : includeStartIndex.hashCode());
    result = prime * result + ((indexNamePrefix == null) ? 0 : indexNamePrefix.hashCode());
    result = prime * result + ((limit == null) ? 0 : limit.hashCode());
    result = prime * result + ((offset == null) ? 0 : offset.hashCode());
    result = prime * result + ((schemaFetched == null) ? 0 : schemaFetched.hashCode());
    result = prime * result + ((startIndexName == null) ? 0 : startIndexName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    GetIndexesRequest other = (GetIndexesRequest) obj;
    return Util.equalObjects(includeStartIndex, other.includeStartIndex)
        && Util.equalObjects(indexNamePrefix, other.indexNamePrefix)
        && Util.equalObjects(limit, other.limit)
        && Util.equalObjects(offset, other.offset)
        && Util.equalObjects(schemaFetched, other.schemaFetched)
        && Util.equalObjects(startIndexName, other.startIndexName);
  }

  @Override
  public String toString() {
    return "GetIndexesRequest(offset=" + offset + ", indexNamePrefix=" + indexNamePrefix
        + ", includeStartIndex=" + includeStartIndex + ", startIndexName=" + startIndexName
        + ", limit=" + limit + ", schemaFetched=" + schemaFetched
        + ")";
  }
}
