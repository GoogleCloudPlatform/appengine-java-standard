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

/**
 * This package contains a framework for detecting backwards incompatible changes to the structure
 * of App Engine's Java API. We maintain classes that exercise every part of the APIs that are
 * accessible to our users in the 'usage' subpackage (see {@link AccessibleApiDetector} for our
 * definition of "accessible"). These classes all extend {@link ExhaustiveApiUsage}, and they serve
 * as an early warning system because they will stop compiling if we make a backwards incompatible
 * change. We also have the ability to compile these classes against version X of the App Engine
 * Java APIs and then run them against version X + 1, which is a more accurate test of what happens
 * in production when we push a new API jar.
 *
 * Note that this approach only allows us to detect backwards incompatible changes to aspects of the
 * API that are understood by the Java compiler and the JRE (field names and types, method names and
 * signatures, inheritance hierarchy). It does not help us detect backwards incompatible changes to
 * the semantics of our APIs (the meaning of a return value or a thrown exception, for example). It
 * also does not help us detect backwards incompatible changes involving methods that start throwing
 * new or different runtime exceptions, since the compiler cannot force calling code to handle
 * these.
 *
 * Now, this all works nicely so long as each concrete subclass of {@link ExhaustiveApiUsage} does
 * in fact make exhaustive use of the accessible API. In order to ensure this is the case, we have
 * the {@link ExhaustiveApiUsageVerifier}, which uses a custom classloader
 * ({@link UsageTrackingClassLoader}) to track the fields that are referenced and the
 * methods/constructors that are invoked from within each {@link ExhaustiveApiUsage}. It then
 * compares those fields, methods, and constructors against the fields, methods, and constructors
 * that are accessible on the API (determined via {@link AccessibleApiDetector}). The goal here is
 * to ensure that every backwards incompatible API change yields a compilation error, and every
 * forwards compatible API change (adding a new method for example) yields a test failure unless the
 * {@link ExhaustiveApiUsage} that corresponds to the changed API has been properly updated. Also,
 * by setting up appropriate perforce change notifications on the {@link ExhaustiveApiUsage} impls,
 * we will have an automated way for the API Committee to receive notifications of changes that they
 * should review.
 *
 * This is a work in progress.
 *
 * TODO(): Write usage impls for all our exposed apis (and finish the datastore impl). We could
 * generate this code but I'm inclined to just make people write it.
 *
 * TODO(): We need a test that ensures we have an {@link ExhaustiveApiUsage} impl for every
 * accessible class.
 *
 * TODO(): We need a test that verifies that every {@link ExhaustiveApiUsage} impl is indeed
 * exhaustive (right now the list of impls is hard-coded - one test per impl).
 *
 * TODO(): We need to implement the test that executes {@link ExhaustiveApiUsage} impls compiled
 * against version X against version X + 1 of the api jar.
 *
 * TODO(): We need to implement the test that takes the inheritanceSets reported by
 * {@link ExhaustiveApiUsage#useApi()} in version X and casts the api class to every class in that
 * set in version X + 1.
 */
package com.google.appengine.apicompat;
