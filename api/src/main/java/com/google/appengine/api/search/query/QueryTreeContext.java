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

package com.google.appengine.api.search.query;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The base class for specific query tree context used by the walker.
 * This class is used to maintain additional information gathered while
 * walking the tree. On this level it is used to collect return type
 * information.
 *
 *
 * @param <T> the actual class used by specific tree visitors
 */
public abstract class QueryTreeContext<T extends QueryTreeContext<T>> {

  /**
   * Enumeration of supported return types.
   */
  public enum Type {
    BOOL, TEXT, NUMBER, DATE, LOCATION, DISTANCE;
  }

  /**
   * Enumeration of text terms rewrite mode.
   */
  public enum RewriteMode {
    STRICT, FUZZY
  }

  /**
   * Enumeration of the kind of the term that has a given return
   * type. This enum makes more precise the return type. For example,
   * if the return type is TEXT, and the Kind is PHRASE, this means
   * that the caller supplied "..." as the query term.
   */
  public enum Kind {
    VOID, LITERAL, PHRASE, FIELD, FUNCTION, EXPRESSION
  }

  private final List<T> children;
  private RewriteMode rewriteMode;
  private Set<Type> returnTypes;
  private Kind kind;
  private String text;
  private boolean inDisjunction;

  protected QueryTreeContext() {
    children = new ArrayList<T>();
    rewriteMode = null;
    returnTypes = EnumSet.noneOf(Type.class);
    kind = Kind.VOID;
    inDisjunction = false;
  }

  /**
   * @return a child context for this context
   */
  public T addChild() {
    T childContext = newChildContext();
    if (inDisjunction) {
      childContext.setInDisjunction(true);
    }
    children.add(childContext);
    return childContext;
  }

  /**
   * @return a new child of type T
   */
  protected abstract T newChildContext();

  /**
   * @return iterable over all children contexts
   */
  public Iterable<T> children() {
    return children;
  }

  /**
   * @return the number of children contexts
   */
  public int getChildCount() {
    return children.size();
  }

  /**
   * @param index the index of the child to get
   * @return the child context at the given index
   */
  public T getChild(int index) {
    return children.get(index);
  }

  /**
   * @param type additional type to be added to current return types
   */
  public void addReturnType(Type type) {
    returnTypes.add(type);
  }

  /**
   * @param type the unique return type for this context
   */
  public void setReturnType(Type type) {
    returnTypes = EnumSet.of(type);
  }

  /**
   * @param type a set of types to be set as the only return types
   */
  public void setReturnTypes(Set<Type> type) {
    returnTypes = EnumSet.copyOf(type);
  }

  public void setInDisjunction(boolean inDisjunction) {
    this.inDisjunction = inDisjunction;
  }

  /**
   * @return the set of return types
   */
  public Set<Type> getReturnTypes() {
    return EnumSet.copyOf(returnTypes);
  }

  /**
   * @param other the other context whose types are to be inspected
   * @return a set of types common to this and the other context
   */
  public Set<Type> getCommonReturnTypes(T other) {
    Set<Type> common = getReturnTypes();
    common.retainAll(other.getReturnTypes());
    return common;
  }

  /**
   * @param returnType the type to be checked against types of this context
   * @return whether or not it is compatible with at least one type
   */
  public boolean isCompatibleWith(Type returnType) {
    if (returnType == null) {
      return false;
    }
    Set<Type> set = EnumSet.of(returnType);
    set.retainAll(returnTypes);
    return !set.isEmpty();
  }

  /**
   * @param mode the rewrite mode for the value represented by this context
   */
  public void setRewriteMode(RewriteMode mode) {
    this.rewriteMode = mode;
  }

  /**
   * @return whether or not the value associated with this context is rewritable
   */
  public boolean isFuzzy() {
    return RewriteMode.FUZZY.equals(rewriteMode);
  }

  /**
   * @return whether or not the value associated with this context must
   * not be rewritten
   */
  public boolean isStrict() {
    return RewriteMode.STRICT.equals(rewriteMode);
  }

  /**
   * @param kind the kind of the
   */
  public void setKind(Kind kind) {
    this.kind = kind;
  }

  /**
   * @return whether or not this context represents a phrase (quoted text)
   */
  public boolean isPhrase() {
    return Kind.PHRASE.equals(kind);
  }

  /**
   * @return if this context represents a function
   */
  public boolean isFunction() {
    return Kind.FUNCTION.equals(kind);
  }

  /**
   * @return if this context represents a literal constant
   */
  public boolean isLiteral() {
    return Kind.LITERAL.equals(kind);
  }

  /**
   * @return returns if this context represents a field of a document
   */
  public boolean isField() {
    return Kind.FIELD.equals(kind);
  }

  /**
   * @return true if this context's lowest ancester compounding operator (AND, OR) is a disjunction.
   */
  public boolean inDisjunction() {
    return inDisjunction;
  }

  /**
   * @return sets the text associated with this context
   */
  public String getText() {
    return text;
  }

  /**
   * @param text returns the text associated with this context
   */
  public void setText(String text) {
    this.text = text;
  }

  @Override
  public String toString() {
    return text;
  }
}
