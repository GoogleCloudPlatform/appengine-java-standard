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

import static com.google.common.truth.Truth.assertThat;
import static java.util.Calendar.FEBRUARY;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.Document.OrderIdSource;
import com.google.apphosting.api.search.DocumentPb.FacetValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.common.base.Strings;
import com.google.common.testing.EqualsTester;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Document}.
 *
 */
@RunWith(JUnit4.class)
public class DocumentTest {

  private Field createValidField() {
    return createValidFieldBuilder().build();
  }

  private Facet createValidFacet() {
    return Facet.withAtom("name", "value");
  }

  private Field.Builder createValidFieldBuilder() {
    return Field.newBuilder().setName("name").setText("value");
  }

  @Test
  public void testSetId() {
    assertThat(Document.newBuilder().build().getId()).isNull();
    assertThat(Document.newBuilder().setId(null).build().getId()).isNull();
    assertThrows(IllegalArgumentException.class, () -> Document.newBuilder().setId(""));
    assertThrows(IllegalArgumentException.class, () -> Document.newBuilder().setId("!reserved"));
    for (int i = 0; i < 33; ++i) {
      String illegalId = String.valueOf((char) i);
      assertThrows(IllegalArgumentException.class, () -> Document.newBuilder().setId(illegalId));
    }
    for (int i = 33; i < 127; ++i) {
      char c = (char) i;
      if (c != '!') {
        String legalId = String.valueOf(c);
        assertThat(Document.newBuilder().setId(legalId).build().getId()).isEqualTo(legalId);
      }
    }
    for (int i = 127; i < 250; ++i) {
      String illegalId = String.valueOf((char) i);
      assertThrows(IllegalArgumentException.class, () -> Document.newBuilder().setId(illegalId));
    }
    String id = Strings.repeat("a", SearchApiLimits.MAXIMUM_DOCUMENT_ID_LENGTH);
    assertThat(Document.newBuilder().setId(id).build().getId()).isEqualTo(id);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            Document.newBuilder()
                .setId(Strings.repeat("a", SearchApiLimits.MAXIMUM_DOCUMENT_ID_LENGTH + 1)));
  }

  @Test
  public void testSetId_emptyDocument() {
    Document.newBuilder().build();
    // If we are here, we successfully built an empty document.
  }

  @Test
  public void testSetLanguage_nullLanguage() {
    Document doc =
        Document.newBuilder().addField(Field.newBuilder().setName("name")).setLocale(null).build();
    assertThat(doc.getLocale()).isNull();
  }

  @Test
  public void testAddNullField() {
    assertThrows(NullPointerException.class, () -> Document.newBuilder().addField((Field) null));
  }

  @Test
  public void testAddNullFacet() {
    assertThrows(NullPointerException.class, () -> Document.newBuilder().addFacet(null));
  }

  @Test
  public void testAddFieldBuilder() {
    Document document = Document.newBuilder().addField(createValidFieldBuilder()).build();
    assertThat(document.getId()).isNull();
    assertThat(document.getFieldNames()).containsExactly("name");
    Iterator<Field> fieldsWithName = document.getFields("name").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("value");
    assertThat(fieldsWithName.hasNext()).isFalse();

    document =
        Document.newBuilder()
            .addField(Field.newBuilder().setName("name").setText("first value"))
            .addField(Field.newBuilder().setName("name").setText("second value"))
            .build();

    assertThat(document.getFieldNames()).hasSize(1);
    fieldsWithName = document.getFields("name").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("first value");
    assertThat(fieldsWithName.next().getText()).isEqualTo("second value");
    assertThat(fieldsWithName.hasNext()).isFalse();
  }

  @Test
  public void testAddFieldBuilder_invalidField() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Document.newBuilder().addField(Field.newBuilder()).build());
  }

  @Test
  public void testAddFacetBuilder_invalidFacet() {
    assertThrows(
        NullPointerException.class,
        () -> Document.newBuilder().addFacet(Facet.withAtom(null, null)).build());
  }

  @Test
  public void testModifyingIterableField() {
    Document document =
        Document.newBuilder().addField(Field.newBuilder().setName("name").setText("value")).build();
    Iterator<Field> fieldsWithName = document.getFields("name").iterator();
    fieldsWithName.next();
    assertThrows(UnsupportedOperationException.class, fieldsWithName::remove);
  }

  @Test
  public void testModifyingIterableFacet() {
    Document document = Document.newBuilder().addFacet(createValidFacet()).build();
    Iterator<Facet> facetsWithName = document.getFacets("name").iterator();
    facetsWithName.next();
    assertThrows(UnsupportedOperationException.class, facetsWithName::remove);
  }

  @Test
  public void testGetNamedFields() {
    Document document = Document.newBuilder().addField(createValidFieldBuilder()).build();
    assertThat(document.getFields("name")).isNotNull();
    assertThat(document.getFields("blargh")).isNull();
  }

  @Test
  public void testGetNamedFacets() {
    Document document = Document.newBuilder().addFacet(createValidFacet()).build();
    assertThat(document.getFacets("name")).isNotNull();
    assertThat(document.getFacets("blargh")).isNull();
  }

  @Test
  public void testGetOnlyField() {
    Document document1 =
        Document.newBuilder().addField(Field.newBuilder().setName("name").setText("value")).build();

    Field field = document1.getOnlyField("name");
    assertThat(field.getText()).isEqualTo("value");

    Document document2 =
        Document.newBuilder()
            .addField(Field.newBuilder().setName("name").setText("first value"))
            .addField(Field.newBuilder().setName("name").setText("second value"))
            .build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> document2.getOnlyField("name"));
    assertThat(exception).hasMessageThat().matches(".*2 times; expected 1");
  }

  @Test
  public void testGetOnlyFacet() {
    Document document1 = Document.newBuilder().addFacet(createValidFacet()).build();

    Facet facet = document1.getOnlyFacet("name");
    assertThat(facet.getAtom()).isEqualTo("value");

    Document document2 =
        Document.newBuilder()
            .addFacet(Facet.withAtom("name", "first value"))
            .addFacet(Facet.withAtom("name", "second value"))
            .build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> document2.getOnlyFacet("name"));
    assertThat(exception).hasMessageThat().matches(".*2 times; expected 1");
  }

  @Test
  public void testFieldCount() {
    Document document =
        Document.newBuilder()
            .addField(Field.newBuilder().setName("once").setText("present one time"))
            .addField(Field.newBuilder().setName("twice").setText("twice #1"))
            .addField(Field.newBuilder().setName("twice").setText("twice #2"))
            .build();

    assertThat(document.getFieldCount("zero")).isEqualTo(0);
    assertThat(document.getFieldCount("once")).isEqualTo(1);
    assertThat(document.getFieldCount("twice")).isEqualTo(2);
  }

  @Test
  public void testFacetCount() {
    Document document =
        Document.newBuilder()
            .addFacet(Facet.withAtom("once", "present one time"))
            .addFacet(Facet.withAtom("twice", "twice #1"))
            .addFacet(Facet.withAtom("twice", "twice #2"))
            .build();

    assertThat(document.getFacetCount("zero")).isEqualTo(0);
    assertThat(document.getFacetCount("once")).isEqualTo(1);
    assertThat(document.getFacetCount("twice")).isEqualTo(2);
  }

  @Test
  public void testAddNullFieldBuilder() {
    assertThrows(
        NullPointerException.class, () -> Document.newBuilder().addField((Field.Builder) null));
  }

  @Test
  public void testAddNullFacetBuilder() {
    assertThrows(NullPointerException.class, () -> Document.newBuilder().addFacet(null));
  }

  @Test
  public void testCreateDocument() {
    Document document =
        Document.newBuilder()
            .addField(createValidField())
            .addFacet(createValidFacet())
            .setRank(9999)
            .build();
    assertThat(document.getId()).isNull();
    assertThat(document.getLocale()).isNull();
    assertThat(document.getFieldNames()).containsExactly("name");
    assertThat(document.getFacetNames()).containsExactly("name");
    assertThat(document.getRank()).isGreaterThan(0);

    Iterator<Field> fieldsWithName = document.getFields("name").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("value");
    assertThat(fieldsWithName.hasNext()).isFalse();

    Iterator<Facet> facetsWithName = document.getFacets("name").iterator();
    assertThat(facetsWithName.next().getAtom()).isEqualTo("value");
    assertThat(facetsWithName.hasNext()).isFalse();
  }

  @Test
  public void testNewBuilder_protocolBuffer() {
    Document document =
        Document.newBuilder(
                DocumentPb.Document.newBuilder()
                    .setId("docId")
                    .addField(
                        DocumentPb.Field.newBuilder()
                            .setName("name")
                            .setValue(
                                DocumentPb.FieldValue.newBuilder()
                                    .setStringValue("value")
                                    .setType(FieldValue.ContentType.HTML)
                                    .setLanguage("pl_PL")))
                    .addFacet(
                        DocumentPb.Facet.newBuilder()
                            .setName("name")
                            .setValue(
                                DocumentPb.FacetValue.newBuilder()
                                    .setStringValue("value")
                                    .setType(DocumentPb.FacetValue.ContentType.ATOM)))
                    .setOrderId(9999)
                    .setLanguage("en_GB")
                    .build())
            .build();

    assertThat(document.getId()).isEqualTo("docId");
    assertThat(document.getLocale()).isEqualTo(Locale.UK);
    assertThat(document.getFieldNames()).containsExactly("name");
    assertThat(document.getFacetNames()).containsExactly("name");
    assertThat(document.getRank()).isEqualTo(9999);
    assertThat(document.getRank()).isGreaterThan(0);

    Iterator<Field> fieldsWithName = document.getFields("name").iterator();
    Field field = fieldsWithName.next();
    assertThat(fieldsWithName.hasNext()).isFalse();
    assertThat(field.getHTML()).isEqualTo("value");
    assertThat(field.getLocale()).isEqualTo(new Locale("pl", "PL"));

    Iterator<Facet> facetsWithName = document.getFacets("name").iterator();
    Facet facet = facetsWithName.next();
    assertThat(facetsWithName.hasNext()).isFalse();
    assertThat(facet.getAtom()).isEqualTo("value");
  }

  @Test
  public void testCopyToProtocolBuffer() {
    Locale locale = new Locale("pl", "PL");
    DocumentPb.Document pb =
        Document.newBuilder()
            .addField(createValidField())
            .addFacet(createValidFacet())
            .setRank(9999)
            .setLocale(locale)
            .build()
            .copyToProtocolBuffer();

    assertThat(pb.hasId()).isFalse();
    assertThat(pb.getLanguage()).isEqualTo(locale.toString());
    assertThat(pb.getFieldCount()).isEqualTo(1);
    assertThat(pb.getOrderId()).isEqualTo(9999);
    assertThat(pb.getOrderIdSource()).isEqualTo(OrderIdSource.SUPPLIED);
    DocumentPb.Field field = pb.getFieldList().get(0);
    assertThat(field.getName()).isEqualTo("name");
    FieldValue value = field.getValue();
    assertThat(value.getStringValue()).isEqualTo("value");
    DocumentPb.Facet facet = pb.getFacetList().get(0);
    assertThat(facet.getName()).isEqualTo("name");
    FacetValue fvalue = facet.getValue();
    assertThat(fvalue.getStringValue()).isEqualTo("value");

    pb =
        Document.newBuilder()
            .addField(createValidField())
            .addFacet(createValidFacet())
            .build()
            .copyToProtocolBuffer();
    assertThat(pb.hasId()).isFalse();

    assertThrows(
        IllegalArgumentException.class, () -> Document.newBuilder().build().copyToProtocolBuffer());
  }

  @Test
  public void testCopyToProtocolBufferWithDefaultRank() {
    DocumentPb.Document pb =
        Document.newBuilder().addField(createValidField()).build().copyToProtocolBuffer();

    assertThat(pb.getOrderIdSource()).isEqualTo(OrderIdSource.DEFAULTED);
  }

  @Test
  public void testEquals() {
    Document doc1 =
        Document.newBuilder()
            .setId("docId1")
            .addField(createValidField())
            .addFacet(createValidFacet())
            .build();
    Document doc2 =
        Document.newBuilder()
            .setId("docId2")
            .addField(createValidField())
            .addFacet(createValidFacet())
            .build();
    HashSet<Document> docs = new HashSet<>();
    docs.add(doc1);
    docs.add(doc2);
    assertThat(docs).hasSize(2);
    assertThat(doc1.equals(doc2)).isFalse();
    new EqualsTester().addEqualityGroup(doc1).testEquals();
    assertThat(docs).contains(doc1);
    assertThat(docs).contains(doc2);
  }

  @Test
  public void testRank() {
    // NOTE: This is kind of lame, as we have no way of setting our
    // own time and therefore no way of precisely testing order ID. So we just
    // bound it from above and below.
    Document d1 = Document.newBuilder().build();
    Document d2 = Document.newBuilder().build();
    assertThat(d1.getRank()).isAtMost(d2.getRank());
    Calendar jan012011 = DateTestHelper.getCalendar();
    jan012011.set(2011, 0, 1, 0, 0, 0);
    jan012011.set(Calendar.MILLISECOND, 0);
    long currentOrderId = System.currentTimeMillis() - jan012011.getTimeInMillis();

    // d1's order Id should be greater than, but close to, the order Id
    // calculated for the current time. Use a 10 second window to avoid
    // flakyness due to scheduling issues on test machines.
    assertThat(currentOrderId >= d1.getRank() * 1000L).isTrue();
    assertThat(currentOrderId < (10 + d1.getRank()) * 1000L).isTrue();
  }

  @Test
  public void testToString() {
    assertThat(
            Document.newBuilder()
                .setId("docId")
                .addField(Field.newBuilder().setName("name").setText("text"))
                .addFacet(Facet.withAtom("name", "text"))
                .setLocale(new Locale("pl", "PL"))
                .setRank(999)
                .build()
                .toString())
        .isEqualTo(
            "Document(documentId=docId, fields=[Field(name=name, value=text, type=TEXT)], "
                + "facets=[Facet(name=name, atom=text)], locale=pl_PL, rank=999)");

    assertThat(
            Document.newBuilder()
                .setId("docId")
                .addField(Field.newBuilder().setName("name").setText("text"))
                .setRank(999)
                .build()
                .toString())
        .isEqualTo(
            "Document(documentId=docId, fields=[Field(name=name, value=text, type=TEXT)], "
                + "rank=999)");
  }

  @Test
  public void testDocumentSize() {
    int okSize = 1 << 19;
    int tooBig = 1 << 20; // 1 MB plus other parts of protocol buffer
    Document ok =
        Document.newBuilder()
            .addField(Field.newBuilder().setName("name").setText(Strings.repeat("x", okSize)))
            .build();
    Document tooBigDoc =
        Document.newBuilder()
            .addField(Field.newBuilder().setName("name").setText(Strings.repeat("x", tooBig)))
            .build();
    ok.copyToProtocolBuffer();
    assertThrows(Exception.class, tooBigDoc::copyToProtocolBuffer);
  }

  @Test
  public void testInvalidRepeatedFields() {
    Field num1 = Field.newBuilder().setName("repeat").setNumber(6).build();
    Field num2 = Field.newBuilder().setName("repeat").setNumber(1).build();
    Calendar cal = DateTestHelper.getCalendar();
    cal.set(2002, FEBRUARY, 1, 0, 0, 0);
    Field date1 = Field.newBuilder().setName("repeat").setDate((Date) cal.getTime()).build();
    Field date2 = Field.newBuilder().setName("repeat").setDate((Date) cal.getTime()).build();
    Field vector1 =
        Field.newBuilder().setName("repeat").setVector(Arrays.asList(1.0, 2.0, 3.0)).build();
    Field vector2 =
        Field.newBuilder().setName("repeat").setVector(Arrays.asList(1.0, 2.0, 4.0)).build();
    Field text = Field.newBuilder().setName("repeat").setText("hello").build();
    Field geo = Field.newBuilder().setName("repeat").setGeoPoint(new GeoPoint(10, 40)).build();

    // Adding fields with the same name should produce an exception
    assertThrows(
        IllegalArgumentException.class, () -> Document.newBuilder().addField(num1).addField(num2));

    assertThrows(
        IllegalArgumentException.class,
        () -> Document.newBuilder().addField(date1).addField(date2));

    assertThrows(
        IllegalArgumentException.class,
        () -> Document.newBuilder().addField(vector1).addField(vector2));

    // Should not throw an exception
    Document.newBuilder().addField(text).addField(date1).addField(geo);
    Document.newBuilder().addField(text).addField(date1).addField(num1);
  }
}
