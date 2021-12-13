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
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.common.base.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Cursor}.
 *
 */
@RunWith(JUnit4.class)
public class CursorTest {
  @Test
  public void testBuild_emptyString() {
    assertThrows(IllegalArgumentException.class, () -> Cursor.newBuilder().build(""));
    assertThrows(IllegalArgumentException.class, () -> Cursor.newBuilder().build("true:"));
    assertThrows(
        IllegalArgumentException.class, () -> Cursor.newBuilder().build("blah:goodness here"));
  }

  @Test
  public void testBuild() {
    Cursor cursor = Cursor.newBuilder().build("true:here");
    assertThat(cursor.isPerResult()).isTrue();
    assertThat(cursor.toWebSafeString()).isEqualTo("true:here");
  }

  @Test
  public void testBuildInitial() {
    Cursor cursor = Cursor.newBuilder().build();
    assertThat(cursor.isPerResult()).isFalse();
    assertThat(cursor.toWebSafeString()).isNull();
  }

  @Test
  public void testToString() {
    Cursor cursor = Cursor.newBuilder().build("true:here");
    assertThat(cursor.toString()).isEqualTo("Cursor(webSafeString=true:here)");
    cursor = Cursor.newBuilder().build();
    assertThat(cursor.toString()).isEqualTo("Cursor()");
  }

  @Test
  public void testSetCursor_tooLong() {
    int length = SearchApiLimits.MAXIMUM_CURSOR_LENGTH - "true:".length();
    Cursor cursor = Cursor.newBuilder().build("true:" + Strings.repeat("a", length));
    assertThat(cursor.toWebSafeString().length()).isEqualTo(SearchApiLimits.MAXIMUM_CURSOR_LENGTH);
    assertThrows(
        IllegalArgumentException.class,
        () -> Cursor.newBuilder().build("true:" + Strings.repeat("a", length + 1)));
  }

  @Test
  public void testCopyToProtocolBufferDefaultCase() {
    Cursor cursor = Cursor.newBuilder().build();
    assertThat(cursor.toWebSafeString()).isNull();
    SearchParams.Builder builder = SearchParams.newBuilder();
    cursor.copyToProtocolBuffer(builder);
    SearchServicePb.IndexSpec spec = SearchServicePb.IndexSpec.newBuilder().setName("name").build();
    SearchParams paramsPb = builder.setIndexSpec(spec).setQuery("query").build();
    assertThat(paramsPb.getCursorType()).isEqualTo(SearchParams.CursorType.SINGLE);
    assertThat(paramsPb.hasCursor()).isFalse();
  }

  @Test
  public void testCopyToProtocolBufferPerResult() {
    Cursor cursor = Cursor.newBuilder().build("true:some internal cursor");
    assertThat(cursor.toWebSafeString()).isEqualTo("true:some internal cursor");
    SearchParams.Builder builder = SearchParams.newBuilder();
    cursor.copyToProtocolBuffer(builder);
    SearchServicePb.IndexSpec spec = SearchServicePb.IndexSpec.newBuilder().setName("name").build();
    SearchParams paramsPb = builder.setIndexSpec(spec).setQuery("query").build();
    assertThat(paramsPb.getCursorType()).isEqualTo(SearchParams.CursorType.PER_RESULT);
    assertThat(paramsPb.getCursor()).isEqualTo("some internal cursor");
  }

  @Test
  public void testCopyToProtocolBufferSingle() {
    Cursor cursor = Cursor.newBuilder().build("false:some internal cursor");
    assertThat(cursor.toWebSafeString()).isEqualTo("false:some internal cursor");
    SearchParams.Builder builder = SearchParams.newBuilder();
    cursor.copyToProtocolBuffer(builder);
    SearchServicePb.IndexSpec spec = SearchServicePb.IndexSpec.newBuilder().setName("name").build();
    SearchParams paramsPb = builder.setIndexSpec(spec).setQuery("query").build();
    assertThat(paramsPb.getCursorType()).isEqualTo(SearchParams.CursorType.SINGLE);
    assertThat(paramsPb.getCursor()).isEqualTo("some internal cursor");
  }
}
