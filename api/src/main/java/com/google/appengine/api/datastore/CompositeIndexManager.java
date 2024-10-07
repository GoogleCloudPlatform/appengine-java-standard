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

import static java.util.Objects.requireNonNull;

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property.Direction;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property.Mode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

// CAUTION: this is one of several files that implement parsing and
// validation of the index definition schema; they all must be kept in
// sync.  Please refer to java/com/google/appengine/tools/development/datastore-indexes.xsd
// for the list of these files.

/** Composite index management operations needed by the datastore api. */
// This class is public because the dev appserver needs access to it.
public class CompositeIndexManager {

  // Apologies for the lowercase literals, but the whole point of these enums
  // is to represent constants in an xml document, and it's silly to have
  // the code literals not match the xml literals - you end up with a bunch
  // of case conversion just support the java naming conversion.

  /**
   * The source of an index in the index file. These are used as literals in an xml document that we
   * read and write.
   */
  protected enum IndexSource {
    auto,
    manual
  }

  /**
   * Generate an xml representation of the provided {@link Index}.
   *
   * <p><datastore-indexes autoGenerate="true"> <datastore-index kind="a" ancestor="false">
   * <property name="yam" direction="asc"/> <property name="not yam" direction="desc"/>
   * </datastore-index> </datastore-indexes>
   *
   * @param index The index for which we want an xml representation.
   * @param source The source of the provided index.
   * @return The xml representation of the provided index.
   */
  protected String generateXmlForIndex(Index index, IndexSource source) {
    return CompositeIndexUtils.generateXmlForIndex(index, source);
  }

  /**
   * Given a {@link IndexComponentsOnlyQuery}, return the {@link Index} needed to fulfill the query,
   * or {@code null} if no index is needed.
   *
   * <p>This code needs to remain in sync with its counterparts in other languages. If you modify
   * this code please make sure you make the same update in the local datastore for other languages.
   *
   * @param indexOnlyQuery The query.
   * @return The index that must be present in order to fulfill the query, or {@code null} if no
   *     index is needed.
   */
  protected @Nullable Index compositeIndexForQuery(final IndexComponentsOnlyQuery indexOnlyQuery) {
    DatastoreV3Pb.Query query = indexOnlyQuery.getQuery();

    boolean hasKind = query.hasKind();
    boolean isAncestor = query.hasAncestor();
    List<Filter> filters = query.getFilterList();
    List<Order> orders = query.getOrderList();

    if (filters.isEmpty() && orders.isEmpty()) {
      // If there are no filters or sorts no composite index is needed; the
      // built-in primary key or kind index can handle this.
      return null;
    }

    // Group the filters by operator.
    List<String> eqProps = indexOnlyQuery.getPrefix();
    List<Property> indexProperties =
        indexOnlyQuery.isGeo()
            ? getNeededSearchProps(eqProps, indexOnlyQuery.getGeoProperties())
            : getRecommendedIndexProps(indexOnlyQuery);

    if (hasKind
        && !eqProps.isEmpty()
        && eqProps.size() == filters.size()
        && !indexOnlyQuery.hasKeyProperty()
        && orders.isEmpty()) {
      // No sort orders, all filters (if any) are equals filters, and none of
      // the filters are on the key - the
      // datastore can merge join this, with or without an ancestor. No index
      // needed.  Non-empty equality filters is a critical part of this check
      // because without it we would capture queries with only kind and ancestor
      // specified, and those queries can _not_ be satisfied by merge-join.
      return null;
    }

    if (hasKind
        && !isAncestor
        && indexProperties.size() <= 1
        && !indexOnlyQuery.isGeo()
        && (!indexOnlyQuery.hasKeyProperty()
            || indexProperties.get(0).getDirection() == Property.Direction.ASCENDING)) {
      // For traditional indexes, we never need kind-only or
      // single-property composite indexes unless it's a single
      // property, descending index on key. The built-in primary key
      // and single property indexes are good enough.  (But for geo
      // (a.k.a. Search) indexes, we might.)
      return null;
    }

    Index.Builder index = Index.newBuilder();
    index.setEntityType(query.getKind());
    index.setAncestor(isAncestor);
    index.addAllProperty(indexProperties);
    return index.build();
  }

  /** We compare {@link Property Properties} by comparing their names. */
  private static final Comparator<Property> PROPERTY_NAME_COMPARATOR =
      new Comparator<Property>() {
        @Override
        public int compare(Property o1, Property o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };

  private List<Property> getRecommendedIndexProps(IndexComponentsOnlyQuery query) {
    // Construct the list of index properties
    List<Property> indexProps = new ArrayList<Property>();

    // This converts our Set<String> prefix to be a list of {@link Property}s with ascending
    // direction. The list will be ordered lexicographically.
    indexProps.addAll(
        new UnorderedIndexComponent(Sets.newHashSet(query.getPrefix())).preferredIndexProperties());

    for (IndexComponent component : query.getPostfix()) {
      indexProps.addAll(component.preferredIndexProperties());
    }

    return indexProps;
  }

  /**
   * Function which can transform a property name into a Property pb object, optionally including a
   * Mode setting, suitable for use in defining a Search index in normalized order.
   */
  static class SearchPropertyTransform implements Function<String, Property> {
    private final @Nullable Mode mode;

    SearchPropertyTransform(@Nullable Mode mode) {
      this.mode = mode;
    }

    @Override
    public Property apply(String name) {
      Property.Builder p = Property.newBuilder();
      p.setName(name);
      if (mode != null) {
        p.setMode(mode);
      }
      return p.build();
    }
  }

  private static final SearchPropertyTransform TO_MODELESS_PROPERTY =
      new SearchPropertyTransform(null);
  private static final SearchPropertyTransform TO_GEOSPATIAL_PROPERTY =
      new SearchPropertyTransform(Mode.GEOSPATIAL);

  /**
   * Produces the list of Property objects needed for a Search index, properly normalized: all
   * pre-intersection (i.e., modeless) properties come first, followed by all geo-spatial
   * properties. Within type the properties appear in lexicographical order by name.
   */
  private List<Property> getNeededSearchProps(List<String> eqProps, List<String> searchProps) {
    List<Property> result = new ArrayList<>();
    result.addAll(
        FluentIterable.from(eqProps)
            .transform(TO_MODELESS_PROPERTY)
            .toSortedList(PROPERTY_NAME_COMPARATOR));
    result.addAll(
        FluentIterable.from(searchProps)
            .transform(TO_GEOSPATIAL_PROPERTY)
            .toSortedList(PROPERTY_NAME_COMPARATOR));
    return result;
  }

  /**
   * Given a {@link IndexComponentsOnlyQuery} and a collection of existing {@link Index}s, return
   * the minimum {@link Index} needed to fulfill the query, or {@code null} if no index is needed.
   *
   * <p>This code needs to remain in sync with its counterparts in other languages. If you modify
   * this code please make sure you make the same update in the local datastore for other languages.
   *
   * @param indexOnlyQuery The query.
   * @param indexes The existing indexes.
   * @return The minimum index that must be present in order to fulfill the query, or {@code null}
   *     if no index is needed.
   */
  protected @Nullable Index minimumCompositeIndexForQuery(
      IndexComponentsOnlyQuery indexOnlyQuery, Collection<Index> indexes) {

    Index suggestedIndex = compositeIndexForQuery(indexOnlyQuery);
    if (suggestedIndex == null) {
      return null;
    }
    if (indexOnlyQuery.isGeo()) {
      // None of the shortcuts/optimizations below are applicable for Search indexes.
      return suggestedIndex;
    }

    class EqPropsAndAncestorConstraint {
      final Set<String> equalityProperties;
      final boolean ancestorConstraint;

      EqPropsAndAncestorConstraint(Set<String> equalityProperties, boolean ancestorConstraint) {
        this.equalityProperties = equalityProperties;
        this.ancestorConstraint = ancestorConstraint;
      }
    }

    // Map from postfix to the remaining equality properties and ancestor constraints.
    Map<List<Property>, EqPropsAndAncestorConstraint> remainingMap =
        new HashMap<List<Property>, EqPropsAndAncestorConstraint>();

    index_for:
    for (Index index : indexes) {
      if ( // Kind must match.
      !indexOnlyQuery.getQuery().getKind().equals(index.getEntityType())
          ||
          // Ancestor indexes can only be used on ancestor queries.
          (!indexOnlyQuery.getQuery().hasAncestor() && index.getAncestor())) {
        continue;
      }

      // Matching the postfix.
      int postfixSplit = index.getPropertyCount();
      for (IndexComponent component : Lists.reverse(indexOnlyQuery.getPostfix())) {
        if (!component.matches(
            index
                .getPropertyList()
                .subList(Math.max(postfixSplit - component.size(), 0), postfixSplit))) {
          continue index_for;
        }
        postfixSplit -= component.size();
      }

      // Postfix matches! Now checking the prefix.
      Set<String> indexEqProps = Sets.newHashSetWithExpectedSize(postfixSplit);
      for (Property prop : index.getPropertyList().subList(0, postfixSplit)) {
        // Index must not contain extra properties in the prefix.
        if (!indexOnlyQuery.getPrefix().contains(prop.getName())) {
          continue index_for;
        }
        indexEqProps.add(prop.getName());
      }

      // Index matches!

      // Find the matching remaining requirements.
      List<Property> indexPostfix = index.getPropertyList().subList(postfixSplit, index.getPropertyCount());

      Set<String> remainingEqProps;
      boolean remainingAncestor;
      {
        EqPropsAndAncestorConstraint remaining = remainingMap.get(indexPostfix);
        if (remaining == null) {
          remainingEqProps = Sets.newHashSet(indexOnlyQuery.getPrefix());
          remainingAncestor = indexOnlyQuery.getQuery().hasAncestor();
        } else {
          remainingEqProps = remaining.equalityProperties;
          remainingAncestor = remaining.ancestorConstraint;
        }
      }

      // Remove any remaining requirements handled by this index.
      boolean modified = remainingEqProps.removeAll(indexEqProps);
      if (remainingAncestor && index.getAncestor()) {
        modified = true;
        remainingAncestor = false;
      }

      if (remainingEqProps.isEmpty() && !remainingAncestor) {
        return null; // No new index needed!
      }

      if (!modified) {
        // Index made no contribution, don't update the map.
        continue;
      }

      // Save indexes contribution
      remainingMap.put(
          indexPostfix, new EqPropsAndAncestorConstraint(remainingEqProps, remainingAncestor));
    }

    if (remainingMap.isEmpty()) {
      return suggestedIndex; // suggested index is the minimum index
    }

    int minimumCost = Integer.MAX_VALUE;
    List<Property> minimumPostfix = null;
    EqPropsAndAncestorConstraint minimumRemaining = null;
    for (Map.Entry<List<Property>, EqPropsAndAncestorConstraint> entry : remainingMap.entrySet()) {
      int cost = entry.getValue().equalityProperties.size();
      if (entry.getValue().ancestorConstraint) {
        cost += 2; // Arbitrary value picked because ancestor are multi-valued.
      }
      if (cost < minimumCost) {
        minimumCost = cost;
        minimumPostfix = entry.getKey();
        minimumRemaining = entry.getValue();
      }
    }
    requireNonNull(minimumRemaining); // map not empty so we should have found cost < MAX_VALUE.
    requireNonNull(minimumPostfix);

    // Populating suggesting the minimal index instead.
    suggestedIndex.clearProperty();
    suggestedIndex.setAncestor(minimumRemaining.ancestorConstraint);
    for (String name : minimumRemaining.equalityProperties) {
      suggestedIndex.addProperty().setName(name).setDirection(Direction.ASCENDING);
    }
    Collections.sort(suggestedIndex.mutablePropertys(), PROPERTY_NAME_COMPARATOR);

    suggestedIndex.mutablePropertys().addAll(minimumPostfix);
    return suggestedIndex;
  }

  /**
   * Protected alias that allows us to make this class available to the local datastore without
   * publicly exposing it in the api.
   */
  protected static class IndexComponentsOnlyQuery
      extends com.google.appengine.api.datastore.IndexComponentsOnlyQuery {
    public IndexComponentsOnlyQuery(DatastoreV3Pb.Query query) {
      super(query);
    }
  }

  /**
   * Protected alias that allows us to make this class available to the local datastore without
   * publicly exposing it in the api.
   */
  protected static class ValidatedQuery extends com.google.appengine.api.datastore.ValidatedQuery {
    public ValidatedQuery(DatastoreV3Pb.Query query) {
      super(query);
    }
  }

  /**
   * Protected alias that allows us to make this class available to the local datastore without
   * publicly exposing it in the api.
   */
  protected static class KeyTranslator extends com.google.appengine.api.datastore.KeyTranslator {
    protected KeyTranslator() {}
  }
}
