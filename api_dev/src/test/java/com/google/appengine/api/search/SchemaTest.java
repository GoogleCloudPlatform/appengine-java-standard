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

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SortOptions}.
 *
 */
@RunWith(JUnit4.class)
public class SchemaTest {

  @Test
  public void testGetFieldTypes() {
    Schema schema = Schema.newBuilder()
        .addTypedField("text_only", Field.FieldType.TEXT)
        .addTypedField("text_and_numeric", Field.FieldType.TEXT)
        .addTypedField("text_and_numeric", Field.FieldType.NUMBER)
        .build();
    List<Field.FieldType> fieldTypes = schema.getFieldTypes("does_not_exist");
    assertThat(fieldTypes).isEmpty();

    fieldTypes = schema.getFieldTypes("text_only");
    assertThat(fieldTypes).containsExactly(Field.FieldType.TEXT);

    fieldTypes = schema.getFieldTypes("text_and_numeric");
    assertThat(fieldTypes).containsExactly(Field.FieldType.TEXT, Field.FieldType.NUMBER);
  }
}
