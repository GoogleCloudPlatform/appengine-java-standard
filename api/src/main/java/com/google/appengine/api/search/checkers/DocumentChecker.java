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

import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.ByteString;

/**
 * Checks values of a {@link com.google.appengine.api.search.Document}.
 *
 */
public final class DocumentChecker {

  private static final long MILLIS_UP_TO_1ST_JAN_2011 = 1293840000000L;

  /**
   * Checks whether a document id is valid. A document id is a
   * non-null ASCII visible printable string of
   * {@literal #MAXIMUM_DOCUMENT_ID_LENGTH} characters which does not start
   * with '!' which is reserved for system documents.
   *
   * @param documentId the document id to check
   * @return the checked document id
   * @throws IllegalArgumentException if the document id is invalid
   */
  public static String checkDocumentId(String documentId) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(documentId), "Document id is null or empty");
    Preconditions.checkArgument(
        documentId.length() <= SearchApiLimits.MAXIMUM_DOCUMENT_ID_LENGTH,
        "Document id is longer than %s: %s",
        SearchApiLimits.MAXIMUM_DOCUMENT_ID_LENGTH,
        documentId);
    Preconditions.checkArgument(IndexChecker.isAsciiVisiblePrintable(documentId),
        "Document id must be ASCII visible printable: %s", documentId);
    Preconditions.checkArgument(!IndexChecker.isReserved(documentId),
        "Document id must not start with !: %s", documentId);
    return documentId;
  }

  /**
   * Checks whether a document's field set is valid.
   * A field set is valid if it does not contain any number, date, or vector fields with the same
   * name.
   *
   * @param document the document to check
   * @throws IllegalArgumentException if the document contains an invalid set of fields.
   */
  public static void checkFieldSet(DocumentPb.Document document) {
    // TODO should both this and checkValid() be public (checkValid()
    // eventually calls this)?
    SetMultimap<String, ContentType> noRepeatNames = HashMultimap.create();
    for (DocumentPb.Field field : document.getFieldList()) {
      ContentType type = field.getValue().getType();
      if (type == ContentType.NUMBER || type == ContentType.DATE || type == ContentType.VECTOR) {
        if (!noRepeatNames.put(field.getName(), type)) {
          // To keep backward compatibility, report the same type string if the repeated field
          // is not a vector.
          String typeString;
          if (type == ContentType.VECTOR) {
            typeString = "vector";
          } else {
            typeString = "date or number";
          }
          throw new IllegalArgumentException(
              "Invalid document " + document.getId() + ": field " + field.getName()
              + " with type " + typeString + " may not be repeated.");
        }
      }
    }
  }

  /** Checks validity of facets in document. */
  public static void checkFacetSet(DocumentPb.Document document) {
    for (DocumentPb.Facet facet : document.getFacetList()) {
      if (facet.getValue().getStringValue().isEmpty()) {
        throw new IllegalArgumentException("Facet value is empty");
      }
    }
  }

  /**
   * Checks whether a {@link DocumentPb.Document} has a valid set
   * of fields.
   *
   * @param pb the {@link DocumentPb.Document} protocol buffer to check
   * @return the checked document
   * @throws IllegalArgumentException if some field is invalid such as
   * document id or fields
   */
  public static DocumentPb.Document checkValid(DocumentPb.Document pb) {
    Preconditions.checkArgument(
        pb.getSerializedSize() <= SearchApiLimits.MAXIMUM_DOCUMENT_LENGTH,
        "Document length %s is greater than the maximum %s bytes",
        pb.getSerializedSize(),
        SearchApiLimits.MAXIMUM_DOCUMENT_LENGTH);
    if (pb.hasId()) {
      checkUTF8ByteString(pb.getIdBytes(), "Document id has invalid utf8 sequence.");
      checkDocumentId(pb.getId());
    }
    mandatoryCheckValid(pb);
    return pb;
  }

  /**
   * Does only the {@link DocumentPb.Document} validity checks that must be satisfied for all
   * customer types that use the search API.
   *
   * @param pb the {@link DocumentPb.Document} protocol buffer to check
   * @throws IllegalArgumentException if the document is invalid.
   */
  static void mandatoryCheckValid(DocumentPb.Document pb) {
    Preconditions.checkArgument(!pb.getFieldList().isEmpty(),
        "Empty list of fields in document for indexing");
    checkFieldSet(pb);
    checkFacetSet(pb);
    checkValidUtf8(pb);
  }

  /**
   * Check a {@link com.google.protobuf.ByteString} to see if it has an invalud UTF8 sequence.
   *
   * @param utf8 the {@link com.google.protobuf.ByteString} to check
   * @param format the format string of exception message
   * @param args arguments of exception string
   * @throws IllegalArgumentException if {@link com.google.protobuf.ByteString}
   * contains an invalid utf8 sequence.
   */
  private static void checkUTF8ByteString(ByteString utf8, String format, Object... args) {
    if (!utf8.isValidUtf8()) {
      throw new IllegalArgumentException(String.format(format, args));
    }
  }

  /**
   * Make sure all of the string fields of the document has valid utf8 sequence.
   *
   * @param document the {@link DocumentPb.Document} protocol buffer to check
   * @throws IllegalArgumentException if the document has invalid utf8 string field.
   */
  static void checkValidUtf8(DocumentPb.Document document) {
    checkUTF8ByteString(document.getIdBytes(), "Document id has invalid utf8 sequence.");
    String docId = document.getId();
    checkUTF8ByteString(document.getLanguageBytes(),
        "Invalid utf8 sequence in language of document %s", docId);
    for (DocumentPb.Field field : document.getFieldList()) {
      checkUTF8ByteString(field.getNameBytes(),
          "Invalid utf8 sequence in a field name of document %s", docId);
      checkUTF8ByteString(field.getValue().getLanguageBytes(),
          "Invalid utf8 sequence in language of field %s document %s",
          field.getName(), docId);
      checkUTF8ByteString(field.getValue().getStringValueBytes(),
          "Invalid utf8 sequence in value of field %s document %s",
          field.getName(), docId);
    }
    for (DocumentPb.Facet facet : document.getFacetList()) {
      checkUTF8ByteString(facet.getNameBytes(),
          "Invalid utf8 sequence in a facet name of document %s", docId);
      checkUTF8ByteString(facet.getValue().getStringValueBytes(),
          "Invalid utf8 sequence in value of facet %s document %s",
          facet.getName(), docId);
    }
  }

  /**
   * @return the number of seconds since 2011/1/1
   */
  public static int getNumberOfSecondsSince() {
    long millisSince = Math.max(0L,
        (System.currentTimeMillis() - MILLIS_UP_TO_1ST_JAN_2011) / 1000L);
    Preconditions.checkArgument(millisSince <= Integer.MAX_VALUE,
        "API failure due to date conversion overflow");
    return (int) millisSince;
  }
}
