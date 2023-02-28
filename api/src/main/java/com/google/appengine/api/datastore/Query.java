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

package com.google.appengine.api.datastore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.internal.ImmutableCopy;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@link Query} encapsulates a request for zero or more {@link Entity} objects out of the
 * datastore. It supports querying on zero or more properties, querying by ancestor, and sorting.
 * {@link Entity} objects which match the query can be retrieved in a single list, or with an
 * unbounded iterator.
 */
public final class Query implements Serializable {
  @Deprecated public static final String KIND_METADATA_KIND = Entities.KIND_METADATA_KIND;

  @Deprecated public static final String PROPERTY_METADATA_KIND = Entities.PROPERTY_METADATA_KIND;

  @Deprecated public static final String NAMESPACE_METADATA_KIND = Entities.NAMESPACE_METADATA_KIND;

  static final long serialVersionUID = 7090652715949085374L;

  /** SortDirection controls the order of a sort. */
  public enum SortDirection {
    ASCENDING,
    DESCENDING
  }

  /** Operators supported by {@link FilterPredicate}. */
  public enum FilterOperator {
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    EQUAL("="),
    NOT_EQUAL("!="),
    IN("IN");

    private final String shortName;

    private FilterOperator(String shortName) {
      this.shortName = shortName;
    }

    @Override
    public String toString() {
      return shortName;
    }

    public FilterPredicate of(String propertyName, Object value) {
      return new FilterPredicate(propertyName, this, value);
    }
  }

  /** Operators supported by {@link CompositeFilter}. */
  public enum CompositeFilterOperator {
    AND,
    OR;

    public static CompositeFilter and(Filter... subFilters) {
      return and(Arrays.asList(subFilters));
    }

    public static CompositeFilter and(Collection<Filter> subFilters) {
      return new CompositeFilter(AND, subFilters);
    }

    public static CompositeFilter or(Filter... subFilters) {
      return or(Arrays.asList(subFilters));
    }

    public static CompositeFilter or(Collection<Filter> subFilters) {
      return new CompositeFilter(OR, subFilters);
    }

    public CompositeFilter of(Filter... subFilters) {
      return new CompositeFilter(this, Arrays.asList(subFilters));
    }

    public CompositeFilter of(Collection<Filter> subFilters) {
      return new CompositeFilter(this, subFilters);
    }
  }

  private final @Nullable String kind;
  private final List<SortPredicate> sortPredicates = Lists.newArrayList();
  private final List<FilterPredicate> filterPredicates = Lists.newArrayList();
  private @Nullable Filter filter;
  private Key ancestor;
  private boolean keysOnly;
  private final Map<String, Projection> projectionMap = Maps.newLinkedHashMap();
  private boolean distinct;
  private AppIdNamespace appIdNamespace;

  /**
   * Create a new kindless {@link Query} that finds {@link Entity} objects. Note that kindless
   * queries are not yet supported in the Java dev appserver.
   *
   * <p>Currently the only operations supported on a kindless query are filter by __key__, ancestor,
   * and order by __key__ ascending.
   */
  public Query() {
    this(null, null);
  }

  /**
   * Create a new {@link Query} that finds {@link Entity} objects with the specified {@code kind}.
   * Note that kindless queries are not yet supported in the Java dev appserver.
   *
   * @param kind the kind or null to create a kindless query
   */
  public Query(String kind) {
    this(kind, null);
  }

  /**
   * Copy constructor that performs a deep copy of the provided Query.
   *
   * @param query The Query to copy.
   */
  Query(Query query) {
    this(
        query.kind,
        query.ancestor,
        query.sortPredicates,
        query.filter,
        query.filterPredicates,
        query.keysOnly,
        query.appIdNamespace,
        query.projectionMap.values(),
        query.distinct);
  }

  Query(
      @Nullable String kind,
      @Nullable Key ancestor,
      @Nullable Filter filter,
      boolean keysOnly,
      AppIdNamespace appIdNamespace,
      boolean distinct) {
    this.kind = kind;
    this.keysOnly = keysOnly;
    this.appIdNamespace = appIdNamespace;
    this.distinct = distinct;
    this.filter = filter;
    if (ancestor != null) {
      setAncestor(ancestor);
    }
  }

  Query(
      @Nullable String kind,
      Key ancestor,
      Collection<SortPredicate> sortPreds,
      @Nullable Filter filter,
      Collection<FilterPredicate> filterPreds,
      boolean keysOnly,
      AppIdNamespace appIdNamespace,
      Collection<Projection> projections,
      boolean distinct) {
    this(kind, ancestor, filter, keysOnly, appIdNamespace, distinct);
    this.sortPredicates.addAll(sortPreds);
    this.filterPredicates.addAll(filterPreds);
    for (Projection projection : projections) {
      addProjection(projection);
    }
  }

  /**
   * Create a new {@link Query} that finds {@link Entity} objects with the specified {@code Key} as
   * an ancestor.
   *
   * @param ancestor the ancestor key or null
   * @throws IllegalArgumentException If ancestor is not complete.
   */
  public Query(Key ancestor) {
    this(null, ancestor);
  }

  /**
   * Create a new {@link Query} that finds {@link Entity} objects with the specified {@code kind}
   * and the specified {@code ancestor}. Note that kindless queries are not yet supported in the
   * Java dev appserver.
   *
   * @param kind the kind or null to create a kindless query
   * @param ancestor the ancestor key or null
   * @throws IllegalArgumentException If the ancestor is not complete.
   */
  public Query(@Nullable String kind, @Nullable Key ancestor) {
    this(kind, ancestor, null, false, DatastoreApiHelper.getCurrentAppIdNamespace(), false);
  }

  // readObject is called by the deserializer.
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Legacy support.  Always make sure that the appIdNamespace is set.
    if (appIdNamespace == null) {
      if (ancestor != null) {
        appIdNamespace = ancestor.getAppIdNamespace();
      } else {
        appIdNamespace = new AppIdNamespace(DatastoreApiHelper.getCurrentAppId(), "");
      }
    }
  }

  /** Only {@link Entity} objects whose kind matches this value will be returned. */
  public @Nullable String getKind() {
    return kind;
  }

  /**
   * Returns the AppIdNamespace that is being queried.
   *
   * <p>The AppIdNamespace is set at construction time of this object using the {@link
   * com.google.appengine.api.NamespaceManager} to retrieve the current namespace.
   */
  AppIdNamespace getAppIdNamespace() {
    return appIdNamespace;
  }

  /** Returns the appId for this {@link Query}. */
  public String getAppId() {
    return appIdNamespace.getAppId();
  }

  /** Returns the namespace for this {@link Query}. */
  public String getNamespace() {
    return appIdNamespace.getNamespace();
  }

  /** Gets the current ancestor for this query, or null if there is no ancestor specified. */
  public Key getAncestor() {
    return ancestor;
  }

  /**
   * Sets an ancestor for this query.
   *
   * <p>This restricts the query to only return result entities that are descended from a given
   * entity. In other words, all of the results will have the ancestor as their parent, or parent's
   * parent, or etc.
   *
   * <p>If null is specified, unsets any previously-set ancestor. Passing {@code null} as a
   * parameter does not query for entities without ancestors (this type of query is not currently
   * supported).
   *
   * @return {@code this} (for chaining)
   * @throws IllegalArgumentException If the ancestor key is incomplete, or if you try to unset an
   *     ancestor and have not set a kind, or if you try to unset an ancestor and have not
   *     previously set an ancestor.
   */
  public Query setAncestor(Key ancestor) {
    if (ancestor != null && !ancestor.isComplete()) {
      throw new IllegalArgumentException(ancestor + " is incomplete.");
    } else if (ancestor == null) {
      if (this.ancestor == null) {
        throw new IllegalArgumentException(
            "Cannot clear ancestor unless ancestor has already been set");
      }
    }
    if (ancestor != null) {
      if (!ancestor.getAppIdNamespace().equals(appIdNamespace)) {
        throw new IllegalArgumentException("Namespace of ancestor key and query must match.");
      }
    }
    this.ancestor = ancestor;
    return this;
  }

  /**
   * Sets whether to return only results with distinct values of the properties being projected.
   *
   * @param distinct if this query should be distinct. This may only be used when the query has a
   *     projection.
   * @return {@code this} (for chaining)
   */
  public Query setDistinct(boolean distinct) {
    this.distinct = distinct;
    return this;
  }

  /**
   * Returns whether this query is distinct.
   *
   * @see #setDistinct(boolean)
   */
  public boolean getDistinct() {
    return distinct;
  }

  /**
   * Sets the filter to use for this query.
   *
   * @param filter the filter to use for this query, or {@code null}
   * @return {@code this} (for chaining)
   * @see CompositeFilter
   * @see FilterPredicate
   */
  public Query setFilter(@Nullable Filter filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Returns the filter for this query, or {@code null}.
   *
   * @see #setFilter(Filter)
   */
  public @Nullable Filter getFilter() {
    return filter;
  }

  /**
   * Add a {@link FilterPredicate} on the specified property.
   *
   * <p>All {@link FilterPredicate}s added using this message are combined using {@link
   * CompositeFilterOperator#AND}.
   *
   * <p>Cannot be used in conjunction with {@link #setFilter(Filter)} which sets a single {@link
   * Filter} instead of many {@link FilterPredicate}s.
   *
   * @param propertyName The name of the property to which the filter applies.
   * @param operator The filter operator.
   * @param value An instance of a supported datastore type. Note that entities with multi-value
   *     properties identified by {@code propertyName} will match this filter if the multi-value
   *     property has at least one value that matches the condition expressed by {@code operator}
   *     and {@code value}. For more information on multi-value property filtering please see the <a
   *     href="http://cloud.google.com/appengine/docs/java/datastore/">datastore documentation</a>.
   * @return {@code this} (for chaining)
   * @throws NullPointerException If {@code propertyName} or {@code operator} is null.
   * @throws IllegalArgumentException If {@code value} is not of a type supported by the datastore.
   *     See {@link DataTypeUtils#isSupportedType(Class)}. Note that unlike {@link
   *     Entity#setProperty(String, Object)}, you cannot provide a {@link Collection} containing
   *     instances of supported types to this method.
   * @deprecated Use {@link #setFilter(Filter)}
   */
  @Deprecated
  public Query addFilter(String propertyName, FilterOperator operator, Object value) {
    filterPredicates.add(operator.of(propertyName, value));
    return this;
  }

  /**
   * Returns a mutable list of the current filter predicates.
   *
   * @see #addFilter(String, FilterOperator, Object)
   * @deprecated Use {@link #setFilter(Filter)} and {@link #getFilter()} instead
   */
  @Deprecated
  public List<FilterPredicate> getFilterPredicates() {
    return filterPredicates;
  }

  /**
   * Specify how the query results should be sorted.
   *
   * <p>The first call to addSort will register the property that will serve as the primary sort
   * key. A second call to addSort will set a secondary sort key, etc.
   *
   * <p>This method will always sort in ascending order. To control the order of the sort, use
   * {@link #addSort(String,SortDirection)}.
   *
   * <p>Note that entities with multi-value properties identified by {@code propertyName} will be
   * sorted by the smallest value in the list. For more information on sorting properties with
   * multiple values please see the <a
   * href="http://cloud.google.com/appengine/docs/java/datastore/">datastore documentation</a>.
   *
   * @return {@code this} (for chaining)
   * @throws NullPointerException If any argument is null.
   */
  public Query addSort(String propertyName) {
    return addSort(propertyName, SortDirection.ASCENDING);
  }

  /**
   * Specify how the query results should be sorted.
   *
   * <p>The first call to addSort will register the property that will serve as the primary sort
   * key. A second call to addSort will set a secondary sort key, etc.
   *
   * <p>Note that if {@code direction} is {@link SortDirection#ASCENDING}, entities with multi-value
   * properties identified by {@code propertyName} will be sorted by the smallest value in the list.
   * If {@code direction} is {@link SortDirection#DESCENDING}, entities with multi-value properties
   * identified by {@code propertyName} will be sorted by the largest value in the list. For more
   * information on sorting properties with multiple values please see the <a
   * href="http://cloud.google.com/appengine/docs/java/datastore/">datastore documentation</a>.
   *
   * @return {@code this} (for chaining)
   * @throws NullPointerException If any argument is null.
   */
  public Query addSort(String propertyName, SortDirection direction) {
    sortPredicates.add(new SortPredicate(propertyName, direction));
    return this;
  }

  /** Returns a mutable list of the current sort predicates. */
  public List<SortPredicate> getSortPredicates() {
    return sortPredicates;
  }

  /**
   * Makes this query fetch and return only keys, not full entities.
   *
   * @return {@code this} (for chaining)
   */
  public Query setKeysOnly() {
    keysOnly = true;
    return this;
  }

  /**
   * Clears the keys only flag.
   *
   * @see #setKeysOnly()
   * @return {@code this} (for chaining)
   */
  public Query clearKeysOnly() {
    keysOnly = false;
    return this;
  }

  /**
   * Adds a projection for this query.
   *
   * <p>Projections are limited in the following ways:
   *
   * <ul>
   *   <li>Un-indexed properties cannot be projected and attempting to do so will result in no
   *       entities being returned.
   *   <li>Projection {@link Projection#getName() names} must be unique.
   *   <li>Properties that have an equality filter on them cannot be projected. This includes the
   *       operators {@link FilterOperator#EQUAL} and {@link FilterOperator#IN}.
   * </ul>
   *
   * @see #getProjections()
   * @param projection the projection to add
   * @return {@code this} (for chaining)
   * @throws IllegalArgumentException if the query already contains a projection with the same name
   */
  public Query addProjection(Projection projection) {
    Preconditions.checkArgument(
        !projectionMap.containsKey(projection.getName()),
        "Query already contains projection with name: " + projection.getName());
    projectionMap.put(projection.getName(), projection);
    return this;
  }

  /**
   * Returns a mutable collection properties included in the projection for this query.
   *
   * <p>If empty, the full or keys only entities are returned. Otherwise partial entities are
   * returned. A non-empty projection is not compatible with setting keys-only. In this case a
   * {@link IllegalArgumentException} will be thrown when the query is {@link
   * DatastoreService#prepare(Query) prepared}.
   *
   * <p>Projection queries are similar to SQL statements of the form:
   *
   * <pre>{@code SELECT prop1, prop2, ... }</pre>
   *
   * As they return partial entities, which only contain the properties specified in the projection.
   * However, these entities will only contain a single value for any multi-valued property and, if
   * a multi-valued property is specified in the order, an inequality property, or the projected
   * properties, the entity will be returned multiple times. Once for each unique combination of
   * values.
   *
   * <p>Specifying a projection:
   *
   * <ul>
   *   <li>May change the type of any property returned in a projection.
   *   <li>May change the index requirements for the given query.
   *   <li>Will cause a partial entity to be returned.
   *   <li>Will cause only entities that contain those properties to be returned.
   * </ul>
   *
   * However, projection queries are significantly faster than normal queries.
   *
   * @return a mutable collection properties included in the projection for this query
   * @see #addProjection(Projection)
   */
  public Collection<Projection> getProjections() {
    return projectionMap.values();
  }

  /**
   * Returns true if this query will fetch and return keys only, false if it will fetch and return
   * full entities.
   */
  public boolean isKeysOnly() {
    return keysOnly;
  }

  /**
   * Creates a query sorted in the exact opposite direction as the current one.
   *
   * <p>This function requires a sort order on {@link Entity#KEY_RESERVED_PROPERTY} to guarantee
   * that each entity is uniquely identified by the set of properties used in the sort (which is
   * required to exactly reverse the order of a query). Advanced users can reverse the sort orders
   * manually if they know the set of sorted properties meets this requirement without a order on
   * {@link Entity#KEY_RESERVED_PROPERTY}.
   *
   * <p>The results of the reverse query will be the same as the results of the forward query but in
   * reverse direction.
   *
   * <p>{@link Cursor Cursors} from the original query may also be used in the reverse query.
   *
   * @return A new query with the sort order reversed.
   * @throws IllegalStateException if the current query is not sorted by {@link
   *     Entity#KEY_RESERVED_PROPERTY}.
   */
  public Query reverse() {
    List<SortPredicate> order = new ArrayList<SortPredicate>(sortPredicates.size());
    boolean hasKeyOrder = false;
    for (SortPredicate sort : sortPredicates) {
      if (Entity.KEY_RESERVED_PROPERTY.equals(sort.getPropertyName())) {
        hasKeyOrder = true;
      }
      // Reverse the order for all properties in non-distinct queries
      // and only for projected properties in distinct queries.
      if (!distinct || projectionMap.containsKey(sort.getPropertyName())) {
        order.add(sort.reverse());
      } else {
        order.add(sort);
      }
    }
    Preconditions.checkState(
        hasKeyOrder,
        "A sort on " + Entity.KEY_RESERVED_PROPERTY + " is required to reverse a Query");

    return new Query(
        kind,
        ancestor,
        order,
        filter,
        filterPredicates,
        keysOnly,
        appIdNamespace,
        projectionMap.values(),
        distinct);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Query query = (Query) o;

    if (keysOnly != query.keysOnly) {
      return false;
    }

    if (!Objects.equals(ancestor, query.ancestor)) {
      return false;
    }
    if (!appIdNamespace.equals(query.appIdNamespace)) {
      return false;
    }
    if (!Objects.equals(filter, query.filter)) {
      return false;
    }
    if (!filterPredicates.equals(query.filterPredicates)) {
      return false;
    }
    if (!Objects.equals(kind, query.kind)) {
      return false;
    }
    if (!sortPredicates.equals(query.sortPredicates)) {
      return false;
    }
    if (!projectionMap.equals(query.projectionMap)) {
      return false;
    }
    if (distinct != query.distinct) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        kind,
        sortPredicates,
        filterPredicates,
        filter,
        ancestor,
        keysOnly,
        appIdNamespace,
        projectionMap,
        distinct);
  }

  /**
   * Obtains a hash code of the {@code Query} ignoring any 'value' arguments associated with any
   * query filters.
   */
  int hashCodeNoFilterValues() {
    /* based on Arrays.hashCode() */
    int filterHashCode = 1;
    for (FilterPredicate filterPred : filterPredicates) {
      filterHashCode = 31 * filterHashCode + filterPred.hashCodeNoFilterValues();
    }
    filterHashCode = 31 * filterHashCode + ((filter == null) ? 0 : filter.hashCodeNoFilterValues());
    return Objects.hash(
        kind,
        sortPredicates,
        filterHashCode,
        ancestor,
        keysOnly,
        appIdNamespace,
        projectionMap,
        distinct);
  }

  /** Outputs a SQL like string representing the query. */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("SELECT ");
    if (distinct) {
      result.append("DISTINCT ");
    }
    if (!projectionMap.isEmpty()) {
      Joiner.on(", ").appendTo(result, projectionMap.values());
    } else if (keysOnly) {
      result.append("__key__");
    } else {
      result.append('*');
    }

    if (kind != null) {
      result.append(" FROM ");
      result.append(kind);
    }

    if (ancestor != null || !filterPredicates.isEmpty() || filter != null) {
      result.append(" WHERE ");
      final String AND_SEPARATOR = " AND ";

      if (filter != null) {
        result.append(filter);
      } else if (!filterPredicates.isEmpty()) {
        Joiner.on(AND_SEPARATOR).appendTo(result, filterPredicates);
      }

      if (ancestor != null) {
        if (!filterPredicates.isEmpty() || filter != null) {
          result.append(AND_SEPARATOR);
        }
        result.append("__ancestor__ is ");
        result.append(ancestor);
      }
    }

    if (!sortPredicates.isEmpty()) {
      result.append(" ORDER BY ");
      Joiner.on(", ").appendTo(result, sortPredicates);
    }
    return result.toString();
  }

  /** SortPredicate is a data container that holds a single sort predicate. */
  public static final class SortPredicate implements Serializable {
    @SuppressWarnings("hiding")
    static final long serialVersionUID = -623786024456258081L;

    private final String propertyName;
    private final SortDirection direction;

    public SortPredicate(String propertyName, SortDirection direction) {
      if (propertyName == null) {
        throw new NullPointerException("Property name was null");
      }

      if (direction == null) {
        throw new NullPointerException("Direction was null");
      }

      this.propertyName = propertyName;
      this.direction = direction;
    }

    /** Returns a sort predicate with the direction reversed. */
    public SortPredicate reverse() {
      return new SortPredicate(
          propertyName,
          direction == SortDirection.ASCENDING
              ? SortDirection.DESCENDING
              : SortDirection.ASCENDING);
    }

    /** Gets the name of the property to sort on. */
    public String getPropertyName() {
      return propertyName;
    }

    /** Gets the direction of the sort. */
    public SortDirection getDirection() {
      return direction;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      SortPredicate that = (SortPredicate) o;

      if (direction != that.direction) {
        return false;
      }
      if (!propertyName.equals(that.propertyName)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = propertyName.hashCode();
      result = 31 * result + direction.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return propertyName + (direction == SortDirection.DESCENDING ? " DESC" : "");
    }
  }

  /**
   * The base class for a query filter.
   *
   * <p>All sub classes should be immutable.
   */
  // TODO Add an AncestorFilter so (ancestor is A OR ancestor is B) is possible.
  public abstract static class Filter implements Serializable {
    @SuppressWarnings("hiding")
    static final long serialVersionUID = -845113806195204425L;

    Filter() {} // Package protect inheritance.

    /**
     * Obtains the hash code for the {@code Filter} ignoring any value parameters associated with
     * the filter.
     */
    abstract int hashCodeNoFilterValues();
  }

  /**
   * A {@link Filter} that combines several sub filters using a {@link CompositeFilterOperator}.
   *
   * <p>For example, to construct a filter of the form <code>a = 1 AND (b = 2 OR c = 3)</code> use:
   *
   * <pre>{@code
   * new CompositeFilter(CompositeFilterOperator.AND, Arrays.asList(
   *     new FilterPredicate("a", FilterOperator.EQUAL, 1),
   *     new CompositeFilter(CompositeFilterOperator.OR, Arrays.<Filter>asList(
   *         new FilterPredicate("b", FilterOperator.EQUAL, 2),
   *         new FilterPredicate("c", FilterOperator.EQUAL, 3)))));
   * }</pre>
   *
   * or
   *
   * <pre>{@code
   * CompositeFilterOperator.and(
   *     FilterOperator.EQUAL.of("a", 1),
   *     CompositeFilterOperator.or(
   *         FilterOperator.EQUAL.of("b", 2),
   *         FilterOperator.EQUAL.of("c", 3)));
   * }</pre>
   */
  public static final class CompositeFilter extends Filter {
    @SuppressWarnings("hiding")
    static final long serialVersionUID = 7930286402872420509L;

    private final CompositeFilterOperator operator;
    private final List<Filter> subFilters;

    public CompositeFilter(CompositeFilterOperator operator, Collection<Filter> subFilters) {
      Preconditions.checkArgument(subFilters.size() >= 2, "At least two sub filters are required.");
      this.operator = checkNotNull(operator);
      this.subFilters = ImmutableCopy.list(subFilters);
    }

    /** Returns the operator. */
    public CompositeFilterOperator getOperator() {
      return operator;
    }

    /** Returns an immutable list of sub filters. */
    public List<Filter> getSubFilters() {
      return subFilters;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append('(');
      Joiner.on(" " + operator + " ").appendTo(builder, subFilters);
      builder.append(')');
      return builder.toString();
    }

    @Override
    public int hashCode() {
      return Objects.hash(operator, subFilters);
    }

    @Override
    int hashCodeNoFilterValues() {
      /* based on Arrays.hashCode() */
      int result = 1;
      result = 31 * result + operator.hashCode();
      for (Filter filter : subFilters) {
        result = 31 * result + filter.hashCodeNoFilterValues();
      }
      return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof CompositeFilter)) {
        return false;
      }
      CompositeFilter other = (CompositeFilter) obj;
      if (operator != other.operator) {
        return false;
      }
      return subFilters.equals(other.subFilters);
    }
  }

  /** A {@link Filter} on a single property. */
  public static final class FilterPredicate extends Filter {
    @SuppressWarnings("hiding")
    static final long serialVersionUID = 7681475799401864259L;

    private final String propertyName;
    private final FilterOperator operator;
    private final Object value;

    /**
     * Constructs a filter predicate from the given parameters.
     *
     * @param propertyName the name of the property on which to filter
     * @param operator the operator to apply
     * @param value A single instances of a supported type or if {@code operator} is {@link
     *     FilterOperator#IN} a non-empty {@link Iterable} object containing instances of supported
     *     types.
     * @throws IllegalArgumentException If the provided filter values are not supported.
     * @see DataTypeUtils#isSupportedType(Class)
     */
    public FilterPredicate(String propertyName, FilterOperator operator, Object value) {
      // TODO It may be desirable to disallow Key type
      // filters on the primary key that are not in the same
      // appid/namespace.  It's not clear what the right answer is
      // as some comparison operators may be desirable with
      // keys on different namespaces. Comparison of Key values
      // that of non primary key property is likely required.
      if (propertyName == null) {
        throw new NullPointerException("Property name was null");
      } else if (operator == null) {
        throw new NullPointerException("Operator was null");
      } else if (operator == FilterOperator.IN) {
        if (!(value instanceof Collection<?>) && value instanceof Iterable<?>) {
          // This is a hack for backwards compatibility
          List<Object> newValue = new ArrayList<Object>();
          Iterables.addAll(newValue, (Iterable<?>) value);
          value = newValue;
        }
        DataTypeUtils.checkSupportedValue(propertyName, value, true, true, false);
      } else {
        DataTypeUtils.checkSupportedValue(propertyName, value, false, false, false);
      }
      this.propertyName = propertyName;
      this.operator = operator;
      this.value = value;
    }

    /** Gets the name of the property to be filtered on. */
    public String getPropertyName() {
      return propertyName;
    }

    /** Gets the operator describing how to apply the filter. */
    public FilterOperator getOperator() {
      return operator;
    }

    /** Gets the argument to the filter operator. */
    public Object getValue() {
      return value;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      FilterPredicate that = (FilterPredicate) o;

      if (operator != that.operator) {
        return false;
      }
      if (!propertyName.equals(that.propertyName)) {
        return false;
      }
      if (!Objects.equals(value, that.value)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return Objects.hash(propertyName, operator, value);
    }

    @Override
    int hashCodeNoFilterValues() {
      return Objects.hash(propertyName, operator);
    }

    @Override
    public String toString() {
      return propertyName + " " + operator + " " + (value != null ? value : "NULL");
    }
  }

  /** A {@link Filter} representing a geo-region containment predicate. */
  public static final class StContainsFilter extends Filter {
    private static final long serialVersionUID = 1L;
    private final String propertyName;
    private final GeoRegion region;

    /**
     * Constructs a geo-region filter from the given arguments.
     *
     * @param propertyName the name of the property on which to test containment
     * @param region a geo-region object against which to test the property value
     */
    public StContainsFilter(String propertyName, GeoRegion region) {
      this.propertyName = checkNotNull(propertyName);
      this.region = checkNotNull(region);
    }

    public String getPropertyName() {
      return propertyName;
    }

    public GeoRegion getRegion() {
      return region;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      StContainsFilter that = (StContainsFilter) o;

      if (!propertyName.equals(that.propertyName)) {
        return false;
      }
      if (!region.equals(that.region)) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      return Objects.hash(propertyName, region);
    }

    @Override
    int hashCodeNoFilterValues() {
      return propertyName.hashCode();
    }

    @Override
    public String toString() {
      return String.format("StContainsFilter [%s: %s]", propertyName, region);
    }
  }

  /**
   * A geographic region intended for use in a {@link StContainsFilter}. Note that this is the only
   * purpose for which it should be used: in particular, it is not suitable as a Property value to
   * be stored in Datastore.
   */
  public abstract static class GeoRegion implements Serializable {
    private static final long serialVersionUID = 1806920671840003815L;

    // Package-private constructor prevents users from writing their own subclasses.
    GeoRegion() {}

    /**
     * Determines whether the given {@link GeoPt} value lies within this geographic region. If the
     * point lies on the border of the region it is considered to be contained.
     */
    public abstract boolean contains(GeoPt point);

    /**
     * A geographical region representing all points within a fixed distance from a central point,
     * i.e., a circle. Intended for use in a geo-containment predicate filter.
     */
    public static final class Circle extends GeoRegion {
      private static final long serialVersionUID = 1L;

      private final GeoPt center;
      private final double radius;

      /**
       * Creates a new {@code Circle} object from the given arguments.
       *
       * @param center a {@code GeoPt} representing the center of the circle
       * @param radius the radius of the circle, expressed in meters
       */
      public Circle(GeoPt center, double radius) {
        this.center = checkNotNull(center);
        checkArgument(radius >= 0);
        this.radius = radius;
      }

      @Override
      public boolean contains(GeoPt point) {
        return GeoPt.distance(center, point) <= radius;
      }

      public GeoPt getCenter() {
        return center;
      }

      public double getRadius() {
        return radius;
      }

      @Override
      public boolean equals(@Nullable Object o) {
        if (this == o) {
          return true;
        }

        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        Circle other = (Circle) o;

        if (!center.equals(other.center)) {
          return false;
        }
        if (Double.compare(radius, other.radius) != 0) {
          return false;
        }

        return true;
      }

      @Override
      public int hashCode() {
        return Objects.hash(center, radius);
      }

      @Override
      public String toString() {
        return String.format("Circle [(%s),%f]", center, radius);
      }
    }

    /**
     * A simple geographical region bounded by two latitude lines, and two longitude lines, i.e., a
     * "rectangle". It's not really a rectangle, of course, because longitude lines are not really
     * parallel.
     *
     * <p>Intended for use in a geo-containment predicate filter.
     */
    public static final class Rectangle extends GeoRegion {
      private static final long serialVersionUID = 1L;
      private final GeoPt southwest;
      private final GeoPt northeast;

      public Rectangle(GeoPt southwest, GeoPt northeast) {
        checkNotNull(southwest);
        checkNotNull(northeast);
        checkArgument(southwest.getLatitude() <= northeast.getLatitude());
        this.southwest = southwest;
        this.northeast = northeast;
      }

      public GeoPt getSouthwest() {
        return southwest;
      }

      public GeoPt getNortheast() {
        return northeast;
      }

      @Override
      public boolean contains(GeoPt point) {
        if (point.getLatitude() > northeast.getLatitude()
            || point.getLatitude() < southwest.getLatitude()) {
          return false;
        }

        // A box that crosses the +/- 180 degree longitude works backwards.
        if (southwest.getLongitude() > northeast.getLongitude()) {
          return point.getLongitude() <= northeast.getLongitude()
              || point.getLongitude() >= southwest.getLongitude();
        }
        return southwest.getLongitude() <= point.getLongitude()
            && point.getLongitude() <= northeast.getLongitude();
      }

      @Override
      public boolean equals(@Nullable Object o) {
        if (this == o) {
          return true;
        }

        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        Rectangle other = (Rectangle) o;

        if (!southwest.equals(other.southwest)) {
          return false;
        }
        if (!northeast.equals(other.northeast)) {
          return false;
        }

        return true;
      }

      @Override
      public int hashCode() {
        return Objects.hash(southwest, northeast);
      }

      @Override
      public String toString() {
        return String.format("Rectangle [(%s),(%s)]", southwest, northeast);
      }
    }
  }
}
