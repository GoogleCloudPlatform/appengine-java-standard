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

import com.google.appengine.api.search.checkers.FieldChecker;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Represents a document which may have been scored, possibly
 * some computed expression fields, and a cursor to continue
 * the search from.
 */
public final class ScoredDocument extends Document implements Serializable {

  private static final long serialVersionUID = 3062767930105951671L;

  /**
   * A builder of scored documents. This is not thread-safe.
   */
  public static final class Builder extends Document.Builder {
    // Mandatory
    private final List<Double> scores = new ArrayList<Double>();
    private final List<Field> expressions = new ArrayList<Field>();

    // Optional
    @Nullable private Cursor cursor;

    /**
     * Constructs a builder for a scored document.
     */
    private Builder() {
    }

    /**
     * Sets the cursor to the next set of results from search.
     *
     * @param cursor the {@link Cursor} for the next set of results
     * from search
     * @return this builder
     */
    public Builder setCursor(Cursor cursor) {
      this.cursor = cursor;
      return this;
    }

    /**
     * Adds the score to the builder.
     *
     * @param score the score to add
     * @return this builder
     */
    public Builder addScore(double score) {
      scores.add(score);
      return this;
    }

    /**
     * Adds the expression to the builder.
     *
     * @param expression the expression field to add
     * @return this builder
     * @throws IllegalArgumentException if the expression field is invalid
     */
    public Builder addExpression(Field expression) {
      Preconditions.checkNotNull(expression, "expression cannot be null");
      expressions.add(expression);
      return this;
    }

    /**
     * Builds a valid document. The builder must have set a valid document
     * id, and have a non-empty set of valid fields.
     *
     * @return the scored document built by this builder
     * @throws IllegalArgumentException if the scored document built is
     * not valid
     */
    @Override
    public ScoredDocument build() {
      return new ScoredDocument(this);
    }
  }

  // Mandatory
  private final List<Double> scores;
  private final List<Field> expressions;

  // Optional
  @Nullable private final Cursor cursor;

  /**
   * Constructs a scored document with the given builder.
   *
   * @param builder the builder capable of building a document
   */
  private ScoredDocument(Builder builder) {
    super(builder);
    scores = Collections.unmodifiableList(builder.scores);
    expressions = Collections.unmodifiableList(builder.expressions);
    cursor = builder.cursor;
  }

  /**
   * Deprecated method to retrieve sort scores.
   *
   * <p>The right way to retrieve a score is to use {@code _score} in a {@link FieldExpression}.
   *
   * @return a list containing the score, if one was used.
   * @deprecated Use an explicit {@link FieldExpression} in your {@link QueryOptions} instead.
   */
  @Deprecated
  public List<Double> getSortScores() {
    return scores;
  }

  /**
   * The list of Field which are the result of any extra expressions
   * requested. For example, if a request contains fields to snippet or
   * {@link FieldExpression FieldExpressions} which are named snippet
   * expressions, then the returned expression will be a Field with the
   * name specified in the request and HTML value set to the snippet.
   *
   * @return the list of Field which are the result of extra expressions
   * requested.
   */
  public List<Field> getExpressions() {
    return expressions;
  }

  /**
   * A {@link Cursor} to be used continuing search after this search
   * result. For this field to be populated, use
   * {@link QueryOptions.Builder#setCursor(Cursor)}, where the cursor is
   * created by {@code Cursor.newBuilder().setPerResult(true).build()}.
   * Otherwise {@link #getCursor}  will return null.
   *
   * @return a cursor used for issuing a subsequent search that
   * will return elements beginning after this result. Can be null
   */
  public Cursor getCursor() {
    return cursor;
  }

  public static ScoredDocument.Builder newBuilder() {
    return new ScoredDocument.Builder();
  }

  /**
   * Creates a new scored document builder from the given document. All
   * document properties are copied to the returned builder.
   *
   * @param document the document protocol buffer to build a scored document
   * object from
   * @return the scored document builder initialized from a document protocol
   * buffer
   * @throws SearchException if a field value is invalid
   */
  static ScoredDocument.Builder newBuilder(DocumentPb.Document document) {
    ScoredDocument.Builder docBuilder = ScoredDocument.newBuilder();
    docBuilder.setId(document.getId());
    if (document.hasLanguage()) {
      docBuilder.setLocale(FieldChecker.parseLocale(document.getLanguage()));
    }
    for (DocumentPb.Field field : document.getFieldList()) {
      docBuilder.addField(Field.newBuilder(field));
    }
    if (document.hasOrderId()) {
      docBuilder.setRank(document.getOrderId());
    }
    return docBuilder;
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("ScoredDocument")
        .addField("documentId", getId())
        .addIterableField("fields", getFields(), MAX_FIELDS_TO_STRING)
        .addField("locale", getLocale())
        .addField("rank", getRank())
        .addIterableField("scores", scores)
        .addIterableField("expressions", expressions)
        .addField("cursor", cursor)
        .finish();
  }
}
