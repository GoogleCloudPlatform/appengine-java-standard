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

import org.antlr.runtime.tree.Tree;

/**
 * Defines an interface of the visitor invoked by the walker.
 *
 * @param <T> the type of the context object accompanying visited tree nodes.
 *
 */
public interface QueryTreeVisitor<T extends QueryTreeContext<T>> {

  /**
   * Visits a node that represents a sequence of expressions.
   */
  void visitSequence(Tree node, T context);

  /**
   * Visits a node that represents a conjunction (logical and) of conditions.
   */
  void visitConjunction(Tree node, T context);

  /**
   * Visits a node that represents a disjunction (logical or) of conditions.
   */
  void visitDisjunction(Tree node, T context);

  /**
   * Visits a node that represents a negation of conditions.
   */
  void visitNegation(Tree node, T context);

  /**
   * Visits a node that represents a text field that is subject to query rewrite.
   */
  void visitFuzzy(Tree node, T context);

  /**
   * Visits a node that represents a text field that must not be altered.
   */
  void visitLiteral(Tree node, T context);

  /**
   * Visits a node that represents that a field value must be less than some
   * specified value.
   */
  void visitLessThan(Tree node, T context);

  /**
   * Visits a node that represents that a field value must be less than or
   * equal to some specified value.
   */
  void visitLessOrEqual(Tree node, T context);

  /**
   * Visits a node that represents that a field value must be greater than some
   * specified value.
   */
  void visitGreaterThan(Tree node, T context);

  /**
   * Visits a node that represents an inequality between a field and value.
   */
  void visitGreaterOrEqual(Tree node, T context);

  /**
   * Visits a node that represents that a field value must be greater than
   * or equal to some specified value.
   */
  void visitEqual(Tree node, T context);

  /**
   * Visits a node that represents that a field must contain a value.
   */
  void visitContains(Tree node, T context);

  /**
   * Visits a node that represents a constant value.
   */
  void visitValue(Tree node, T context);

  /**
   * Visits a node that represents a function computed on some arguments.
   */
  void visitFunction(Tree node, T context);

  /**
   * Visits a node that represents a global field.
   */
  void visitGlobal(Tree node, T context);

  /**
   * Catch-all method for future type of query nodes.
   */
  void visitOther(Tree node, T context);
}
