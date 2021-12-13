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

package com.google.appengine.api.blobstore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Sets;
import com.google.common.testing.EqualsTester;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ByteRange}.
 *
 */
@RunWith(JUnit4.class)
public class ByteRangeTest {
  @Test
  public void testToString() {
    assertThat(new ByteRange(0).toString()).isEqualTo("bytes=0-");
    assertThat(new ByteRange(10).toString()).isEqualTo("bytes=10-");
    assertThat(new ByteRange(-10).toString()).isEqualTo("bytes=-10");
    assertThat(new ByteRange(0, 0).toString()).isEqualTo("bytes=0-0");
    assertThat(new ByteRange(5, 10).toString()).isEqualTo("bytes=5-10");
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(new ByteRange(0))
        .addEqualityGroup(new ByteRange(10))
        .addEqualityGroup(new ByteRange(0, 0))
        .addEqualityGroup(new ByteRange(0, 10))
        .addEqualityGroup(new ByteRange(2, 12))
        .testEquals();
  }

  @Test
  public void testHashCode() {
    Set<ByteRange> set = Sets.newHashSet();

    set.add(new ByteRange(0));
    set.add(new ByteRange(0));
    set.add(new ByteRange(0, 10));
    set.add(new ByteRange(0, 10));

    assertThat(set).hasSize(2);
    assertThat(set).contains(new ByteRange(0));
    assertThat(set).contains(new ByteRange(0, 10));
  }

  void doInvalidRangeTest(long start, long end) {
    assertThrows(IllegalArgumentException.class, () -> new ByteRange(start, end));
  }

  @Test
  public void testInvalidByteRanges() {
    doInvalidRangeTest(20, 10);
    doInvalidRangeTest(-1, 10);
  }

  @Test
  public void testParseByteRange() {
    assertThat(ByteRange.parse("bytes=0-")).isEqualTo(new ByteRange(0));
    assertThat(ByteRange.parse("bytes=10-")).isEqualTo(new ByteRange(10));
    assertThat(ByteRange.parse("bytes=-10")).isEqualTo(new ByteRange(-10));
    assertThat(ByteRange.parse("bytes=10-20")).isEqualTo(new ByteRange(10, 20));
    assertThat(ByteRange.parse(" bytes = 10 - 20 ")).isEqualTo(new ByteRange(10, 20));
  }

  @Test
  public void testInvalidRangeFormats() {
    doInvalidRangeFormatTest("");
    doInvalidRangeFormatTest("bytes");
    doInvalidRangeFormatTest("bytes=");
    doInvalidRangeFormatTest("not correct at all");
  }

  @Test
  public void testParseContentRange() {
    assertThat(ByteRange.parseContentRange("bytes 0-10/1000")).isEqualTo(new ByteRange(0, 10));
  }

  @Test
  public void testInvalidContentRangeFormats() {
    doInvalidContentRangeFormatTest("");
    doInvalidContentRangeFormatTest("bytes");
    doInvalidContentRangeFormatTest("bytes ");
    doInvalidContentRangeFormatTest("Very bad");
    doInvalidContentRangeFormatTest("bytes=1-20/1000");
    doInvalidContentRangeFormatTest("bytes 1-20");
    doInvalidContentRangeFormatTest("bytes */1000");
    doInvalidContentRangeFormatTest("bytes */*");
    doInvalidContentRangeFormatTest("bytes 1-20/*");
  }

  void doInvalidRangeFormatTest(String byteRange) {
    assertThrows(RangeFormatException.class, () -> ByteRange.parse(byteRange));
  }

  void doUnsupportedRangeFormatTest(String byteRange) {
    assertThrows(UnsupportedRangeFormatException.class, () -> ByteRange.parse(byteRange));
  }

  void doInvalidContentRangeFormatTest(String contentRange) {
    assertThrows(RangeFormatException.class, () -> ByteRange.parseContentRange(contentRange));
  }

  @Test
  public void testUnsupportedRangeFormats() {
    doUnsupportedRangeFormatTest("words=10-");
    doUnsupportedRangeFormatTest("bytes=10-,20-,20-40");
    doUnsupportedRangeFormatTest("bytes=1-2, 4-4, -1");
  }

  @Test
  public void testAccessUnsetEnd() {
    assertThrows(IllegalStateException.class, () -> new ByteRange(10).getEnd());
  }
}
