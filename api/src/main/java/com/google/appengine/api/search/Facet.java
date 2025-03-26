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

import com.google.appengine.api.search.checkers.FacetChecker;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FacetValue;
import com.google.apphosting.api.search.DocumentPb.FacetValue.ContentType;
import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A {@code Facet} can be used to categorize a {@link Document}. It is not a {@link Field}.
 * <p>
 * Search results can contain facets for the extended result set and their value frequency.
 * For example, if a search query is related to "wine", then facets could be "color" with values
 * of "red" and "white", and "year" with values of "2000" and "2005".
 * <p>
 * Each facet has a name and exactly one value: atom or number. Facet name lengths are between 1 and
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_NAME_LENGTH} characters,
 * and atoms are
 * limited to {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_ATOM_LENGTH}
 * characters. Numbers must be between
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MINIMUM_NUMBER_VALUE} and
 * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_NUMBER_VALUE}.
 */
public final class Facet implements Serializable {

  /**
   * Creates and returns an atom facet with the given {@code name} and {@code value}.
   *
   * @return an instance of {@link Facet}.
   * @throws IllegalArgumentException if the facet name or value are invalid.
   */
  public static Facet withAtom(String name, String value) {
    return new Facet(name, value, null);
  }

  /**
   * Creates and returns a number facet with the given {@code name} and {@code value}.
   *
   * @return an instance of {@link Facet}.
   * @throws IllegalArgumentException if the facet name or value are invalid.
   */
  public static Facet withNumber(String name, Double value) {
    return new Facet(name, null, value);
  }

  private static final long serialVersionUID = 3297968709406212335L;

  // Mandatory
  private final String name;

  // Optional
  @Nullable private String atom;
  private Double number;

  private Facet(String name, String atom, Double number) {
    this.name = name;
    this.atom = atom;
    this.number = number;
    checkValid();
  }

  /**
   * Returns the name of the facet.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the atomic value of the facet. Returns null if the value is not atomic.
   */
  public String getAtom() {
    return atom;
  }

  /**
   * Returns the numeric value of the facet. Returns null if the value is not numeric.
   */
  public Double getNumber() {
    return number;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, atom, number);
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof Facet)) {
      return false;
    }
    Facet facet = (Facet) object;
    return Util.equalObjects(name, facet.name)
        && Util.equalObjects(atom, facet.atom)
        && Util.equalObjects(number, facet.number);
  }

  /**
   * Checks whether the facet is valid, specifically,
   * whether the facet name, value are valid.
   * Also that at most one value: atom or number is set.
   *
   * @throws IllegalArgumentException if facet name, atom, or number is invalid
   */
  private void checkValid() {
    FacetChecker.checkFacetName(name);
    if (atom != null) {
      if (number != null) {
        throw new IllegalArgumentException(
            String.format("both atom and number are set for facet %s", name));
      }
      FacetChecker.checkAtom(atom);
    } else if (number != null) {
      FacetChecker.checkNumber(number);
    } else {
      throw new IllegalArgumentException(
          String.format("neither atom nor number is set for facet %s", name));
    }
  }

  /**
   * Creates and returns a facet reflecting a {@link DocumentPb.Facet} protocol message.
   *
   * @throws SearchException if the facet contains an invalid name, atom or number.
   */
  static Facet withProtoMessage(DocumentPb.Facet facet) {
    FacetValue value = facet.getValue();
    switch (value.getType()) {
      case ATOM:
        try {
        return withAtom(facet.getName(), value.getStringValue());
        } catch (IllegalArgumentException e) {
          throw new SearchException(
              String.format("Failed to create facet %s from protocol message: %s", facet.getName(),
              e.getMessage()));
        }
      case NUMBER:
        try {
          return withNumber(facet.getName(), Facet.stringToNumber(value.getStringValue()));
        } catch (NumberFormatException e) {
          throw new SearchException("Failed to parse double: " + value.getStringValue());
        } catch (IllegalArgumentException e) {
          throw new SearchException(
              String.format("Failed to create facet %s from protocol message: %s", facet.getName(),
              e.getMessage()));
        }
      default:
        throw new SearchException(
            String.format("unknown facet type %s for facet %s",
                String.valueOf(value.getType()), facet.getName()));
    }
  }


  /**
   * Copies a {@link Facet} object into a {@link com.google.apphosting.api.search.DocumentPb.Facet}
   * protocol buffer.
   *
   * @return the facet protocol buffer copy of this facet object
   */
  DocumentPb.Facet copyToProtocolBuffer() {
    DocumentPb.FacetValue.Builder facetValueBuilder = DocumentPb.FacetValue.newBuilder();
    if (atom != null) {
      facetValueBuilder.setType(ContentType.ATOM);
      facetValueBuilder.setStringValue(atom);
    } else if (number != null) {
      facetValueBuilder.setType(ContentType.NUMBER);
      facetValueBuilder.setStringValue(numberToString(number));
    }
    DocumentPb.Facet.Builder builder = DocumentPb.Facet.newBuilder()
        .setName(name)
        .setValue(facetValueBuilder);
    return builder.build();
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("Facet")
        .addField("name", name)
        .addField("atom", atom)
        .addField("number", number)
        .finish();
  }

  /**
   * Converts a double number into a string acceptable by faceting backend as a number.
   *
   * @throws IllegalArgumentException if the {@code number} is NaN.
   */
  static String numberToString(double number) {
    Preconditions.checkArgument(!Double.isNaN(number), "should be a number.");
    return Double.toString(number);
  }

  /**
   * Converts a string with faceting backend format into double number.
   *
   * @throws NullPointerException if the string is null
   * @throws NumberFormatException if the string does not contain a parsable double.
   */
  static Double stringToNumber(String string) {
    return Double.parseDouble(string);
  }
}
