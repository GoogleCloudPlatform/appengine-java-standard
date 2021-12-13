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

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import java.io.IOException;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

class GeometricTokenStream extends TokenStream {
  public static final String TOKEN_TYPE_GEOMETRIC = "geometric";
  private static final int ANYWHERE_LEVEL = -1;
  static final String ANYWHERE_TOKEN = "anywhere";
  private int i = ANYWHERE_LEVEL;
  private final TermAttribute termAtt = (TermAttribute) addAttribute(TermAttribute.class);
  private final TypeAttribute typeAtt = (TypeAttribute) addAttribute(TypeAttribute.class);
  private final S2CellId maxCell;

  GeometricTokenStream(S2LatLng point) {
    maxCell = S2CellId.fromLatLng(point);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (i == ANYWHERE_LEVEL) {
      typeAtt.setType(TOKEN_TYPE_GEOMETRIC);
      termAtt.setTermBuffer(ANYWHERE_TOKEN);
      i = GeometricField.MIN_LEVEL;
      return true;
    } else if (i > GeometricField.MAX_LEVEL) {
      return false;
    } else {
      S2CellId cellId = maxCell.parent(i++);
      typeAtt.setType(TOKEN_TYPE_GEOMETRIC);
      termAtt.setTermBuffer("S2:" + cellId.level() + ":" + cellId.toToken());
      return true;
    }
  }
}
