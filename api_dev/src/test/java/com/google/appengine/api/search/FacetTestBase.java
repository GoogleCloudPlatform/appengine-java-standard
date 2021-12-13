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

import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;

/** Base class for facet unit tests. */
abstract class FacetTestBase {
  protected static List<String> generateInvalidNames(){
    ArrayList<String> ret = new ArrayList<>();
    ret.add("");
    ret.add(Strings.repeat("a", SearchApiLimits.MAXIMUM_NAME_LENGTH + 1));
    ret.add("!reserved");
    for (int i = 0; i < 255; ++i) {
      char c = (char) i;
      if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')) {
          ret.add(String.valueOf((char) i));
      }
      if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
          && !(c >= '0' && c <= '9') && c != '_') {
        ret.add("A" + c);
      }
    }
    return ret;
  }

  protected static List<String> generateValidNames() {
    ArrayList<String> ret = new ArrayList<>();
    ret.add("a");
    ret.add(Strings.repeat("a", SearchApiLimits.MAXIMUM_NAME_LENGTH));
    for (int i = 0; i < 255; ++i) {
      char c = (char) i;
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
          ret.add(String.valueOf((char) i));
      }
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9') || c == '_') {
        ret.add("A" + c);
      }
    }
    return ret;
  }
}
