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

import static com.google.appengine.api.datastore.NamespaceUtils.NAMESPACE;
import static com.google.appengine.api.datastore.NamespaceUtils.getAppId;
import static com.google.appengine.api.datastore.NamespaceUtils.getAppIdWithNamespace;
import static com.google.appengine.api.datastore.NamespaceUtils.setNonEmptyDefaultApiNamespace;
import static com.google.appengine.api.datastore.Query.CompositeFilterOperator.and;
import static com.google.appengine.api.datastore.Query.CompositeFilterOperator.or;
import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;
import static com.google.appengine.api.datastore.Query.FilterOperator.GREATER_THAN;
import static com.google.appengine.api.datastore.Query.FilterOperator.LESS_THAN;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the DatastoreService Query class.
 *
 */
@RunWith(JUnit4.class)
public class QueryTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testCompositeFilterDocExcamples() {
    Filter f1 =
        new CompositeFilter(
            CompositeFilterOperator.AND,
            Arrays.asList(
                new FilterPredicate("a", FilterOperator.EQUAL, 1),
                new CompositeFilter(
                    CompositeFilterOperator.OR,
                    Arrays.<Filter>asList(
                        new FilterPredicate("b", FilterOperator.EQUAL, 2),
                        new FilterPredicate("c", FilterOperator.EQUAL, 3)))));

    Filter f2 =
        CompositeFilterOperator.and(
            FilterOperator.EQUAL.of("a", 1),
            CompositeFilterOperator.or(
                FilterOperator.EQUAL.of("b", 2), FilterOperator.EQUAL.of("c", 3)));

    assertThat(f2).isEqualTo(f1);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testHashCodeNoFilterValues() {
    Filter f1V1 =
        CompositeFilterOperator.and(
            FilterOperator.EQUAL.of("a", 1),
            CompositeFilterOperator.or(
                FilterOperator.EQUAL.of("b", 2), FilterOperator.EQUAL.of("c", 3)));
    Filter f1V2 =
        CompositeFilterOperator.and(
            FilterOperator.EQUAL.of("a", 4),
            CompositeFilterOperator.or(
                FilterOperator.EQUAL.of("b", 5), FilterOperator.EQUAL.of("c", 6)));

    assertThat(f1V2.hashCodeNoFilterValues()).isEqualTo(f1V1.hashCodeNoFilterValues());
    assertThat(f1V2.hashCode()).isNotEqualTo(f1V1.hashCode());

    Query q1 = new Query("kind");
    q1.setFilter(f1V1);
    Query q2 = new Query("kind");
    q2.setFilter(f1V2);

    assertThat(q2.hashCodeNoFilterValues()).isEqualTo(q1.hashCodeNoFilterValues());
    assertThat(q2.hashCode()).isNotEqualTo(q1.hashCode());

    Filter f2V1 =
        CompositeFilterOperator.and(
            FilterOperator.EQUAL.of("d", 4),
            CompositeFilterOperator.or(
                FilterOperator.EQUAL.of("bstr", "y"), FilterOperator.EQUAL.of("c", 6)));
    Filter f2V2 =
        CompositeFilterOperator.and(
            FilterOperator.EQUAL.of("d", 24),
            CompositeFilterOperator.or(
                FilterOperator.EQUAL.of("bstr", "x"), FilterOperator.EQUAL.of("c", 26)));

    assertThat(f2V1.hashCodeNoFilterValues()).isNotEqualTo(f1V1.hashCodeNoFilterValues());
    assertThat(f2V1.hashCodeNoFilterValues()).isNotEqualTo(f1V2.hashCodeNoFilterValues());
    q1.setFilter(f1V1);
    q2.setFilter(f2V1);
    assertThat(q2.hashCodeNoFilterValues()).isNotEqualTo(q1.hashCodeNoFilterValues());

    assertThat(f2V2.hashCodeNoFilterValues()).isEqualTo(f2V1.hashCodeNoFilterValues());
    assertThat(f2V2.hashCode()).isNotEqualTo(f2V1.hashCode());

    /* verify old style for backwards compatibility */
    Query q3 = new Query("kind");
    q3.addFilter("aString", Query.FilterOperator.EQUAL, "testing");
    q3.addFilter("anInteger", Query.FilterOperator.LESS_THAN, 42);
    q3.addFilter("aLong", Query.FilterOperator.EQUAL, 10000000000L);
    q3.addFilter("aDouble", Query.FilterOperator.GREATER_THAN_OR_EQUAL, 42.24);
    q3.addFilter("aBoolean", Query.FilterOperator.EQUAL, true);
    q3.addFilter("aNull", Query.FilterOperator.EQUAL, null);
    Query q4 = new Query("kind");
    q4.addFilter("aString", Query.FilterOperator.EQUAL, "testing2");
    q4.addFilter("anInteger", Query.FilterOperator.LESS_THAN, 45);
    q4.addFilter("aLong", Query.FilterOperator.EQUAL, 1000034530L);
    q4.addFilter("aDouble", Query.FilterOperator.GREATER_THAN_OR_EQUAL, 42.78);
    q4.addFilter("aBoolean", Query.FilterOperator.EQUAL, false);
    q4.addFilter("aNull", Query.FilterOperator.EQUAL, "not_null");
    assertThat(q4.hashCodeNoFilterValues()).isEqualTo(q3.hashCodeNoFilterValues());
    assertThat(q4.hashCode()).isNotEqualTo(q3.hashCode());

    Query q5 = new Query("kind");
    q5.setDistinct(false);
    Query q6 = new Query("kind");
    q6.setDistinct(true);
    assertThat(q6.hashCode()).isNotEqualTo(q5.hashCode());
    assertThat(q6.hashCodeNoFilterValues()).isNotEqualTo(q5.hashCodeNoFilterValues());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testFilterKnownType() {
    Query query = new Query("kind");
    query.addFilter("aString", Query.FilterOperator.EQUAL, "testing");
    query.addFilter("anInteger", Query.FilterOperator.LESS_THAN, 42);
    query.addFilter("aLong", Query.FilterOperator.EQUAL, 10000000000L);
    query.addFilter("aDouble", Query.FilterOperator.GREATER_THAN_OR_EQUAL, 42.24);
    query.addFilter("aBoolean", Query.FilterOperator.EQUAL, true);
    query.addFilter("aNull", Query.FilterOperator.EQUAL, null);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testFilterUnknownType() {
    Query query = new Query("kind");
    assertThrows(
        IllegalArgumentException.class,
        () -> query.addFilter("foo", Query.FilterOperator.EQUAL, new StringWriter()));
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testReverse() {
    Query query = new Query("kind");
    Query manualReverse = new Query("kind");

    query.addFilter("prop0", FilterOperator.EQUAL, 5);
    manualReverse.addFilter("prop0", FilterOperator.EQUAL, 5);
    query.setAncestor(new Key("yam", "name"));
    manualReverse.setAncestor(new Key("yam", "name"));

    assertThrows(IllegalStateException.class, query::reverse);

    query.addSort("prop1");
    manualReverse.addSort("prop1", SortDirection.DESCENDING);
    assertThrows(IllegalStateException.class, query::reverse);

    query.addSort("prop2", SortDirection.DESCENDING);
    manualReverse.addSort("prop2");
    assertThrows(IllegalStateException.class, query::reverse);

    query.addSort(Entity.KEY_RESERVED_PROPERTY, SortDirection.DESCENDING);
    manualReverse.addSort(Entity.KEY_RESERVED_PROPERTY);

    Query reversed = query.reverse();

    assertThat(reversed).isEqualTo(manualReverse);
    assertThat(manualReverse.reverse()).isEqualTo(query);

    assertThat(reversed).isNotEqualTo(query);
    assertThat(reversed.getSortPredicates()).isNotEqualTo(query.getSortPredicates());

    assertThat(reversed.getKind()).isEqualTo(query.getKind());
    assertThat(reversed.isKeysOnly()).isEqualTo(query.isKeysOnly());

    // Key is same because it is immutable (at least publicly)
    assertThat(reversed.getAncestor()).isSameInstanceAs(query.getAncestor());

    // AppIdNamespace is immutable
    assertThat(reversed.getAppIdNamespace()).isSameInstanceAs(query.getAppIdNamespace());

    // Filter predicates are mutable, so make sure it is copied.
    assertThat(reversed.getFilterPredicates()).isEqualTo(query.getFilterPredicates());
    assertThat(reversed.getFilterPredicates()).isNotSameInstanceAs(query.getFilterPredicates());

    Query doubleReversed = reversed.reverse();
    assertThat(doubleReversed).isEqualTo(query);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testSortFilterRetrieval() {
    Query query = new Query("kind");
    query.addFilter("name", Query.FilterOperator.EQUAL, "testing");
    query.addFilter("name2", Query.FilterOperator.EQUAL, "testing2");
    assertThat(query.getFilterPredicates()).hasSize(2);
    Query.FilterPredicate firstFilter = query.getFilterPredicates().get(0);
    assertThat(firstFilter.getPropertyName()).isEqualTo("name");
    assertThat(firstFilter.getOperator()).isEqualTo(Query.FilterOperator.EQUAL);
    assertThat(firstFilter.getValue()).isEqualTo("testing");

    query.addSort("name", Query.SortDirection.ASCENDING);
    Query.SortPredicate sort = query.getSortPredicates().get(0);
    assertThat(sort.getPropertyName()).isEqualTo("name");
    assertThat(sort.getDirection()).isEqualTo(Query.SortDirection.ASCENDING);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testSerialization() throws Exception {
    Query query = new Query("foo");
    query.setDistinct(true);
    query.addFilter("aString", Query.FilterOperator.EQUAL, "testing");
    query.addFilter("anInteger", Query.FilterOperator.LESS_THAN, 42);
    query.addFilter("aLong", Query.FilterOperator.EQUAL, 10000000000L);
    query.addFilter("aDouble", Query.FilterOperator.GREATER_THAN_OR_EQUAL, 42.24);
    query.addFilter("aBoolean", Query.FilterOperator.EQUAL, true);
    query.addFilter("aNull", Query.FilterOperator.EQUAL, null);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(query);

    byte[] bytes = baos.toByteArray();

    ObjectInputStream iis = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Query readQuery = (Query) iis.readObject();

    assertThat(query).isNotSameInstanceAs(readQuery);
    // Query uses identity-based equality.  Don't bother checking
    // content-based equality.
  }

  @Test
  public void testSetAncestor() {
    // try setting an incomplete ancestor
    Query q1 = new Query("yam");
    Key incomplete = new Key("yam");
    assertThrows(IllegalArgumentException.class, () -> q1.setAncestor(incomplete));

    Query q2 = new Query("yam");
    assertThrows(IllegalArgumentException.class, () -> q2.setAncestor(null));

    Query q3 = new Query("yam");
    Key complete = new Key("yam", "name");
    q3.setAncestor(complete); // set a fresh value
    q3.setAncestor(null); // clear the value
    assertThat(q3.getAncestor()).isNull();

    Query q4 = new Query(complete);
    // can clear the ancestor when we have no kind
    q4.setAncestor(null);
  }

  @Test
  public void testQueryCtor() {
    // Null is acceptable in all cases
    Query unused1 = new Query((String) null);
    Query unused2 = new Query((Key) null);
    Query unused3 = new Query(null, null);

    Key incomplete = new Key("yam");
    assertThrows(IllegalArgumentException.class, () -> new Query(incomplete));
  }

  @Test
  public void testINFilterPredicate() {
    // testing that IN works both with arrays and lists
    Query.FilterPredicate unused =
        new Query.FilterPredicate(
            "p1", Query.FilterOperator.IN, Arrays.<Object>asList(null, 3.14, 41, "good"));

    // Testing that IN on a single value is valid
    assertThat(
            new Query.FilterPredicate("p1", Query.FilterOperator.IN, Arrays.<Object>asList(41))
                .getOperator())
        .isEqualTo(Query.FilterOperator.IN);

    // Testing bad values
    assertThrows(
        IllegalArgumentException.class,
        () -> new Query.FilterPredicate("p1", Query.FilterOperator.IN, null));

    assertThrows(
        IllegalArgumentException.class,
        () -> new Query.FilterPredicate("p1", Query.FilterOperator.IN, 5));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Query.FilterPredicate(
                "p1", Query.FilterOperator.IN, new Object[] {null, new Query(), "good"}));
  }

  @Test
  public void testFilterPredicateEquality() {
    Query.FilterPredicate pred1 = new Query.FilterPredicate("p1", Query.FilterOperator.EQUAL, 21);
    Query.FilterPredicate pred2 = new Query.FilterPredicate("p1", Query.FilterOperator.EQUAL, null);
    Query.FilterPredicate pred3 = new Query.FilterPredicate("p1", Query.FilterOperator.EQUAL, 21);
    assertThat(pred1.equals(pred2)).isFalse();
    assertThat(pred2.equals(pred1)).isFalse();
    assertThat(pred3).isEqualTo(pred1);
  }

  @Test
  public void testFilterPredicateHashCodeOkWithNull() {
    Query.FilterPredicate pred1 = new Query.FilterPredicate("p1", Query.FilterOperator.EQUAL, null);
    // the test is that we don't throw npe
    int unused = pred1.hashCode();
  }

  @Test
  public void testNamespaceDefault() {
    Query query = new Query("foo");
    assertThat(getAppId()).isEqualTo(query.getAppIdNamespace().toEncodedString());
  }

  @Test
  public void testGetAppId() {
    Query query = new Query("foo");
    assertThat(getAppId()).isEqualTo(query.getAppId());
  }

  @Test
  public void testGetNamespace() {
    setNonEmptyDefaultApiNamespace();
    Query query = new Query("foo");
    assertThat(query.getNamespace()).isEqualTo(NAMESPACE);
  }

  @Test
  public void testNamespaceNonDefault() {
    setNonEmptyDefaultApiNamespace();
    Query query = new Query("foo");
    assertThat(getAppIdWithNamespace()).isEqualTo(query.getAppIdNamespace().toEncodedString());
  }

  @Test
  public void testComparisonWithNamespace() {
    Query queryA = new Query("foo");
    setNonEmptyDefaultApiNamespace();
    Query queryB = new Query("foo");
    assertThat(queryA.equals(queryB)).isFalse(); // Should be different query.
    assertThat(queryA.hashCode()).isNotEqualTo(queryB.hashCode()); // Should be different hash.
  }

  @Test
  public void testValidateAncestorNamespace() {
    Query query = new Query("foo");
    Key key1 = new Key("foo", null, 2);
    query.setAncestor(key1);
    setNonEmptyDefaultApiNamespace();
    Key key2 = new Key("foo", null, 2);
    assertThrows(IllegalArgumentException.class, () -> query.setAncestor(key2));
  }

  @Test
  public void testPropertyProjection() {
    Query query = new Query("foo");
    assertThat(query.getProjections()).isEmpty();
    assertThat(query.addProjection(new PropertyProjection("prop", null))).isSameInstanceAs(query);
    assertThat(query.getProjections()).contains(new PropertyProjection("prop", null));

    // Test duplicate.
    assertThrows(
        IllegalArgumentException.class,
        () -> query.addProjection(new PropertyProjection("prop", String.class)));
    assertThat(query.getProjections()).contains(new PropertyProjection("prop", null));

    // Test mutable.
    assertThat(query.getProjections().remove(new PropertyProjection("prop", String.class)))
        .isFalse();
    assertThat(query.getProjections()).contains(new PropertyProjection("prop", null));
    assertThat(query.getProjections().remove(new PropertyProjection("prop", null))).isTrue();
    assertThat(query.getProjections()).isEmpty();
  }

  @Test
  public void testSelectDistinct() {
    Query query = new Query("foo");
    assertThat(query.getDistinct()).isFalse();
    query.setDistinct(true);
    assertThat(query.getDistinct()).isTrue();
  }

  @Test
  public void testCompositeFilterConstructor() {
    assertThrows(
        NullPointerException.class,
        () -> new CompositeFilter(null, Arrays.<Filter>asList(EQUAL.of("a", 1), EQUAL.of("a", 1))));

    assertThrows(
        NullPointerException.class, () -> new CompositeFilter(CompositeFilterOperator.AND, null));

    assertThrows(IllegalArgumentException.class, () -> and(Arrays.<Filter>asList()));

    assertThrows(
        IllegalArgumentException.class, () -> and(Arrays.<Filter>asList(EQUAL.of("a", 1))));

    // Testing does not aggressively optimize
    CompositeFilter filter = and(EQUAL.of("a", 1), and(EQUAL.of("a", 1), EQUAL.of("a", 1)));
    assertThat(filter.getSubFilters()).hasSize(2);
    assertThat(filter.getSubFilters())
        .containsExactly(EQUAL.of("a", 1), and(EQUAL.of("a", 1), EQUAL.of("a", 1)))
        .inOrder();
    assertThat(((CompositeFilter) filter.getSubFilters().get(1)).getSubFilters())
        .containsExactly(EQUAL.of("a", 1), EQUAL.of("a", 1))
        .inOrder();
  }

  @Test
  public void testCompositeFilterImmutable() {
    List<Filter> subFilters = Lists.<Filter>newArrayList(EQUAL.of("a", 1), EQUAL.of("b", 2));
    CompositeFilter filter = and(subFilters);
    List<Filter> retrievedSubFilters = filter.getSubFilters();
    assertThat(retrievedSubFilters).isEqualTo(subFilters);
    assertThat(retrievedSubFilters).isNotSameInstanceAs(subFilters);
    assertThrows(
        UnsupportedOperationException.class, () -> retrievedSubFilters.set(0, EQUAL.of("c", 3)));
  }

  @Test
  public void testEquals() {
    assertThat(new Query()).isEqualTo(new Query());
    assertThat(new Query("kind")).isNotEqualTo(new Query());
    assertThat(new Query("kind").setKeysOnly()).isEqualTo(new Query("kind").setKeysOnly());
    assertThat(new Query("kind")).isNotEqualTo(new Query("kind").setKeysOnly());
    assertThat(new Query().setFilter(new FilterPredicate("p", FilterOperator.EQUAL, "John")))
        .isEqualTo(new Query().setFilter(new FilterPredicate("p", FilterOperator.EQUAL, "John")));
    assertThat(new Query())
        .isNotEqualTo(
            new Query().setFilter(new FilterPredicate("p", FilterOperator.EQUAL, "John")));
    assertThat(new Query().addSort("p")).isEqualTo(new Query().addSort("p"));
    assertThat(new Query().addSort("p", SortDirection.DESCENDING))
        .isNotEqualTo(new Query().addSort("p"));
    assertThat(new Query().addSort("p").addSort("x"))
        .isEqualTo(new Query().addSort("p").addSort("x"));
    assertThat(new Query().addSort("x").addSort("p"))
        .isNotEqualTo(new Query().addSort("p").addSort("x"));
    assertThat(new Query()).isNotEqualTo(new Query().addSort("p"));
    assertThat(new Query().setAncestor(new Key("kind2", "root")))
        .isEqualTo(new Query().setAncestor(new Key("kind2", "root")));
    assertThat(new Query().setAncestor(new Key("kind2", "root2")))
        .isNotEqualTo(new Query().setAncestor(new Key("kind2", "root1")));
    assertThat(
            new Query()
                .addProjection(new PropertyProjection("age", Long.class))
                .addProjection(new PropertyProjection("name", String.class)))
        .isEqualTo(
            new Query()
                .addProjection(new PropertyProjection("name", String.class))
                .addProjection(new PropertyProjection("age", Long.class)));
    assertThat(new Query().addProjection(new PropertyProjection("y", Long.class)))
        .isNotEqualTo(new Query().addProjection(new PropertyProjection("x", Long.class)));
    assertThat(new Query().setDistinct(true)).isEqualTo(new Query().setDistinct(true));
    assertThat(new Query()).isNotEqualTo(new Query().setDistinct(true));
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testToString() {
    assertThat(new Query().toString()).isEqualTo("SELECT *");

    Query query = new Query("kind").setKeysOnly();
    assertThat(query.toString()).isEqualTo("SELECT __key__ FROM kind");

    query.addFilter("age", FilterOperator.EQUAL, 34);
    assertThat(query.toString()).isEqualTo("SELECT __key__ FROM kind WHERE age = 34");

    query.addFilter("name", FilterOperator.EQUAL, "John");
    assertThat(query.toString())
        .isEqualTo("SELECT __key__ FROM kind WHERE age = 34 AND name = John");

    query.addSort("age", SortDirection.DESCENDING);
    assertThat(query.toString())
        .isEqualTo("SELECT __key__ FROM kind WHERE age = 34 AND name = John ORDER BY age DESC");

    query.addSort("name");
    assertThat(query.toString())
        .isEqualTo(
            "SELECT __key__ FROM kind WHERE age = 34 AND name = John ORDER BY age DESC, name");

    query.setAncestor(new Key("kind2", "root"));
    assertThat(query.toString())
        .isEqualTo(
            "SELECT __key__ FROM kind WHERE age = 34 AND name = John AND "
                + "__ancestor__ is kind2(\"root\") ORDER BY age DESC, name");

    query.clearKeysOnly();
    query.addProjection(new PropertyProjection("age", Long.class));
    query.addProjection(new PropertyProjection("name", String.class));
    query.setDistinct(true);
    query.getFilterPredicates().clear();
    query.setFilter(
        and(EQUAL.of("name", "John"), or(GREATER_THAN.of("age", 34), LESS_THAN.of("age", 10))));
    assertThat(query.toString())
        .isEqualTo(
            "SELECT DISTINCT age, name FROM kind WHERE (name = John AND (age > 34 OR age < 10)) AND"
                + " __ancestor__ is kind2(\"root\") ORDER BY age DESC, name");
  }
}
