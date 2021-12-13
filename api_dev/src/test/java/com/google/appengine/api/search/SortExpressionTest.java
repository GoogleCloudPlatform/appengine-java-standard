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
import com.google.common.base.Strings;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.MutableDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SortExpression}.
 *
 */
@RunWith(JUnit4.class)
public class SortExpressionTest {

  private static final String DATE_STRING = "2011-01-01";
  private Date date;

  @Before
  public void setUp() {
    date = DateTestHelper.parseDate(DATE_STRING);
  }

  private SortExpression.Builder mandatoryFields(String expression, String defaultValue) {
    return SortExpression.newBuilder().setExpression(expression).setDefaultValue(defaultValue);
  }

  @Test
  public void testSetExpression_ok() {
    assertThat(mandatoryFields("count(tag)", "value").build().getExpression())
        .isEqualTo("count(tag)");
  }

  @Test
  public void testSetExpression_parsable() {
    assertThat(mandatoryFields("price + tax", "value").build().getExpression())
        .isEqualTo("price + tax");
    assertThrows(IllegalArgumentException.class, () -> mandatoryFields("price + + tax", "value"));
  }

  @Test
  public void testNoExpressionGiven() {
    SortExpression.Builder builder = SortExpression.newBuilder();
    assertThrows(NullPointerException.class, builder::build);
  }

  @Test
  public void testNoDefaultValueGiven() {
    SortExpression.Builder builder = SortExpression.newBuilder().setExpression("expression");
    builder.build();
  }

  @Test
  public void testSetExpression_null() {
    assertThrows(NullPointerException.class, () -> SortExpression.newBuilder().setExpression(null));
  }

  @Test
  public void testSetFieldName_setExpressionTwice() {
    assertThat(
            SortExpression.newBuilder()
                .setExpression("name")
                .setExpression("count(tag)")
                .setDefaultValue("value")
                .build()
                .getExpression())
        .isEqualTo("count(tag)");
  }

  @Test
  public void testSetDefaultValue_null() {
    SortExpression.Builder builder =
        SortExpression.newBuilder().setExpression("name").setDefaultValue(null);
    builder.build();
  }

  @Test
  public void testSetDefaultValue_empty() {
    SortExpression sort =
        SortExpression.newBuilder().setExpression("name").setDefaultValue("").build();
    assertThat(sort.getDefaultValue()).isEmpty();
  }

  @Test
  public void testSetDefaultValueDate() {
    SortExpression sort =
        SortExpression.newBuilder().setExpression("name").setDefaultValueDate(date).build();
    assertThat(sort.getDefaultValueDate()).isEqualTo(date);
  }

  private DateTime getDateTimeUTC(Date date) {
    return new DateTime(date.getTime(), DateTimeZone.UTC);
  }

  private MutableDateTime getMutableDateTimeUTC(Date date) {
    return new MutableDateTime(date.getTime(), DateTimeZone.UTC);
  }

  @Test
  public void testMaxDate() {
    SortExpression when =
        SortExpression.newBuilder()
            .setExpression("when")
            .setDefaultValueDate(SearchApiLimits.MAXIMUM_DATE_VALUE)
            .build();
    assertThat(when.getDefaultValueDate()).isEqualTo(SearchApiLimits.MAXIMUM_DATE_VALUE);

    MutableDateTime afterMax = getMutableDateTimeUTC(SearchApiLimits.MAXIMUM_DATE_VALUE);
    afterMax.addDays(1);
    assertThat(
            Days.daysBetween(getDateTimeUTC(SearchApiLimits.MAXIMUM_DATE_VALUE), afterMax)
                .getDays())
        .isEqualTo(1);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SortExpression.newBuilder()
                    .setExpression("when")
                    .setDefaultValueDate(afterMax.toDate()));
    String message =
        String.format(
            "date %s must be before %s",
            DateUtil.formatDateTime(afterMax.toDate()),
            DateUtil.formatDateTime(SearchApiLimits.MAXIMUM_DATE_VALUE));
    assertThat(e).hasMessageThat().isEqualTo(message);
  }

  @Test
  public void testMinDate() {
    SortExpression when =
        SortExpression.newBuilder()
            .setExpression("when")
            .setDefaultValueDate(SearchApiLimits.MINIMUM_DATE_VALUE)
            .build();
    assertThat(when.getDefaultValueDate()).isEqualTo(SearchApiLimits.MINIMUM_DATE_VALUE);

    MutableDateTime beforeMin = getMutableDateTimeUTC(SearchApiLimits.MINIMUM_DATE_VALUE);
    beforeMin.addDays(-1);
    assertThat(
            Days.daysBetween(beforeMin, getDateTimeUTC(SearchApiLimits.MINIMUM_DATE_VALUE))
                .getDays())
        .isEqualTo(1);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SortExpression.newBuilder()
                    .setExpression("when")
                    .setDefaultValueDate(beforeMin.toDate()));
    String message =
        String.format(
            "date %s must be after %s",
            DateUtil.formatDateTime(beforeMin.toDate()),
            DateUtil.formatDateTime(SearchApiLimits.MINIMUM_DATE_VALUE));
    assertThat(e).hasMessageThat().isEqualTo(message);
  }

  @Test
  public void testSetDefaultValue_tooLong() {
    SortExpression sort =
        SortExpression.newBuilder()
            .setExpression("expression")
            .setDefaultValue(Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH))
            .build();
    assertThat(sort.getDefaultValue())
        .isEqualTo(Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SortExpression.newBuilder()
                .setDefaultValue(Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH + 1)));
  }

  @Test
  public void testSetDefaultValue_tooMany() {
    SortExpression.Builder builder1 =
        SortExpression.newBuilder()
            .setExpression("name")
            .setDefaultValue("string")
            .setDefaultValueNumeric(99.9);
    assertThrows(IllegalArgumentException.class, builder1::build);
    SortExpression.Builder builder2 =
        SortExpression.newBuilder()
            .setExpression("name")
            .setDefaultValue("string")
            .setDefaultValueDate(date);
    assertThrows(IllegalArgumentException.class, builder2::build);
    SortExpression.Builder builder3 =
        SortExpression.newBuilder()
            .setExpression("name")
            .setDefaultValueNumeric(99.9)
            .setDefaultValueDate(date);
    assertThrows(IllegalArgumentException.class, builder3::build);
  }

  @Test
  public void testValidSpec() {
    SortExpression spec =
        SortExpression.newBuilder()
            .setExpression("name")
            .setDirection(SortExpression.SortDirection.ASCENDING)
            .setDefaultValue("value")
            .build();
    assertThat(spec.getExpression()).isEqualTo("name");
    assertThat(spec.getDefaultValue()).isEqualTo("value");
    assertThat(spec.getDirection()).isEqualTo(SortExpression.SortDirection.ASCENDING);
  }

  @Test
  public void testCopyToProtocolBuffer_fieldName() {
    SortExpression spec =
        SortExpression.newBuilder()
            .setExpression("name")
            .setDefaultValue("a")
            .setDirection(SortExpression.SortDirection.DESCENDING)
            .build();
    SearchServicePb.SortSpec specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getSortExpression()).isEqualTo("name");
    assertThat(specPb.hasSortDescending()).isFalse();
    assertThat(specPb.getSortDescending()).isTrue();
    assertThat(specPb.getDefaultValueText()).isEqualTo("a");
  }

  @Test
  public void testCopyToProtocolBuffer_expression() {
    SortExpression spec =
        SortExpression.newBuilder()
            .setExpression("count(tag)")
            .setDefaultValueNumeric(0)
            .setDirection(SortExpression.SortDirection.DESCENDING)
            .build();
    SearchServicePb.SortSpec specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getSortExpression()).isEqualTo("count(tag)");
    assertThat(specPb.hasSortDescending()).isFalse();
    assertThat(specPb.getSortDescending()).isTrue();
    assertThat(specPb.getDefaultValueNumeric()).isEqualTo(0.0);
  }

  private int getDaysSinceEpoch(String dateLong) {
    return (int) (Long.parseLong(dateLong) / (24L * 60 * 60 * 1000));
  }

  private String getDateLongString(Date date) {
    return Long.toString(date.getTime());
  }

  @Test
  public void testCopyToProtocolBuffer_dateField() {
    SortExpression spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(date)
            .setDirection(SortExpression.SortDirection.DESCENDING)
            .build();
    SearchServicePb.SortSpec specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getSortExpression()).isEqualTo("published");
    assertThat(specPb.hasSortDescending()).isFalse();
    assertThat(specPb.getSortDescending()).isTrue();
    assertThat(specPb.hasDefaultValueNumeric()).isFalse();
    assertThat(specPb.getDefaultValueText()).isEqualTo("1293840000000");
    assertThat(specPb.getDefaultValueText()).isEqualTo(getDateLongString(date));

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(DateTestHelper.parseDate("1969-12-30"))
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(DateTestHelper.parseDate("1969-12-30")));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(-2);

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(DateTestHelper.parseDate("1969-12-31"))
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(DateTestHelper.parseDate("1969-12-31")));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(-1);

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(DateTestHelper.parseDate("1970-01-01"))
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(DateTestHelper.parseDate("1970-01-01")));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(0);

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(DateTestHelper.parseDate("1970-01-02"))
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(DateTestHelper.parseDate("1970-01-02")));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(1);

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(DateTestHelper.parseDate("1914-04-19"))
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(DateTestHelper.parseDate("1914-04-19")));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(-20346);

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(SearchApiLimits.MAXIMUM_DATE_VALUE)
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(SearchApiLimits.MAXIMUM_DATE_VALUE));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(Integer.MAX_VALUE);

    spec =
        SortExpression.newBuilder()
            .setExpression("published")
            .setDefaultValueDate(SearchApiLimits.MINIMUM_DATE_VALUE)
            .build();
    specPb = spec.copyToProtocolBuffer();
    assertThat(specPb.getDefaultValueText())
        .isEqualTo(getDateLongString(SearchApiLimits.MINIMUM_DATE_VALUE));
    assertThat(getDaysSinceEpoch(specPb.getDefaultValueText())).isEqualTo(Integer.MIN_VALUE);
  }

  @Test
  public void testToString() {
    assertThat(
            SortExpression.newBuilder()
                .setExpression("expression")
                .setDefaultValue("value")
                .build()
                .toString())
        .isEqualTo(
            "SortExpression(direction=DESCENDING, expression=expression, defaultValue=value)");
  }
}
