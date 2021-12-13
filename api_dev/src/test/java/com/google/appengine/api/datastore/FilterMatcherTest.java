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

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.datastore.DatastoreV3Pb.GeoRegion;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.DatastoreV3Pb.RectangleRegion;
import com.google.apphosting.datastore.DatastoreV3Pb.RegionPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FilterMatcherTest {
  FilterMatcher filterMatcher = new FilterMatcher();

  @Test
  public void testRangeMatchMax() {
    filterMatcher.addFilter(filter(Operator.LESS_THAN_OR_EQUAL, 0));
    assertMatch((Object) null);
    assertNoMatch("bob");
    assertMatch(0);
    filterMatcher.addFilter(filter(Operator.LESS_THAN, 0));
    assertNoMatch(0);
    filterMatcher.addFilter(filter(Operator.LESS_THAN_OR_EQUAL, 0));
    assertNoMatch(0);
  }

  @Test
  public void testRangeMatchMin() {
    filterMatcher.addFilter(filter(Operator.GREATER_THAN_OR_EQUAL, 0));
    assertNoMatch((Object) null);
    assertMatch("bob");
    assertMatch(0);
    filterMatcher.addFilter(filter(Operator.GREATER_THAN, 0));
    assertNoMatch(0);
    filterMatcher.addFilter(filter(Operator.GREATER_THAN_OR_EQUAL, 0));
    assertNoMatch(0);
  }

  @Test
  public void testEquals() {
    filterMatcher.addFilter(filter(Operator.EQUAL, 1));
    assertNoMatch(0);
    assertNoMatch(null, 0, 2, 5);
    assertMatch(1);
    assertMatch(null, 1, 2, 5);

    filterMatcher.addFilter(filter(Operator.EQUAL, null));
    assertNoMatch(0);
    assertNoMatch(null, 0, 2, 5);
    assertNoMatch(1);
    assertMatch(null, 1, 2, 5, "bob");

    filterMatcher.addFilter(filter(Operator.EQUAL, "bob"));
    assertNoMatch(0);
    assertNoMatch(null, 1, 2, 5);
    assertNoMatch(1);
    assertMatch(null, 1, 2, 5, "bob");
  }

  @Test
  public void testRangeAndEquals() {
    filterMatcher.addFilter(filter(Operator.LESS_THAN, 0));
    filterMatcher.addFilter(filter(Operator.GREATER_THAN_OR_EQUAL, -5));
    filterMatcher.addFilter(filter(Operator.EQUAL, "bob"));
    filterMatcher.addFilter(filter(Operator.EQUAL, 1));

    assertNoMatch(-1);
    assertNoMatch(-1, "bob");
    assertNoMatch("bob", 1);
    assertNoMatch(-1, 1);
    assertNoMatch(-6, "bob", 1);

    assertMatch(-1, "bob", 1);
    assertMatch(null, -1, "bob", 1, "fred");
  }

  @Test
  public void testGeo_Rectangle() {
    RegionPoint sw = new RegionPoint().setLatitude(0).setLongitude(-180);
    RegionPoint ne = new RegionPoint().setLatitude(90).setLongitude(0);
    RectangleRegion rect = new RectangleRegion().setSouthwest(sw).setNortheast(ne);
    filterMatcher.addFilter(geoFilter(new GeoRegion().setRectangle(rect)));
    assertNoMatch("bob");
    assertMatch("bob", new GeoPt(12, -100));

    assertNoMatch(new GeoPt(12, 24));
    assertNoMatch(new GeoPt(-12, -24));
  }

  @Test
  public void testGeo_MultiPreintersection() {
    RegionPoint sw = new RegionPoint().setLatitude(0).setLongitude(-180);
    RegionPoint ne = new RegionPoint().setLatitude(90).setLongitude(0);
    RectangleRegion rect = new RectangleRegion().setSouthwest(sw).setNortheast(ne);
    filterMatcher.addFilter(geoFilter(new GeoRegion().setRectangle(rect)));
    filterMatcher.addFilter(filter(Operator.EQUAL, 1732));
    filterMatcher.addFilter(filter(Operator.EQUAL, 87));

    assertMatch("bob", new GeoPt(12, -100), 87, 1732, 99);

    assertNoMatch(new GeoPt(12, 24), 1732);
    assertNoMatch(1732, 87);
  }

  private void assertNoMatch(Object... values) {
    assertMatches(values, false);
  }

  private void assertMatch(Object... values) {
    assertMatches(values, true);
  }

  private void assertMatches(Object[] values, boolean shouldMatch) {
    List<Comparable<Object>> cvalues = new ArrayList<>(values.length);
    for (Object value : values) {
      cvalues.add(DataTypeTranslator.getComparablePropertyValue(value));
    }

    assertThat(filterMatcher.matches(cvalues)).isEqualTo(shouldMatch);
  }

  private Filter filter(Filter.Operator op, Object value) {
    Filter result = new Filter();
    result.setOp(op);
    result.addProperty().setName("noname").setValue(DataTypeTranslator.toV3Value(value));
    return result;
  }

  private Filter geoFilter(GeoRegion region) {
    Filter result = new Filter();
    result.setOp(Operator.CONTAINED_IN_REGION);
    result.addProperty().setName("noname");
    result.setGeoRegion(region);
    return result;
  }
}
