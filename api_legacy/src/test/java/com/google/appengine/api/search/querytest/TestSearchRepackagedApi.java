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

package com.google.appengine.api.search.querytest;

import com.google.appengine.api.search.query.QueryTreeBuilder;
import com.google.appengine.repackaged.org.antlr.runtime.RecognitionException;

public class TestSearchRepackagedApi {

  // Just need to see if this code compiles with the repackaged class. If not, this is an API
  // breakage, it was a mistake, but we cannot revert it so far.
  public void testSearchQueryApiIsWorking() throws RecognitionException {
    QueryTreeBuilder builder = new QueryTreeBuilder();
    // This method returns a repackaged class, so calling it ensures that we are handling the
    // repackaging correctly. Internal bug b/111683348
    builder.parse("random query string");
  }
}
