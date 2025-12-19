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

package com.google.appengine.api.testing;

/**
 * ASCII representations of {@link com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query} instances.
 *
 */
public final class TestQueries {

  private TestQueries() {}

  public static final Object[][] TEST_QUERIES = {
      { // FROM X WHERE a == 'x' AND a > 'y'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 3\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 5, true
      },

      { // FROM X WHERE a == 'x' AND b > 'y'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 3\n" +
              "  property <\n" +
              "    name: \"b\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 5, true
      },
      { // FROM X
          "app: \"myapp\"\n" +
              "kind: \"X\"\n"
          , 1, false
      },
      { // FROM X WHERE b == 'y' AND a == 'x' ancestor is key ORDER BY a, b
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"b\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"a\"\n" +
              "  direction: 1\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"b\"\n" +
              "  direction: 1\n" +
              "}\n" +
              "ancestor <\n" +
              "  app: \"myapp\"\n" +
              "  path <\n" +
              "    Element {\n" +
              "      type: \"Foo\"\n" +
              "      id: 1\n" +
              "    }\n" +
              "  >\n" +
              ">\n"
          , 11, false
      },
      { // FROM X ORDER BY b, a desc
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"b\"\n" +
              "  direction: 1\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"a\"\n" +
              "  direction: 2\n" +
              "}\n"
          , 9, true
      },
      { // FROM X WHERE b <= 'y' AND a = 'x'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 2\n" +
              "  property <\n" +
              "    name: \"b\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 6, true
      },
      { // FROM X ORDER BY b, a
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"b\"\n" +
              "  direction: 1\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"a\"\n" +
              "  direction: 1\n" +
              "}\n"
          , 8, true
      },
      { // FROM X WHERE a > 'x'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 3\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 4, false
      },
      { // FROM X ORDER BY a, b
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"a\"\n" +
              "  direction: 1\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"b\"\n" +
              "  direction: 1\n" +
              "}\n"
          , 7, true
      },
      { // FROM X WHERE ancestor is key
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "ancestor <\n" +
              "  app: \"myapp\"\n" +
              "  path <\n" +
              "    Element {\n" +
              "      type: \"Foo\"\n" +
              "      id: 1\n" +
              "    }\n" +
              "  >\n" +
              ">\n"
          , 10, false
      },
      { // FROM X WHERE a = 5
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      int64Value: 5\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 3, false
      },
      { // FROM X WHERE a = 'x'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 2, false
      },

// Equals filter and ascending sort order on the same property. No composite
// index needed.
      { // FROM X WHERE a = 'x' ORDER BY a
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"a\"\n" +
              "  direction: 1\n" +
              "}\n"
          , 2, false
      },
// Inequality filter and ascending sort order on the same property. No
// composite index needed.
      { // FROM X WHERE a < 'x' ORDER BY a
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 1\n" +
              "  property <\n" +
              "    name: \"a\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"a\"\n" +
              "  direction: 1\n" +
              "}\n"
          , 2, false
      },
// Inequality filter and descending sort order on the same property. No
// composite index needed.
      { // FROM X WHERE d < 'x' ORDER BY d desc
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 1\n" +
              "  property <\n" +
              "    name: \"d\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"d\"\n" +
              "  direction: 2\n" +
              "}\n"
          , 4, false
      },
// Single sort orders. Neither one needs a composite index.
      { // FROM X ORDER BY c
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"c\"\n" +
              "  direction: 1 \n" + // ascending
              "}\n"
          , 2, false
      },
      { // FROM X ORDER BY d desc
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"d\"\n" +
              "  direction: 2  \n" + // descending
              "}\n"
          , 2, false
      },

// Multiple equals filters, no sort orders. One with an ancestor, one without.
// Neither needs a composite index.
      { // FROM X WHERE e = null AND f = null AND ancestor is key
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"e\"\n" +
              "    multiple: false\n" +
              "    value <>\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"f\"\n" +
              "    multiple: false\n" +
              "    value <>\n" +
              "  >\n" +
              "}\n" +
              "ancestor <\n" +
              "  app: \"myapp\"\n" +
              "  path <\n" +
              "    Element {\n" +
              "      type: \"Foo\"\n" +
              "      id: 1\n" +
              "    }\n" +
              "  >\n" +
              ">\n"
          , 1, false
      },
      { // FROM X WHERE f = null AND e = null
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"f\"\n" +
              "    multiple: false\n" +
              "    value <>\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"e\"\n" +
              "    multiple: false\n" +
              "    value <>\n" +
              "  >\n" +
              "}\n"
          , 1, false
      },
// Queries that have multiple filters and/or sort orders on the same property,
// and nothing else.
      { // FROM X WHERE g = 'x' AND g = 'y'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 2, false
      },
      { // FROM X WHERE g = 'x' AND g > 'y'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 3\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 2, true
      },
      { // FROM X WHERE g = 'x' AND g > 'y' AND ancestor is key
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 3\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "ancestor <\n" +
              "  app: \"myapp\"\n" +
              "  path <\n" +
              "    Element {\n" +
              "      type: \"Foo\"\n" +
              "      id: 1\n" +
              "    }\n" +
              "  >\n" +
              ">\n"
          , 2, true
      },
      { // FROM X ORDER BY g, g
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"g\"\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"g\"\n" +
              "}\n"
          , 2, false
      },
      // This one actually requires an index as ['x', 'z'] should match. Without an index no values
      // can match this query
      { // FROM X WHERE g = 'x' AND g > 'y' ORDER BY g, g
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 3\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"g\"\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"g\"\n" +
              "}"
          , 2, true
      },
      { // FROM X WHERE __key__ = ... AND g = 'y'
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"__key__\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      ReferenceValue {\n" +
              "        app: \"myapp\"\n" +
              "      }\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"g\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n"
          , 1, false
      },
      { // FROM X WHERE listprop = 'x' AND listprop = 'y' ORDER BY z
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"listprop\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"x\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Filter {\n" +
              "  op: 5\n" +
              "  property <\n" +
              "    name: \"listprop\"\n" +
              "    multiple: false\n" +
              "    value <\n" +
              "      stringValue: \"y\"\n" +
              "    >\n" +
              "  >\n" +
              "}\n" +
              "Order {\n" +
              "  property: \"z\"\n" +
              "}\n"
          , 1, true
      },
      { // FROM X ORDER BY __key__ desc
          "app: \"myapp\"\n" +
              "kind: \"X\"\n" +
              "Order {\n" +
              "  property: \"__key__\"\n" +
              "  direction: 2\n" +
              "}\n"
          , 1, true
      },
      { // FROM X WHERE __key__ >= ... AND __key__ < ... AND ancestor is key'
        "app: \"myapp\"\n" +
            "kind: \"X\"\n" +
            "Filter {\n" +
            "  op: 4\n" +
            "  property <\n" +
            "    name: \"__key__\"\n" +
            "    multiple: false\n" +
            "    value <\n" +
            "      ReferenceValue {\n" +
            "        app: \"myapp\"\n" +
            "      }\n" +
            "    >\n" +
            "  >\n" +
            "}\n" +
            "Filter {\n" +
            "  op: 1\n" +
            "  property <\n" +
            "    name: \"__key__\"\n" +
            "    multiple: false\n" +
            "    value <\n" +
            "      ReferenceValue {\n" +
            "        app: \"myapp\"\n" +
            "      }\n" +
            "    >\n" +
            "  >\n" +
            "}\n" +
            "ancestor <\n" +
            "  app: \"myapp\"\n" +
            "  path <\n" +
            "    Element {\n" +
            "      type: \"Foo\"\n" +
            "      id: 1\n" +
            "    }\n" +
            "  >\n" +
            ">\n"
        , 1, false
      },
      { // FROM X WHERE a = 'x' AND __key__ >= ... AND __key__ < ... AND ancestor is key'
        "app: \"myapp\"\n" +
            "kind: \"X\"\n" +
            "Filter {\n" +
            "  op: 5\n" +
            "  property <\n" +
            "    name: \"a\"\n" +
            "    multiple: false\n" +
            "    value <\n" +
            "      stringValue: \"z\"\n" +
            "    >\n" +
            "  >\n" +
            "}\n" +
            "Filter {\n" +
            "  op: 4\n" +
            "  property <\n" +
            "    name: \"__key__\"\n" +
            "    multiple: false\n" +
            "    value <\n" +
            "      ReferenceValue {\n" +
            "        app: \"myapp\"\n" +
            "      }\n" +
            "    >\n" +
            "  >\n" +
            "}\n" +
            "Filter {\n" +
            "  op: 1\n" +
            "  property <\n" +
            "    name: \"__key__\"\n" +
            "    multiple: false\n" +
            "    value <\n" +
            "      ReferenceValue {\n" +
            "        app: \"myapp\"\n" +
            "      }\n" +
            "    >\n" +
            "  >\n" +
            "}\n" +
            "ancestor <\n" +
            "  app: \"myapp\"\n" +
            "  path <\n" +
            "    Element {\n" +
            "      type: \"Foo\"\n" +
            "      id: 1\n" +
            "    }\n" +
            "  >\n" +
            ">\n"
        , 1, false
      },
  };
}
