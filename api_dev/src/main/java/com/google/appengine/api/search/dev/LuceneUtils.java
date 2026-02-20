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

import com.google.apphosting.api.AppEngineInternal;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FacetValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.jsoup.Jsoup;

/** Various utilities to interface with Lucene. */
@AppEngineInternal
public final class LuceneUtils {

  /**
   * The name of the field under which we store tokens for all fields, so
   * that we can search for them without a field prefix.
   */
  public static final String FIELDLESS_FIELD_NAME = "_GLOBAL";

  /**
   * The name of the field under which we store the AppEngine document ID.
   */
  static final String DOCID_FIELD_NAME = "_DOCID";

  /**
   * The name of the field under which we store the AppEngine original document.
   */
  static final String ORIGINAL_DOC_FIELD_NAME = "_ORIGINALDOC";

  /**
   * The name of the field under which we store a value that allows us
   * to search for all documents.
   */
  static final String ALLDOCS_FIELD_NAME = "_ALLDOC";

  /**
   * The name of the field under which we store the document's locale code.
   */
  static final String LOCALE_FIELD_NAME = "_LOCALE";

  /**
   * The token stored with each document to allows us to find all documents.
   */
  static final String ALLDOCS_FIELD_VALUE = "X";

  /**
   * This token in not stored in the ALLDOCS_FIELD_NAME field. It is used to
   * construct queries matching none.
   */
  static final String ALLDOCS_FIELD_MISSING_VALUE = "Y";

  /** The field that stores order ID. */
  static final String ORDER_ID_FIELD_NAME = "_rank";

  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();

  static final String CONVERTED_HTML_TYPE = "HTML2TEXT";

  public static final long MSEC_PER_DAY = 86400000L;

  /**
   * Prefix string for a Lucene field that represents a facet.
   */
  static final String FACET_NAME_PREFIX = "facet_";

  /**
   * Prefix string for a Lucene field that represents a Search API field.
   * Note: kept as empty string for backward compatibility.
   */
  static final String FIELD_NAME_PREFIX = "";

  /**
   * Word separator characters. This is package-private for testing.
   */
  static final ImmutableSet<Character> WORD_SEPARATORS = ImmutableSet.of(
      '!', '"', '%', '(', ')', '*', ',', '.', '/', ':', '=', '>', '?', '@', '[', '\\', ']', '^',
      '`', '{', '|', '}', '~', '\t', '\n', '\f', '\r', ' ', '&', '#', '$', ';');

   /**
   * The percentage of characters that must be CJK for us to tokenize the string as CJK.
   *
   * This should be set fairly low (certainly no higher than 50%, probably
   * closer to 20%); it's only purpose is to make sure mostly-latin text with a
   * few CJK characters is still segmented as latin.
   */
  private static final float CJK_CHARACTER_THRESHOLD = 0.2f;
  
  /**
   * Set of all CJK Unicode Blocks.
   */
  private static final ImmutableSet<Character.UnicodeBlock> CJK_BLOCKS = ImmutableSet.of(
      Character.UnicodeBlock.BOPOMOFO,
      Character.UnicodeBlock.BOPOMOFO_EXTENDED,
      Character.UnicodeBlock.CJK_COMPATIBILITY,
      Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS,
      Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
      Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
      Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT,
      Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
      Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
      Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
      Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS,
      Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS,
      Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
      Character.UnicodeBlock.HANGUL_JAMO,
      Character.UnicodeBlock.HANGUL_SYLLABLES,
      Character.UnicodeBlock.HIRAGANA,
      Character.UnicodeBlock.IDEOGRAPHIC_DESCRIPTION_CHARACTERS,
      Character.UnicodeBlock.KANBUN,
      Character.UnicodeBlock.KANGXI_RADICALS,
      Character.UnicodeBlock.KATAKANA,
      Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
      Character.UnicodeBlock.TAI_XUAN_JING_SYMBOLS,
      Character.UnicodeBlock.YI_RADICALS,
      Character.UnicodeBlock.YI_SYLLABLES,
      Character.UnicodeBlock.YIJING_HEXAGRAM_SYMBOLS
      );

  /**
   * The UTC time zone.
   */
  private static final ThreadLocal<TimeZone> UTC_TZ =
      new ThreadLocal<TimeZone>() {
        @Override protected TimeZone initialValue() {
          return TimeZone.getTimeZone("UTC");
        }
      };

  private static DateFormat getDateFormat(String formatString) {
    DateFormat format = new SimpleDateFormat(formatString, Locale.US);
    format.setTimeZone(UTC_TZ.get());
    return format;
  }

  private static final ThreadLocal<DateFormat> ISO8601_SIMPLE =
      new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
          return getDateFormat("yyyy-MM-dd");
        }
      };

  public static String makeLuceneFieldName(DocumentPb.Field field) {
    return makeLuceneFieldName(field.getName(), field.getValue().getType());
  }

  public static String makeLuceneFieldName(String name, DocumentPb.FieldValue.ContentType type) {
    return FIELD_NAME_PREFIX + type + "@" + name;
  }

  static String makeLuceneFieldName(String name, FacetValue.ContentType contentType) {
    return FACET_NAME_PREFIX + contentType + "@" + name;
  }

  public static String makeLuceneFieldNameWithExtractedText(
      String name, DocumentPb.FieldValue.ContentType type) {
    if (type == ContentType.HTML) {
      return CONVERTED_HTML_TYPE + "@" + name;
    }
    return makeLuceneFieldName(name, type);
  }

  public static String makeLuceneFieldNameWithExtractedText(DocumentPb.Field field) {
    return makeLuceneFieldNameWithExtractedText(field.getName(), field.getValue().getType());
  }

  public static String extractTextFromHtml(String html) {
    org.jsoup.nodes.Document doc = Jsoup.parse(html);
    org.jsoup.nodes.Element body = doc.body();
    return body != null ? body.text() : "";
  }

  /**
   * Locale-aware Double parsing.
   *
   * Double.valueOf(String) fails if your locale uses commas as decimal separators. This takes that
   * into account.
   */
  public static double stringValueToDouble(String value) {
    try {
      return NumberFormat.getNumberInstance().parse(value).doubleValue();
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  static AbstractField convertFacetToLuceneField(DocumentPb.Facet facet) {
    DocumentPb.FacetValue facetValue = facet.getValue();
    String facetName = makeLuceneFieldName(facet.getName(), facet.getValue().getType());
    String value = facet.getValue().getStringValue();
    return switch (facetValue.getType()) {
      case ATOM -> new Field(facetName, value, Field.Store.YES, Field.Index.NOT_ANALYZED);
      case NUMBER -> {
        NumericField numericField = new NumericField(facetName, Field.Store.YES, true);
        numericField.setDoubleValue(stringValueToDouble(value));
        yield numericField;
      }
      default ->
          throw new IllegalArgumentException("Facet type " + facetValue.getType() + " not handled");
    };
  }

  static boolean isFacetField(Fieldable field) {
    return field.name().startsWith(FACET_NAME_PREFIX);
  }

  static DocumentPb.Facet convertLuceneFieldToFacet(Fieldable field) {
    if (!isFacetField(field)) {
      throw new IllegalArgumentException(
          String.format("Field %s is not a facet field", field.name()));
    }
    String typeName = field.name().substring(LuceneUtils.FACET_NAME_PREFIX.length());
    int atIndex = typeName.indexOf("@");
    return DocumentPb.Facet.newBuilder().setName(typeName.substring(atIndex + 1))
        .setValue(DocumentPb.FacetValue.newBuilder().setStringValue(field.stringValue()).setType(
            DocumentPb.FacetValue.ContentType.valueOf(typeName.substring(0, atIndex))).build())
        .build();
  }
  
 
  public static List<AbstractField> toLuceneFields(DocumentPb.Field field) {
    List<AbstractField> output = new ArrayList<>();
    DocumentPb.FieldValue fieldValue = field.getValue();
    Field.Index globalIndexStrategy = Field.Index.ANALYZED;
    String fieldName = makeLuceneFieldName(field);
    String value = fieldValue.getStringValue();
    boolean makeGlobalField = true;

    switch (fieldValue.getType()) {
      case HTML -> {
        // Store original html
        output.add(new Field(fieldName, value, Field.Store.YES, Field.Index.NOT_ANALYZED));
        value = extractTextFromHtml(value);
        output.add(
            new Field(makeLuceneFieldNameWithExtractedText(field), value, Field.Store.YES, Field.Index.ANALYZED));
      }
      case TEXT ->
          output.add(new Field(fieldName, value, Field.Store.YES, globalIndexStrategy));
      case ATOM -> {
        value = value.toLowerCase(Locale.ROOT);
        output.add(new Field(fieldName, value, Field.Store.YES, Field.Index.NOT_ANALYZED));
        globalIndexStrategy = Field.Index.NOT_ANALYZED;
      }
      case UNTOKENIZED_PREFIX -> {
        globalIndexStrategy = Field.Index.NOT_ANALYZED;
        value = PrefixFieldAnalyzerUtil.normalizePrefixField(value);
        for (String prefix : PrefixFieldAnalyzerUtil.createUntokenizedPrefixes(value)) {
          output.add(new Field(fieldName, prefix, Field.Store.NO, Field.Index.NOT_ANALYZED));
        }
        makeGlobalField = false;
      }
      case TOKENIZED_PREFIX -> {
        TokenStream stream = PrefixFieldAnalyzerUtil.getTokenizedPrefixTokenStreamForIndexing(
            new StringReader(value));
        output.add(new Field(fieldName, stream));
        makeGlobalField = false;
      }
      case DATE -> {
        NumericField dateField = new NumericField(fieldName, Field.Store.YES, true);
        // Store date as long value of days since Jan 1 1970
        try {
          long days = dateStringToLong(value) / MSEC_PER_DAY;
          value = Long.toString(days);
          dateField.setLongValue(days);
        } catch (ParseException e) {
          log.atWarning().log("Failed to parse date for %s: %s", fieldName, value);
          dateField.setLongValue(0L);
        }
        output.add(dateField);
        globalIndexStrategy = Field.Index.NOT_ANALYZED;
      }
      case NUMBER -> {
        // TODO: Lucene docs insist on reusing the same NumericFields across documents.
        NumericField numericField = new NumericField(fieldName, Field.Store.YES, true);
        numericField.setDoubleValue(stringValueToDouble(value));
        output.add(numericField);
        globalIndexStrategy = Field.Index.NOT_ANALYZED;
      }
      case GEO -> {
        output.add(new GeometricField(fieldName, fieldValue.getGeo()));
        makeGlobalField = false;
      }
      default ->
          throw new IllegalArgumentException("Field type " + fieldValue.getType() + " not handled");
    }

    if (makeGlobalField) {
      output.add(new Field(FIELDLESS_FIELD_NAME, value, Field.Store.NO, globalIndexStrategy));
    }
    return output;
  }

  public static Document toLuceneDocument(String docId, DocumentPb.Document input) {
    Document output = new Document();
    // Don't store the locale if the user didn't set it explicitly.
    if (input.hasLanguage()) {
      // Store the locale in an unindexed field
      output.add(
          new Field(LOCALE_FIELD_NAME, input.getLanguage(), Field.Store.YES, Field.Index.NO));
    }

    for (DocumentPb.Facet facet : input.getFacetList()) {
      output.add(convertFacetToLuceneField(facet));
    }

    for (DocumentPb.Field field : input.getFieldList()) {
      for (AbstractField luceneField : toLuceneFields(field)) {
        output.add(luceneField);
       }
    }

    // Special fields, added to each document. We add the following:
    //   a field that allows us to match all documents
    //   a field that keeps AppEngine document's docId
    //   a field that keeps the original AppEngine document
    //   a field that keeps an encoded map from field names to field types
    output.add(new Field(ALLDOCS_FIELD_NAME,
        ALLDOCS_FIELD_VALUE, Field.Store.NO, Field.Index.NOT_ANALYZED));
    output.add(new Field(DOCID_FIELD_NAME, docId,
        Field.Store.YES, Field.Index.NOT_ANALYZED));
    output.add(new Field(ORIGINAL_DOC_FIELD_NAME, input.toByteArray(),
        Field.Store.YES));
    output.add(new Field(ORDER_ID_FIELD_NAME, Integer.toString(input.getOrderId()),
        Field.Store.YES, Field.Index.NOT_ANALYZED));
    return output;
  }

  /**
   * Heuristically guesses whether the data in reader is in a CJK language.
   *
   * @param reader The data to tokenize.
   * @param readerContents The data in reader will be copied to readerContents
   * so that the caller can also use the data in reader.
   *
   * @return True if the text contains more than CJK_CHARACTER_THRESHOLD-percent Chinese, Japanese,
   * or Korean characters.
   *
   * @throws IOException if reading from the reader throws an IOException when read()-ing.
   */
  static boolean isProbablyCjk(Reader reader, StringBuilder readerContents) throws IOException {
    /* Buffer of size 1024 used because most documents (95%) are under 1kb in
     * size and thus will fit into the buffer with a single read. */
    char[] buffer = new char[1024];
    long cjkChars = 0L;
    long totalChars = 0L;

    /* Can't use reader.ready(), because it's possible that reader.ready() will return false when
     * there's still more data but it's not ready to be read yet (because of buffering or some other
     * source of asynchrony). reader.read() will block if necessary, and return -1 if there's no
     * more data to read, so we use that to control the loop instead. */
    while (true) {
      int len = reader.read(buffer);
      if (len < 0) {
        break;
      }
      totalChars += len;
      readerContents.append(buffer, 0, len);

      for (int i = 0; i < len; i++) {
        if (CJK_BLOCKS.contains(Character.UnicodeBlock.of(buffer[i]))) {
          cjkChars++;
        }
      }
    }

    return (float) cjkChars / totalChars > CJK_CHARACTER_THRESHOLD;
  }

  public static Long dateStringToLong(String value) throws ParseException {
    try {
      return Long.parseLong(value);
    } catch (IllegalArgumentException exception) {
      // TODO: delete support for passing ISO 8601 formatted strings when
      // have switched over to storing milliseconds.
      return ISO8601_SIMPLE.get().parse(value).getTime();
    }
  }

  /**
   * Checks whether provided string is an ISO-8601 date.
   */
  public static boolean isDateString(String value) {
    try {
      ISO8601_SIMPLE.get().parse(value);
      return true;
    } catch (ParseException e) {
      return false;
    }
  }

  public static double numericFieldToDouble(Fieldable f) {
    // Lucene returns Field instead of NumericField in getFields() call
    // for documents in index.
    if (f instanceof NumericField numericField) {
      return numericField.getNumericValue().doubleValue();
    } else {
      return Double.parseDouble(f.stringValue());
    }
  }

  public static Object luceneFieldToValue(Fieldable f, ContentType type) {
    return switch (type) {
      case TEXT, HTML, ATOM -> f.stringValue();
      case DATE -> {
        // Lucene returns Field instead of NumericField in getFields() call
        // for documents in index.
        long value;
        if (f instanceof NumericField numericField) {
          value = numericField.getNumericValue().longValue();
        } else {
          value = Long.parseLong(f.stringValue());
        }
        yield Long.toString(value);
      }
      case NUMBER -> {
        // Lucene returns Field instead of NumericField in getFields() call
        // for documents in index.
        if (f instanceof NumericField numericField) {
          yield Double.toString(numericField.getNumericValue().doubleValue());
        } else {
          yield f.stringValue();
        }
      }
      case GEO -> {
        String[] parts = ((Field) f).stringValue().split(",", 2);
        yield new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
      }
      default -> throw new IllegalArgumentException("Failed to correctly handle type " + type);
    };
  }

  static DocumentPb.Document.Builder toAppengineDocumentIdBuilder(Document d) {
    String docId = ((Field) d.getFieldable(DOCID_FIELD_NAME)).stringValue();
    DocumentPb.Document.Builder docBuilder = DocumentPb.Document.newBuilder();
    docBuilder.setId(docId);
    return docBuilder;
  }

  public static DocumentPb.Document toAppengineDocumentId(Document d) {
    return toAppengineDocumentIdBuilder(d).build();
  }

  static DocumentPb.Document.Builder toAppengineDocumentBuilder(Document d)
      throws InvalidProtocolBufferException {
    Fieldable doc = d.getFieldable(ORIGINAL_DOC_FIELD_NAME);
    if (doc == null) {
      return null;
    }
    DocumentPb.Document.Builder docBuilder = DocumentPb.Document.newBuilder();
    docBuilder.mergeFrom(doc.getBinaryValue(), doc.getBinaryOffset(), doc.getBinaryLength());
    return docBuilder;
  }

  public static DocumentPb.Document toAppengineDocument(Document d)
      throws InvalidProtocolBufferException {
    DocumentPb.Document.Builder docBuilder = toAppengineDocumentBuilder(d);
    if (docBuilder != null) {
      return docBuilder.build();
    } else {
      return null;
    }
  }

  public static Query getMatchAnyDocumentQuery() {
    return new TermQuery(new Term(ALLDOCS_FIELD_NAME, ALLDOCS_FIELD_VALUE));
  }

  public static Query getMatchNoneQuery() {
    return new TermQuery(new Term(ALLDOCS_FIELD_NAME, ALLDOCS_FIELD_MISSING_VALUE));
  }

  public static Term newDeleteTerm(String docId) {
    return new Term(DOCID_FIELD_NAME, docId);
  }
}
