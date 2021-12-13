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

import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

/**
 * Expression which evalutes to the content of a field.
 *
 */
public class FieldExpression extends NumericExpression {
  private final Map<ContentType, String> luceneFields;
  private final List<ContentType> typePriority;
  private final List<ContentType> typePriorityWithoutNumeric;
  private final boolean hasNumericFields;

  public FieldExpression(String fieldName, Set<ContentType> fieldTypes) {
    this.luceneFields = new HashMap<ContentType, String>(fieldTypes.size());
    this.typePriority = new ArrayList<ContentType>(fieldTypes.size());
    this.typePriorityWithoutNumeric = new ArrayList<ContentType>(fieldTypes.size());

    for (ContentType type : fieldTypes) {
      this.luceneFields.put(type, LuceneUtils.makeLuceneFieldName(fieldName, type));
    }
    for (ContentType type : new ContentType[] {ContentType.NUMBER, ContentType.DATE,
        ContentType.TEXT, ContentType.HTML, ContentType.ATOM}) {
      if (fieldTypes.contains(type)) {
        this.typePriority.add(type);
      }
    }
    for (ContentType type : new ContentType[] {ContentType.DATE, ContentType.TEXT,
        ContentType.HTML, ContentType.ATOM}) {
      if (fieldTypes.contains(type)) {
        this.typePriorityWithoutNumeric.add(type);
      }
    }
    hasNumericFields =
        fieldTypes.contains(ContentType.NUMBER) || fieldTypes.contains(ContentType.DATE);
  }

  public static FieldExpression makeFieldExpression(String fieldName, Set<ContentType> fieldTypes) {
    if (fieldTypes == null) {
      throw new IllegalArgumentException("Unknown field:" + fieldName);
    }
    return new FieldExpression(fieldName, fieldTypes);
  }

  @Override
  public double evalDouble(Document doc) throws EvaluationException {
    String luceneFieldName = luceneFields.get(ContentType.NUMBER);
    if (luceneFieldName == null) {
      luceneFieldName = luceneFields.get(ContentType.DATE);
      if (luceneFieldName == null) {
        throw new EvaluationException("incorrect field type");
      }
    }
    Field[] fields = doc.getFields(luceneFieldName);
    if (fields.length == 0) {
      throw new EvaluationException("numeric field was not found");
    }
    return LuceneUtils.numericFieldToDouble(fields[0]);
  }

  @Override
  public FieldValue eval(Document doc) throws EvaluationException {
    return evalWithTypePriority(doc, typePriority);
  }

  public FieldValue evalWithTypePriority(Document doc, List<ContentType> typePriority)
      throws EvaluationException {
    for (ContentType type : typePriority) {
      String fieldName = luceneFields.get(type);
      Field[] fields = doc.getFields(fieldName);
      if (fields.length == 0) {
        continue;
      }
      String stringValue = String.valueOf(LuceneUtils.luceneFieldToValue(fields[0], type));
      if (type == ContentType.TEXT || type == ContentType.ATOM) {
        type = ContentType.HTML;
      }
      return makeValue(type, stringValue);
    }
    throw new EvaluationException("field was not found");
  }

  void checkType(ContentType type) {
    if (luceneFields.get(type) == null) {
      throw new IllegalArgumentException("Field type mismatch");
    }
  }

  /**
   * @return at most 2 sorters. First, special case for numberic fields.
   * Second, alpha-numerical sort for other field types.
   */
  @Override
  public List<Sorter> getSorters(final int sign, double defaultValueNumeric,
      final String defaultValueText) {
    List<Sorter> sorters = new ArrayList<Sorter>(1);

    if (hasNumericFields) {
      sorters.add(getNumericSorter(sign, defaultValueNumeric));
    }
    if (!typePriorityWithoutNumeric.isEmpty()) {
      sorters.add(new Sorter() {
        @Override
        public Object eval(Document doc) {
          try {
            return evalWithTypePriority(doc, typePriorityWithoutNumeric).getStringValue();
          } catch (EvaluationException e) {
            return defaultValueText;
          }
        }

        @Override
        public int compare(Object left, Object right) {
          String leftString = (String) left;
          String rightString = (String) right;
          return sign * leftString.compareToIgnoreCase(rightString);
        }
      });
    }
    return sorters;
  }
}
