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

package com.google.appengine.api.search.dev;

import com.google.appengine.api.search.dev.LuceneQueryTreeContext.ComparisonOp;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.common.base.VerifyException;
import com.google.common.geometry.S1Angle;
import com.google.common.geometry.S2Cap;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2RegionCoverer;
import java.lang.reflect.Method;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Convenient form of {@link BooleanQuery} for generation of geopoint distance queries.
 */
class GeometricQuery extends BooleanQuery {

  private final String fieldName;
  private final String luceneFieldName;
  private final S2LatLng point;
  private final ComparisonOp op;
  private final double distance;

  static Query create(String fieldName, double latitude, double longitude,
      ComparisonOp op, double distance) {
    switch (op) {
      case GE:
      case GT:
      case LE:
      case LT:
        return new GeometricQuery(fieldName, latitude, longitude, op, distance);
      default:
        return LuceneUtils.getMatchNoneQuery();        
    }
  }

  private GeometricQuery(String fieldName, double latitude, double longitude,
      ComparisonOp op, double distance) {
    this.fieldName = fieldName;
    this.luceneFieldName = LuceneUtils.makeLuceneFieldName(fieldName, ContentType.GEO);
    this.point = S2LatLng.fromDegrees(latitude, longitude).normalized();
    this.op = op;
    this.distance = distance;
    init();
  }

  private void init() {
    S1Angle angle = S1Angle.radians(distance / GeometricField.EARTH_RADIUS_METERS);
    S2Cap cap = S2Cap.fromAxisAngle(point.toPoint(), angle);
    S2RegionCoverer coverer;
    try {
      coverer = newCoverer();
    } catch (ReflectiveOperationException e) {
      throw new VerifyException(e);
    }
    switch (op) {
      case LE:
      case LT:
        for (S2CellId cell : coverer.getCovering(cap)) {
          Term term = new Term(luceneFieldName, "S2:" + cell.level() + ":" + cell.toToken());
          add(new TermQuery(term), BooleanClause.Occur.SHOULD);
        }
        break;
      case GE:
      case GT:
        for (S2CellId cell : coverer.getInteriorCovering(cap)) {
          Term term = new Term(luceneFieldName, "S2:" + cell.level() + ":" + cell.toToken());
          add(new TermQuery(term), BooleanClause.Occur.MUST_NOT);
        }
        Term term = new Term(LuceneUtils.ALLDOCS_FIELD_NAME, LuceneUtils.ALLDOCS_FIELD_VALUE);
        add(new TermQuery(term), BooleanClause.Occur.MUST);
        break;
      default:
        throw new IllegalStateException("op " + op);
    }
  }

  private static S2RegionCoverer newCoverer() throws ReflectiveOperationException {
    // Depending on the version, S2RegionCoverer might be an immutable object with a builder, or
    // a mutable object. Use reflection so both cases work.
    Object mutable;
    Method builder = null;
    try {
      builder = S2RegionCoverer.class.getMethod("builder");
      mutable = builder.invoke(null);
    } catch (NoSuchMethodException e) {
      // OK, we're using the older version with the mutable classes.
      mutable = S2RegionCoverer.class.getConstructor().newInstance();
    }
    mutable
        .getClass()
        .getMethod("setMinLevel", int.class)
        .invoke(mutable, GeometricField.MIN_LEVEL);
    mutable
        .getClass()
        .getMethod("setMaxLevel", int.class)
        .invoke(mutable, GeometricField.MAX_LEVEL);
    Object result =
        (builder == null) ? mutable : mutable.getClass().getMethod("build").invoke(mutable);
    return (S2RegionCoverer) result;
  }

  @Override
  public String toString() {
    return String.format("GeometricQuery(field='%s' geopoint=(%f,%f) op=%s distance=%f)",
        fieldName, point.latDegrees(), point.lngDegrees(), op, distance);
  }
}
