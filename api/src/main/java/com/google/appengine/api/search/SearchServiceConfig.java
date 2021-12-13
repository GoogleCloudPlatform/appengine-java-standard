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

import com.google.appengine.api.NamespaceManager;

/**
 * Configuration options for Search API.
 */
public final class SearchServiceConfig {

  private final Double deadline;
  private final String namespace;

  private SearchServiceConfig(SearchServiceConfig.Builder builder) {
    deadline = builder.deadline;
    namespace = builder.namespace;
  }

  /**
   * Builder for {@link SearchServiceConfig}.
   */
  public static final class Builder {

    private Double deadline;
    private String namespace;

    private Builder(SearchServiceConfig config) {
      deadline = config.deadline;
      namespace = config.namespace;
    }

    private Builder() {
    }

    /*
     * Sets the API deadline in seconds.
     *
     * @param deadlineInSeconds the deadline in seconds.
     *   If null, the default service deadline will be used.
     *
     * @return the modified {@link SearchServiceConfig.Builder} instance
     * @throws IllegalArgumentException if the deadline is negative.
     */
    public SearchServiceConfig.Builder setDeadline(Double deadlineInSeconds)
        throws SearchServiceException {
      if (deadlineInSeconds != null && deadlineInSeconds <= 0.0) {
        throw new IllegalArgumentException("Invalid Deadline. Must be a positive number.");
      }
      this.deadline = deadlineInSeconds;
      return this;
    }

    /*
     * Sets the namespace to use for API calls.
     *
     * @param namespace a valid namespace, as per
     *   {@link NamespaceManager#validateNamespace(String)}
     *
     * @return the modified {@link SearchServiceConfig.Builder} instance
     * @throws IllegalArgumentException if the provided namespace is not valid as per
     *   {@link NamespaceManager#validateNamespace(String)}
     */
    public SearchServiceConfig.Builder setNamespace(String namespace) {
      if (namespace != null) {
        NamespaceManager.validateNamespace(namespace);
      }
      this.namespace = namespace;
      return this;
    }

    /**
     * Builds a configuration.
     *
     * @return the configuration built by this builder
     */
    public SearchServiceConfig build() {
      return new SearchServiceConfig(this);
    }
  }

  /**
   * Creates a new {@link SearchServiceConfig.Builder}.
   *
   * @return the newly created {@link SearchServiceConfig.Builder} instance
   */
  public static SearchServiceConfig.Builder newBuilder() {
    return new SearchServiceConfig.Builder();
  }

  /**
   * Converts this config instance to a builder.
   *
   * @return the newly created {@link SearchServiceConfig.Builder} instance
   */
  public SearchServiceConfig.Builder toBuilder() {
    return new SearchServiceConfig.Builder(this);
  }

  /**
   * Returns the API deadline in seconds.
   *
   * @return the deadline in seconds or null if no deadline has been set
   */
  public Double getDeadline() {
    return deadline;
  }

  /*
   * Returns the configured namespace.
   *
   * @return the namespace or null if no namespace has been set
   */
  public String getNamespace() {
    return namespace;
  }
}

