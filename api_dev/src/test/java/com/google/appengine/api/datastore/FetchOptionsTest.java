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

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withEndCursor;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withOffset;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withPrefetchSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withStartCursor;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FetchOptionsTest {
  @Test
  public void testLimit() {
    assertThrows(IllegalArgumentException.class, () -> withLimit(-1));
    FetchOptions fo = withLimit(0);
    assertThat(fo.getLimit().intValue()).isEqualTo(0);
    assertThat(fo.getOffset()).isNull();
    assertThat(fo.getChunkSize()).isNull();
    assertThat(fo.getPrefetchSize()).isNull();
    assertThat(fo.getStartCursor()).isNull();
    assertThat(fo.getEndCursor()).isNull();
    assertThat(fo.getCompile()).isNull();
    fo.clearLimit();
    assertThat(fo.getLimit()).isNull();
  }

  @Test
  public void testOffset() {
    assertThrows(IllegalArgumentException.class, () -> withOffset(-1));
    FetchOptions fo = withOffset(0);
    assertThat(fo.getOffset().intValue()).isEqualTo(0);
    fo = withOffset(1);
    assertThat(fo.getOffset().intValue()).isEqualTo(1);
    assertThat(fo.getLimit()).isNull();
    assertThat(fo.getChunkSize()).isNull();
    assertThat(fo.getPrefetchSize()).isNull();
    assertThat(fo.getStartCursor()).isNull();
    assertThat(fo.getEndCursor()).isNull();
    assertThat(fo.getCompile()).isNull();
    fo.clearOffset();
    assertThat(fo.getOffset()).isNull();
  }

  @Test
  public void testChunkSize() {
    assertThrows(IllegalArgumentException.class, () -> withChunkSize(-1));
    assertThrows(IllegalArgumentException.class, () -> withChunkSize(0));
    FetchOptions fo = withChunkSize(1);
    assertThat(fo.getChunkSize().intValue()).isEqualTo(1);
    assertThat(fo.getLimit()).isNull();
    assertThat(fo.getOffset()).isNull();
    assertThat(fo.getPrefetchSize()).isNull();
    assertThat(fo.getStartCursor()).isNull();
    assertThat(fo.getEndCursor()).isNull();
    assertThat(fo.getCompile()).isNull();
    fo.clearChunkSize();
    assertThat(fo.getChunkSize()).isNull();
  }

  @Test
  public void testPrefetchSize() {
    assertThrows(IllegalArgumentException.class, () -> withPrefetchSize(-1));
    FetchOptions fo = withPrefetchSize(0);
    assertThat(fo.getPrefetchSize().intValue()).isEqualTo(0);
    fo = withPrefetchSize(1);
    assertThat(fo.getPrefetchSize().intValue()).isEqualTo(1);
    assertThat(fo.getChunkSize()).isNull();
    assertThat(fo.getLimit()).isNull();
    assertThat(fo.getOffset()).isNull();
    assertThat(fo.getStartCursor()).isNull();
    assertThat(fo.getEndCursor()).isNull();
    assertThat(fo.getCompile()).isNull();
    fo.clearPrefetchSize();
    assertThat(fo.getPrefetchSize()).isNull();
  }

  @Test
  public void testStartCursor() {
    assertThrows(NullPointerException.class, () -> withStartCursor(null));
    Cursor start = new Cursor();
    FetchOptions fo = withStartCursor(start);
    assertThat(fo.getStartCursor()).isEqualTo(start);
    assertThat(fo.getEndCursor()).isNull();
    assertThat(fo.getChunkSize()).isNull();
    assertThat(fo.getLimit()).isNull();
    assertThat(fo.getOffset()).isNull();
    assertThat(fo.getPrefetchSize()).isNull();
    assertThat(fo.getCompile()).isNull();
    fo.clearStartCursor();
    assertThat(fo.getStartCursor()).isNull();
  }

  @Test
  public void testEndCursor() {
    assertThrows(NullPointerException.class, () -> withStartCursor(null));

    Cursor end = new Cursor();
    FetchOptions fo = withEndCursor(end);
    assertThat(fo.getEndCursor()).isEqualTo(end);
    assertThat(fo.getStartCursor()).isNull();
    assertThat(fo.getChunkSize()).isNull();
    assertThat(fo.getLimit()).isNull();
    assertThat(fo.getOffset()).isNull();
    assertThat(fo.getPrefetchSize()).isNull();
    assertThat(fo.getCompile()).isNull();
    fo.clearEndCursor();
    assertThat(fo.getEndCursor()).isNull();
  }

  @Test
  public void testCompile() {
    FetchOptions fo = withDefaults();
    assertThat(fo.getCompile()).isNull();
    fo.compile(true);
    assertThat(fo.getCompile()).isTrue();
    fo.compile(false);
    assertThat(fo.getCompile()).isFalse();

    assertThat(fo.getChunkSize()).isNull();
    assertThat(fo.getLimit()).isNull();
    assertThat(fo.getOffset()).isNull();
    assertThat(fo.getPrefetchSize()).isNull();
    assertThat(fo.getStartCursor()).isNull();
    assertThat(fo.getEndCursor()).isNull();
    fo.clearCompile();
    assertThat(fo.getCompile()).isNull();
  }
}
