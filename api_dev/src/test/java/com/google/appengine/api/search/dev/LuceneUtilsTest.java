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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.SortExpression;
import com.google.apphosting.api.search.DocumentPb;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link LuceneUtils}.
 *
 */
@RunWith(JUnit4.class)
public class LuceneUtilsTest {

  private DocumentPb.FieldValue atomValue;
  private DocumentPb.FieldValue textValue;
  private DocumentPb.FieldValue htmlValue;
  private DocumentPb.FieldValue dateValue;
  private DocumentPb.FieldValue numericValue;

  private static final Logger log = Logger.getLogger(LuceneUtilsTest.class.getName());

  @Before
  public void setUp() {
    atomValue = DocumentPb.FieldValue.newBuilder().setType(DocumentPb.FieldValue.ContentType.ATOM)
        .setStringValue("atom").build();
    textValue = DocumentPb.FieldValue.newBuilder().setType(DocumentPb.FieldValue.ContentType.TEXT)
        .setStringValue("hello, hello").build();
    htmlValue = DocumentPb.FieldValue.newBuilder().setType(DocumentPb.FieldValue.ContentType.HTML)
        .setStringValue("<b>world</b>").build();
    dateValue = DocumentPb.FieldValue.newBuilder().setType(DocumentPb.FieldValue.ContentType.DATE)
        .setStringValue("1295740800000").build();
    numericValue = DocumentPb.FieldValue.newBuilder()
        .setType(DocumentPb.FieldValue.ContentType.NUMBER).setStringValue("1.2345567").build();
  }

  @Test
  public void testToDocumentConversion() {
    DocumentPb.Document appEngineDoc = DocumentPb.Document.newBuilder().setId("docid")
        .addField(DocumentPb.Field.newBuilder().setName("subject").setValue(textValue))
        .addField(DocumentPb.Field.newBuilder().setName("body").setValue(htmlValue))
        .addField(DocumentPb.Field.newBuilder().setName("published").setValue(dateValue))
        .addField(DocumentPb.Field.newBuilder().setName("published").setValue(textValue))
        .addField(DocumentPb.Field.newBuilder().setName("label").setValue(atomValue))
        .addField(DocumentPb.Field.newBuilder().setName("amount").setValue(numericValue))
        .addField(DocumentPb.Field.newBuilder().setName("amount").setValue(htmlValue))
        .setOrderId(12345)
        .build();
    assertEquals(appEngineDoc,
        toAppengineDocument(LuceneUtils.toLuceneDocument("docid", appEngineDoc)));
  }

  @Test
  public void testToDocumentConversionDates() {
    DocumentPb.Document appEngineDoc = DocumentPb.Document.newBuilder().setId("docid")
        .addField(DocumentPb.Field.newBuilder().setName("published").setValue(
            DocumentPb.FieldValue.newBuilder().setType(DocumentPb.FieldValue.ContentType.DATE)
            .setStringValue("2011-01-23").build()))
        .setOrderId(12345)
        .build();
    DocumentPb.Document translatedDoc =
        toAppengineDocument(LuceneUtils.toLuceneDocument("docid", appEngineDoc));
    DocumentPb.Field appEngineDocField  = appEngineDoc.getFieldList().get(0);
    DocumentPb.Field translatedDocField = translatedDoc.getFieldList().get(0);
    assertEquals("published", appEngineDocField.getName());
    assertEquals(appEngineDocField.getName(), translatedDocField.getName());
    assertEquals(DocumentPb.FieldValue.ContentType.DATE, translatedDocField.getValue().getType());
    assertEquals(appEngineDocField.getValue().getType(), translatedDocField.getValue().getType());
    assertEquals("2011-01-23", appEngineDocField.getValue().getStringValue());
    assertEquals("1295740800000", translatedDocField.getValue().getStringValue());
  }

  @Test
  public void testExtractTextFromHtml() {
    assertEquals("abc < def", LuceneUtils.extractTextFromHtml("<h1>abc</h1> &lt; <b>def</b>"));
    assertEquals("abc def", LuceneUtils.extractTextFromHtml("<html><body><h1>abc</h1> <p>def</p>"));
    assertEquals("Hello", LuceneUtils.extractTextFromHtml(
        "Hello <img src=x onerror=\"alert(1);//\n<p>world</p>"));
    assertEquals("", LuceneUtils.extractTextFromHtml("<frameset>"));
  }

  /** b/24309093 was caused by a mismatch between the dev server and the API's constants. */
  @Test
  public void testRankNameMatchesAPI() {
    assertThat(LuceneUtils.ORDER_ID_FIELD_NAME).isEqualTo(SortExpression.RANK_FIELD_NAME);
  }

  /**
   * Converts a "Lucene document" to something almost the same as the original AppEngine
   * document from which it came.  The difference is that dates cannot be fully restored,
   * because they will have been normalized to 1-day granularity (to match Dexter behavior).
   * This is fine for tests, but probably shouldn't be used in real jsdk code, because it
   * may be confusing.
   */
  private static DocumentPb.Document toAppengineDocument(Document d) {
    DocumentPb.Document.Builder docBuilder = LuceneUtils.toAppengineDocumentIdBuilder(d);

    String orderIdStr = ((Field) d.getFieldable(LuceneUtils.ORDER_ID_FIELD_NAME)).stringValue();
    docBuilder.setOrderId(Integer.parseInt(orderIdStr));

    Field localeField = (Field) d.getFieldable(LuceneUtils.LOCALE_FIELD_NAME);
    if (localeField != null) {
      docBuilder.setLanguage(localeField.stringValue());
    }

    Iterator<?> fieldIter = d.getFields().iterator();
    while (fieldIter.hasNext()) {
      Fieldable f = (Fieldable) fieldIter.next();
      if (isPlainField(f.name())) {
        DocumentPb.FieldValue.Builder valueBuilder = DocumentPb.FieldValue.newBuilder();
        LuceneFieldName fieldName = splitLuceneFieldName(f.name());
        if (fieldName == null) {
          log.warning("Unable to find type for " + f.name() + "; ignored");
          continue;
        } else {
          Object value = LuceneUtils.luceneFieldToValue(f, fieldName.type);
          if (value instanceof String) {
            String stringValue = (String) value;
            if (fieldName.type == DocumentPb.FieldValue.ContentType.DATE) {
              stringValue = Long.toString(Long.parseLong(stringValue) * LuceneUtils.MSEC_PER_DAY);
            }
            valueBuilder.setStringValue(stringValue);
          } else if (value instanceof GeoPoint) {
            GeoPoint point = (GeoPoint) value;
            valueBuilder.setGeo(DocumentPb.FieldValue.Geo.newBuilder()
                .setLat(point.getLatitude()).setLng(point.getLongitude()));
          }
          valueBuilder.setType(fieldName.type);
        }
        docBuilder.addField(DocumentPb.Field.newBuilder()
            .setName(fieldName.name).setValue(valueBuilder));
      }
    }
    return docBuilder.build();
  }

  private static boolean isPlainField(String fieldName) {
    return !(LuceneUtils.DOCID_FIELD_NAME.equals(fieldName)
        || LuceneUtils.FIELDLESS_FIELD_NAME.equals(fieldName)
        || LuceneUtils.ALLDOCS_FIELD_NAME.equals(fieldName)
        || LuceneUtils.ORDER_ID_FIELD_NAME.equals(fieldName)
        || LuceneUtils.LOCALE_FIELD_NAME.equals(fieldName));
  }

  /**
   * Tuple returned from splitLuceneFieldName() function containing name and
   * type of field.
   */
  public static class LuceneFieldName {
    public final String name;
    public final DocumentPb.FieldValue.ContentType type;

    LuceneFieldName(String name, DocumentPb.FieldValue.ContentType type) {
      this.name = name;
      this.type = type;
    }
  }

  public static LuceneFieldName splitLuceneFieldName(String field) {
    int colon = field.indexOf('@');
    if (colon == -1) {
      log.severe("Misformed field name encountered " + field);
      return null;
    } else {
      String type = field.substring(0, colon);

      // Skip converted html fields
      if (LuceneUtils.CONVERTED_HTML_TYPE.equals(type)) {
        return null;
      }
      String name = field.substring(colon + 1);
      DocumentPb.FieldValue.ContentType typeEnum;
      try {
        typeEnum = DocumentPb.FieldValue.ContentType.valueOf(type);
      } catch (IllegalArgumentException e) {
        log.log(
            Level.SEVERE,
            "Failed to convert '" + name + "' extracted from '" + field + "' to ContentType",
            e);
        typeEnum = DocumentPb.FieldValue.ContentType.TEXT;
      }
      return new LuceneFieldName(name, typeEnum);
    }
  }
}
