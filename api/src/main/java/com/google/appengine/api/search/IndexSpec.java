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

import com.google.appengine.api.search.checkers.IndexChecker;
import com.google.appengine.api.search.proto.SearchServicePb;

/**
 * Represents information about an index. This class is used to fully specify
 * the index you want to retrieve from the {@link SearchService}.
 * To build an instance use the {@link #newBuilder()} method and set
 * all required parameters, plus optional values different than the defaults.
 * <pre>{@code
 *   SearchService searchService = SearchServiceFactory.getSearchService();
 *
 *   IndexSpec spec = IndexSpec.newBuilder()
 *       .setName("docs")
 *       .build();
 *
 *   Index index = searchService.getIndex(spec);
 * }
 * </pre>
 *
 */
public class IndexSpec {

  /**
   * A builder of IndexSpec.
   */
  public static final class Builder {
    private String name;

    /**
     * Constructs a builder for an IndexSpec.
     */
    private Builder() {
    }

    /**
     * Sets the unique name of the index.
     *
     * @param name the name of the index
     * @return this Builder
     * @throws IllegalArgumentException if the index name length is not between 1
     * and {@literal IndexChecker#MAXIMUM_INDEX_NAME_LENGTH}
     */
    public Builder setName(String name) {
      this.name = IndexChecker.checkName(name);
      return this;
    }

    /**
     * Builds a valid IndexSpec. The builder must have set a valid
     * index name.
     *
     * @return the IndexSpec built by this builder
     * @throws IllegalArgumentException if the IndexSpec built is not valid
     */
    public IndexSpec build() {
      return new IndexSpec(this);
    }
  }

  private final String name;

  /**
   * Creates new index specification.
   *
   * @param builder the IndexSpec builder to use to construct an instance
   * @throws IllegalArgumentException if the index name is invalid
   */
  private IndexSpec(Builder builder) {
    name = builder.name;
  }

  /**
   * @return the name of the index
   */
  public String getName() {
    return name;
  }

  /**
   * Creates a new IndexSpec builder. You must use this method to obtain a new
   * builder. The returned builder must be used to specify all properties of
   * the IndexSpec. To obtain the IndexSpec call the {@link Builder#build()}
   * method on the returned builder.
   *
   * @return a builder which constructs a IndexSpec object
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
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
    IndexSpec other = (IndexSpec) obj;
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return String.format("IndexSpec{name: %s}", name);
  }

  /**
   * Creates an SearchServicePb.IndexSpec from the given Index.
   *
   * @param namespace the namespace for the index
   * @return a valid SearchServicePb.IndexSpec.Builder
   */
  SearchServicePb.IndexSpec.Builder copyToProtocolBuffer(String namespace) {
      SearchServicePb.IndexSpec.Builder builder = SearchServicePb.IndexSpec.newBuilder()
        .setName(getName())
        .setNamespace(namespace);
    return builder;
  }
}
