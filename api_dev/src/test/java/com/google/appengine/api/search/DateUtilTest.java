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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link DateUtil}.
 *
 */
@RunWith(JUnit4.class)
public class DateUtilTest {

  @Test
  public void testSerializeDate() {
    assertThat(DateUtil.serializeDate(DateTestHelper.parseDate("1970-1-1"))).isEqualTo("0");
    assertThat(DateUtil.serializeDate(DateTestHelper.parseDate("1970-01-01"))).isEqualTo("0");
    assertThat(DateUtil.serializeDate(DateTestHelper.parseDate("1969-12-31")))
        .isEqualTo(Long.toString(-DateTestHelper.MILLISECONDS_PER_DAY));
    assertThat(DateUtil.serializeDate(DateTestHelper.parseDate("1970-1-2")))
        .isEqualTo(Long.toString(DateTestHelper.MILLISECONDS_PER_DAY));
    assertThat(DateUtil.serializeDate(DateTestHelper.parseDate("1970-01-02")))
        .isEqualTo(Long.toString(DateTestHelper.MILLISECONDS_PER_DAY));
    assertThat(DateUtil.serializeDate(DateTestHelper.parseDate("1801-11-21")))
        .isEqualTo(Long.toString(-61402L * DateTestHelper.MILLISECONDS_PER_DAY));
    assertThat(
            Long.parseLong(DateUtil.serializeDate(DateTestHelper.parseDate("1582-10-15")))
                - Long.parseLong(DateUtil.serializeDate(DateTestHelper.parseDate("1582-10-14"))))
        .isEqualTo(DateTestHelper.MILLISECONDS_PER_DAY);
    assertThat(DateUtil.serializeDate(SearchApiLimits.MINIMUM_DATE_VALUE))
        .isEqualTo("-185542587187200000");
    assertThat(DateUtil.serializeDate(SearchApiLimits.MAXIMUM_DATE_VALUE))
        .isEqualTo("185542587187199999");
  }

  @Test
  public void testDeserializeDate() {
    assertThat(DateUtil.deserializeDate("0")).isEqualTo(DateTestHelper.parseDate("1970-1-1"));
    assertThat(DateUtil.deserializeDate("0")).isEqualTo(DateTestHelper.parseDate("1970-01-01"));
    assertThat(DateUtil.deserializeDate("-86400000"))
        .isEqualTo(DateTestHelper.parseDate("1969-12-31"));
    assertThat(DateUtil.deserializeDate("86400000"))
        .isEqualTo(DateTestHelper.parseDate("1970-1-2"));
    assertThat(DateUtil.deserializeDate("86400000"))
        .isEqualTo(DateTestHelper.parseDate("1970-01-02"));
    assertThat(DateUtil.deserializeDate("" + (-61402L * DateTestHelper.MILLISECONDS_PER_DAY)))
        .isEqualTo(DateTestHelper.parseDate("1801-11-21"));

    String[] dates = {"2012-08-07", "1970-01-01", "1801-11-21", "1582-10-15"};
    for (String dateStr : dates) {
      assertThat(DateTestHelper.formatDate(DateUtil.deserializeDate(dateStr))).isEqualTo(dateStr);
    }

    // Time component won't parse.
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> DateUtil.deserializeDate("2000-1-1 10:30"));
    assertThat(e).hasMessageThat().isEqualTo("Failed to parse date string \"2000-1-1 10:30\"");
  }

  @Test
  public void testFormatDateTime() {
    assertThat(DateUtil.formatDateTime(null)).isEqualTo(null);
    assertThat(
            DateUtil.formatDateTime(
                DateTestHelper.ISO8601_HMSM_FORMATTER
                    .parseDateTime("2012-08-13T11:27:09.123")
                    .toDate()))
        .isEqualTo("2012-08-13T11:27:09.123Z");
    assertThat(
            DateUtil.formatDateTime(
                DateTestHelper.ISO8601_HMSM_FORMATTER
                    .parseDateTime("1700-08-13T11:27:09.123")
                    .toDate()))
        .isEqualTo("1700-08-13T11:27:09.123Z");
    assertThat(DateUtil.formatDateTime(SearchApiLimits.MINIMUM_DATE_VALUE))
        .isEqualTo("5877521-03-03T00:00:00.000ZBC");
    assertThat(DateUtil.formatDateTime(SearchApiLimits.MAXIMUM_DATE_VALUE))
        .isEqualTo("5881580-07-11T23:59:59.999Z");
  }

  @Test
  public void testMinDate() {
    assertThat(DateTestHelper.getDaysSinceEpoch(SearchApiLimits.MINIMUM_DATE_VALUE))
        .isEqualTo(Integer.MIN_VALUE);
  }

  @Test
  public void testMaxDate() {
    assertThat(DateTestHelper.getDaysSinceEpoch(SearchApiLimits.MAXIMUM_DATE_VALUE))
        .isEqualTo(Integer.MAX_VALUE);
  }
}
