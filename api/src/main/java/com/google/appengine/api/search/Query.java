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

import com.google.appengine.api.search.checkers.QueryChecker;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A query to search an index for documents which match,
 * restricting the document fields returned to those given, and
 * scoring and sorting the results, whilst supporting pagination.
 * <p>
 * For example, the following query will search for documents where
 * the tokens 'good' and 'story' occur in some fields,
 * returns up to 20 results including the fields 'author' and 'date-sent'
 * as well as snippeted fields 'subject' and 'body'. The results
 * are sorted by 'author' in descending order, getting the next 20 results
 * from the responseCursor in the previously returned results, giving
 * back a single cursor in the {@link Results} to get the next
 * batch of results after this.
 * <p>
 * <pre>{@code
 * QueryOptions options = QueryOptions.newBuilder()
 *     .setLimit(20)
 *     .setFieldsToSnippet("subject", "body")
 *     .setScorer(CustomScorer.newBuilder()
 *         .addSortExpression(SortExpression.newBuilder()
 *             .setExpression("author")
 *             .setDirection(SortDirection.DESCENDING)
 *             .setDefaultValue("")))
 *     .setCursor(responseCursor)
 *     .build();
 * Query query = Query.newBuilder()
 *     .setOptions(options)
 *     .build("good story");
 * }
 * </pre>
 *
 * The following query will return facet information with the query result:
 * <pre>
 * Query query = Query.newBuilder()
 *     .setOptions(options)
 *     .setEnableFacetDiscovery(true)
 *     .build("tablet");
 * </pre>
 *
 * To customize returned facet or refine the result using a previously returned
 * {@link FacetResultValue#getRefinementToken}:
 * <pre>
 * Query query = Query.newBuilder()
 *     .setOptions(options)
 *     .setEnableFacetDiscovery(true)
 *     .setFacetOptions(FacetOptions.newBuilder().setDiscoveryLimit(5).build())
 *     .addReturnFacet("shipping")
 *     .addReturnFacet(FacetRequest.newBuilder().setName("department")
 *         .addValueConstraint("Computers")
 *         .addValueConstraint("Electronics")
 *         .build())
 *     .addRefinementToken(refinementToken1)
 *     .addRefinementToken(refinementToken2)
 *     .build("tablet");
 * </pre>
 */
public class Query {

  /**
   * A builder which constructs Query objects.
   */
  public static class Builder {
    // Mandatory
    private String queryString;
    private boolean enableFacetDiscovery;

    // Optional
    @Nullable private QueryOptions options;
    @Nullable private FacetOptions facetOptions;
    @Nullable private List<FacetRequest> returnFacets = new ArrayList<>();
    @Nullable private List<FacetRefinement> refinements = new ArrayList<>();
    // NOTE: We will add query id and schema as Nullable attributes.

    protected Builder() {}

    /**
     * Constructs a {@link Query} builder with the given query.
     *
     * @param query the query to populate the builder
     */
    private Builder(Query query) {
      this.queryString = query.getQueryString();
      this.enableFacetDiscovery = query.getEnableFacetDiscovery();
      this.options = query.getOptions();
      this.facetOptions = query.getFacetOptions();
      this.returnFacets = new ArrayList<>(query.getReturnFacets());
      this.refinements = new ArrayList<>(query.getRefinements());
    }

    /**
     * Sets the query options.
     *
     * @param options the {@link QueryOptions} to apply to the search results
     * @return this builder
     */
    public Builder setOptions(QueryOptions options) {
      this.options = options;
      return this;
    }

    /**
     * Sets the query options from a builder.
     *
     * @param optionsBuilder the {@link QueryOptions.Builder} build a
     * {@link QueryOptions} to apply to the search results
     * @return this builder
     */
    public Builder setOptions(QueryOptions.Builder optionsBuilder) {
      return setOptions(optionsBuilder.build());
    }

    /**
     * Sets the facet options.
     *
     * @param options the {@link FacetOptions} to apply to the facet results
     * @return this builder
     */
    public Builder setFacetOptions(FacetOptions options) {
      this.facetOptions = options;
      return this;
    }

    /**
     * Sets the facet options from a builder.
     *
     * @param builder the {@link FacetOptions.Builder} build a {@link FacetOptions}
     * to apply to the facet results
     * @return this builder
     */
    public Builder setFacetOptions(FacetOptions.Builder builder) {
      return this.setFacetOptions(builder.build());
    }

    /**
     * Sets enable facet discovery flag.
     *
     * @return this builder
     */
    public Builder setEnableFacetDiscovery(boolean value) {
      this.enableFacetDiscovery = value;
      return this;
    }

    /**
     * Requests a facet to be returned with search results. The facet will be included in
     * the result regardless of the number of values it has.
     *
     * @param facet the {@link FacetRequest} to be added to return facets.
     * @return this builder
     */
    public Builder addReturnFacet(FacetRequest facet) {
      this.returnFacets.add(facet);
      return this;
    }

    /**
     * Adds a facet refinement token. The token is returned by each FacetResultValue. There will be
     * disjunction between tokens for the same facet and conjunction between tokens for different
     * facets. For example if the refinement tokens are (name=wine_type,value=red),
     * (name=wine_type,value=white) and (name=year, Range(2000,2010)),
     * the result will be refined according to:
     * <pre>
     * ((wine_type is red) OR (wine_type is white)) AND (year in Range(2000,2010))
     * </pre>
     *
     * @param token the token returned by {@link FacetResultValue#getRefinementToken} or
     *   {@link FacetRefinement#toTokenString}.
     * @return this builder
     * @throws IllegalArgumentException if token is not valid.
     */
    public Builder addFacetRefinementFromToken(String token) {
      return addFacetRefinement(FacetRefinement.fromTokenString(token));
    }

    /**
     * Adds a facet refinement. There will be disjunction between refinements for the same facet
     * and conjunction between refinements for different facets. For example if the refinements are
     * (name=wine_type,value=red), (name=wine_type,value=white) and (name=year, Range(2000,2010)),
     * the result will be refined according to:
     * <pre>
     * ((wine_type is red) OR (wine_type is white)) AND (year in Range.closedOpen(2000,2010))
     * </pre>
     *
     * @param refinement a {@link FacetRefinement} object.
     * @return this builder
     */
    public Builder addFacetRefinement(FacetRefinement refinement) {
      this.refinements.add(refinement);
      return this;
    }

    /**
     * Adds a facet request from a builder.
     *
     * @param builder the {@link FacetRequest.Builder} build a {@link FacetRequest}
     * to be added to return facets.
     * @return this builder
     */
    public Builder addReturnFacet(FacetRequest.Builder builder) {
      return addReturnFacet(builder.build());
    }

    /**
     * Adds a facet request by its name only.
     *
     * @param facetName the name of the facet to be added to return facets.
     * @return this builder
     */
    public Builder addReturnFacet(String facetName) {
      this.returnFacets.add(FacetRequest.newBuilder().setName(facetName).build());
      return this;
    }

    /**
     * Sets the query string used to construct the query.
     *
     * @param query a query string used to construct the query
     * @return this Builder
     * @throws SearchQueryException if the query string does not parse
     */
    protected Builder setQueryString(String query) {
      this.queryString = QueryChecker.checkQuery(query);
      return this;
    }

    /**
     * Build a {@link Query} from  the query string and the parameters set on
     * the {@link Builder}. A query string can be as simple as a single term
     * ("foo"), or as complex as a boolean expression, including field names
     * ("title:hello OR body:important -october").
     *
     * @param queryString the query string to parse and apply to an index
     * @return the Query built from the parameters entered on this
     * Builder including the queryString
     * @throws SearchQueryException if the query string is invalid
     */
    public Query build(String queryString) {
      // TODO: put in url to publically available documentation about
      // VanillaST in the javadoc.
      setQueryString(queryString);
      return new Query(this);
    }

    /**
     * Construct the message.
     *
     * @return the Query built from the parameters entered on this
     * Builder
     * @throws IllegalArgumentException if the query string is invalid
     */
    public Query build() {
      return new Query(this);
    }
  }

  // Mandatory
  private final String query;
  private final boolean enableFacetDiscovery;

  // Optional
  @Nullable private final QueryOptions options;
  @Nullable private final FacetOptions facetOptions;
  @Nullable private final ImmutableList<FacetRequest> returnFacets;
  @Nullable private final ImmutableList<FacetRefinement> refinements;

  /**
   * Creates a query from the builder.
   *
   * @param builder the query builder to populate with
   */
  protected Query(Builder builder) {
    query = builder.queryString;
    enableFacetDiscovery = builder.enableFacetDiscovery;
    options = builder.options;
    facetOptions = builder.facetOptions;
    returnFacets = ImmutableList.copyOf(builder.returnFacets);
    refinements = ImmutableList.copyOf(builder.refinements);
    checkValid();
  }

  /**
   * The query can be as simple as a single term ("foo"), or as complex
   * as a boolean expression, including field names ("title:hello OR
   * body:important -october").
   *
   * @return the query
   */
  public String getQueryString() {
    return query;
  }

  /**
   * Returns the {@link QueryOptions} for controlling the what is returned
   * in the result set matching the query
   */
  public QueryOptions getOptions() {
    return options;
  }

  /**
   * Returns the {@link FacetOptions} for controlling faceted search or null if unset.
   */
  public FacetOptions getFacetOptions() {
    return facetOptions;
  }

  /**
   * Returns true if facet discovery is enabled.
   */
  public boolean getEnableFacetDiscovery() {
    return enableFacetDiscovery;
  }

  /**
   * Returns an unmodifiable list of requests for facets to be returned with the search results.
   */
  public ImmutableList<FacetRequest> getReturnFacets() {
    return returnFacets;
  }

  /**
   * Returns an unmodifiable list of facet refinements for the search.
   */
  public ImmutableList<FacetRefinement> getRefinements() {
    return refinements;
  }

  /**
   * Creates and returns a {@link Query} builder. Set the query
   * parameters and use the {@link Builder#build()} method to create a concrete
   * instance of Query.
   *
   * @return a {@link Builder} which can construct a query
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a builder from the given query.
   *
   * @param query the query for the builder to use to build another query
   * @return a new builder with values based on the given request
   */
  public static Builder newBuilder(Query query) {
    return new Builder(query);
  }

  /**
   * Checks the query is valid, specifically, has a non-null
   * query string.
   *
   * @return this checked Query
   * @throws NullPointerException if query is null
   */
  private Query checkValid() {
    Preconditions.checkNotNull(query, "query cannot be null");
    return this;
  }

  /**
   * Copies the contents of this {@link Query} object into a
   * {@link SearchParams} protocol buffer builder.
   *
   * @return a search params protocol buffer builder initialized with
   * the values from this query
   * @throws IllegalArgumentException if the cursor type is
   * unknown
   */
  SearchParams.Builder copyToProtocolBuffer() {
    SearchParams.Builder builder = SearchParams.newBuilder().setQuery(query);
    if (options == null) {
      // Use all the default settings from QueryOptions.
      QueryOptions.newBuilder().build().copyToProtocolBuffer(builder, query);
    } else {
      options.copyToProtocolBuffer(builder, query);
    }
    if (facetOptions == null) {
      FacetOptions.newBuilder().build().copyToProtocolBuffer(builder, enableFacetDiscovery);
    } else {
      facetOptions.copyToProtocolBuffer(builder, enableFacetDiscovery);
    }
    for (FacetRequest facet : returnFacets) {
      builder.addIncludeFacet(facet.copyToProtocolBuffer());
    }
    for (FacetRefinement ref : refinements) {
      builder.addFacetRefinement(ref.toProtocolBuffer());
    }
    return builder;
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("Query")
        .addField("queryString", query)
        .addField("options", options)
        .addField("enableFacetDiscovery", enableFacetDiscovery ? Boolean.TRUE : null)
        .addField("facetOptions", facetOptions)
        .addIterableField("returnFacets", returnFacets)
        .addIterableField("refinements", refinements)
        .finish();
  }
}
