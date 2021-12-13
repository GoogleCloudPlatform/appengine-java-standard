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

import com.google.apphosting.api.search.DocumentPb;
import com.google.common.geometry.S2LatLng;
import java.io.Reader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

/**
 * Lucene support for geopoint field indexing.
 */
class GeometricField extends AbstractField {
  // We use the same value for Earth's radius as ST uses in production.
  static final double EARTH_RADIUS_METERS = 6371010;
  static final int MIN_LEVEL = 0;
  static final int MAX_LEVEL = 30;

  GeometricField(String name, DocumentPb.FieldValue.Geo value) {
    super(name, Store.YES, Index.ANALYZED, TermVector.NO);
    this.fieldsData = value;
  }

  private DocumentPb.FieldValue.Geo data() {
    if (fieldsData instanceof DocumentPb.FieldValue.Geo) {
      return (DocumentPb.FieldValue.Geo) fieldsData;
    }
    return null;
  }

  @Override
  public String stringValue() {
    DocumentPb.FieldValue.Geo data = data();
    return data != null ? data.getLat() + "," + data.getLng() : null;
  }

  @Override
  public Reader readerValue() {
    return null;
  }

  @Override
  public byte[] binaryValue() {
    return null;
  }

  @Override
  public TokenStream tokenStreamValue() {
    DocumentPb.FieldValue.Geo value = data();
    S2LatLng point = S2LatLng.fromDegrees(value.getLat(), value.getLng());
    return new GeometricTokenStream(point.normalized());
  }
}
