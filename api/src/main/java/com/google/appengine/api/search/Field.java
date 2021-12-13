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

import com.google.appengine.api.internal.ImmutableCopy;
import com.google.appengine.api.search.checkers.FieldChecker;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a field of a {@link Document}, which is a name, an optional locale, and at most one
 * value: text, HTML, atom, date, GeoPoint, untokenizedPrefix, tokenizedPrefix or vector. Field name
 * lengths are between 1 and
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_NAME_LENGTH} characters,
 * and text and HTML values are limited to
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_TEXT_LENGTH}. Atoms are
 * limited to {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_ATOM_LENGTH}
 * characters, both prefix types are limited to
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_PREFIX_LENGTH}
 * . Vector field size is limited to
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#VECTOR_FIELD_MAX_SIZE}
 * and dates must not have a time component.
 *
 * <p>
 * There are 5 types of text fields, ATOM, TEXT, HTML, UNTOKENIZED_PREFIX, and TOKENIZED_PREFIX.
 * Atom fields when queried, are checked for equality. For example, if you add a field with name
 * {@code code} and an ATOM value of "928A 33B-1", then query {@code code:"928A 33B-1"} would match
 * the document with this field, while query {@code code:928A} would not. TEXT fields, unlike ATOM,
 * match both on equality or if any token extracted from the original field matches. Thus if
 * {@code code} field had the value set with {@link Field.Builder#setText(String)} method, both
 * queries would match. HTML fields have HTML tags stripped before tokenization. Untokenized prefix
 * fields match queries that are prefixes containing the contiguous starting characters of the whole
 * field. For example if the field was "the quick brown fox", the query "the qui" would match
 * whereas "th qui" would not. On the other hand, Tokenized prefix fields match if the query terms
 * are prefixes of individual terms in the field. If the query is a phrase of terms, the ordering of
 * the terms will matter. For example if the field is "the quick brown fox", the query "th qui bro"
 * would match whereas "bro qui the" would not. Vector fields are only used to compute the dot
 * product between a given constant vector and the provided vector field for sorting and field
 * expressions only. for example, if a 3d vector is named "scores" and has a value of (1,2,3) then
 * the expression {@code dot(scores, vector(3,2,1))} will be evaluated to 10.
 */
public final class Field implements Serializable {
  /**
   * A field builder. Fields must have a name, and optionally a locale
   * and at most one of text, html, atom or date.
   */
  public static final class Builder {
    // Mandatory
    private String name;

    // Optional
    @Nullable private Locale locale;

    // At most one of the following values specified.
    @Nullable private FieldType type;
    @Nullable private String text;
    @Nullable private String html;
    @Nullable private String atom;
    @Nullable private Date date;
    @Nullable private Double number;
    @Nullable private GeoPoint geoPoint;
    @Nullable private String untokenizedPrefix;
    @Nullable private String tokenizedPrefix;
    private List<Double> vector = Collections.emptyList();

    /**
     * Constructs a field builder.
     */
    private Builder() {
    }

    /**
     * Sets a name for the field. The field name length must be
     * between 1 and {@literal FieldChecker#MAXIMUM_NAME_LENGTH} and it should match
     * {@link com.google.appengine.api.search.checkers.SearchApiLimits#FIELD_NAME_PATTERN}.
     *
     * @param name the name of the field
     * @return this builder
     * @throws IllegalArgumentException if the name or value is invalid
     */
    public Builder setName(String name) {
      this.name = FieldChecker.checkFieldName(name);
      return this;
    }

    /**
     * Sets a text value for the field.
     *
     * @param text the text value of the field
     * @return this builder
     * @throws IllegalArgumentException if the text is invalid
     */
    public Builder setText(String text) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      this.type = FieldType.TEXT;
      this.text = FieldChecker.checkText(text);
      return this;
    }

    /**
     * Sets a HTML value for the field.
     *
     * @param html the HTML value of the field
     * @return this builder
     * @throws IllegalArgumentException if the HTML is invalid
     */
    public Builder setHTML(String html) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      this.type = FieldType.HTML;
      this.html = FieldChecker.checkHTML(html);
      return this;
    }

    /**
     * Sets an atomic value, indivisible text, for the field.
     *
     * @param atom the indivisible text of the field
     * @return this builder
     * @throws IllegalArgumentException if the atom is invalid
     */
    public Builder setAtom(String atom) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      this.type = FieldType.ATOM;
      this.atom = FieldChecker.checkAtom(atom);
      return this;
    }

    /**
     * Sets a date associated with the field.
     *
     * @param date the date of the field
     * @return this builder
     * @throws IllegalArgumentException if the date is out of range
     */
    public Builder setDate(Date date) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      Preconditions.checkArgument(date != null, "Cannot set date field to null.");
      this.type = FieldType.DATE;
      this.date = FieldChecker.checkDate(date);
      return this;
    }

    /**
     * Sets a numeric value for the field. The {@code number} must be between
     * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MINIMUM_NUMBER_VALUE} and
     * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_NUMBER_VALUE}.
     *
     * @param number the numeric value of the field
     * @return this builder
     * @throws IllegalArgumentException if the number is outside the valid range
     */
    public Builder setNumber(double number) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      this.type = FieldType.NUMBER;
      this.number = FieldChecker.checkNumber(Double.valueOf(number));
      return this;
    }

    /**
     * Sets a {@link GeoPoint} value for the field.
     *
     * @param geoPoint the {@link GeoPoint} value of the field
     * @return this builder
     */
    public Builder setGeoPoint(GeoPoint geoPoint) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      Preconditions.checkArgument(geoPoint != null, "Cannot set geo field to null.");
      this.type = FieldType.GEO_POINT;
      this.geoPoint = geoPoint;
      return this;
    }

    /**
     * Sets an untokenized prefix value for the field.
     *
     * @param untokenizedPrefix the string value of the field
     * @return this builder
     * @throws IllegalArgumentException if the untokenized prefix field is invalid
     */
    public Builder setUntokenizedPrefix(String untokenizedPrefix) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      this.type = FieldType.UNTOKENIZED_PREFIX;
      this.untokenizedPrefix = FieldChecker.checkPrefix(untokenizedPrefix);
      return this;
    }

    /**
     * Sets a tokenized prefix value for the field.
     *
     * @param tokenizedPrefix the string value of the field
     * @return this builder
     * @throws IllegalArgumentException if the tokenized prefix field is invalid
     */
    public Builder setTokenizedPrefix(String tokenizedPrefix) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      this.type = FieldType.TOKENIZED_PREFIX;
      this.tokenizedPrefix = FieldChecker.checkPrefix(tokenizedPrefix);
      return this;
    }

    /**
     * Sets a vector value for the field.
     *
     * @param vector a list of Double values forming a vector field value
     * @return this builder
     * @throws IllegalArgumentException if the vector field is invalid
     */
    public Builder setVector(List<Double> vector) {
      Preconditions.checkArgument(type == null, "Field value must not be already set");
      Preconditions.checkArgument(vector != null, "Cannot set vector field to null");
      List<Double> v = ImmutableCopy.list(vector);
      FieldChecker.checkVector(v);
      this.type = FieldType.VECTOR;
      this.vector = v;
      return this;
    }

    /**
     * Sets the Locale of the field value. If none is given, then the locale
     * of the document will be used.
     *
     * @param locale the locale the field value is written in
     * @return this builder
     */
    public Builder setLocale(Locale locale) {
      this.locale = locale;
      return this;
    }

    /**
     * Builds a field using this builder. The field must have a
     * valid name, string value, type.
     *
     * @return a {@link Field} built by this builder
     * @throws IllegalArgumentException if the field has an invalid
     * name, text, HTML, atom, date
     */
    public Field build() {
      return new Field(this);
    }
  }

  /**
   * The type of the field value.
   */
  public enum FieldType {
    /**
     * Text content.
     */
    TEXT,
    /**
     * HTML content.
     */
    HTML,
    /**
     * An indivisible text content.
     */
    ATOM,
    /**
     * A Date with no time component.
     */
    DATE,

    // TODO: use self descriptive DOUBLE as a name.
    /**
     * Double precision floating-point number.
     */
    NUMBER,
    /**
     * Geographical coordinates of a point, in WGS84.
     */
    GEO_POINT,
    /**
     * Untokenized prefix field content.
     */
    UNTOKENIZED_PREFIX,
    /**
     * Tokenized prefix field content.
     */
    TOKENIZED_PREFIX,
    /**
     * Vector field content.
     */
     VECTOR,
  }

  private static final long serialVersionUID = 6829483617830682721L;

  // Mandatory
  private final String name;

  // Optional
  @Nullable private final Locale locale;
  @Nullable private final FieldType type;
  @Nullable private String text;
  @Nullable private String html;
  @Nullable private String atom;
  @Nullable private Date date;
  @Nullable private Double number;
  @Nullable private GeoPoint geoPoint;
  @Nullable private String untokenizedPrefix;
  @Nullable private String tokenizedPrefix;
  private List<Double> vector = Collections.<Double>emptyList();

  /**
   * Constructs a field using the builder.
   *
   * @param builder a builder used to construct the Field
   */
  private Field(Builder builder) {
    name = builder.name;
    type = builder.type;
    if (builder.type != null) {
      switch (builder.type) {
        case TEXT:
          text = builder.text;
          break;
        case HTML:
          html = builder.html;
          break;
        case ATOM:
          atom = builder.atom;
          break;
        case DATE:
          date = builder.date;
          break;
        case NUMBER:
          number = builder.number;
          break;
        case GEO_POINT:
          geoPoint = builder.geoPoint;
          break;
        case UNTOKENIZED_PREFIX:
          untokenizedPrefix = builder.untokenizedPrefix;
          break;
        case TOKENIZED_PREFIX:
          tokenizedPrefix = builder.tokenizedPrefix;
          break;
        case VECTOR:
          vector = builder.vector;
          break;
        default:
          throw new IllegalArgumentException(String.format("Unknown field type given %s",
                                                           builder.type));
      }
    }
    locale = builder.locale;
    checkValid();
  }

  /**
   * @return the name of the field
   */
  public String getName() {
    return name;
  }

  /**
   * @return the type of value of the field. Can be null
   */
  public FieldType getType() {
    return type;
  }

  /**
   * @return the text value of the field. Can be null
   */
  public String getText() {
    return text;
  }

  /**
   * @return the HTML value of the field. Can be null
   */
  public String getHTML() {
    return html;
  }

  /**
   * @return the atomic value of the field. Can be null
   */
  public String getAtom() {
    return atom;
  }

  /**
   * @return the date value of the field. Can be null
   */
  public Date getDate() {
    return date;
  }

  /**
   * @return the numeric value of the field. Can be null
   */
  public Double getNumber() {
    return number;
  }

  /**
   * @return the {@link GeoPoint} value of the field. Can be null
   */
  public GeoPoint getGeoPoint() {
    return geoPoint;
  }

  /**
   * @return the String value of the untokenized prefix field. Can be null
   */
  public String getUntokenizedPrefix() {
    return untokenizedPrefix;
  }

  /**
   * @return the String value of the tokenized prefix field. Can be null
   */
  public String getTokenizedPrefix() {
    return tokenizedPrefix;
  }

  /**
   * @return the vector value of the field.
   */
  public List<Double> getVector() {
    return vector;
  }

  /**
   * @return the locale the field value is written in. Can be null. If none
   * is given the locale of the document will be used
   */
  public Locale getLocale() {
    return locale;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof Field)) {
      return false;
    }
    Field field = (Field) object;
    return Util.equalObjects(name, field.name);
  }

  /**
   * Checks whether the field is valid, specifically,
   * whether the field name, value are valid.
   * Also that at most one value: text, HTML, atom, date, untokenizedPrefix, tokenizedPrefix, or
   * vector is set.
   *
   * @return this Field
   * @throws IllegalArgumentException if field name, text, HTML, atom,
   * date, untokenizedPrefix, tokenizedPrefix, or vector are invalid
   */
  private Field checkValid() {
    FieldChecker.checkFieldName(name);
    if (type != null) {
      switch (type) {
        case TEXT:
          FieldChecker.checkText(text);
          break;
        case HTML:
          FieldChecker.checkHTML(html);
          break;
        case ATOM:
          FieldChecker.checkAtom(atom);
          break;
        case DATE:
          FieldChecker.checkDate(date);
          break;
        case NUMBER:
        case GEO_POINT:
          break;
        case UNTOKENIZED_PREFIX:
          FieldChecker.checkPrefix(untokenizedPrefix);
          break;
        case TOKENIZED_PREFIX:
          FieldChecker.checkPrefix(tokenizedPrefix);
          break;
        case VECTOR:
          FieldChecker.checkVector(vector);
          break;
        default:
          throw new IllegalArgumentException(String.format("unknown field type %s", type));
      }
    }
    return this;
  }

  /**
   * Creates a field builder.
   *
   * @return a new builder for creating fields
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a builder of a field from the given field.
   *
   * @param field the field protocol buffer used to create the builder
   * @return a field builder created from the given field
   * @throws SearchException if the field contains invalid name, text, html,
   * atom, date
   */
  static Builder newBuilder(DocumentPb.Field field) {
    FieldValue value = field.getValue();
    Field.Builder fieldBuilder =
        Field.newBuilder().setName(field.getName());
    if (value.hasLanguage()) {
      fieldBuilder.setLocale(FieldChecker.parseLocale(value.getLanguage()));
    }
    switch (value.getType()) {
      case TEXT:
        fieldBuilder.setText(value.getStringValue());
        break;
      case HTML:
        fieldBuilder.setHTML(value.getStringValue());
        break;
      case ATOM:
        fieldBuilder.setAtom(value.getStringValue());
        break;
      case NUMBER:
        try {
          fieldBuilder.setNumber(
              NumberFormat.getNumberInstance().parse(value.getStringValue()).doubleValue());
        } catch (ParseException e) {
          throw new SearchException("Failed to parse double: " + value.getStringValue());
        }
        break;
      case GEO:
        fieldBuilder.setGeoPoint(GeoPoint.newGeoPoint(value.getGeo()));
        break;
      case DATE:
        String dateString = value.getStringValue();
        if (dateString.isEmpty()) {
          throw new SearchException(
              String.format("date not specified for field %s", field.getName()));
        }
        fieldBuilder.setDate(DateUtil.deserializeDate(dateString));
        break;
      case UNTOKENIZED_PREFIX:
        fieldBuilder.setUntokenizedPrefix(value.getStringValue());
        break;
      case TOKENIZED_PREFIX:
        fieldBuilder.setTokenizedPrefix(value.getStringValue());
        break;
      case VECTOR:
        fieldBuilder.setVector(value.getVectorValueList());
        break;
      default:
        throw new SearchException(
            String.format("unknown field value type %s for field %s", value.getType(),
                field.getName()));
    }
    return fieldBuilder;
  }

  /**
   * Copies a {@link Field} object into a {@link com.google.apphosting.api.search.DocumentPb.Field}
   * protocol buffer.
   *
   * @return the field protocol buffer copy of this field object
   * @throws IllegalArgumentException if the field value type is unknown
   */
  DocumentPb.Field copyToProtocolBuffer() {
    DocumentPb.FieldValue.Builder fieldValueBuilder = DocumentPb.FieldValue.newBuilder();
    if (locale != null) {
      fieldValueBuilder.setLanguage(locale.toString());
    }
    if (type != null) {
      switch (type) {
        case TEXT:
          if (text != null) {
            fieldValueBuilder.setStringValue(text);
          }
          fieldValueBuilder.setType(ContentType.TEXT);
          break;
        case HTML:
          if (html != null) {
            fieldValueBuilder.setStringValue(html);
          }
          fieldValueBuilder.setType(ContentType.HTML);
          break;
        case ATOM:
          if (atom != null) {
            fieldValueBuilder.setStringValue(atom);
          }
          fieldValueBuilder.setType(ContentType.ATOM);
          break;
        case DATE:
          fieldValueBuilder.setStringValue(DateUtil.serializeDate(date));
          fieldValueBuilder.setType(ContentType.DATE);
          break;
        case NUMBER:
          // TODO: use binary number representation instead
          DecimalFormat format = new DecimalFormat();
          format.setDecimalSeparatorAlwaysShown(false);
          format.setGroupingUsed(false);
          format.setMaximumFractionDigits(Integer.MAX_VALUE);
          fieldValueBuilder.setStringValue(format.format(number));
          fieldValueBuilder.setType(ContentType.NUMBER);
          break;
        case GEO_POINT:
          fieldValueBuilder.setGeo(geoPoint.copyToProtocolBuffer());
          fieldValueBuilder.setType(ContentType.GEO);
          break;
        case UNTOKENIZED_PREFIX:
          if (untokenizedPrefix != null) {
            fieldValueBuilder.setStringValue(untokenizedPrefix);
          }
          fieldValueBuilder.setType(ContentType.UNTOKENIZED_PREFIX);
          break;
        case TOKENIZED_PREFIX:
          if (tokenizedPrefix != null) {
            fieldValueBuilder.setStringValue(tokenizedPrefix);
          }
          fieldValueBuilder.setType(ContentType.TOKENIZED_PREFIX);
          break;
        case VECTOR:
          fieldValueBuilder.addAllVectorValue(vector);
          fieldValueBuilder.setType(ContentType.VECTOR);
          break;
        default:
          throw new IllegalArgumentException(String.format("unknown field type %s", type));
      }
    }

    DocumentPb.Field.Builder builder = DocumentPb.Field.newBuilder()
        .setName(name)
        .setValue(fieldValueBuilder);
    return builder.build();
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("Field")
        .addField("name", name)
        .addField("value", valueToString())
        .addField("type", String.valueOf(type))
        .addField("locale", locale)
        .finish();
  }

  private String valueToString() throws IllegalArgumentException {
    if (type == null) {
      return "null";
    }
    switch (type) {
      case TEXT:
        return text;
      case HTML:
        return html;
      case ATOM:
        return atom;
      case DATE:
        return DateUtil.formatDateTime(date);
      case GEO_POINT:
        return geoPoint.toString();
      case NUMBER:
        DecimalFormat format = new DecimalFormat();
        format.setDecimalSeparatorAlwaysShown(false);
        format.setMaximumFractionDigits(Integer.MAX_VALUE);
        return format.format(number);
      case UNTOKENIZED_PREFIX:
        return untokenizedPrefix;
      case TOKENIZED_PREFIX:
        return tokenizedPrefix;
      case VECTOR:
        return vector.toString();
      default:
        throw new IllegalArgumentException(String.format("unknown field type %s", type));
    }
  }
}
