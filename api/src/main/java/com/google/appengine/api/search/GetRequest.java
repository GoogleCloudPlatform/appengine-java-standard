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

import com.google.appengine.api.search.checkers.GetRequestChecker;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb.ListDocumentsParams;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A request to list objects in an index. You can specify a number of
 * restrictions, such as the number of objects to return, the id of the
 * first object to return, whether to only return keys, etc.
 *
 * <pre>{@code
 *   GetRequest request = GetRequest.newBuilder()
 *       .setLimit(500)
 *       .setStartId("some-id")
 *       .setReturningIdsOnly(true)
 *       .build();
 * }</pre>
 *
 */
public class GetRequest {

  /**
   * The builder of {@link GetRequest}s.
   */
  public static class Builder {

    // Optional
    @Nullable private String startId;
    @Nullable private Boolean includeStart;
    @Nullable private Integer limit;
    @Nullable private Boolean returningIdsOnly;

    protected Builder() {
      includeStart = true;
      returningIdsOnly = false;
    }

    private Builder(GetRequest request) {
      startId = request.getStartId();
      includeStart = request.isIncludeStart();
      limit = request.getLimit();
      returningIdsOnly = request.isReturningIdsOnly();
    }

    /**
     * Sets the Id of the first object to return. You may exclude this
     * object by using the {@link #setIncludeStart(boolean)} method.
     *
     * @param startId the Id of the first object to return
     * @return this builder
     * @throws IllegalArgumentException if invalid object Id is given
     */
    public Builder setStartId(String startId) {
      this.startId = GetRequestChecker.checkStartDocId(startId);
      return this;
    }

    /**
     * Sets whether or not to include the object whose ID is specified via
     * the {@link #setStartId(String)} method.
     *
     * @param includeStart whether or not to return the start index
     * @return this builder
     */
    public Builder setIncludeStart(boolean includeStart) {
      this.includeStart = includeStart;
      return this;
    }

    /**
     * Sets the maximum number of objects to return.
     *
     * @param limit the maximum number of objects to return
     * @return this builder
     * @throws IllegalArgumentException if negative or too large limit is given
     */
    public Builder setLimit(Integer limit) {
      this.limit = GetRequestChecker.checkLimit(limit);
      return this;
    }

    /**
     * Sets whether just objects containing just their key are returned, or
     * whether the complete objects are returned.
     *
     * @param returningIdsOnly whether to only return object keys
     * @return this builder
     */
    public Builder setReturningIdsOnly(boolean returningIdsOnly) {
      this.returningIdsOnly = returningIdsOnly;
      return this;
    }

    /**
     * @return builds and returns a brand new instance of
     * a {@link GetRequest} using values set on this builder
     */
    public GetRequest build() {
      return new GetRequest(this);
    }
  }

  private final String startId;
  private final boolean includeStart;
  private final int limit;
  private final boolean returningIdsOnly;

  protected GetRequest(Builder builder) {
    startId = builder.startId;
    includeStart = (startId == null)
        ? Boolean.FALSE : Util.defaultIfNull(builder.includeStart, Boolean.TRUE);
    limit = Util.defaultIfNull(builder.limit, SearchApiLimits.GET_RANGE_DEFAULT_LIMIT);
    returningIdsOnly = Util.defaultIfNull(builder.returningIdsOnly, Boolean.FALSE);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final Builder newBuilder(GetRequest request) {
    return new Builder(request);
  }

  /**
   * @return the Id of the first object to return
   */
  public String getStartId() {
    return startId;
  }

  /**
   * @return whether or not the object with the start Id is returned
   */
  public boolean isIncludeStart() {
    return includeStart;
  }

  /**
   * @return the maximum number of objects returned by this request
   */
  public int getLimit() {
    return limit;
  }

  /**
   * @return whether or not index schema is returned with each index
   */
  public Boolean isReturningIdsOnly() {
    return returningIdsOnly;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (includeStart ? 1 : 0);
    result = prime * result + (returningIdsOnly ? 1 : 0);
    result = prime * result + limit;
    result = prime * result + ((startId == null) ? 0 : startId.hashCode());
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
    GetRequest other = (GetRequest) obj;
    return includeStart == other.includeStart
        && returningIdsOnly == other.returningIdsOnly
        && limit == other.limit
        && Util.equalObjects(startId, other.startId);
  }

  @Override
  public String toString() {
    return "GetRequest(includeStart=" + includeStart + ", startId=" + startId
        + ", limit=" + limit + ", returningIdsOnly=" + returningIdsOnly + ")";
  }

  ListDocumentsParams.Builder copyToProtocolBuffer() {
    ListDocumentsParams.Builder builder = ListDocumentsParams.newBuilder();
    if (isReturningIdsOnly()) {
      builder.setKeysOnly(true);
    }
    if (getStartId() != null) {
      builder.setStartDocId(getStartId());
    }
    if (!isIncludeStart()) {
      builder.setIncludeStartDoc(false);
    }
    builder.setLimit(getLimit());
    return builder;
  }
}
