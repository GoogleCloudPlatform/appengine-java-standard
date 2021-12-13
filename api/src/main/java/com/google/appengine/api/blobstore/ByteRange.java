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

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A byte range as parsed from a request Range header.  Format produced by this class is
 * also compatible with the X-AppEngine-BlobRange header, used for serving sub-ranges of
 * blobs.
 *
 */
public class ByteRange {
  private final long start;
  private final @Nullable Long end;

  static final String BYTES_UNIT = "bytes";
  static final String UNIT_REGEX = "([^=\\s]+)";
  static final String VALID_RANGE_HEADER_REGEX = UNIT_REGEX + "\\s*=\\s*(\\d*)\\s*-\\s*(\\d*)";
  static final String INVALID_RANGE_HEADER_REGEX = "((?:\\s*,\\s*(?:\\d*)-(?:\\d*))*)";
  static final Pattern RANGE_HEADER_PATTERN =
      Pattern.compile("^\\s*" + VALID_RANGE_HEADER_REGEX + INVALID_RANGE_HEADER_REGEX + "\\s*$");

  static final String CONTENT_RANGE_UNIT_REGEX = "([^\\s]+)";
  static final String VALID_CONTENT_RANGE_HEADER_REGEX =
      BYTES_UNIT + "\\s+(\\d+)-(\\d+)/(\\d+)";
  static final Pattern CONTENT_RANGE_HEADER_PATTERN = Pattern.compile(
      "^\\s*" + VALID_CONTENT_RANGE_HEADER_REGEX + "\\s*$");

  /**
   * Constructor.
   *
   * @param start Start index of blob range to serve.  If negative, serve the last abs(start) bytes
   * of the blob.
   */
  public ByteRange(long start) {
    this(start, null);
  }

  /**
   * Constructor.
   *
   * @param start Start index of blob range to serve.  May not be negative.
   * @param end End index of blob range to serve.  Index is inclusive, meaning the byte indicated
   * by end is included in the response.
   */
  public ByteRange(long start, long end) {
    this(start, Long.valueOf(end));

    if (start < 0) {
      throw new IllegalArgumentException("If end is provided, start must be positive.");
    }

    if (end < start) {
      throw new IllegalArgumentException("end must be >= start.");
    }
  }

  protected ByteRange(long start, @Nullable Long end) {
    this.start = start;
    this.end = end;
  }

  /**
   * Indicates whether or not this byte range indicates an end.
   *
   * @return true if byte range has an end.
   */
  public boolean hasEnd() {
    return end != null;
  }

  /**
   * Get start index of byte range.
   *
   * @return Start index of byte range.
   */
  public long getStart() {
    return start;
  }

  /**
   * Get end index of byte range.
   *
   * @return End index of byte range.
   *
   * @throws IllegalStateException if byte range does not have an end range.
   */
  public long getEnd() {
    if (!hasEnd()) {
      throw new IllegalStateException("Byte-range does not have end.  Check hasEnd() before use");
    }
    return requireNonNull(end);
  }

  /**
   * Format byte range for use in header.
   */
  @Override
  public String toString() {
    if (end != null) {
      return BYTES_UNIT + "=" + start + "-" + end;
    } else {
      if (start < 0) {
        return BYTES_UNIT + "="  + start;
      } else {
        return BYTES_UNIT + "=" + start + "-";
      }
    }
  }

  /**
   * Parse byte range from header.
   *
   * @param byteRange Byte range string as received from header.
   *
   * @return ByteRange object set to byte range as parsed from string.
   *
   * @throws RangeFormatException Unable to parse header because of invalid format.
   * @throws UnsupportedRangeFormatException Header is a valid HTTP range header, the specific
   * form is not supported by app engine.  This includes unit types other than "bytes" and multiple
   * ranges.
   */
  public static ByteRange parse(String byteRange) {
    Matcher matcher = RANGE_HEADER_PATTERN.matcher(byteRange);
    if (!matcher.matches()) {
      throw new RangeFormatException("Invalid range format: " + byteRange);
    }

    String unsupportedRange = requireNonNull(matcher.group(4));
    if (!"".equals(unsupportedRange)) {
      throw new UnsupportedRangeFormatException("Unsupported range format: " + byteRange);
    }

    String units = requireNonNull(matcher.group(1));
    if (!BYTES_UNIT.equals(units)) {
      throw new UnsupportedRangeFormatException("Unsupported unit: " + units);
    }

    String start = requireNonNull(matcher.group(2));
    Long startValue;
    if ("".equals(start)) {
      startValue = null;
    } else {
      startValue = Long.parseLong(start);
    }

    String end = requireNonNull(matcher.group(3));
    Long endValue;
    if ("".equals(end)) {
      endValue = null;
    } else {
      endValue = Long.parseLong(end);
    }

    // Handle formats such as "bytes=-100".
    if (startValue == null && endValue != null) {
      startValue = -endValue;
      endValue = null;
    }

    requireNonNull(startValue, () -> "Invalid range format: " + byteRange);
    if (endValue == null) {
      return new ByteRange(startValue);
    } else {
      try {
        return new ByteRange((long) startValue, (long) endValue);
      } catch (IllegalArgumentException ex) {
        throw new RangeFormatException("Invalid range format: " + byteRange, ex);
      }
    }
  }

  /**
   * Parse content range from header for byte-range only.
   *
   * @param contentRange Content range string as received from header.
   *
   * @return ByteRange object set to byte range as parsed from string, but does not include the
   * size information.
   *
   * @throws RangeFormatException Unable to parse header because of invalid format.
   */
  public static ByteRange parseContentRange(String contentRange) {
    Matcher matcher = CONTENT_RANGE_HEADER_PATTERN.matcher(contentRange);
    if (!matcher.matches()) {
      throw new RangeFormatException("Invalid content-range format: " + contentRange);
    }
    String start = requireNonNull(matcher.group(1));
    String end = requireNonNull(matcher.group(2));

    return new ByteRange(Long.parseLong(start), Long.parseLong(end));
  }

  @Override
  public int hashCode() {
    int hash = 17;
    hash = hash * 37 + Long.hashCode(start);
    if (end != null) {
      hash = hash * 37 + end.hashCode();
    }
    return hash;
  }

  /**
   * Two {@code ByteRange} objects are considered equal if they have the same start and end.
   */
  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof ByteRange) {
      ByteRange key = (ByteRange) object;
      return start == key.getStart() && Objects.equals(end, key.end);
    }

    return false;
  }
}
