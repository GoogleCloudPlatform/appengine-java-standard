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

package com.google.appengine.api.search.checkers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FacetValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DocumentChecker}. */
@RunWith(JUnit4.class)
public class DocumentCheckerTest {
  @Test
  public void testFieldSet() {

    DocumentPb.Field num1 =
        DocumentPb.Field.newBuilder()
            .setName("repeat")
            .setValue(
                DocumentPb.FieldValue.newBuilder()
                    .setType(DocumentPb.FieldValue.ContentType.NUMBER)
                    .setStringValue("1.3")
                    .build())
            .build();
    DocumentPb.Field num2 = DocumentPb.Field.newBuilder().mergeFrom(num1).build();

    DocumentPb.Field date1 =
        DocumentPb.Field.newBuilder()
            .setName("repeat")
            .setValue(
                DocumentPb.FieldValue.newBuilder()
                    .setType(DocumentPb.FieldValue.ContentType.DATE)
                    .setStringValue("102423014")
                    .build())
            .build();
    DocumentPb.Field date2 = DocumentPb.Field.newBuilder().mergeFrom(date1).build();

    DocumentPb.Field geo =
        DocumentPb.Field.newBuilder()
            .setName("repeat")
            .setValue(
                DocumentPb.FieldValue.newBuilder()
                    .setType(DocumentPb.FieldValue.ContentType.GEO)
                    .setStringValue("40,56")
                    .build())
            .build();

    DocumentPb.Field text =
        DocumentPb.Field.newBuilder()
            .setName("repeat")
            .setValue(
                DocumentPb.FieldValue.newBuilder()
                    .setType(DocumentPb.FieldValue.ContentType.TEXT)
                    .setStringValue("abort abort abort")
                    .build())
            .build();

    DocumentPb.Document doc2 =
        DocumentPb.Document.newBuilder()
            .setId("should-break")
            .addField(num1)
            .addField(num2)
            .build();
    assertThrows(IllegalArgumentException.class, () -> DocumentChecker.checkValid(doc2));

    DocumentPb.Document doc3 =
        DocumentPb.Document.newBuilder()
            .setId("should-break")
            .addField(date1)
            .addField(date2)
            .build();
    assertThrows(IllegalArgumentException.class, () -> DocumentChecker.checkValid(doc3));

    DocumentPb.Document doc4 =
        DocumentPb.Document.newBuilder()
            .setId("should-not-break")
            .addField(num1)
            .addField(date2)
            .build();
    DocumentChecker.checkValid(doc4);

    DocumentPb.Document doc5 =
        DocumentPb.Document.newBuilder().setId("should-break-no-fields").build();
    assertThrows(IllegalArgumentException.class, () -> DocumentChecker.checkValid(doc5));

    DocumentPb.Document doc6 =
        DocumentPb.Document.newBuilder()
            .setId("should-not-break")
            .addField(num1)
            .addField(geo)
            .addField(text)
            .build();
    DocumentChecker.checkValid(doc6);
  }

  @Test
  public void testFacetSet_missing() throws Exception {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                singleFacetCheck(
                    DocumentPb.Facet.newBuilder()
                        .setName("nameonly")
                        .setValue(DocumentPb.FacetValue.getDefaultInstance())));
    assertThat(expected).hasMessageThat().isEqualTo("Facet value is empty");
  }

  @Test
  public void testFacetSet_empty() throws Exception {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                singleFacetCheck(
                    DocumentPb.Facet.newBuilder()
                        .setName("emptyvalue")
                        .setValue(DocumentPb.FacetValue.newBuilder().setStringValue(""))));
    assertThat(expected).hasMessageThat().isEqualTo("Facet value is empty");
  }

  @Test
  public void testFacetSet_valid() throws Exception {
    singleFacetCheck(
        DocumentPb.Facet.newBuilder()
            .setName("withvalue")
            .setValue(DocumentPb.FacetValue.newBuilder().setStringValue("x")));
  }

  private static void singleFacetCheck(DocumentPb.Facet.Builder facetBuilder) throws Exception {
    DocumentPb.Document doc =
        DocumentPb.Document.newBuilder()
            .addField(
                DocumentPb.Field.newBuilder()
                    .setName("text")
                    .setValue(DocumentPb.FieldValue.newBuilder().setStringValue("text")))
            .addFacet(facetBuilder)
            .build();
    DocumentChecker.checkFacetSet(doc);
  }

  @Test
  public void testInvalidUTF8Stream() throws Exception {
    byte[][] testCases =
        new byte[][] {
          // Test case #0: invalid surrogates
          new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
          // Test case #1: a lonely continution character
          new byte[] {(byte) 0x80},
          // Test case #2: invalid two byte sequence
          new byte[] {(byte) 0xC3, (byte) 0x28},
          // Test case #3: invalid sequence identifier
          new byte[] {(byte) 0xa0, (byte) 0xa1},
          // Test case #4: invalid three byte sequence
          new byte[] {(byte) 0xe2, (byte) 0x28, (byte) 0xa1},
          // Test case #5: invalid four byte sequence
          new byte[] {(byte) 0xf0, (byte) 0x28, (byte) 0x8c, (byte) 0x28}
        };

    for (int i = 0; i < testCases.length; i++) {
      ByteString testCase = ByteString.copyFrom(testCases[i]);

      // test invalid sequence inside document id
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setIdBytes(testCase)
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setNameBytes(testCase)
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValue("value")
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguage("pl_PL")))
              .setLanguage("en_GB")
              .build(),
          i);

      // test invalid sequence inside field name
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setId("docId")
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setNameBytes(testCase)
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValue("value")
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguage("pl_PL")))
              .setLanguage("en_GB")
              .build(),
          i);

      // test invalid sequence inside field value
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setId("docId")
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setName("name")
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValueBytes(testCase)
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguage("pl_PL")))
              .setLanguage("en_GB")
              .build(),
          i);

      // test invalid sequence inside field language
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setId("docId")
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setName("name")
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValue("value")
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguageBytes(testCase)))
              .setLanguage("en_GB")
              .build(),
          i);

      // test invalid sequence inside document language
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setId("docId")
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setName("name")
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValue("value")
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguage("pl_PL")))
              .setLanguageBytes(testCase)
              .build(),
          i);

      // test invalid sequence inside facet name
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setId("docId")
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setName("name")
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValue("value")
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguage("pl_PL")))
              .addFacet(
                  DocumentPb.Facet.newBuilder()
                      .setNameBytes(testCase)
                      .setValue(
                          DocumentPb.FacetValue.newBuilder()
                              .setStringValue("value")
                              .setType(FacetValue.ContentType.ATOM)))
              .setLanguage("en_GB")
              .build(),
          i);

      // test invalid sequence inside facet value
      singleUTF8Test(
          DocumentPb.Document.newBuilder()
              .setId("docId")
              .addField(
                  DocumentPb.Field.newBuilder()
                      .setName("name")
                      .setValue(
                          DocumentPb.FieldValue.newBuilder()
                              .setStringValue("value")
                              .setType(FieldValue.ContentType.TEXT)
                              .setLanguage("pl_PL")))
              .addFacet(
                  DocumentPb.Facet.newBuilder()
                      .setName("name")
                      .setValue(
                          DocumentPb.FacetValue.newBuilder()
                              .setStringValueBytes(testCase)
                              .setType(FacetValue.ContentType.ATOM)))
              .setLanguage("en_GB")
              .build(),
          i);
    }
  }

  private static void singleUTF8Test(DocumentPb.Document document, int testCaseNo)
      throws Exception {
    IllegalArgumentException e =
        assertThrows(
            "test case #" + testCaseNo,
            IllegalArgumentException.class,
            () -> DocumentChecker.checkValid(document));
    assertThat(e).hasMessageThat().containsMatch(".*[Ii]nvalid utf8.*");
  }
}
