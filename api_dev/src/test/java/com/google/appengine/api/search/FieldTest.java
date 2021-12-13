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

import com.google.appengine.api.search.checkers.FieldChecker;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.joda.time.Days;
import org.joda.time.MutableDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@link Field}.
 *
 */
@RunWith(JUnit4.class)
public class FieldTest {

  /** @return A minimal legal field builder ready for a build command. */
  private Field.Builder newField() {
    return Field.newBuilder().setName("name");
  }

  @Test
  public void testBuild_newBuilder() {
    assertThrows(IllegalArgumentException.class, () -> Field.newBuilder().build());
  }

  @Test
  public void testSetName() {
    assertThrows(IllegalArgumentException.class, () -> Field.newBuilder().setName(null));
    assertThrows(IllegalArgumentException.class, () -> Field.newBuilder().setName(""));
    assertThrows(IllegalArgumentException.class, () -> Field.newBuilder().setName("!reserved"));
    for (int i = 0; i < 255; ++i) {
      char c = (char) i;
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
        String legalName = String.valueOf(c);
        assertThat(Field.newBuilder().setName(legalName).build().getName()).isEqualTo(legalName);
      } else {
        String illegalName = String.valueOf((char) i);
        assertThrows(IllegalArgumentException.class, () -> Field.newBuilder().setName(illegalName));
      }
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
        String legalName = "A" + c;
        assertThat(Field.newBuilder().setName(legalName).build().getName()).isEqualTo(legalName);
      } else {
        String illegalName = "A" + c;
        assertThrows(IllegalArgumentException.class, () -> Field.newBuilder().setName(illegalName));
      }
    }

    assertThat(Field.newBuilder().setName("a").build().getName()).isEqualTo("a");

    String name = Strings.repeat("a", SearchApiLimits.MAXIMUM_NAME_LENGTH);
    assertThat(Field.newBuilder().setName(name).build().getName()).isEqualTo(name);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            Field.newBuilder()
                .setName(Strings.repeat("a", SearchApiLimits.MAXIMUM_NAME_LENGTH + 1)));
  }

  @Test
  public void testSetText() {
    assertThat(newField().setText(null).build().getText()).isNull();

    assertThat(newField().setText("").build().getText()).isEmpty();

    String text1 = Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH);
    assertThat(newField().setText(text1).build().getText()).isEqualTo(text1);

    assertThrows(
        IllegalArgumentException.class,
        () -> newField().setText(Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH + 1)));

    // Try with a UTF-16 string: this should fail because each character
    // requires more than one byte.
    String text2 = Strings.repeat("\u3052", SearchApiLimits.MAXIMUM_TEXT_LENGTH);
    assertThrows(IllegalArgumentException.class, () -> newField().setText(text2));
  }

  @Test
  public void testSetHTML() {
    assertThat(newField().setHTML(null).build().getHTML()).isNull();

    assertThat(newField().setHTML("").build().getHTML()).isEmpty();

    String html = Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH);
    assertThat(newField().setHTML(html).build().getHTML()).isEqualTo(html);

    assertThrows(
        IllegalArgumentException.class,
        () -> newField().setHTML(Strings.repeat("a", SearchApiLimits.MAXIMUM_TEXT_LENGTH + 1)));
  }

  @Test
  public void testSetAtom() {
    assertThat(newField().setAtom(null).build().getAtom()).isNull();
    assertThat(newField().setAtom("").build().getAtom()).isEmpty();
    assertThat(newField().setAtom("a").build().getAtom()).isEqualTo("a");
    String atom = Strings.repeat("a", SearchApiLimits.MAXIMUM_ATOM_LENGTH);
    assertThat(newField().setAtom(atom).build().getAtom()).isEqualTo(atom);

    assertThrows(
        IllegalArgumentException.class,
        () -> newField().setAtom(Strings.repeat("a", SearchApiLimits.MAXIMUM_ATOM_LENGTH + 1)));
  }

  @Test
  public void testSetUntokenizedPrefix() {
    assertThat(newField().setUntokenizedPrefix(null).build().getUntokenizedPrefix()).isNull();
    assertThat(newField().setUntokenizedPrefix("").build().getUntokenizedPrefix()).isEmpty();
    assertThat(newField().setUntokenizedPrefix("a").build().getUntokenizedPrefix()).isEqualTo("a");
    String untokenizedPrefix = Strings.repeat("a", SearchApiLimits.MAXIMUM_PREFIX_LENGTH);
    assertThat(newField().setUntokenizedPrefix(untokenizedPrefix).build().getUntokenizedPrefix())
        .isEqualTo(untokenizedPrefix);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            newField()
                .setUntokenizedPrefix(
                    Strings.repeat("a", SearchApiLimits.MAXIMUM_PREFIX_LENGTH + 1)));
  }

  @Test
  public void testSetTokenizedPrefix() {
    assertThat(newField().setTokenizedPrefix(null).build().getTokenizedPrefix()).isNull();
    assertThat(newField().setTokenizedPrefix("").build().getTokenizedPrefix()).isEmpty();
    assertThat(newField().setTokenizedPrefix("a").build().getTokenizedPrefix()).isEqualTo("a");
    String tokenizedPrefix = Strings.repeat("a", SearchApiLimits.MAXIMUM_PREFIX_LENGTH);
    assertThat(newField().setTokenizedPrefix(tokenizedPrefix).build().getTokenizedPrefix())
        .isEqualTo(tokenizedPrefix);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            newField()
                .setTokenizedPrefix(
                    Strings.repeat("a", SearchApiLimits.MAXIMUM_PREFIX_LENGTH + 1)));
  }

  @Test
  public void testSetVector() {
    assertThat(newField().setVector(Arrays.<Double>asList()).build().getVector()).isEmpty();
    assertThat(newField().setVector(Arrays.asList(5.0)).build().getVector()).containsExactly(5.0);
    List<Double> bigVector = new ArrayList<>(SearchApiLimits.VECTOR_FIELD_MAX_SIZE);
    for (int i = 0; i < SearchApiLimits.VECTOR_FIELD_MAX_SIZE; i++) {
      bigVector.add((double) i);
    }
    assertThat(newField().setVector(bigVector).build().getVector())
        .isEqualTo(ImmutableList.copyOf(bigVector));

    bigVector.add(0.0);
    assertThrows(IllegalArgumentException.class, () -> newField().setVector(bigVector));
  }

  @Test
  public void testSetNumber() throws Exception {
    assertThat(newField().build().getNumber()).isNull();
    assertThat(newField().setNumber(-1023.2).build().getNumber()).isEqualTo(-1023.2);
    assertThat(newField().setNumber(0).build().getNumber()).isEqualTo(0.0);
    assertThat(newField().setNumber(1023.4).build().getNumber()).isEqualTo(1023.4);

    try {
      newField().setNumber(SearchApiLimits.MINIMUM_NUMBER_VALUE - 0.001);
    } catch (IllegalArgumentException e) {
      // Success.
    }
    try {
      newField().setNumber(SearchApiLimits.MAXIMUM_NUMBER_VALUE + 0.001);
    } catch (IllegalArgumentException e) {
      // Success.
    }
  }

  @Test
  public void testSetDate() throws ParseException {
    DateFormat dateFormat = DateTestHelper.getDateFormat();
    Date date = dateFormat.parse("2011-01-01");
    assertThat(newField().setDate(date).build().getDate()).isEqualTo(date);
    dateFormat = DateTestHelper.getDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    date = dateFormat.parse("2011-01-01 01:01:01.001");
    assertThat(newField().setDate(date).build().getDate()).isEqualTo(date);

    dateFormat = DateTestHelper.getDateFormat("yyyy-MM-dd HH:mm:ss");
    date = dateFormat.parse("2011-01-01 01:01:01");
    assertThat(newField().setDate(date).build().getDate()).isEqualTo(date);

    dateFormat = DateTestHelper.getDateFormat("yyyy-MM-dd HH:mm");
    date = dateFormat.parse("2011-01-01 01:01");
    assertThat(newField().setDate(date).build().getDate()).isEqualTo(date);

    dateFormat = DateTestHelper.getDateFormat("yyyy-MM-dd HH");
    date = dateFormat.parse("2011-01-01 01");
    assertThat(newField().setDate(date).build().getDate()).isEqualTo(date);
  }

  @Test
  public void testSetGeoPoint() {
    GeoPoint geoPoint = new GeoPoint(-33.84, 151.26);
    assertThat(newField().build().getGeoPoint()).isNull();
    assertThat(newField().setGeoPoint(geoPoint).build().getGeoPoint()).isEqualTo(geoPoint);
  }

  @Test
  public void testSetLocale() {
    assertThat(newField().setLocale(null).build().getText()).isNull();
    assertThat(newField().setLocale(Locale.US).build().getLocale()).isEqualTo(Locale.US);
    assertThat(newField().setLocale(Locale.US).setLocale(Locale.UK).build().getLocale())
        .isEqualTo(Locale.UK);
  }

  @Test
  public void testMinimalValidField() {
    Field field = newField().build();
    assertThat(field.getName()).isEqualTo("name");
    assertThat(field.getText()).isNull();
    assertThat(field.getHTML()).isNull();
    assertThat(field.getAtom()).isNull();
    assertThat(field.getDate()).isNull();
    assertThat(field.getGeoPoint()).isNull();
    assertThat(field.getUntokenizedPrefix()).isNull();
    assertThat(field.getTokenizedPrefix()).isNull();
    assertThat(field.getVector()).isEmpty();
    assertThat(field.getLocale()).isNull();
  }

  @Test
  public void testBuilder_validLanguageAndHtml() {
    Locale locale = new Locale("pl", "PL");
    Field field = newField().setHTML("value").setLocale(locale).build();
    assertThat(field.getName()).isEqualTo("name");
    assertThat(field.getHTML()).isEqualTo("value");
    assertThat(field.getText()).isEqualTo(null);
    assertThat(field.getLocale()).isEqualTo(locale);
  }

  @Test
  public void testGetType() throws ParseException {
    assertThat(newField().setText("text").build().getType()).isEqualTo(Field.FieldType.TEXT);
    assertThat(newField().setHTML("html").build().getType()).isEqualTo(Field.FieldType.HTML);
    assertThat(newField().setAtom("atom").build().getType()).isEqualTo(Field.FieldType.ATOM);
    DateFormat dateFormat = DateTestHelper.getDateFormat();
    Date date = dateFormat.parse("2011-01-01");
    assertThat(newField().setDate(date).build().getType()).isEqualTo(Field.FieldType.DATE);
    assertThat(newField().setGeoPoint(new GeoPoint(-33.84, 151.26)).build().getType())
        .isEqualTo(Field.FieldType.GEO_POINT);
    assertThat(newField().setUntokenizedPrefix("prefix").build().getType())
        .isEqualTo(Field.FieldType.UNTOKENIZED_PREFIX);
    assertThat(newField().setTokenizedPrefix("prefix").build().getType())
        .isEqualTo(Field.FieldType.TOKENIZED_PREFIX);
    assertThat(newField().setVector(Arrays.asList(1.0, 2.0)).build().getType())
        .isEqualTo(Field.FieldType.VECTOR);
  }

  @Test
  public void testAtMostOneValue() throws ParseException {
    DateFormat dateFormat = DateTestHelper.getDateFormat();
    Date date = dateFormat.parse("2011-01-01");

    assertThrows(
        IllegalArgumentException.class, () -> Field.newBuilder().setText("text").setHTML("<html>"));
    assertThrows(
        IllegalArgumentException.class, () -> Field.newBuilder().setHTML("<html>").setAtom("atom"));
    assertThrows(
        IllegalArgumentException.class, () -> Field.newBuilder().setDate(date).setHTML("<html>"));
    assertThrows(
        IllegalArgumentException.class, () -> Field.newBuilder().setText("text").setDate(date));
    assertThrows(
        IllegalArgumentException.class, () -> Field.newBuilder().setDate(date).setText("text"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setDate(date).setGeoPoint(new GeoPoint(-33.84, 151.26)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setUntokenizedPrefix("prefix").setText("text"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setUntokenizedPrefix("prefix").setDate(date));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setUntokenizedPrefix("prefix").setTokenizedPrefix("prefix"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setTokenizedPrefix("prefix").setText("text"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setTokenizedPrefix("prefix").setDate(date));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setVector(Arrays.asList(1.0)).setDate(date));
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setText("text").setVector(Arrays.asList(1.0)));
  }

  @Test
  public void testBuilder_protocolBufferHTML() {
    Locale locale = new Locale("pl", "PL");
    DocumentPb.Field pb =
        DocumentPb.Field.newBuilder()
            .setName("name")
            .setValue(
                FieldValue.newBuilder()
                    .setStringValue("value")
                    .setType(FieldValue.ContentType.HTML)
                    .setLanguage(locale.toString()))
            .build();
    Field field = Field.newBuilder(pb).build();
    assertThat(field.getName()).isEqualTo("name");
    assertThat(field.getHTML()).isEqualTo("value");
    assertThat(field.getLocale()).isEqualTo(locale);
  }

  @Test
  public void testBuilder_protocolBufferVectorField() {
    Locale locale = new Locale("pl", "PL");
    DocumentPb.Field pb =
        DocumentPb.Field.newBuilder()
            .setName("vector")
            .setValue(
                FieldValue.newBuilder()
                    .addVectorValue(1.0)
                    .addVectorValue(2.0)
                    .addVectorValue(3.0)
                    .setType(FieldValue.ContentType.VECTOR)
                    .setLanguage(locale.toString()))
            .build();
    Field field = Field.newBuilder(pb).build();
    assertThat(field.getName()).isEqualTo("vector");
    assertThat(field.getVector()).containsExactly(1.0, 2.0, 3.0).inOrder();
    assertThat(field.getType()).isEqualTo(Field.FieldType.VECTOR);
    assertThat(field.getLocale()).isEqualTo(locale);
  }

  @Test
  public void testBuilder_protocolBufferUntokenizedPrefix() {
    Locale locale = new Locale("pl", "PL");
    DocumentPb.Field pb =
        DocumentPb.Field.newBuilder()
            .setName("uprefix")
            .setValue(
                FieldValue.newBuilder()
                    .setStringValue("uprefix_value")
                    .setType(FieldValue.ContentType.UNTOKENIZED_PREFIX)
                    .setLanguage(locale.toString()))
            .build();
    Field field = Field.newBuilder(pb).build();
    assertThat(field.getName()).isEqualTo("uprefix");
    assertThat(field.getUntokenizedPrefix()).isEqualTo("uprefix_value");
    assertThat(field.getType()).isEqualTo(Field.FieldType.UNTOKENIZED_PREFIX);
    assertThat(field.getLocale()).isEqualTo(locale);
  }

  @Test
  public void testBuilder_protocolBufferTokenizedPrefix() {
    Locale locale = new Locale("pl", "PL");
    DocumentPb.Field pb =
        DocumentPb.Field.newBuilder()
            .setName("tprefix")
            .setValue(
                FieldValue.newBuilder()
                    .setStringValue("tprefix_value")
                    .setType(FieldValue.ContentType.TOKENIZED_PREFIX)
                    .setLanguage(locale.toString()))
            .build();
    Field field = Field.newBuilder(pb).build();
    assertThat(field.getName()).isEqualTo("tprefix");
    assertThat(field.getTokenizedPrefix()).isEqualTo("tprefix_value");
    assertThat(field.getType()).isEqualTo(Field.FieldType.TOKENIZED_PREFIX);
    assertThat(field.getLocale()).isEqualTo(locale);
  }

  private DocumentPb.Field newFieldPB(FieldValue.Builder builder) {
    return DocumentPb.Field.newBuilder().setName("name").setValue(builder).build();
  }

  @Test
  public void testBuilder_protocolBufferText() {
    DocumentPb.Field pb =
        newFieldPB(
            FieldValue.newBuilder().setStringValue("value").setType(FieldValue.ContentType.TEXT));
    Field field = Field.newBuilder(pb).build();
    assertThat(field.getName()).isEqualTo("name");
    assertThat(field.getText()).isEqualTo("value");

    pb =
        newFieldPB(
            FieldValue.newBuilder().setStringValue("value").setType(FieldValue.ContentType.HTML));
    field = Field.newBuilder(pb).build();
    assertThat(field.getHTML()).isEqualTo("value");

    pb =
        newFieldPB(
            FieldValue.newBuilder().setStringValue("value").setType(FieldValue.ContentType.ATOM));
    field = Field.newBuilder(pb).build();
    assertThat(field.getAtom()).isEqualTo("value");

    pb =
        newFieldPB(
            FieldValue.newBuilder()
                .setStringValue("uprefix_value")
                .setType(FieldValue.ContentType.UNTOKENIZED_PREFIX));
    field = Field.newBuilder(pb).build();
    assertThat(field.getUntokenizedPrefix()).isEqualTo("uprefix_value");

    pb =
        newFieldPB(
            FieldValue.newBuilder()
                .setStringValue("tprefix_value")
                .setType(FieldValue.ContentType.TOKENIZED_PREFIX));
    field = Field.newBuilder(pb).build();
    assertThat(field.getTokenizedPrefix()).isEqualTo("tprefix_value");

    pb =
        newFieldPB(
            FieldValue.newBuilder()
                .setStringValue("2011-01-01")
                .setType(FieldValue.ContentType.DATE));
    field = Field.newBuilder(pb).build();
    Calendar cal = DateTestHelper.getCalendar();
    cal.setTime(field.getDate());
    assertThat(cal.get(Calendar.YEAR)).isEqualTo(2011);
    assertThat(cal.get(Calendar.MONTH)).isEqualTo(0);
    assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(1);
    assertThat(cal.get(Calendar.HOUR)).isEqualTo(0);
    assertThat(cal.get(Calendar.SECOND)).isEqualTo(0);
    assertThat(cal.get(Calendar.MILLISECOND)).isEqualTo(0);

    // wrong format for date, includes time component.
    DocumentPb.Field pb2 =
        newFieldPB(
            FieldValue.newBuilder()
                .setStringValue("2011-01-01 13:56")
                .setType(FieldValue.ContentType.DATE));
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> Field.newBuilder(pb2).build());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Failed to parse date string \"2011-01-01 13:56\"");
    pb =
        newFieldPB(
            FieldValue.newBuilder()
                .setStringValue(String.format("%f", 123.456))
                .setType(FieldValue.ContentType.NUMBER));
    field = Field.newBuilder(pb).build();
    assertThat(field.getNumber()).isEqualTo(123.456);
    assertThat(field.getType()).isEqualTo(Field.FieldType.NUMBER);

    DocumentPb.Field pb3 =
        newFieldPB(
            FieldValue.newBuilder()
                .setStringValue("not a number")
                .setType(FieldValue.ContentType.NUMBER));
    assertThrows(SearchException.class, () -> Field.newBuilder(pb3).build());

    DocumentPb.FieldValue.Geo geoPtPb =
        DocumentPb.FieldValue.Geo.newBuilder().setLat(-33.84).setLng(151.26).build();
    pb = newFieldPB(FieldValue.newBuilder().setGeo(geoPtPb).setType(FieldValue.ContentType.GEO));
    field = Field.newBuilder(pb).build();
    GeoPoint geoPoint = field.getGeoPoint();
    assertThat(geoPoint.getLatitude()).isEqualTo(-33.84);
    assertThat(geoPoint.getLongitude()).isEqualTo(151.26);
    assertThat(field.getType()).isEqualTo(Field.FieldType.GEO_POINT);
  }

  @Test
  public void testCopyToProtocolBuffer() throws ParseException {
    Locale locale = new Locale("pl", "PL");
    DocumentPb.Field pb =
        newField().setText("value").setLocale(locale).build().copyToProtocolBuffer();
    assertThat(pb.getName()).isEqualTo("name");
    FieldValue value = pb.getValue();
    assertThat(value.getStringValue()).isEqualTo("value");
    assertThat(value.getLanguage()).isEqualTo(locale.toString());
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.TEXT);

    value = newField().setHTML("<html>").build().copyToProtocolBuffer().getValue();
    assertThat(value.getStringValue()).isEqualTo("<html>");
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.HTML);

    value = newField().setAtom("atom").build().copyToProtocolBuffer().getValue();
    assertThat(value.getStringValue()).isEqualTo("atom");
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.ATOM);

    value = newField().setUntokenizedPrefix("uprefix").build().copyToProtocolBuffer().getValue();
    assertThat(value.getStringValue()).isEqualTo("uprefix");
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.UNTOKENIZED_PREFIX);

    value = newField().setTokenizedPrefix("tprefix").build().copyToProtocolBuffer().getValue();
    assertThat(value.getStringValue()).isEqualTo("tprefix");
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.TOKENIZED_PREFIX);

    value =
        newField()
            .setVector(Arrays.asList(1.0, 2.0, 3.0))
            .build()
            .copyToProtocolBuffer()
            .getValue();
    assertThat(value.getVectorValueList()).containsExactly(1.0, 2.0, 3.0).inOrder();
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.VECTOR);

    DateFormat dateFormat = DateTestHelper.getDateFormat();
    Date date = dateFormat.parse("2011-01-01");
    value = newField().setDate(date).build().copyToProtocolBuffer().getValue();
    assertThat(value.getStringValue()).isEqualTo("" + date.getTime());
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.DATE);

    value =
        newField()
            .setGeoPoint(new GeoPoint(-33.84, 151.26))
            .build()
            .copyToProtocolBuffer()
            .getValue();
    DocumentPb.FieldValue.Geo geoPt = value.getGeo();
    assertThat(geoPt.getLat()).isEqualTo(-33.84);
    assertThat(geoPt.getLng()).isEqualTo(151.26);
    assertThat(value.getType()).isEqualTo(FieldValue.ContentType.GEO);
  }

  @Test
  public void testEquals() {
    Field field1 = Field.newBuilder().setName("name1").setText("value").build();
    Field field2 = Field.newBuilder().setName("name2").setText("value").build();
    Field field3 = Field.newBuilder().setName("name1").setText("value2").build();
    new EqualsTester().addEqualityGroup(field1, field3).addEqualityGroup(field2).testEquals();
    HashSet<Field> fields = new HashSet<>();
    fields.add(field1);
    fields.add(field2);
    fields.add(field3);
    assertThat(fields).hasSize(2);
    assertThat(fields).contains(field1);
    assertThat(fields).contains(field2);
    assertThat(fields).contains(field3);
  }

  private static Date getDate(int year, int month, int date, int hour, int min, int sec, int ms) {
    Calendar cal = DateTestHelper.getCalendar();
    return getDate(cal, year, month, date, hour, min, sec, ms);
  }

  private static Date getDate(
      Calendar cal, int year, int month, int date, int hour, int min, int sec, int ms) {
    cal.set(year, month, date, hour, min, sec);
    cal.set(Calendar.MILLISECOND, ms);
    return cal.getTime();
  }

  @Test
  public void testMaxDate() {
    int days = DateTestHelper.getDaysSinceEpoch(SearchApiLimits.MAXIMUM_DATE_VALUE);
    assertThat(days).isEqualTo(Integer.MAX_VALUE);
    FieldChecker.checkNumber(Double.valueOf(days));
    Field when =
        Field.newBuilder().setName("when").setDate(SearchApiLimits.MAXIMUM_DATE_VALUE).build();
    assertThat(when.getDate()).isEqualTo(SearchApiLimits.MAXIMUM_DATE_VALUE);

    assertThat(
            Days.daysBetween(
                    DateTestHelper.getDateTimeUTC(0L),
                    DateTestHelper.getDateTimeUTC(SearchApiLimits.MAXIMUM_DATE_VALUE))
                .getDays())
        .isEqualTo(Integer.MAX_VALUE);

    MutableDateTime afterMax =
        DateTestHelper.getMutableDateTimeUTC(SearchApiLimits.MAXIMUM_DATE_VALUE);
    afterMax.addDays(1);
    assertThat(
            Days.daysBetween(
                    DateTestHelper.getDateTimeUTC(SearchApiLimits.MAXIMUM_DATE_VALUE), afterMax)
                .getDays())
        .isEqualTo(1);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> Field.newBuilder().setName("when").setDate(afterMax.toDate()));
    String message =
        String.format(
            "date %s must be before %s",
            DateUtil.formatDateTime(afterMax.toDate()),
            DateUtil.formatDateTime(SearchApiLimits.MAXIMUM_DATE_VALUE));
    assertThat(e).hasMessageThat().isEqualTo(message);
  }

  @Test
  public void testMinDate() {
    int days = DateTestHelper.getDaysSinceEpoch(SearchApiLimits.MINIMUM_DATE_VALUE);
    assertThat(days).isEqualTo(Integer.MIN_VALUE);
    Field when =
        Field.newBuilder().setName("when").setDate(SearchApiLimits.MINIMUM_DATE_VALUE).build();
    assertThat(when.getDate()).isEqualTo(SearchApiLimits.MINIMUM_DATE_VALUE);

    assertThat(
            Days.daysBetween(
                    DateTestHelper.getDateTimeUTC(0L),
                    DateTestHelper.getDateTimeUTC(SearchApiLimits.MINIMUM_DATE_VALUE))
                .getDays())
        .isEqualTo(Integer.MIN_VALUE);

    MutableDateTime beforeMin =
        DateTestHelper.getMutableDateTimeUTC(SearchApiLimits.MINIMUM_DATE_VALUE);
    beforeMin.addDays(-1);
    assertThat(
            Days.daysBetween(
                    beforeMin, DateTestHelper.getDateTimeUTC(SearchApiLimits.MINIMUM_DATE_VALUE))
                .getDays())
        .isEqualTo(1);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> Field.newBuilder().setName("when").setDate(beforeMin.toDate()));
    String message =
        String.format(
            "date %s must be after %s",
            DateUtil.formatDateTime(beforeMin.toDate()),
            DateUtil.formatDateTime(SearchApiLimits.MINIMUM_DATE_VALUE));
    assertThat(e).hasMessageThat().isEqualTo(message);
  }

  @Test
  public void testToString() {
    assertThat(Field.newBuilder().setName("name").build().toString())
        .isEqualTo("Field(name=name, value=null, type=null)");

    assertThat(
            Field.newBuilder()
                .setName("name")
                .setText("text")
                .setLocale(Locale.US)
                .build()
                .toString())
        .isEqualTo("Field(name=name, value=text, type=TEXT, locale=en_US)");

    assertThat(Field.newBuilder().setName("name").setText("text").build().toString())
        .isEqualTo("Field(name=name, value=text, type=TEXT)");

    Date date = getDate(2011, 11, 13, 0, 0, 0, 0);
    Field dateField = Field.newBuilder().setName("date").setDate(date).build();
    assertThat(dateField.toString())
        .isEqualTo("Field(name=date, value=2011-12-13T00:00:00.000Z, type=DATE)");

    assertThat(
            Field.newBuilder()
                .setName("name")
                .setGeoPoint(new GeoPoint(-33.84, 151.26))
                .build()
                .toString())
        .isEqualTo(
            "Field(name=name, value=GeoPoint(latitude=-33.840000, longitude=151.260000),"
                + " type=GEO_POINT)");

    assertThat(Field.newBuilder().setName("name").setHTML("<html>here</html>").build().toString())
        .isEqualTo("Field(name=name, value=<html>here</html>, type=HTML)");

    assertThat(Field.newBuilder().setName("name").setAtom("donottokenize").build().toString())
        .isEqualTo("Field(name=name, value=donottokenize, type=ATOM)");

    assertThat(
            Field.newBuilder()
                .setName("name")
                .setUntokenizedPrefix("untokenized_prefix")
                .build()
                .toString())
        .isEqualTo("Field(name=name, value=untokenized_prefix, type=UNTOKENIZED_PREFIX)");

    assertThat(
            Field.newBuilder()
                .setName("name")
                .setTokenizedPrefix("tokenized_prefix")
                .build()
                .toString())
        .isEqualTo("Field(name=name, value=tokenized_prefix, type=TOKENIZED_PREFIX)");

    assertThat(Field.newBuilder().setName("name").setNumber(123.456).build().toString())
        .isEqualTo("Field(name=name, value=123.456, type=NUMBER)");

    assertThat(
            Field.newBuilder()
                .setName("name")
                .setVector(Arrays.asList(1.0, 2.0, 3.0))
                .build()
                .toString())
        .isEqualTo("Field(name=name, value=[1.0, 2.0, 3.0], type=VECTOR)");
  }

  @Test
  public void testDoubleIsParsable() {
    double value = 1000000;
    Field numberField = Field.newBuilder().setName("number").setNumber(value).build();
    DocumentPb.Field pbField = numberField.copyToProtocolBuffer();
    assertThat(Double.parseDouble(pbField.getValue().getStringValue())).isEqualTo(value);
  }

  @Test
  public void testValidDateFieldChecksOut() {
    Field when =
        Field.newBuilder().setName("when").setDate(getDate(2011, 11, 9, 0, 0, 0, 0)).build();
    FieldChecker.checkValid(when.copyToProtocolBuffer());
    // No exception means success; we do not need any assertions.
  }

  @Test
  public void testMaxMinDates() {
    Field when =
        Field.newBuilder().setName("when").setDate(SearchApiLimits.MAXIMUM_DATE_VALUE).build();
    DocumentPb.Field whenPb = when.copyToProtocolBuffer();
    FieldChecker.checkValid(whenPb);
    assertThat(whenPb.getValue().getType()).isEqualTo(FieldValue.ContentType.DATE);
    String dateString = whenPb.getValue().getStringValue();
    assertThat(dateString).isEqualTo("185542587187199999");
    assertThat(new Date(Long.parseLong(dateString))).isEqualTo(SearchApiLimits.MAXIMUM_DATE_VALUE);

    when = Field.newBuilder().setName("when").setDate(SearchApiLimits.MINIMUM_DATE_VALUE).build();
    whenPb = when.copyToProtocolBuffer();
    FieldChecker.checkValid(whenPb);
    assertThat(whenPb.getValue().getType()).isEqualTo(FieldValue.ContentType.DATE);
    dateString = whenPb.getValue().getStringValue();
    assertThat(dateString).isEqualTo("-185542587187200000");
    assertThat(new Date(Long.parseLong(dateString))).isEqualTo(SearchApiLimits.MINIMUM_DATE_VALUE);
  }

  @Test
  public void testNullFieldValues() {
    // Text-type fields should not throw an exception on null
    Field.newBuilder().setName("test").setText(null).build().copyToProtocolBuffer();
    Field.newBuilder().setName("test").setAtom(null).build().copyToProtocolBuffer();
    Field.newBuilder().setName("test").setHTML(null).build().copyToProtocolBuffer();
    Field.newBuilder().setName("test").setUntokenizedPrefix(null).build().copyToProtocolBuffer();
    Field.newBuilder().setName("test").setTokenizedPrefix(null).build().copyToProtocolBuffer();

    // All others should fail when given null. Numbers cannot be set to null because setNumber()
    // takes a double, not a Double, so calling it with null won't compile.
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setName("test").setDate(null).build().copyToProtocolBuffer());

    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setName("test").setGeoPoint(null).build().copyToProtocolBuffer());
    assertThrows(
        IllegalArgumentException.class,
        () -> Field.newBuilder().setName("test").setVector(null).build().copyToProtocolBuffer());
  }
}
